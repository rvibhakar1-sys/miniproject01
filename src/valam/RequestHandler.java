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
import java.util.*;

public class RequestHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        String[] parts = path.split("/");

        if ("GET".equalsIgnoreCase(method)) {
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
                getRequests(exchange);
            } else if (parts.length == 4) {
                getRequestById(exchange, parts[3]);
            } else if (parts.length == 5 && "timeline".equalsIgnoreCase(parts[4])) {
                getRequestTimeline(exchange, parts[3]);
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid request endpoint");
            }
        } else if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
                createRequest(exchange);
            } else if (parts.length >= 5) {
                String reqId = parts[3];
                String action = parts[4].toLowerCase();
                switch (action) {
                    case "process":
                    case "accept":
                        processRequest(exchange, reqId);
                        break;
                    case "assign-vehicle":
                    case "vehicle":
                        assignVehicle(exchange, reqId);
                        break;
                    case "in-transit":
                        markInTransit(exchange, reqId);
                        break;
                    case "deliver":
                    case "delivered":
                        markDelivered(exchange, reqId);
                        break;
                    default:
                        HttpUtil.sendError(exchange, 404, "Unknown action: " + action);
                }
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid request action");
            }
        } else {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
        }
    }

    private void getRequests(HttpExchange exchange) throws IOException {
        Map<String, String> query = HttpUtil.parseQueryParams(exchange);
        String shopId = query.get("shopId");
        String warehouseId = query.get("warehouseId");
        String status = query.get("status");
        String fromDate = query.get("fromDate");
        String toDate = query.get("toDate");

        StringBuilder sql = new StringBuilder("SELECT r.*, s.name as shop_name FROM requests r LEFT JOIN shops s ON r.shop_id = s.id WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (shopId != null && !shopId.trim().isEmpty() && !shopId.equalsIgnoreCase("all")) {
            sql.append("AND UPPER(r.shop_id) = UPPER(?) ");
            params.add(shopId.trim());
        }
        if (warehouseId != null && !warehouseId.trim().isEmpty() && !warehouseId.equalsIgnoreCase("all")) {
            sql.append("AND (UPPER(r.warehouse_id) = UPPER(?) OR UPPER(r.warehouse_name) LIKE UPPER(?)) ");
            params.add(warehouseId.trim());
            params.add("%" + warehouseId.trim() + "%");
        }
        if (status != null && !status.trim().isEmpty() && !status.equalsIgnoreCase("all")) {
            sql.append("AND LOWER(r.status) = LOWER(?) ");
            params.add(status.trim());
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND r.submitted_date >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND r.submitted_date <= ? ");
            params.add(toDate.trim());
        }

        sql.append("ORDER BY r.submitted_date DESC, r.submitted_time DESC, r.id DESC;");

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
                    appendRequestJson(conn, json, rs);
                }
                json.append("]");

                HttpUtil.sendJsonResponse(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error fetching requests: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching requests: " + e.getMessage());
        }
    }

    private void getRequestById(HttpExchange exchange, String reqId) throws IOException {
        String sql = "SELECT r.*, s.name as shop_name FROM requests r LEFT JOIN shops s ON r.shop_id = s.id WHERE UPPER(r.id) = UPPER(?);";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reqId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder json = new StringBuilder();
                    appendRequestJson(conn, json, rs);
                    HttpUtil.sendJsonResponse(exchange, 200, json.toString());
                } else {
                    HttpUtil.sendError(exchange, 404, "Request not found in database: " + reqId);
                }
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error fetching request: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching request: " + e.getMessage());
        }
    }

    private void getRequestTimeline(HttpExchange exchange, String reqId) throws IOException {
        String sql = "SELECT stage_title, event_date, event_time, status, performed_by, details FROM request_timeline WHERE UPPER(request_id) = UPPER(?) ORDER BY stage_order ASC, id ASC;";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, reqId);
            try (ResultSet rs = ps.executeQuery()) {
                StringBuilder json = new StringBuilder("[");
                boolean first = true;
                while (rs.next()) {
                    if (!first) json.append(",");
                    first = false;
                    json.append("{")
                        .append("\"title\":\"").append(JsonUtil.escape(rs.getString("stage_title"))).append("\",")
                        .append("\"date\":\"").append(JsonUtil.escape(rs.getString("event_date"))).append("\",")
                        .append("\"time\":\"").append(JsonUtil.escape(rs.getString("event_time"))).append("\",")
                        .append("\"status\":\"").append(JsonUtil.escape(rs.getString("status"))).append("\",")
                        .append("\"by\":\"").append(JsonUtil.escape(rs.getString("performed_by"))).append("\",")
                        .append("\"details\":\"").append(JsonUtil.escape(rs.getString("details"))).append("\"")
                        .append("}");
                }
                json.append("]");
                HttpUtil.sendJsonResponse(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error fetching timeline: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching timeline: " + e.getMessage());
        }
    }

    private void createRequest(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);

        String shopId = data.get("shopId");
        String warehouseInput = data.get("warehouse");
        String warehouseId = data.get("warehouseId");

        int rice = parseInt(data.get("rice"));
        int wheat = parseInt(data.get("wheat"));
        int sugar = parseInt(data.get("sugar"));
        String urgency = data.get("urgency");
        if (urgency == null || urgency.trim().isEmpty()) urgency = "Normal";

        String submittedBy = data.get("submittedBy");
        if (submittedBy == null || submittedBy.trim().isEmpty()) {
            submittedBy = shopId != null ? shopId : "Shop User";
        }

        // Basic sanity validation
        if (shopId == null || shopId.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Shop ID is required.");
            return;
        }
        if (rice < 0 || wheat < 0 || sugar < 0 || (rice == 0 && wheat == 0 && sugar == 0)) {
            HttpUtil.sendError(exchange, 400, "Please enter at least one valid commodity quantity (greater than 0).");
            return;
        }
        if (rice > 2000 || wheat > 2000 || sugar > 1000) {
            HttpUtil.sendError(exchange, 400, "Requested quantities exceed maximum allowable limits (Rice/Wheat: 2000kg, Sugar: 1000kg).");
            return;
        }

        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Verify Shop actually exists in SQLite database
                String shopName = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM shops WHERE UPPER(id) = UPPER(?);")) {
                    ps.setString(1, shopId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Shop not found in database: " + shopId);
                            return;
                        }
                        shopId = rs.getString("id");
                        shopName = rs.getString("name");
                    }
                }

                // 2. Verify Warehouse actually exists in SQLite database
                String resolvedWhId = null;
                String resolvedWhName = null;
                String whSearch = (warehouseId != null && !warehouseId.trim().isEmpty()) ? warehouseId.trim() : (warehouseInput != null ? warehouseInput.trim() : "");

                try (PreparedStatement ps = conn.prepareStatement("SELECT id, name FROM warehouses WHERE UPPER(id) = UPPER(?) OR UPPER(name) = UPPER(?) OR UPPER(name) LIKE ? LIMIT 1;")) {
                    ps.setString(1, whSearch);
                    ps.setString(2, whSearch);
                    ps.setString(3, "%" + whSearch + "%");
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            resolvedWhId = rs.getString("id");
                            resolvedWhName = rs.getString("name");
                        }
                    }
                }

                if (resolvedWhId == null) {
                    // Fall back to first warehouse in database if none matched
                    try (Statement st = conn.createStatement();
                         ResultSet rs = st.executeQuery("SELECT id, name FROM warehouses ORDER BY id ASC LIMIT 1;")) {
                        if (rs.next()) {
                            resolvedWhId = rs.getString("id");
                            resolvedWhName = rs.getString("name");
                        } else {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "No warehouses found in database.");
                            return;
                        }
                    }
                }

                // 3. Generate sequential next Request ID from SQLite
                String newReqId = getNextRequestId(conn);

                String route = resolvedWhName + " → " + shopName;

                // 4. Insert Request into SQLite
                String insertReqSql = "INSERT INTO requests (id, shop_id, warehouse_id, warehouse_name, rice, wheat, sugar, urgency, status, vehicle, route, submitted_date, submitted_time, updated_date, updated_time) " +
                                      "VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'Pending', 'Not Assigned', ?, ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(insertReqSql)) {
                    ps.setString(1, newReqId);
                    ps.setString(2, shopId);
                    ps.setString(3, resolvedWhId);
                    ps.setString(4, resolvedWhName);
                    ps.setInt(5, rice);
                    ps.setInt(6, wheat);
                    ps.setInt(7, sugar);
                    ps.setString(8, urgency);
                    ps.setString(9, route);
                    ps.setString(10, curDate);
                    ps.setString(11, curTime);
                    ps.setString(12, curDate);
                    ps.setString(13, curTime);
                    ps.executeUpdate();
                }

                // 5. Insert Timeline Stage 1 into SQLite
                String tlSql = "INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) " +
                               "VALUES (?, 'Request Submitted', ?, ?, 'Pending', ?, ?, 1);";
                try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
                    ps.setString(1, newReqId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, submittedBy);
                    ps.setString(5, "Shop " + shopId + " (" + shopName + ") submitted commodity request " + newReqId + ". Rice: " + rice + " kg, Wheat: " + wheat + " kg, Sugar: " + sugar + " kg. Urgency: " + urgency + ".");
                    ps.executeUpdate();
                }

                // 6. Insert Operation History Entry into SQLite
                String opId = getNextOpId(conn);
                String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) " +
                               "VALUES (?, ?, ?, 'request', 'Request Received', '—', 'Pending', ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                    ps.setString(1, opId);
                    ps.setString(2, newReqId);
                    ps.setString(3, shopId);
                    ps.setString(4, curDate);
                    ps.setString(5, curTime);
                    ps.setString(6, submittedBy);
                    ps.setString(7, "Shop " + shopId + " (" + shopName + ") submitted commodity request " + newReqId + ". Rice: " + rice + " kg, Wheat: " + wheat + " kg, Sugar: " + sugar + " kg. Urgency: " + urgency + ". Warehouse: " + resolvedWhName + ".");
                    ps.executeUpdate();
                }

                conn.commit();

                // Return newly created request from SQLite
                getRequestById(exchange, newReqId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error creating request: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error creating request: " + e.getMessage());
        }
    }

    private void processRequest(HttpExchange exchange, String reqId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String updatedBy = data.get("updatedBy");
        if (updatedBy == null || updatedBy.trim().isEmpty()) updatedBy = "Warehouse Staff";

        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch Request from SQLite
                String reqSql = "SELECT shop_id, warehouse_id, warehouse_name, rice, wheat, sugar, status FROM requests WHERE UPPER(id) = UPPER(?);";
                String whId = null, whName = null, shopId = null, currentStatus = null;
                int reqRice = 0, reqWheat = 0, reqSugar = 0;

                try (PreparedStatement ps = conn.prepareStatement(reqSql)) {
                    ps.setString(1, reqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Request not found in database: " + reqId);
                            return;
                        }
                        shopId = rs.getString("shop_id");
                        whId = rs.getString("warehouse_id");
                        whName = rs.getString("warehouse_name");
                        reqRice = rs.getInt("rice");
                        reqWheat = rs.getInt("wheat");
                        reqSugar = rs.getInt("sugar");
                        currentStatus = rs.getString("status");
                    }
                }

                if (!"Pending".equalsIgnoreCase(currentStatus)) {
                    conn.rollback();
                    HttpUtil.sendError(exchange, 400, "Request " + reqId + " cannot be accepted because it is already " + currentStatus + " in the database.");
                    return;
                }

                // 2. Fetch Warehouse stock from SQLite
                String whSql = "SELECT rice_available, wheat_available, sugar_available, rice_reserved, wheat_reserved, sugar_reserved FROM warehouses WHERE id = ?;";
                int availRice = 0, availWheat = 0, availSugar = 0;
                int resRice = 0, resWheat = 0, resSugar = 0;

                try (PreparedStatement ps = conn.prepareStatement(whSql)) {
                    ps.setString(1, whId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Warehouse not found in database: " + whId);
                            return;
                        }
                        availRice = rs.getInt("rice_available");
                        availWheat = rs.getInt("wheat_available");
                        availSugar = rs.getInt("sugar_available");
                        resRice = rs.getInt("rice_reserved");
                        resWheat = rs.getInt("wheat_reserved");
                        resSugar = rs.getInt("sugar_reserved");
                    }
                }

                if (availRice < reqRice || availWheat < reqWheat || availSugar < reqSugar) {
                    conn.rollback();
                    HttpUtil.sendError(exchange, 400, "Insufficient stock at " + whName + " (" + whId + ") in database! Available: Rice " + availRice + "kg, Wheat " + availWheat + "kg, Sugar " + availSugar + "kg.");
                    return;
                }

                // 3. Atomically update warehouse available & reserved stock in SQLite
                int newAvailRice = availRice - reqRice;
                int newAvailWheat = availWheat - reqWheat;
                int newAvailSugar = availSugar - reqSugar;

                int newResRice = resRice + reqRice;
                int newResWheat = resWheat + reqWheat;
                int newResSugar = resSugar + reqSugar;

                String updateWhSql = "UPDATE warehouses SET rice_available = ?, wheat_available = ?, sugar_available = ?, " +
                                     "rice_reserved = ?, wheat_reserved = ?, sugar_reserved = ?, " +
                                     "last_update_date = ?, last_update_time = ?, last_update_by = ?, " +
                                     "last_update_reason = ? WHERE id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(updateWhSql)) {
                    ps.setInt(1, newAvailRice);
                    ps.setInt(2, newAvailWheat);
                    ps.setInt(3, newAvailSugar);
                    ps.setInt(4, newResRice);
                    ps.setInt(5, newResWheat);
                    ps.setInt(6, newResSugar);
                    ps.setString(7, curDate);
                    ps.setString(8, curTime);
                    ps.setString(9, updatedBy);
                    ps.setString(10, "Stock reserved for " + reqId + " (" + shopId + ")");
                    ps.setString(11, whId);
                    ps.executeUpdate();
                }

                // 4. Update Request status to Reserved in SQLite
                String updateReqSql = "UPDATE requests SET status = 'Reserved', processed_date = ?, processed_time = ?, updated_date = ?, updated_time = ? WHERE UPPER(id) = UPPER(?);";
                try (PreparedStatement ps = conn.prepareStatement(updateReqSql)) {
                    ps.setString(1, curDate);
                    ps.setString(2, curTime);
                    ps.setString(3, curDate);
                    ps.setString(4, curTime);
                    ps.setString(5, reqId);
                    ps.executeUpdate();
                }

                // 5. Insert Timeline Stage 2
                String tlSql = "INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) " +
                               "VALUES (?, 'Request Accepted / Reserved', ?, ?, 'Reserved', ?, ?, 2);";
                try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
                    ps.setString(1, reqId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, updatedBy);
                    ps.setString(5, "Warehouse staff accepted request " + reqId + ". Stock successfully reserved: Rice " + reqRice + " kg, Wheat " + reqWheat + " kg, Sugar " + reqSugar + " kg.");
                    ps.executeUpdate();
                }

                // 6. Insert into stock_update_history
                String logSql = "INSERT INTO stock_update_history (update_id, warehouse_id, commodity, previous_stock, quantity_added, quantity_deducted, current_stock, reason, log_date, log_time, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(logSql)) {
                    if (reqRice > 0) insertStockLog(ps, getNextStockUpdateId(conn), whId, "Rice", availRice, 0, reqRice, newAvailRice, "Stock reserved for " + reqId, curDate, curTime, updatedBy);
                    if (reqWheat > 0) insertStockLog(ps, getNextStockUpdateId(conn), whId, "Wheat", availWheat, 0, reqWheat, newAvailWheat, "Stock reserved for " + reqId, curDate, curTime, updatedBy);
                    if (reqSugar > 0) insertStockLog(ps, getNextStockUpdateId(conn), whId, "Sugar", availSugar, 0, reqSugar, newAvailSugar, "Stock reserved for " + reqId, curDate, curTime, updatedBy);
                }

                // 7. Insert Operation History: Request Accepted & Stock Updated
                String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                    String op1 = getNextOpId(conn);
                    ps.setString(1, op1);
                    ps.setString(2, reqId);
                    ps.setString(3, shopId);
                    ps.setString(4, "request");
                    ps.setString(5, "Request Accepted");
                    ps.setString(6, "Pending");
                    ps.setString(7, "Reserved");
                    ps.setString(8, curDate);
                    ps.setString(9, curTime);
                    ps.setString(10, updatedBy);
                    ps.setString(11, "Warehouse staff accepted request " + reqId + " from Shop " + shopId + " at " + whName + ". Status changed from Pending to Reserved.");
                    ps.executeUpdate();

                    String op2 = getNextOpId(conn);
                    ps.setString(1, op2);
                    ps.setString(2, reqId);
                    ps.setString(3, whId);
                    ps.setString(4, "warehouse");
                    ps.setString(5, "Stock Updated");
                    ps.setString(6, "Rice: " + availRice + " kg");
                    ps.setString(7, "Rice: " + newAvailRice + " kg");
                    ps.setString(8, curDate);
                    ps.setString(9, curTime);
                    ps.setString(10, updatedBy);
                    ps.setString(11, "Stock reserved for " + reqId + " at " + whName + " (" + whId + "). Rice: " + availRice + "→" + newAvailRice + " kg (-" + reqRice + "), Wheat: " + availWheat + "→" + newAvailWheat + " kg (-" + reqWheat + "), Sugar: " + availSugar + "→" + newAvailSugar + " kg (-" + reqSugar + ").");
                    ps.executeUpdate();
                }

                conn.commit();
                getRequestById(exchange, reqId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error processing request: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error during request processing: " + e.getMessage());
        }
    }

    private void assignVehicle(HttpExchange exchange, String reqId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String vehicleId = data.get("vehicleId");
        String updatedBy = data.get("updatedBy");
        if (updatedBy == null || updatedBy.trim().isEmpty()) updatedBy = "Warehouse Staff";

        if (vehicleId == null || vehicleId.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Vehicle ID is required.");
            return;
        }

        vehicleId = vehicleId.trim().toUpperCase();
        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch Request from SQLite
                String reqSql = "SELECT shop_id, warehouse_id, warehouse_name, rice, wheat, sugar, status FROM requests WHERE UPPER(id) = UPPER(?);";
                String whId = null, whName = null, shopId = null, currentStatus = null;
                int reqRice = 0, reqWheat = 0, reqSugar = 0;

                try (PreparedStatement ps = conn.prepareStatement(reqSql)) {
                    ps.setString(1, reqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Request not found in database: " + reqId);
                            return;
                        }
                        shopId = rs.getString("shop_id");
                        whId = rs.getString("warehouse_id");
                        whName = rs.getString("warehouse_name");
                        reqRice = rs.getInt("rice");
                        reqWheat = rs.getInt("wheat");
                        reqSugar = rs.getInt("sugar");
                        currentStatus = rs.getString("status");
                    }
                }

                // 2. Fetch Vehicle details strictly from SQLite database
                String vNum = null;
                String vType = "";
                String resolvedVehId = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT id, vehicle_number, vehicle_type, availability FROM vehicles WHERE UPPER(id) = UPPER(?) OR UPPER(vehicle_number) = UPPER(?);")) {
                    ps.setString(1, vehicleId);
                    ps.setString(2, vehicleId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Vehicle not found in database: " + vehicleId);
                            return;
                        }
                        resolvedVehId = rs.getString("id");
                        vNum = rs.getString("vehicle_number");
                        vType = rs.getString("vehicle_type");
                    }
                }

                // 3. Deduct from reserved stock (goods leave warehouse)
                String deductResSql = "UPDATE warehouses SET rice_reserved = MAX(0, rice_reserved - ?), " +
                                      "wheat_reserved = MAX(0, wheat_reserved - ?), " +
                                      "sugar_reserved = MAX(0, sugar_reserved - ?) WHERE id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(deductResSql)) {
                    ps.setInt(1, reqRice);
                    ps.setInt(2, reqWheat);
                    ps.setInt(3, reqSugar);
                    ps.setString(4, whId);
                    ps.executeUpdate();
                }

                // 4. Update Vehicle in SQLite
                String updateVehSql = "UPDATE vehicles SET availability = 'On Trip', status = 'Dispatched', assigned_request = ? WHERE UPPER(id) = UPPER(?);";
                try (PreparedStatement ps = conn.prepareStatement(updateVehSql)) {
                    ps.setString(1, reqId);
                    ps.setString(2, resolvedVehId);
                    ps.executeUpdate();
                }

                // 5. Update Request in SQLite
                String updateReqSql = "UPDATE requests SET vehicle = ?, status = 'Dispatched', updated_date = ?, updated_time = ? WHERE UPPER(id) = UPPER(?);";
                try (PreparedStatement ps = conn.prepareStatement(updateReqSql)) {
                    ps.setString(1, resolvedVehId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, reqId);
                    ps.executeUpdate();
                }

                // 6. Insert Timeline Events: Vehicle Assigned & Dispatched
                String tlSql = "INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
                    ps.setString(1, reqId);
                    ps.setString(2, "Vehicle Assigned");
                    ps.setString(3, curDate);
                    ps.setString(4, curTime);
                    ps.setString(5, "Reserved");
                    ps.setString(6, updatedBy);
                    ps.setString(7, "Vehicle " + resolvedVehId + " (" + vNum + (vType != null && !vType.isEmpty() ? ", " + vType : "") + ") assigned to " + reqId + ".");
                    ps.setInt(8, 3);
                    ps.executeUpdate();

                    ps.setString(1, reqId);
                    ps.setString(2, "Dispatched");
                    ps.setString(3, curDate);
                    ps.setString(4, curTime);
                    ps.setString(5, "Dispatched");
                    ps.setString(6, updatedBy);
                    ps.setString(7, "Consignment dispatched from " + whName + " via Vehicle " + resolvedVehId + " to Shop " + shopId + ".");
                    ps.setInt(8, 4);
                    ps.executeUpdate();
                }

                // 7. Insert Operation History
                String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                    String op1 = getNextOpId(conn);
                    ps.setString(1, op1);
                    ps.setString(2, reqId);
                    ps.setString(3, resolvedVehId);
                    ps.setString(4, "vehicle");
                    ps.setString(5, "Vehicle Assigned");
                    ps.setString(6, "Available");
                    ps.setString(7, "On Trip");
                    ps.setString(8, curDate);
                    ps.setString(9, curTime);
                    ps.setString(10, updatedBy);
                    ps.setString(11, "Vehicle " + resolvedVehId + " (" + vNum + ") assigned to " + reqId + ". Route: " + whName + " → Shop " + shopId + ".");
                    ps.executeUpdate();

                    String op2 = getNextOpId(conn);
                    ps.setString(1, op2);
                    ps.setString(2, reqId);
                    ps.setString(3, shopId);
                    ps.setString(4, "delivery");
                    ps.setString(5, "Dispatched");
                    ps.setString(6, "Reserved");
                    ps.setString(7, "Dispatched");
                    ps.setString(8, curDate);
                    ps.setString(9, curTime);
                    ps.setString(10, updatedBy);
                    ps.setString(11, reqId + " dispatched from " + whName + " via Vehicle " + resolvedVehId + " to Shop " + shopId + ".");
                    ps.executeUpdate();
                }

                conn.commit();
                getRequestById(exchange, reqId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[RequestHandler] Error assigning vehicle: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error during vehicle assignment: " + e.getMessage());
        }
    }

    private void markInTransit(HttpExchange exchange, String reqId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String updatedBy = data.get("updatedBy");
        if (updatedBy == null || updatedBy.trim().isEmpty()) updatedBy = "System";

        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Verify request exists
                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM requests WHERE UPPER(id) = UPPER(?);")) {
                    ps.setString(1, reqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Request not found in database: " + reqId);
                            return;
                        }
                    }
                }

                // Update Request in SQLite
                try (PreparedStatement ps = conn.prepareStatement("UPDATE requests SET status = 'In Transit', updated_date = ?, updated_time = ? WHERE UPPER(id) = UPPER(?);")) {
                    ps.setString(1, curDate);
                    ps.setString(2, curTime);
                    ps.setString(3, reqId);
                    ps.executeUpdate();
                }

                // Add Timeline
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) VALUES (?, 'In Transit', ?, ?, 'In Transit', ?, 'Delivery vehicle is actively in transit.', 5);")) {
                    ps.setString(1, reqId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, updatedBy);
                    ps.executeUpdate();
                }

                // Add Op History
                String opId = getNextOpId(conn);
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, 'DELIVERY', 'delivery', 'In Transit', 'Dispatched', 'In Transit', ?, ?, ?, ?);")) {
                    ps.setString(1, opId);
                    ps.setString(2, reqId);
                    ps.setString(3, curDate);
                    ps.setString(4, curTime);
                    ps.setString(5, updatedBy);
                    ps.setString(6, reqId + " delivery is in transit to destination shop.");
                    ps.executeUpdate();
                }

                conn.commit();
                getRequestById(exchange, reqId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            HttpUtil.sendError(exchange, 500, "Database error setting in-transit status: " + e.getMessage());
        }
    }

    private void markDelivered(HttpExchange exchange, String reqId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);
        String updatedBy = data.get("updatedBy");
        if (updatedBy == null || updatedBy.trim().isEmpty()) updatedBy = "Shop Staff";

        String curDate = LocalDate.now().toString();
        String curTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Fetch vehicle and shopId from request in SQLite
                String assignedVehicle = null;
                String shopId = null;
                try (PreparedStatement ps = conn.prepareStatement("SELECT vehicle, shop_id FROM requests WHERE UPPER(id) = UPPER(?);")) {
                    ps.setString(1, reqId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Request not found in database: " + reqId);
                            return;
                        }
                        assignedVehicle = rs.getString("vehicle");
                        shopId = rs.getString("shop_id");
                    }
                }

                // Update Request status to Delivered in SQLite
                try (PreparedStatement ps = conn.prepareStatement("UPDATE requests SET status = 'Delivered', updated_date = ?, updated_time = ? WHERE UPPER(id) = UPPER(?);")) {
                    ps.setString(1, curDate);
                    ps.setString(2, curTime);
                    ps.setString(3, reqId);
                    ps.executeUpdate();
                }

                // Release Vehicle in SQLite if assigned
                if (assignedVehicle != null && !assignedVehicle.equalsIgnoreCase("Not Assigned")) {
                    try (PreparedStatement ps = conn.prepareStatement("UPDATE vehicles SET availability = 'Available', status = 'Idle / Ready', assigned_request = NULL WHERE UPPER(id) = UPPER(?) OR UPPER(vehicle_number) = UPPER(?);")) {
                        ps.setString(1, assignedVehicle);
                        ps.setString(2, assignedVehicle);
                        ps.executeUpdate();
                    }

                    // Op History: Vehicle Released
                    String vOpId = getNextOpId(conn);
                    try (PreparedStatement ps = conn.prepareStatement("INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, 'vehicle', 'Vehicle Released', 'On Trip', 'Available', ?, ?, ?, ?);")) {
                        ps.setString(1, vOpId);
                        ps.setString(2, reqId);
                        ps.setString(3, assignedVehicle);
                        ps.setString(4, curDate);
                        ps.setString(5, curTime);
                        ps.setString(6, updatedBy);
                        ps.setString(7, "Vehicle " + assignedVehicle + " released after completing delivery for " + reqId + ".");
                        ps.executeUpdate();
                    }
                }

                // Add Timeline in SQLite
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) VALUES (?, 'Delivered', ?, ?, 'Delivered', ?, 'Consignment reached destination shop and received successfully.', 6);")) {
                    ps.setString(1, reqId);
                    ps.setString(2, curDate);
                    ps.setString(3, curTime);
                    ps.setString(4, updatedBy);
                    ps.executeUpdate();
                }

                // Add Op History: Delivered in SQLite
                String delOpId = getNextOpId(conn);
                try (PreparedStatement ps = conn.prepareStatement("INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, 'delivery', 'Delivered', 'In Transit', 'Delivered', ?, ?, ?, ?);")) {
                    ps.setString(1, delOpId);
                    ps.setString(2, reqId);
                    ps.setString(3, shopId != null ? shopId : "SHOP");
                    ps.setString(4, curDate);
                    ps.setString(5, curTime);
                    ps.setString(6, updatedBy);
                    ps.setString(7, reqId + " successfully delivered to destination. Delivery confirmed by staff.");
                    ps.executeUpdate();
                }

                conn.commit();
                getRequestById(exchange, reqId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            HttpUtil.sendError(exchange, 500, "Database error marking request delivered: " + e.getMessage());
        }
    }

    private void appendRequestJson(Connection conn, StringBuilder json, ResultSet rs) throws SQLException {
        String reqId = rs.getString("id");
        json.append("{")
            .append("\"id\":\"").append(JsonUtil.escape(reqId)).append("\",")
            .append("\"requestId\":\"").append(JsonUtil.escape(reqId)).append("\",")
            .append("\"shopId\":\"").append(JsonUtil.escape(rs.getString("shop_id"))).append("\",")
            .append("\"shopName\":\"").append(JsonUtil.escape(rs.getString("shop_name") != null ? rs.getString("shop_name") : "")).append("\",")
            .append("\"warehouseId\":\"").append(JsonUtil.escape(rs.getString("warehouse_id"))).append("\",")
            .append("\"warehouse\":\"").append(JsonUtil.escape(rs.getString("warehouse_name"))).append("\",")
            .append("\"warehouseName\":\"").append(JsonUtil.escape(rs.getString("warehouse_name"))).append("\",")
            .append("\"rice\":\"").append(rs.getInt("rice")).append("\",")
            .append("\"wheat\":\"").append(rs.getInt("wheat")).append("\",")
            .append("\"sugar\":\"").append(rs.getInt("sugar")).append("\",")
            .append("\"urgency\":\"").append(JsonUtil.escape(rs.getString("urgency"))).append("\",")
            .append("\"status\":\"").append(JsonUtil.escape(rs.getString("status"))).append("\",")
            .append("\"vehicle\":\"").append(JsonUtil.escape(rs.getString("vehicle"))).append("\",")
            .append("\"route\":\"").append(JsonUtil.escape(rs.getString("route"))).append("\",")
            .append("\"submittedDate\":\"").append(JsonUtil.escape(rs.getString("submitted_date"))).append("\",")
            .append("\"submittedTime\":\"").append(JsonUtil.escape(rs.getString("submitted_time"))).append("\",")
            .append("\"processedDate\":").append(rs.getString("processed_date") != null ? "\"" + JsonUtil.escape(rs.getString("processed_date")) + "\"" : "null").append(",")
            .append("\"processedTime\":").append(rs.getString("processed_time") != null ? "\"" + JsonUtil.escape(rs.getString("processed_time")) + "\"" : "null").append(",")
            .append("\"updatedDate\":\"").append(JsonUtil.escape(rs.getString("updated_date"))).append("\",")
            .append("\"updatedTime\":\"").append(JsonUtil.escape(rs.getString("updated_time"))).append("\",")
            .append("\"timeline\":");

        // Fetch Timeline directly from SQLite for this request
        String tlSql = "SELECT stage_title, event_date, event_time, status, performed_by, details FROM request_timeline WHERE request_id = ? ORDER BY stage_order ASC, id ASC;";
        try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
            ps.setString(1, reqId);
            try (ResultSet tlRs = ps.executeQuery()) {
                json.append("[");
                boolean firstTl = true;
                while (tlRs.next()) {
                    if (!firstTl) json.append(",");
                    firstTl = false;
                    json.append("{")
                        .append("\"title\":\"").append(JsonUtil.escape(tlRs.getString("stage_title"))).append("\",")
                        .append("\"date\":\"").append(JsonUtil.escape(tlRs.getString("event_date"))).append("\",")
                        .append("\"time\":\"").append(JsonUtil.escape(tlRs.getString("event_time"))).append("\",")
                        .append("\"status\":\"").append(JsonUtil.escape(tlRs.getString("status"))).append("\",")
                        .append("\"by\":\"").append(JsonUtil.escape(tlRs.getString("performed_by"))).append("\",")
                        .append("\"details\":\"").append(JsonUtil.escape(tlRs.getString("details"))).append("\"")
                        .append("}");
                }
                json.append("]");
            }
        }
        json.append("}");
    }

    private void insertStockLog(PreparedStatement ps, String updateId, String whId, String comm, int prev, int add, int ded, int cur, String reason, String date, String time, String by) throws SQLException {
        ps.setString(1, updateId);
        ps.setString(2, whId);
        ps.setString(3, comm);
        ps.setInt(4, prev);
        ps.setInt(5, add);
        ps.setInt(6, ded);
        ps.setInt(7, cur);
        ps.setString(8, reason);
        ps.setString(9, date);
        ps.setString(10, time);
        ps.setString(11, by);
        ps.executeUpdate();
    }

    private int parseInt(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s.trim());
        } catch (Exception e) {
            return 0;
        }
    }

    public static synchronized String getNextRequestId(Connection conn) throws SQLException {
        int maxNum = 100;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT id FROM requests;")) {
            while (rs.next()) {
                String existingId = rs.getString(1);
                if (existingId != null && existingId.toUpperCase().startsWith("REQ-")) {
                    try {
                        int n = Integer.parseInt(existingId.substring(4).trim());
                        if (n > maxNum) maxNum = n;
                    } catch (Exception ignored) {}
                }
            }
        }
        return "REQ-" + (maxNum + 1);
    }

    public static synchronized String getNextOpId(Connection conn) throws SQLException {
        int maxOp = 0;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT op_id FROM operation_history;")) {
            while (rs.next()) {
                String existingOp = rs.getString(1);
                if (existingOp != null && existingOp.toUpperCase().startsWith("OP-")) {
                    try {
                        int n = Integer.parseInt(existingOp.substring(3).trim());
                        if (n > maxOp) maxOp = n;
                    } catch (Exception ignored) {}
                }
            }
        }
        return "OP-" + String.format("%03d", maxOp + 1);
    }

    public static synchronized String getNextStockUpdateId(Connection conn) throws SQLException {
        int maxStk = 3000;
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT update_id FROM stock_update_history;")) {
            while (rs.next()) {
                String existingStk = rs.getString(1);
                if (existingStk != null && existingStk.toUpperCase().startsWith("STK-")) {
                    try {
                        int n = Integer.parseInt(existingStk.substring(4).trim());
                        if (n > maxStk) maxStk = n;
                    } catch (Exception ignored) {}
                }
            }
        }
        return "STK-" + (maxStk + 1);
    }
}
