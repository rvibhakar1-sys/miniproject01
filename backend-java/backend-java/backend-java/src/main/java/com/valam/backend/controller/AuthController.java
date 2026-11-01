
package com.valam.backend.controller;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.http.ResponseEntity;
import com.valam.backend.service.PasswordService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;
@CrossOrigin(origins = "http://127.0.0.1:5500")
@RestController
public class AuthController {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordService passwordService;

    public AuthController(
            JdbcTemplate jdbcTemplate,
            PasswordService passwordService) {

        this.jdbcTemplate = jdbcTemplate;
        this.passwordService = passwordService;
    }

    @PostMapping("/api/auth/signup")
   

    public ResponseEntity<?> signup(@RequestBody Map<String, Object> data) {

        String username = (String) data.get("username");
        String password = (String) data.get("password");
        String role = (String) data.get("role");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()
                || role == null || role.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "All fields are required"));
        }

        // Only SHOP and WAREHOUSE can register publicly
        if (!role.equals("SHOP") && !role.equals("WAREHOUSE")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid role"));
        }

        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?",
                Integer.class, username
        );

        if (count != null && count > 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username already exists"));
        }

        String hashedPassword = passwordService.hashPassword(password);

        String sql = """
                INSERT INTO users (username, password_hash, role)
                VALUES (?, ?, ?)
                """;

        jdbcTemplate.update(sql, username, hashedPassword, role);

        return ResponseEntity.ok(
                Map.of("message", "Signup successful!")
        );
    }

    
    @PostMapping("/api/auth/login")
    public ResponseEntity<?> login(@RequestBody Map<String, Object> data) {

        String username = (String) data.get("username");
        String password = (String) data.get("password");

        if (username == null || username.isBlank()
                || password == null || password.isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Username and password are required"));
        }

        String sql = """
                SELECT password_hash, role
                FROM users
                WHERE username = ?
                """;

        try {

            Map<String, Object> user = jdbcTemplate.queryForMap(sql, username);

            String hashedPassword = (String) user.get("password_hash");
            String role = (String) user.get("role");

            if (!passwordService.checkPassword(password, hashedPassword)) {
                return ResponseEntity.status(401)
                        .body(Map.of("message", "Invalid username or password"));
            }

            return ResponseEntity.ok(
                    Map.of(
                            "message", "Login successful!",
                            "username", username,
                            "role", role
                    )
            );

        } catch (org.springframework.dao.EmptyResultDataAccessException e) {

            return ResponseEntity.status(401)
                    .body(Map.of("message", "Invalid username or password"));
        }
    }

}
