package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

public class VehicleHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String method = exchange.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            HttpUtil.sendError(exchange, 405, "Method not allowed");
            return;
        }

        String sql = "SELECT id, vehicle_number, vehicle_type, capacity, availability, status, assigned_request FROM vehicles ORDER BY id ASC;";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            StringBuilder json = new StringBuilder("[");
            boolean first = true;
            while (rs.next()) {
                if (!first) json.append(",");
                first = false;

                String id = rs.getString("id");
                String vNum = rs.getString("vehicle_number");
                String vType = rs.getString("vehicle_type");
                String fullName = vNum + (vType != null ? " (" + vType + ")" : "");
                String assigned = rs.getString("assigned_request");

                json.append("{")
                    .append("\"id\":\"").append(JsonUtil.escape(id)).append("\",")
                    .append("\"vehicleId\":\"").append(JsonUtil.escape(id)).append("\",")
                    .append("\"vehicleNumber\":\"").append(JsonUtil.escape(vNum)).append("\",")
                    .append("\"vehicleType\":\"").append(JsonUtil.escape(vType != null ? vType : "")).append("\",")
                    .append("\"vehicleName\":\"").append(JsonUtil.escape(fullName)).append("\",")
                    .append("\"capacity\":\"").append(JsonUtil.escape(rs.getString("capacity"))).append("\",")
                    .append("\"availability\":\"").append(JsonUtil.escape(rs.getString("availability"))).append("\",")
                    .append("\"status\":\"").append(JsonUtil.escape(rs.getString("status"))).append("\",")
                    .append("\"assignedRequest\":").append(assigned != null ? "\"" + JsonUtil.escape(assigned) + "\"" : "null")
                    .append("}");
            }
            json.append("]");

            HttpUtil.sendJsonResponse(exchange, 200, json.toString());
        } catch (SQLException e) {
            System.err.println("[VehicleHandler] Error fetching vehicles: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error fetching vehicles.");
        }
    }
}

