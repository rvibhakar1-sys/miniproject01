package valam;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;

public class AuthHandler implements HttpHandler {

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (HttpUtil.handleCors(exchange)) return;

        String path = exchange.getRequestURI().getPath();
        String method = exchange.getRequestMethod();

        if ("POST".equalsIgnoreCase(method) && path.endsWith("/login")) {
            handleLogin(exchange);
        } else {
            HttpUtil.sendError(exchange, 404, "Endpoint not found: " + path);
        }
    }

    private void handleLogin(HttpExchange exchange) throws IOException {
        String body = HttpUtil.readRequestBody(exchange);
        Map<String, String> data = JsonUtil.parseSimpleObject(body);

        String username = data.get("username");
        String password = data.get("password");
        String expectedRole = data.get("role");

        if (username == null || username.trim().isEmpty() || password == null || password.trim().isEmpty()) {
            HttpUtil.sendError(exchange, 400, "Username and password are required.");
            return;
        }

        username = username.trim();
        password = password.trim();

        String query = "SELECT username, password_hash, salt, role, entity_id, entity_name FROM users WHERE LOWER(username) = LOWER(?);";

        try (Connection conn = Database.getConnection();
             PreparedStatement ps = conn.prepareStatement(query)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String actualUsername = rs.getString("username");
                    String passwordHash = rs.getString("password_hash");
                    String salt = rs.getString("salt");
                    String role = rs.getString("role");
                    String entityId = rs.getString("entity_id");
                    String entityName = rs.getString("entity_name");

                    if (PasswordUtil.verifyPassword(password, salt, passwordHash)) {
                        if (expectedRole != null && !expectedRole.trim().isEmpty() && !expectedRole.equalsIgnoreCase(role)) {
                            HttpUtil.sendError(exchange, 403, "User is not authorized for the " + expectedRole + " portal.");
                            return;
                        }

                        StringBuilder json = new StringBuilder();
                        json.append("{")
                            .append("\"success\":true,")
                            .append("\"user\":\"").append(JsonUtil.escape(actualUsername)).append("\",")
                            .append("\"role\":\"").append(JsonUtil.escape(role)).append("\",")
                            .append("\"entityId\":\"").append(JsonUtil.escape(entityId != null ? entityId : "")).append("\",")
                            .append("\"entityName\":\"").append(JsonUtil.escape(entityName != null ? entityName : "")).append("\"")
                            .append("}");

                        HttpUtil.sendJsonResponse(exchange, 200, json.toString());
                        return;
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("[AuthHandler] Database error: " + e.getMessage());
            HttpUtil.sendError(exchange, 500, "Database error during authentication.");
            return;
        }

        HttpUtil.sendError(exchange, 401, "Invalid username or password. Please try again.");
    }
}

