package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class ShopHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();

        if (!"GET".equalsIgnoreCase(method)) {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        // Path can be /api/shops or /api/shops/S01
        String[] parts = path.split("/");
        if (parts.length == 3 || (parts.length == 4 && parts[3].isEmpty())) {
            // GET /api/shops
            getAllShops(exchange);
        } else if (parts.length == 4) {
            // GET /api/shops/{id}
            getShopById(exchange, parts[3]);
        } else {
            HttpUtil.sendError(exchange, 404, "Invalid shop path");
        }
    }

    private void getAllShops(HttpExchange exchange) throws IOException {
        String sql = "SELECT id, name, location, nearest_wh, nearest_wh_name, distance, status FROM shops ORDER BY id ASC;";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;
                appendShopJson(json, rs);
            }
            json.append("]");

            HttpUtil.sendJsonResponse(exchange, 200, json.toString());
        } catch (SQLException e) {
            System.err.println("[ShopHandler] Error fetching shops: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching shops.");
        }
    }

    private void getShopById(HttpExchange exchange, String shopId) throws IOException {
        String sql = "SELECT id, name, location, nearest_wh, nearest_wh_name, distance, status FROM shops WHERE UPPER(id) = UPPER(?);";
        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, shopId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    StringBuilder json = new StringBuilder();
                    appendShopJson(json, rs);
                    HttpUtil.sendJsonResponse(exchange, 200, json.toString());
                } else {
                    HttpUtil.sendError(exchange, 404, "Shop not found: " + shopId);
                }
            }
        } catch (SQLException e) {
            System.err.println("[ShopHandler] Error fetching shop: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching shop.");
        }
    }

    private void appendShopJson(StringBuilder json, ResultSet rs) throws SQLException {
        json.append("{")
            .append("\"id\":\"").append(JsonUtil.escape(rs.getString("id"))).append("\",")
            .append("\"shopId\":\"").append(JsonUtil.escape(rs.getString("id"))).append("\",")
            .append("\"name\":\"").append(JsonUtil.escape(rs.getString("name"))).append("\",")
            .append("\"shopName\":\"").append(JsonUtil.escape(rs.getString("name"))).append("\",")
            .append("\"location\":\"").append(JsonUtil.escape(rs.getString("location"))).append("\",")
            .append("\"nearestWh\":\"").append(JsonUtil.escape(rs.getString("nearest_wh"))).append("\",")
            .append("\"nearestWhName\":\"").append(JsonUtil.escape(rs.getString("nearest_wh_name"))).append("\",")
            .append("\"distance\":\"").append(JsonUtil.escape(rs.getString("distance"))).append("\",")
            .append("\"status\":\"").append(JsonUtil.escape(rs.getString("status"))).append("\"")
            .append("}");
    }
}

