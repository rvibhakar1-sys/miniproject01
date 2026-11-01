package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;

public class WarehouseHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        // /api/warehouses, /api/warehouses/{id}, /api/warehouses/{id}/stock
        String[] parts = path.split("/");

        if ("GET".equalsIgnoreCase(method)) {
            if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
                getAllWarehouses(exchange);
            } else if (parts.length == 4) {
                getWarehouseById(exchange, parts[3]);
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid warehouse endpoint");
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            if (parts.length >= 4 && (path.endsWith("/stock") || path.endsWith("/add-stock"))) {
                addWarehouseStock(exchange, parts[3]);
            } else {
                HttpUtil.sendError(exchange, 404, "Invalid warehouse action");
            }
        } else {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
        }
    }

    private void getAllWarehouses(HttpExchange exchange) throws IOException {
        String sql = "SELECT id, name, location, distance, rice_available, wheat_available, sugar_available, " +
                     "rice_reserved, wheat_reserved, sugar_reserved, last_update_date, last_update_time, " +
                     "last_update_by, last_update_reason FROM warehouses ORDER BY id ASC;";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                appendWarehouseJson(json, rs);
            }
            json.append("]");

            HttpUtil.sendJsonResponse(exchange, 200, json.toString());
        } catch (SQLException e) {
            System.err.println("[WarehouseHandler] Error fetching warehouses: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching warehouses.");
        }
    }

    private void getWarehouseById(HttpExchange exchange, String whId) throws IOException {
        String sql = "SELECT id, name, location, distance, rice_available, wheat_available, sugar_available, " +
                     "rice_reserved, wheat_reserved, sugar_reserved, last_update_date, last_update_time, " +
                     "last_update_by, last_update_reason FROM warehouses WHERE UPPER(id) = UPPER(?);";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, whId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder json = new StringBuilder();
                    appendWarehouseJson(json, rs);
                    HttpUtil.sendJsonResponse(exchange, 200, json.toString());
                } else {
                    HttpUtil.sendError(exchange, 404, "Warehouse not found: " + whId);
                }
            }
        } catch (SQLException e) {
            System.err.println("[WarehouseHandler] Error fetching warehouse: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching warehouse.");
        }
    }

    private void addWarehouseStock(HttpExchange exchange, String whId) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);

        whId = whId.toUpperCase();
        String commodity = data.get("commodity"); // "Rice", "Wheat", "Sugar" (or null if multi-commodity)
        int quantity = 0;
        try {
            if (data.containsKey("quantity")) {
                quantity = Integer.parseInt(data.get("quantity"));
            }
        } catch (NumberFormatException ignored) {}

        int riceAdd = 0;
        int wheatAdd = 0;
        int sugarAdd = 0;

        if (commodity != null && !commodity.isEmpty()) {
            if (commodity.equalsIgnoreCase("Rice")) riceAdd = quantity;
            else if (commodity.equalsIgnoreCase("Wheat")) wheatAdd = quantity;
            else if (commodity.equalsIgnoreCase("Sugar")) sugarAdd = quantity;
        } else {
            // Check direct rice, wheat, sugar fields
            if (data.containsKey("rice")) {
                try { riceAdd = Integer.parseInt(data.get("rice")); } catch (Exception ignored) {}
            }
            if (data.containsKey("wheat")) {
                try { wheatAdd = Integer.parseInt(data.get("wheat")); } catch (Exception ignored) {}
            }
            if (data.containsKey("sugar")) {
                try { sugarAdd = Integer.parseInt(data.get("sugar")); } catch (Exception ignored) {}
            }
        }

        if (riceAdd <= 0 && wheatAdd <= 0 && sugarAdd <= 0) {
            HttpUtil.sendError(exchange, 400, "Please specify a positive quantity of commodities to add.");
            return;
        }

        String reason = data.get("reason");
        if (reason == null || reason.trim().isEmpty()) {
            reason = "Consignment received from FCI Central Depot";
        }
        String updatedBy = data.get("updatedBy");
        if (updatedBy == null || updatedBy.trim().isEmpty()) {
            updatedBy = whId + " Staff";
        }

        String currentDate = LocalDate.now().toString();
        String currentTime = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm:ss"));

        try (Connection conn = Database.getConnection()) {
            conn.setAutoCommit(false);
            try {
                // 1. Fetch current stock
                String fetchSql = "SELECT rice_available, wheat_available, sugar_available, name FROM warehouses WHERE id = ?;";
                int prevRice = 0, prevWheat = 0, prevSugar = 0;
                String whName = "Warehouse " + whId;
                try (PreparedStatement ps = conn.prepareStatement(fetchSql)) {
                    ps.setString(1, whId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            HttpUtil.sendError(exchange, 404, "Warehouse not found: " + whId);
                            return;
                        }
                        prevRice = rs.getInt("rice_available");
                        prevWheat = rs.getInt("wheat_available");
                        prevSugar = rs.getInt("sugar_available");
                        whName = rs.getString("name");
                    }
                }

                int newRice = prevRice + riceAdd;
                int newWheat = prevWheat + wheatAdd;
                int newSugar = prevSugar + sugarAdd;

                // 2. Update warehouse
                String updateSql = "UPDATE warehouses SET rice_available = ?, wheat_available = ?, sugar_available = ?, " +
                                  "last_update_date = ?, last_update_time = ?, last_update_by = ?, last_update_reason = ? " +
                                  "WHERE id = ?;";
                try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                    ps.setInt(1, newRice);
                    ps.setInt(2, newWheat);
                    ps.setInt(3, newSugar);
                    ps.setString(4, currentDate);
                    ps.setString(5, currentTime);
                    ps.setString(6, updatedBy);
                    ps.setString(7, reason);
                    ps.setString(8, whId);
                    ps.executeUpdate();
                }

                // 3. Insert into stock_update_history for each commodity added
                String logSql = "INSERT INTO stock_update_history (update_id, warehouse_id, commodity, previous_stock, quantity_added, quantity_deducted, current_stock, reason, log_date, log_time, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                long nextLogId = System.currentTimeMillis() % 100000;
                try (PreparedStatement ps = conn.prepareStatement(logSql)) {
                    if (riceAdd > 0) {
                        insertStockHistory(ps, "STK-" + (nextLogId++), whId, "Rice", prevRice, riceAdd, 0, newRice, reason, currentDate, currentTime, updatedBy);
                    }
                    if (wheatAdd > 0) {
                        insertStockHistory(ps, "STK-" + (nextLogId++), whId, "Wheat", prevWheat, wheatAdd, 0, newWheat, reason, currentDate, currentTime, updatedBy);
                    }
                    if (sugarAdd > 0) {
                        insertStockHistory(ps, "STK-" + (nextLogId++), whId, "Sugar", prevSugar, sugarAdd, 0, newSugar, reason, currentDate, currentTime, updatedBy);
                    }
                }

                // 4. Insert into operation_history
                String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
                String opId = "OP-" + String.format("%03d", (int)(Math.random() * 900 + 100));
                try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                    ps.setString(1, opId);
                    ps.setString(2, "—");
                    ps.setString(3, whId);
                    ps.setString(4, "warehouse");
                    ps.setString(5, "Stock Updated");
                    ps.setString(6, "Stock Replenishment");
                    ps.setString(7, "Added: Rice " + riceAdd + "kg, Wheat " + wheatAdd + "kg, Sugar " + sugarAdd + "kg");
                    ps.setString(8, currentDate);
                    ps.setString(9, currentTime);
                    ps.setString(10, updatedBy);
                    ps.setString(11, "Stock added at " + whName + " (" + whId + "). Added Rice: " + riceAdd + " kg, Wheat: " + wheatAdd + " kg, Sugar: " + sugarAdd + " kg. Reason: " + reason);
                    ps.executeUpdate();
                }

                conn.commit();

                // Return updated warehouse
                getWarehouseById(exchange, whId);
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (SQLException e) {
            System.err.println("[WarehouseHandler] Stock update error: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error during stock update.");
        }
    }

    private void insertStockHistory(PreparedStatement ps, String updateId, String whId, String comm, int prev, int add, int ded, int cur, String reason, String date, String time, String by) throws SQLException {
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

    public static void appendWarehouseJson(StringBuilder json, ResultSet rs) throws SQLException {
        int riceAvail = rs.getInt("rice_available");
        int wheatAvail = rs.getInt("wheat_available");
        int sugarAvail = rs.getInt("sugar_available");
        int riceRes = rs.getInt("rice_reserved");
        int wheatRes = rs.getInt("wheat_reserved");
        int sugarRes = rs.getInt("sugar_reserved");

        json.append("{")
            .append("\"id\":\"").append(JsonUtil.escape(rs.getString("id"))).append("\",")
            .append("\"warehouseId\":\"").append(JsonUtil.escape(rs.getString("id"))).append("\",")
            .append("\"name\":\"").append(JsonUtil.escape(rs.getString("name"))).append("\",")
            .append("\"warehouseName\":\"").append(JsonUtil.escape(rs.getString("name"))).append("\",")
            .append("\"location\":\"").append(JsonUtil.escape(rs.getString("location"))).append("\",")
            .append("\"distance\":\"").append(JsonUtil.escape(rs.getString("distance"))).append("\",")
            .append("\"rice\":").append(riceAvail).append(",")
            .append("\"wheat\":").append(wheatAvail).append(",")
            .append("\"sugar\":").append(sugarAvail).append(",")
            .append("\"available\":{")
                .append("\"rice\":").append(riceAvail).append(",")
                .append("\"wheat\":").append(wheatAvail).append(",")
                .append("\"sugar\":").append(sugarAvail)
            .append("},")
            .append("\"reserved\":{")
                .append("\"rice\":").append(riceRes).append(",")
                .append("\"wheat\":").append(wheatRes).append(",")
                .append("\"sugar\":").append(sugarRes)
            .append("},")
            .append("\"lastUpdate\":{")
                .append("\"date\":\"").append(JsonUtil.escape(rs.getString("last_update_date"))).append("\",")
                .append("\"time\":\"").append(JsonUtil.escape(rs.getString("last_update_time"))).append("\",")
                .append("\"updatedBy\":\"").append(JsonUtil.escape(rs.getString("last_update_by"))).append("\",")
                .append("\"reason\":\"").append(JsonUtil.escape(rs.getString("last_update_reason"))).append("\"")
            .append("}")
            .append("}");
    }
}

