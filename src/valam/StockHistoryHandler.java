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

public class StockHistoryHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        Map<String, String> query = HttpUtil.parseQueryParams(exchange);
        String warehouseId = query.get("warehouseId");
        String commodity = query.get("commodity");
        String fromDate = query.get("fromDate");
        String toDate = query.get("toDate");

        StringBuilder sql = new StringBuilder("SELECT * FROM stock_update_history WHERE 1=1 ");
        List<Object> params = new ArrayList<>();

        if (warehouseId != null && !warehouseId.trim().isEmpty() && !warehouseId.equalsIgnoreCase("all")) {
            sql.append("AND UPPER(warehouse_id) = UPPER(?) ");
            params.add(warehouseId.trim());
        }
        if (commodity != null && !commodity.trim().isEmpty() && !commodity.equalsIgnoreCase("all")) {
            sql.append("AND LOWER(commodity) = LOWER(?) ");
            params.add(commodity.trim());
        }
        if (fromDate != null && !fromDate.trim().isEmpty()) {
            sql.append("AND log_date >= ? ");
            params.add(fromDate.trim());
        }
        if (toDate != null && !toDate.trim().isEmpty()) {
            sql.append("AND log_date <= ? ");
            params.add(toDate.trim());
        }

        sql.append("ORDER BY log_date DESC, log_time DESC, id DESC;");

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

                    json.append("{")
                        .append("\"id\":").append(rs.getInt("id")).append(",")
                        .append("\"updateId\":\"").append(JsonUtil.escape(rs.getString("update_id"))).append("\",")
                        .append("\"warehouseId\":\"").append(JsonUtil.escape(rs.getString("warehouse_id"))).append("\",")
                        .append("\"commodity\":\"").append(JsonUtil.escape(rs.getString("commodity"))).append("\",")
                        .append("\"previousStock\":").append(rs.getInt("previous_stock")).append(",")
                        .append("\"quantityAdded\":").append(rs.getInt("quantity_added")).append(",")
                        .append("\"quantityDeducted\":").append(rs.getInt("quantity_deducted")).append(",")
                        .append("\"currentStock\":").append(rs.getInt("current_stock")).append(",")
                        .append("\"reason\":\"").append(JsonUtil.escape(rs.getString("reason"))).append("\",")
                        .append("\"date\":\"").append(JsonUtil.escape(rs.getString("log_date"))).append("\",")
                        .append("\"time\":\"").append(JsonUtil.escape(rs.getString("log_time"))).append("\",")
                        .append("\"updatedBy\":\"").append(JsonUtil.escape(rs.getString("updated_by"))).append("\"")
                        .append("}");
                }
                json.append("]");

                HttpUtil.sendJsonResponse(exchange, 200, json.toString());
            }
        } catch (SQLException e) {
            System.err.println("[StockHistoryHandler] Error: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching stock history.");
        }
    }
}

