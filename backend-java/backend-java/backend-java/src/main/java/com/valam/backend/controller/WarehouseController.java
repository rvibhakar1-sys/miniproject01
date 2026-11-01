package com.valam.backend.controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CrossOrigin;

import java.util.List;
import java.util.Map;
@CrossOrigin(origins = "http://127.0.0.1:5500")
@RestController
public class WarehouseController {

    private final JdbcTemplate jdbcTemplate;

    public WarehouseController(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @GetMapping("/api/warehouses")
    public List<Map<String, Object>> getWarehouses() {

        String sql = "SELECT * FROM warehouses";

        return jdbcTemplate.queryForList(sql);
    }


    @GetMapping("/api/warehouses/{id}/stock")
public List<Map<String, Object>> getWarehouseStock(
        @PathVariable int id) {

    String sql = """
            SELECT c.commodity_name, ws.quantity_kg
            FROM warehouse_stock ws
            JOIN commodities c
            ON ws.commodity_id = c.commodity_id
            WHERE ws.warehouse_id = ?
            """;

    return jdbcTemplate.queryForList(sql, id);
}

    @PostMapping("/api/warehouses/stock/add")
    public ResponseEntity<?> addStock(@RequestBody Map<String, Object> data) {

        String username = (String) data.get("username");
        Integer commodityId = (Integer) data.get("commodityId");
        Double quantity = ((Number) data.get("quantity")).doubleValue();

        if (username == null || commodityId == null
                || quantity == null || quantity <= 0) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Invalid stock details"));
        }

        String userSql = """
                SELECT warehouse_id
                FROM users
                WHERE username = ? AND role = 'WAREHOUSE'
                """;

        List<Map<String, Object>> users =
                jdbcTemplate.queryForList(userSql, username);

        if (users.isEmpty() || users.get(0).get("warehouse_id") == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "Warehouse account not linked"));
        }

        int warehouseId = ((Number) users.get(0).get("warehouse_id")).intValue();

        String stockSql = """
                INSERT INTO warehouse_stock (warehouse_id, commodity_id, quantity_kg)
                VALUES (?, ?, ?)
                ON DUPLICATE KEY UPDATE
                quantity_kg = quantity_kg + VALUES(quantity_kg)
                """;

        jdbcTemplate.update(stockSql, warehouseId, commodityId, quantity);

        return ResponseEntity.ok(
                Map.of("message", "Stock updated successfully!")
        );
    }
}