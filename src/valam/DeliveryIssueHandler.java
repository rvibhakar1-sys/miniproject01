package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class DeliveryIssueHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String[] parts = path.split("/");

        if ("GET".equalsIgnoreCase(method)) {
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
                getDeliveryIssues(exchange);
            } else if (parts.length == 4) {
                getIssueById(exchange, parts[3]);
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid delivery issue endpoint");
            }
        } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (parts.length >= 5 && "resolve".equalsIgnoreCase(parts[4])) {
                resolveIssue(exchange, parts[3]);
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid action for delivery issue");
            }
        } else {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
        }
    }

    private void getDeliveryIssues(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQueryParams(exchange);
        String shopId = query.get("shopId");
        String warehouseId = query.get("warehouseId");
        String status = query.get("status");
        String reqId = query.get("requestId");

        StringBuilder sql = new StringBuilder(
            "SELECT di.*, s.name as shop_name, w.name as warehouse_name " +
            "FROM delivery_issues di " +
            "LEFT JOIN shops s ON di.shop_id = s.id " +
            "LEFT JOIN warehouses w ON di.warehouse_id = w.id WHERE 1=1 "
        );
        List<Object> params = new ArrayList<>();

        if (shopId != null && !shopId.trim().isEmpty() && !shopId.equalsIgnoreCase("all")) {
            sql.append("AND UPPER(di.shop_id) = UPPER(?) ");
            params.add(shopId.trim());
        }
        if (warehouseId != null && !warehouseId.trim().isEmpty() && !warehouseId.equalsIgnoreCase("all")) {
            sql.append("AND (UPPER(di.warehouse_id) = UPPER(?) OR UPPER(w.name) LIKE UPPER(?)) ");
            params.add(warehouseId.trim());
            params.add("%" + warehouseId.trim() + "%");
        }
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("all")) {
            sql.append("AND LOWER(di.status) = LOWER(?) ");
            params.add(status.trim());
        }
        if (reqId != null && !reqId.trim().isEmpty()) {
            sql.append("AND UPPER(di.request_id) = UPPER(?) ");
            params.add(reqId.trim());
        }

        sql.append("ORDER BY di.id DESC;");

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql.toString())) {

            for (int i = 0; i < params.size(); i++) {
                ps.setObject(i + 1, params.get(i));
            }

            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    appendIssueJson(json, rs);
                }
                json.append("]");

                HttpUtil.sendJsonResponse(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("[DeliveryIssueHandler] Error fetching delivery issues: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching delivery issues.");
        }
    }

    private void getIssueById(HttpExchange exchange, String issueIdentifier) throws IOException {
        String sql = "SELECT di.*, s.name as shop_name, w.name as warehouse_name " +
                     "FROM delivery_issues di " +
                     "LEFT JOIN shops s ON di.shop_id = s.id " +
                     "LEFT JOIN warehouses w ON di.warehouse_id = w.id " +
                     "WHERE UPPER(di.issue_id) = UPPER(?) OR UPPER(di.request_id) = UPPER(?);";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, issueIdentifier);
            ps.setString(2, issueIdentifier);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder json = new StringBuilder();
                    appendIssueJson(json, rs);
                    HttpUtil.sendJsonResponse(exchange, 200, json.toString());
                } else {
                    HttpUtil.sendError(exchange, 404, "Delivery issue not found: " + issueIdentifier);
                }
            }
        } catch (SQLException e) {
            System.err.println("[DeliveryIssueHandler] Error fetching issue: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching issue.");
        }
    }

    private void resolveIssue(HttpExchange exchange, String issueIdentifier) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String resolvedBy = data.get("resolvedBy");
        if (resolvedBy == null || resolvedBy.trim().isEmpty()) resolvedBy = "Warehouse Staff";

        String resolutionNotes = data.get("resolutionNotes");
        if (resolutionNotes == null || resolutionNotes.trim().isEmpty()) {
            resolutionNotes = "Delivery discrepancy verified and rectified by warehouse. Consignment is ready for shop receipt confirmation.";
        }

        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch issue details from SQLite
                String issueSql = "SELECT id, issue_id, request_id, shop_id, warehouse_id, issue_type, status FROM delivery_issues " +
                                  "WHERE (UPPER(issue_id) = UPPER(?) OR UPPER(request_id) = UPPER(?)) AND status = 'Open' ORDER BY id DESC LIMIT 1;";
                String actualIssueId = null;
                String requestId = null;
                String shopId = null;
                String warehouseId = null;
                String issueType = null;

                try (PreparedStatement ps = conn.prepareStatement(issueSql)) {
                    ps.setString(1, issueIdentifier);
                    ps.setString(2, issueIdentifier);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "No active open delivery issue found for: " + issueIdentifier);
                            return;
                        }
                        actualIssueId = rs.getString("issue_id");
                        requestId = rs.getString("request_id");
                        shopId = rs.getString("shop_id");
                        warehouseId = rs.getString("warehouse_id");
                        issueType = rs.getString("issue_type");
                    }
                }

                // 2. Mark issue as Resolved in SQLite
                String updateIssueSql = "UPDATE delivery_issues SET status = 'Resolved', resolved_by = ?, resolution_notes = ?, resolved_date = ?, resolved_time = ? WHERE issue_id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(updateIssueSql)) {
                    ps.setString(1, resolvedBy);
                    ps.setString(2, resolutionNotes);
                    ps.setString(3, curDate);
                    ps.setString(4, curTime);
                    ps.setString(5, actualIssueId);
                    ps.executeUpdate();
                }

                // 3. Update Request status to 'Awaiting Receipt'
                String updateReqSql = "UPDATE requests SET status = 'Awaiting Receipt', receipt_status = 'Awaiting Receipt', updated_date = ?, updated_time = ? WHERE UPPER(id) = UPPER(?);";
                try (PreparedStatement ps = conn.prepareStatement(updateReqSql)) {
                    ps.setString(1, curDate);
                    ps.setString(2, curTime);
                    ps.setString(3, requestId);
                    ps.executeUpdate();
                }

                // 4. Add Timeline Event
                String tlSql = "INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) " +
                               "VALUES (?, 'Delivery Issue Resolved', ?, ?, 'Awaiting Receipt', ?, ?, 5);";
                try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
                    ps.setString(1, requestId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, resolvedBy);
                    ps.setString(5, "Warehouse resolved reported issue [" + issueType + "]: " + resolutionNotes + ". Delivery is ready for shop confirmation.");
                    ps.executeUpdate();
                }

                // 5. Add Operation History Audit Log
                String opId = RequestHandler.getNextOpId(conn);
                String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) " +
                               "VALUES (?, ?, ?, 'delivery', 'Issue Resolved', 'Delivery Issue', 'Awaiting Receipt', ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                    ps.setString(1, opId);
                    ps.setString(2, requestId);
                    ps.setString(3, warehouseId);
                    ps.setString(4, curDate);
                    ps.setString(5, curTime);
                    ps.setString(6, resolvedBy);
                    ps.setString(7, "Resolved delivery issue " + actualIssueId + " for " + requestId + " (" + shopId + "): " + resolutionNotes);
                    ps.executeUpdate();
                }

                conn.commit();

                // Return success
                StringBuilder resJson = new StringBuilder("{");
                resJson.append("\"success\":true,")
                       .append("\"issueId\":\"").append(JsonUtil.escape(actualIssueId)).append("\",")
                       .append("\"requestId\":\"").append(JsonUtil.escape(requestId)).append("\",")
                       .append("\"status\":\"Resolved\",")
                       .append("\"requestStatus\":\"Awaiting Receipt\",")
                       .append("\"message\":\"Delivery issue resolved successfully. Shop can now confirm receipt.\"")
                       .append("}");
                HttpUtil.sendJsonResponse(exchange, 200, resJson.toString());

            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[DeliveryIssueHandler] Error resolving issue: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error during issue resolution: " + e.getMessage());
        }
    }

    private void appendIssueJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{")
            .append("\"id\":").append(rs.getInt("id")).append(",")
            .append("\"issueId\":\"").append(JsonUtil.escape(rs.getString("issue_id"))).append("\",")
            .append("\"requestId\":\"").append(JsonUtil.escape(rs.getString("request_id"))).append("\",")
            .append("\"shopId\":\"").append(JsonUtil.escape(rs.getString("shop_id"))).append("\",")
            .append("\"shopName\":\"").append(JsonUtil.escape(rs.getString("shop_name") != null ? rs.getString("shop_name") : "")).append("\",")
            .append("\"warehouseId\":\"").append(JsonUtil.escape(rs.getString("warehouse_id"))).append("\",")
            .append("\"warehouseName\":\"").append(JsonUtil.escape(rs.getString("warehouse_name") != null ? rs.getString("warehouse_name") : "")).append("\",")
            .append("\"issueType\":\"").append(JsonUtil.escape(rs.getString("issue_type"))).append("\",")
            .append("\"description\":\"").append(JsonUtil.escape(rs.getString("description") != null ? rs.getString("description") : "")).append("\",")
            .append("\"reportedDate\":\"").append(JsonUtil.escape(rs.getString("reported_date"))).append("\",")
            .append("\"reportedTime\":\"").append(JsonUtil.escape(rs.getString("reported_time"))).append("\",")
            .append("\"reportedBy\":\"").append(JsonUtil.escape(rs.getString("reported_by"))).append("\",")
            .append("\"status\":\"").append(JsonUtil.escape(rs.getString("status"))).append("\",")
            .append("\"resolvedBy\":").append(rs.getString("resolved_by") != null ? "\"" + JsonUtil.escape(rs.getString("resolved_by")) + "\"" : "null").append(",")
            .append("\"resolutionNotes\":").append(rs.getString("resolution_notes") != null ? "\"" + JsonUtil.escape(rs.getString("resolution_notes")) + "\"" : "null").append(",")
            .append("\"resolvedDate\":").append(rs.getString("resolved_date") != null ? "\"" + JsonUtil.escape(rs.getString("resolved_date")) + "\"" : "null").append(",")
            .append("\"resolvedTime\":").append(rs.getString("resolved_time") != null ? "\"" + JsonUtil.escape(rs.getString("resolved_time")) + "\"" : "null")
            .append("}");
    }
}

