package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class OperationHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        Map<String, String> query = HttpUtil.parseQueryParams(exchange);
        String fromDate = query.get("fromDate");
        if (fromDate == null || fromDate.trim().isEmpty()) fromDate = query.get("from");
        String toDate = query.get("toDate");
        if (toDate == null || toDate.trim().isEmpty()) toDate = query.get("to");
        String category = query.get("category");
        String warehouseId = query.get("warehouseId");
        String opType = query.get("type");

        StringBuilder sql = new StringBuilder("SELECT * FROM operation_history WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND op_date >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND op_date <= ? ");
            params.add(toDate.trim());
        }
        if (category != null && !category.trim().isEmpty() && !category.equalsIgnoreCase("all")) {
            if ("shop".equalsIgnoreCase(category)) {
                sql.append("AND (category = 'request' OR category = 'shop') ");
            } else {
                sql.append("AND LOWER(category) = LOWER(?) ");
                params.add(category.trim());
            }
        }
        if (warehouseId != null && !warehouseId.trim().isEmpty() && !warehouseId.equalsIgnoreCase("all")) {
            sql.append("AND (UPPER(entity_id) = UPPER(?) OR details LIKE ?) ");
            params.add(warehouseId.trim());
            params.add("%" + warehouseId.trim() + "%");
        }
        if (opType != null && !opType.trim().isEmpty() && !opType.equalsIgnoreCase("all")) {
            sql.append("AND LOWER(operation_type) = LOWER(?) ");
            params.add(opType.trim());
        }

        sql.append("ORDER BY op_date DESC, op_time DESC, id DESC;");

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

                    String prev = rs.getString("prev_status");
                    String next = rs.getString("new_status");

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"opId\":\"").append(JsonUtil.escape(rs.getString("op_id"))).append("\",")
                        .append("\"requestId\":\"").append(JsonUtil.escape(rs.getString("request_id"))).append("\",")
                        .append("\"entityId\":\"").append(JsonUtil.escape(rs.getString("entity_id"))).append("\",")
                        .append("\"category\":\"").append(JsonUtil.escape(rs.getString("category"))).append("\",")
                        .append("\"operationType\":\"").append(JsonUtil.escape(rs.getString("operation_type"))).append("\",")
                        .append("\"prevStatus\":\"").append(JsonUtil.escape(prev != null ? prev : "")).append("\",")
                        .append("\"newStatus\":\"").append(JsonUtil.escape(next != null ? next : "")).append("\",")
                        .append("\"prevValue\":\"").append(JsonUtil.escape(prev != null ? prev : "")).append("\",")
                        .append("\"newValue\":\"").append(JsonUtil.escape(next != null ? next : "")).append("\",")
                        .append("\"date\":\"").append(JsonUtil.escape(rs.getString("op_date"))).append("\",")
                        .append("\"time\":\"").append(JsonUtil.escape(rs.getString("op_time"))).append("\",")
                        .append("\"updatedBy\":\"").append(JsonUtil.escape(rs.getString("updated_by"))).append("\",")
                        .append("\"details\":\"").append(JsonUtil.escape(rs.getString("details"))).append("\"")
                        .append("}");
                }
                json.append("]");

                HttpUtil.sendJsonResponse(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("[OperationHandler] Error: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching operation history.");
        }
    }
}

