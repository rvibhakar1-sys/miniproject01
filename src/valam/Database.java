package valam;

import java.io.File;
import java.io.IOException;
import java.sql.*;

public class Database {
    private static File dbFile;
    private static String dbUrl;

    static {
        try {
            Class.forName("org.sqlite.JDBC");
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("SQLite JDBC driver not found on classpath", e);
        }
    }

    /**
     * Resolves the single, authoritative SQLite database file.
     * Ensures all Java modules and threads access the exact same database file
     * regardless of the working directory from which Java was launched.
     */
    public static synchronized File getDbFile() {
        if (dbFile == null) {
            String override = System.getProperty("valam.db.path");
            if (override != null && !override.trim().isEmpty()) {
                dbFile = new File(override.trim()).getAbsoluteFile();
            } else {
                // Check if running from project root (miniproject01)
                File f1 = new File("database/valam.db").getAbsoluteFile();
                // Check if running from parent repository (d:\M0001)
                File f2 = new File("miniproject01/database/valam.db").getAbsoluteFile();
                // Check relative to parent directory
                File f3 = new File("../database/valam.db").getAbsoluteFile();

                if (f2.exists()) {
                    dbFile = f2;
                } else if (f1.exists()) {
                    dbFile = f1;
                } else if (f3.exists()) {
                    dbFile = f3;
                } else {
                    // Default to database/valam.db
                    dbFile = f1;
                }
            }

            try {
                dbFile = dbFile.getCanonicalFile();
            } catch (IOException ignored) {}

            File parentDir = dbFile.getParentFile();
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs();
            }

            dbUrl = "jdbc:sqlite:" + dbFile.getAbsolutePath();
        }
        return dbFile;
    }

    public static String getDbUrl() {
        if (dbUrl == null) {
            getDbFile();
        }
        return dbUrl;
    }

    /**
     * Obtains a fresh SQLite database connection.
     * Always configures foreign keys and WAL mode for high concurrency and data consistency.
     */
    public static Connection getConnection() throws SQLException {
        getDbFile();
        Connection conn = DriverManager.getConnection(getDbUrl());
        try (Statement s = conn.createStatement()) {
            s.execute("PRAGMA foreign_keys = ON;");
            s.execute("PRAGMA journal_mode = WAL;");
        }
        return conn;
    }

    /**
     * Verifies connectivity, logs the absolute path, and creates tables if they do not exist.
     * Never overwrites existing committed records.
     */
    public static void init() {
        File file = getDbFile();
        System.out.println("=============================================================");
        System.out.println("[Database] SQLite Database File Absolute Path:");
        System.out.println("           " + file.getAbsolutePath());
        System.out.println("=============================================================");

        try (Connection conn = getConnection()) {
            createTables(conn);
            seedInitialDataIfEmpty(conn);
            System.out.println("[Database] SQLite Database verified and ready. Single source of truth active.\n");
        } catch (SQLException e) {
            System.err.println("[Database] Critical Error during SQLite initialization: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static void createTables(Connection conn) throws SQLException {
        try (Statement s = conn.createStatement()) {
            // 1. Users table
            s.execute("CREATE TABLE IF NOT EXISTS users (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "username TEXT UNIQUE NOT NULL, " +
                    "password_hash TEXT NOT NULL, " +
                    "salt TEXT NOT NULL, " +
                    "role TEXT NOT NULL, " +
                    "entity_id TEXT, " +
                    "entity_name TEXT, " +
                    "created_at TEXT DEFAULT CURRENT_TIMESTAMP" +
                    ");");

            // 2. Warehouses table
            s.execute("CREATE TABLE IF NOT EXISTS warehouses (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "distance TEXT, " +
                    "rice_available INTEGER DEFAULT 0, " +
                    "wheat_available INTEGER DEFAULT 0, " +
                    "sugar_available INTEGER DEFAULT 0, " +
                    "rice_reserved INTEGER DEFAULT 0, " +
                    "wheat_reserved INTEGER DEFAULT 0, " +
                    "sugar_reserved INTEGER DEFAULT 0, " +
                    "last_update_date TEXT, " +
                    "last_update_time TEXT, " +
                    "last_update_by TEXT, " +
                    "last_update_reason TEXT" +
                    ");");

            // 3. Shops table
            s.execute("CREATE TABLE IF NOT EXISTS shops (" +
                    "id TEXT PRIMARY KEY, " +
                    "name TEXT NOT NULL, " +
                    "location TEXT NOT NULL, " +
                    "nearest_wh TEXT, " +
                    "nearest_wh_name TEXT, " +
                    "distance TEXT, " +
                    "status TEXT DEFAULT 'Pending'" +
                    ");");

            // 4. Vehicles table
            s.execute("CREATE TABLE IF NOT EXISTS vehicles (" +
                    "id TEXT PRIMARY KEY, " +
                    "vehicle_number TEXT NOT NULL, " +
                    "vehicle_type TEXT, " +
                    "capacity TEXT, " +
                    "availability TEXT DEFAULT 'Available', " +
                    "status TEXT DEFAULT 'Idle / Ready', " +
                    "assigned_request TEXT" +
                    ");");

            // 5. Requests table
            s.execute("CREATE TABLE IF NOT EXISTS requests (" +
                    "id TEXT PRIMARY KEY, " +
                    "shop_id TEXT NOT NULL, " +
                    "warehouse_id TEXT NOT NULL, " +
                    "warehouse_name TEXT, " +
                    "rice INTEGER DEFAULT 0, " +
                    "wheat INTEGER DEFAULT 0, " +
                    "sugar INTEGER DEFAULT 0, " +
                    "urgency TEXT DEFAULT 'normal', " +
                    "status TEXT DEFAULT 'Pending', " +
                    "vehicle TEXT DEFAULT 'Not Assigned', " +
                    "route TEXT, " +
                    "submitted_date TEXT, " +
                    "submitted_time TEXT, " +
                    "processed_date TEXT, " +
                    "processed_time TEXT, " +
                    "updated_date TEXT, " +
                    "updated_time TEXT" +
                    ");");

            // 6. Request Timeline table
            s.execute("CREATE TABLE IF NOT EXISTS request_timeline (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "request_id TEXT NOT NULL, " +
                    "stage_title TEXT NOT NULL, " +
                    "event_date TEXT NOT NULL, " +
                    "event_time TEXT NOT NULL, " +
                    "status TEXT, " +
                    "performed_by TEXT, " +
                    "details TEXT, " +
                    "stage_order INTEGER DEFAULT 0" +
                    ");");

            // 7. Stock Update History table
            s.execute("CREATE TABLE IF NOT EXISTS stock_update_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "update_id TEXT, " +
                    "warehouse_id TEXT NOT NULL, " +
                    "commodity TEXT, " +
                    "previous_stock INTEGER, " +
                    "quantity_added INTEGER DEFAULT 0, " +
                    "quantity_deducted INTEGER DEFAULT 0, " +
                    "current_stock INTEGER, " +
                    "reason TEXT, " +
                    "log_date TEXT, " +
                    "log_time TEXT, " +
                    "updated_by TEXT" +
                    ");");

            // 8. Operation History (Audit Log) table
            s.execute("CREATE TABLE IF NOT EXISTS operation_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                    "op_id TEXT UNIQUE NOT NULL, " +
                    "request_id TEXT, " +
                    "entity_id TEXT, " +
                    "category TEXT, " +
                    "operation_type TEXT, " +
                    "prev_status TEXT, " +
                    "new_status TEXT, " +
                    "op_date TEXT, " +
                    "op_time TEXT, " +
                    "updated_by TEXT, " +
                    "details TEXT" +
                    ");");
        }
    }

    private static void seedInitialDataIfEmpty(Connection conn) throws SQLException {
        // STRICT RULE: If database already contains user accounts, DO NOT overwrite or reseed.
        try (Statement s = conn.createStatement();
             ResultSet rs = s.executeQuery("SELECT COUNT(*) FROM users;")) {
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("[Database] Existing records found in SQLite (" + rs.getInt(1) + " users). Preserving all persistent records.");
                return;
            }
        }

        System.out.println("[Database] Empty database detected. Performing initial baseline setup for VALAM system...");

        conn.setAutoCommit(false);
        try {
            // 1. Users with Salted Hashed Passwords
            insertUser(conn, "Admin", "admin@01", "admin", "ADMIN", "VALAM Headquarters");
            insertUser(conn, "Wh01", "wh@01", "warehouse", "W01", "Valam Central Warehouse");
            insertUser(conn, "Wh02", "wh@02", "warehouse", "W02", "Valam North Warehouse");
            insertUser(conn, "Wh03", "wh@03", "warehouse", "W03", "Valam South Warehouse");
            insertUser(conn, "Shop01", "shop@01", "shop", "S01", "FPS Nagercoil");
            insertUser(conn, "Shop02", "shop@02", "shop", "S02", "FPS Marthandam");
            insertUser(conn, "Shop03", "shop@03", "shop", "S03", "FPS Colachel");
            insertUser(conn, "Shop04", "shop@04", "shop", "S04", "FPS Kanyakumari");
            insertUser(conn, "Shop05", "shop@05", "shop", "S05", "FPS Thuckalay");

            // 2. 3 Warehouses
            String whSql = "INSERT INTO warehouses (id, name, location, distance, rice_available, wheat_available, sugar_available, rice_reserved, wheat_reserved, sugar_reserved, last_update_date, last_update_time, last_update_by, last_update_reason) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(whSql)) {
                // W01 Central
                ps.setString(1, "W01");
                ps.setString(2, "Valam Central Warehouse");
                ps.setString(3, "Central Area (Nagercoil)");
                ps.setString(4, "5 km");
                ps.setInt(5, 5000);
                ps.setInt(6, 3200);
                ps.setInt(7, 1500);
                ps.setInt(8, 800);
                ps.setInt(9, 400);
                ps.setInt(10, 200);
                ps.setString(11, "2026-10-24");
                ps.setString(12, "13:16:00");
                ps.setString(13, "Wh01 Staff");
                ps.setString(14, "Request REQ-106 processed (Shop S06)");
                ps.executeUpdate();

                // W02 North
                ps.setString(1, "W02");
                ps.setString(2, "Valam North Warehouse");
                ps.setString(3, "North Area (Marthandam)");
                ps.setString(4, "8 km");
                ps.setInt(5, 4500);
                ps.setInt(6, 2800);
                ps.setInt(7, 1200);
                ps.setInt(8, 550);
                ps.setInt(9, 350);
                ps.setInt(10, 180);
                ps.setString(11, "2026-10-22");
                ps.setString(12, "09:46:00");
                ps.setString(13, "Wh02 Staff");
                ps.setString(14, "Request REQ-102 processed (Shop S02)");
                ps.executeUpdate();

                // W03 South
                ps.setString(1, "W03");
                ps.setString(2, "Valam South Warehouse");
                ps.setString(3, "South Area (Kanyakumari)");
                ps.setString(4, "3 km");
                ps.setInt(5, 4000);
                ps.setInt(6, 2500);
                ps.setInt(7, 1000);
                ps.setInt(8, 600);
                ps.setInt(9, 300);
                ps.setInt(10, 150);
                ps.setString(11, "2026-10-23");
                ps.setString(12, "09:46:00");
                ps.setString(13, "Wh03 Staff");
                ps.setString(14, "Request REQ-104 processed (Shop S04)");
                ps.executeUpdate();
            }

            // 3. 8 Fair-Price Shops
            String shopSql = "INSERT INTO shops (id, name, location, nearest_wh, nearest_wh_name, distance, status) VALUES (?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(shopSql)) {
                String[][] shops = {
                        {"S01", "FPS Nagercoil", "Nagercoil Town", "W01", "Valam Central Warehouse", "5 km", "Dispatched"},
                        {"S02", "FPS Marthandam", "Marthandam Central", "W02", "Valam North Warehouse", "8 km", "Delivered"},
                        {"S03", "FPS Colachel", "Colachel Port", "W01", "Valam Central Warehouse", "6 km", "Pending"},
                        {"S04", "FPS Kanyakumari", "Kanyakumari Beach Road", "W03", "Valam South Warehouse", "3 km", "In Transit"},
                        {"S05", "FPS Thuckalay", "Thuckalay Market", "W01", "Valam Central Warehouse", "7 km", "Pending"},
                        {"S06", "FPS Padmanabhapuram", "Padmanabhapuram Fort", "W01", "Valam Central Warehouse", "9 km", "Dispatched"},
                        {"S07", "FPS Karungal", "Karungal Junction", "W02", "Valam North Warehouse", "11 km", "Delivered"},
                        {"S08", "FPS Kulasekharam", "Kulasekharam Bus Stand", "W03", "Valam South Warehouse", "14 km", "Pending"}
                };
                for (String[] sh : shops) {
                    ps.setString(1, sh[0]);
                    ps.setString(2, sh[1]);
                    ps.setString(3, sh[2]);
                    ps.setString(4, sh[3]);
                    ps.setString(5, sh[4]);
                    ps.setString(6, sh[5]);
                    ps.setString(7, sh[6]);
                    ps.executeUpdate();
                }
            }

            // 4. 3 Vehicles
            String vehSql = "INSERT INTO vehicles (id, vehicle_number, vehicle_type, capacity, availability, status, assigned_request) VALUES (?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(vehSql)) {
                ps.setString(1, "V01");
                ps.setString(2, "TN-74-AA-1234");
                ps.setString(3, "Eicher 14ft");
                ps.setString(4, "3000 kg");
                ps.setString(5, "On Trip");
                ps.setString(6, "Dispatched");
                ps.setString(7, "REQ-101");
                ps.executeUpdate();

                ps.setString(1, "V02");
                ps.setString(2, "TN-74-BB-5678");
                ps.setString(3, "Tata 407");
                ps.setString(4, "2500 kg");
                ps.setString(5, "Available");
                ps.setString(6, "Idle / Ready");
                ps.setString(7, null);
                ps.executeUpdate();

                ps.setString(1, "V03");
                ps.setString(2, "TN-74-CC-9012");
                ps.setString(3, "Ashok Leyland");
                ps.setString(4, "1500 kg");
                ps.setString(5, "On Trip");
                ps.setString(6, "In Transit");
                ps.setString(7, "REQ-104");
                ps.executeUpdate();
            }

            // 5. Baseline Requests (REQ-101 to REQ-108)
            String reqSql = "INSERT INTO requests (id, shop_id, warehouse_id, warehouse_name, rice, wheat, sugar, urgency, status, vehicle, route, submitted_date, submitted_time, processed_date, processed_time, updated_date, updated_time) " +
                    "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(reqSql)) {
                insertReq(ps, "REQ-101", "S01", "W01", "Valam Central Warehouse", 200, 100, 50, "critical", "Dispatched", "V01", "Valam Central Warehouse → FPS Nagercoil", "2026-10-20", "09:15:00", "2026-10-20", "10:30:00", "2026-10-21", "08:30:00");
                insertReq(ps, "REQ-102", "S02", "W02", "Valam North Warehouse", 300, 150, 80, "normal", "Delivered", "V02", "Valam North Warehouse → FPS Marthandam", "2026-10-18", "11:00:00", "2026-10-18", "14:20:00", "2026-10-19", "11:45:00");
                insertReq(ps, "REQ-103", "S03", "W01", "Valam Central Warehouse", 500, 400, 50, "normal", "Pending", "Not Assigned", "Valam Central Warehouse → FPS Colachel", "2026-10-25", "10:00:00", null, null, "2026-10-25", "10:00:00");
                insertReq(ps, "REQ-104", "S04", "W03", "Valam South Warehouse", 250, 200, 100, "urgent", "In Transit", "V03", "Valam South Warehouse → FPS Kanyakumari", "2026-10-22", "09:00:00", "2026-10-22", "09:45:00", "2026-10-23", "09:15:00");
                insertReq(ps, "REQ-105", "S05", "W01", "Valam Central Warehouse", 400, 250, 75, "normal", "Pending", "Not Assigned", "Valam Central Warehouse → FPS Thuckalay", "2026-10-26", "14:30:00", null, null, "2026-10-26", "14:30:00");
                insertReq(ps, "REQ-106", "S06", "W01", "Valam Central Warehouse", 350, 200, 60, "normal", "Dispatched", "V01", "Valam Central Warehouse → FPS Padmanabhapuram", "2026-10-24", "11:00:00", "2026-10-24", "13:15:00", "2026-10-25", "07:30:00");
                insertReq(ps, "REQ-107", "S07", "W02", "Valam North Warehouse", 200, 100, 40, "normal", "Delivered", "V02", "Valam North Warehouse → FPS Karungal", "2026-10-15", "08:30:00", "2026-10-15", "10:00:00", "2026-10-17", "16:00:00");
                insertReq(ps, "REQ-108", "S08", "W03", "Valam South Warehouse", 300, 200, 120, "normal", "Pending", "Not Assigned", "Valam South Warehouse → FPS Kulasekharam", "2026-10-27", "15:45:00", null, null, "2026-10-27", "15:45:00");
            }

            // 6. Request Timeline
            String tlSql = "INSERT INTO request_timeline (request_id, stage_title, event_date, event_time, status, performed_by, details, stage_order) VALUES (?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(tlSql)) {
                insertTimeline(ps, "REQ-101", "Request Submitted", "2026-10-20", "09:15:00", "Pending", "Shop01", "Commodity request submitted: Rice: 200 kg, Wheat: 100 kg, Sugar: 50 kg.", 1);
                insertTimeline(ps, "REQ-101", "Request Accepted / Reserved", "2026-10-20", "10:30:00", "Reserved", "Wh01", "Warehouse staff accepted request. Stock reserved.", 2);
                insertTimeline(ps, "REQ-101", "Vehicle Assigned", "2026-10-21", "08:00:00", "Reserved", "Wh01", "Vehicle V01 (TN-74-AA-1234, Eicher 14ft) assigned.", 3);
                insertTimeline(ps, "REQ-101", "Dispatched", "2026-10-21", "08:30:00", "Dispatched", "Wh01", "Consignment dispatched via Vehicle V01 to FPS Nagercoil.", 4);

                insertTimeline(ps, "REQ-102", "Request Submitted", "2026-10-18", "11:00:00", "Pending", "Shop02", "Commodity request submitted: Rice: 300 kg, Wheat: 150 kg, Sugar: 80 kg.", 1);
                insertTimeline(ps, "REQ-102", "Request Accepted / Reserved", "2026-10-18", "14:20:00", "Reserved", "Wh02", "Stock reserved at Valam North Warehouse.", 2);
                insertTimeline(ps, "REQ-102", "Vehicle Assigned", "2026-10-19", "07:45:00", "Reserved", "Wh02", "Vehicle V02 (TN-74-BB-5678, Tata 407) assigned.", 3);
                insertTimeline(ps, "REQ-102", "Dispatched", "2026-10-19", "08:15:00", "Dispatched", "Wh02", "Dispatched from Valam North Warehouse.", 4);
                insertTimeline(ps, "REQ-102", "In Transit", "2026-10-19", "09:30:00", "In Transit", "System", "Vehicle V02 en route to FPS Marthandam.", 5);
                insertTimeline(ps, "REQ-102", "Delivered", "2026-10-19", "11:45:00", "Delivered", "Shop02", "Delivery confirmed and accepted by Shop S02 staff.", 6);

                insertTimeline(ps, "REQ-103", "Request Submitted", "2026-10-25", "10:00:00", "Pending", "Shop03", "Commodity request submitted: Rice: 500 kg, Wheat: 400 kg, Sugar: 50 kg.", 1);

                insertTimeline(ps, "REQ-104", "Request Submitted", "2026-10-22", "09:00:00", "Pending", "Shop04", "Commodity request submitted: Rice: 250 kg, Wheat: 200 kg, Sugar: 100 kg.", 1);
                insertTimeline(ps, "REQ-104", "Request Accepted / Reserved", "2026-10-22", "09:45:00", "Reserved", "Wh03", "Stock reserved at Valam South Warehouse.", 2);
                insertTimeline(ps, "REQ-104", "Vehicle Assigned", "2026-10-23", "07:30:00", "Reserved", "Wh03", "Vehicle V03 (TN-74-CC-9012, Ashok Leyland) assigned.", 3);
                insertTimeline(ps, "REQ-104", "Dispatched", "2026-10-23", "08:00:00", "Dispatched", "Wh03", "Dispatched from Valam South Warehouse via V03.", 4);
                insertTimeline(ps, "REQ-104", "In Transit", "2026-10-23", "09:15:00", "In Transit", "System", "Vehicle V03 en route to FPS Kanyakumari.", 5);

                insertTimeline(ps, "REQ-105", "Request Submitted", "2026-10-26", "14:30:00", "Pending", "Shop05", "Commodity request submitted: Rice: 400 kg, Wheat: 250 kg, Sugar: 75 kg.", 1);

                insertTimeline(ps, "REQ-106", "Request Submitted", "2026-10-24", "11:00:00", "Pending", "Shop01", "Commodity request submitted for Shop S06: Rice: 350 kg, Wheat: 200 kg, Sugar: 60 kg.", 1);
                insertTimeline(ps, "REQ-106", "Request Accepted / Reserved", "2026-10-24", "13:15:00", "Reserved", "Wh01", "Stock reserved at Valam Central Warehouse.", 2);
                insertTimeline(ps, "REQ-106", "Vehicle Assigned", "2026-10-25", "07:00:00", "Reserved", "Wh01", "Vehicle V01 assigned to REQ-106.", 3);
                insertTimeline(ps, "REQ-106", "Dispatched", "2026-10-25", "07:30:00", "Dispatched", "Wh01", "Dispatched from Valam Central Warehouse to FPS Padmanabhapuram.", 4);

                insertTimeline(ps, "REQ-107", "Request Submitted", "2026-10-15", "08:30:00", "Pending", "Shop01", "Commodity request submitted for Shop S07: Rice: 200 kg, Wheat: 100 kg, Sugar: 40 kg.", 1);
                insertTimeline(ps, "REQ-107", "Request Accepted / Reserved", "2026-10-15", "10:00:00", "Reserved", "Wh02", "Stock reserved at Valam North Warehouse.", 2);
                insertTimeline(ps, "REQ-107", "Dispatched", "2026-10-16", "08:00:00", "Dispatched", "Wh02", "Dispatched via Vehicle V02.", 3);
                insertTimeline(ps, "REQ-107", "Delivered", "2026-10-17", "16:00:00", "Delivered", "Shop01", "Delivery confirmed by FPS Karungal staff.", 4);

                insertTimeline(ps, "REQ-108", "Request Submitted", "2026-10-27", "15:45:00", "Pending", "Shop01", "Commodity request submitted for Shop S08: Rice: 300 kg, Wheat: 200 kg, Sugar: 120 kg.", 1);
            }

            // 7. Stock Update History
            String stSql = "INSERT INTO stock_update_history (update_id, warehouse_id, commodity, previous_stock, quantity_added, quantity_deducted, current_stock, reason, log_date, log_time, updated_by) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(stSql)) {
                insertStockLog(ps, "STK-3001", "W01", "Rice", 5200, 0, 200, 5000, "Stock reserved for REQ-101 (Shop S01)", "2026-10-20", "10:31:00", "Wh01 Staff");
                insertStockLog(ps, "STK-3002", "W01", "Wheat", 3300, 0, 100, 3200, "Stock reserved for REQ-101 (Shop S01)", "2026-10-20", "10:31:00", "Wh01 Staff");
                insertStockLog(ps, "STK-3003", "W01", "Sugar", 1550, 0, 50, 1500, "Stock reserved for REQ-101 (Shop S01)", "2026-10-20", "10:31:00", "Wh01 Staff");

                insertStockLog(ps, "STK-3004", "W02", "Rice", 4800, 0, 300, 4500, "Stock reserved for REQ-102 (Shop S02)", "2026-10-18", "14:21:00", "Wh02 Staff");
                insertStockLog(ps, "STK-3005", "W02", "Wheat", 2950, 0, 150, 2800, "Stock reserved for REQ-102 (Shop S02)", "2026-10-18", "14:21:00", "Wh02 Staff");
                insertStockLog(ps, "STK-3006", "W02", "Sugar", 1280, 0, 80, 1200, "Stock reserved for REQ-102 (Shop S02)", "2026-10-18", "14:21:00", "Wh02 Staff");

                insertStockLog(ps, "STK-3007", "W03", "Rice", 4250, 0, 250, 4000, "Stock reserved for REQ-104 (Shop S04)", "2026-10-22", "09:46:00", "Wh03 Staff");
                insertStockLog(ps, "STK-3008", "W03", "Wheat", 2700, 0, 200, 2500, "Stock reserved for REQ-104 (Shop S04)", "2026-10-22", "09:46:00", "Wh03 Staff");
                insertStockLog(ps, "STK-3009", "W03", "Sugar", 1100, 0, 100, 1000, "Stock reserved for REQ-104 (Shop S04)", "2026-10-22", "09:46:00", "Wh03 Staff");

                insertStockLog(ps, "STK-3010", "W01", "Rice", 4500, 500, 0, 5000, "Consignment received from FCI depot", "2026-10-24", "13:16:00", "Wh01 Staff");
                insertStockLog(ps, "STK-3011", "W03", "Rice", 3500, 500, 0, 4000, "Routine stock replenishment", "2026-10-28", "10:00:00", "Wh03 Staff");
            }

            // 8. Operation History (OP-001 to OP-030)
            String opSql = "INSERT INTO operation_history (op_id, request_id, entity_id, category, operation_type, prev_status, new_status, op_date, op_time, updated_by, details) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);";
            try (PreparedStatement ps = conn.prepareStatement(opSql)) {
                insertOp(ps, "OP-001", "REQ-101", "S01", "request", "Request Received", "—", "Pending", "2026-10-20", "09:15:00", "Shop01", "Shop S01 (FPS Nagercoil) submitted commodity request REQ-101. Rice: 200 kg, Wheat: 100 kg, Sugar: 50 kg. Urgency: Critical. Warehouse: Valam Central Warehouse.");
                insertOp(ps, "OP-002", "REQ-101", "S01", "request", "Request Accepted", "Pending", "Reserved", "2026-10-20", "10:30:00", "Wh01", "Warehouse staff accepted request REQ-101 from Shop S01. Status changed from Pending to Reserved.");
                insertOp(ps, "OP-003", "REQ-101", "W01", "warehouse", "Stock Updated", "Rice: 5200 kg", "Rice: 5000 kg", "2026-10-20", "10:31:00", "Wh01", "Stock reserved for REQ-101 at Valam Central Warehouse (W01). Rice: 5200→5000 kg (-200), Wheat: 3300→3200 kg (-100), Sugar: 1550→1500 kg (-50).");
                insertOp(ps, "OP-004", "REQ-101", "V01", "vehicle", "Vehicle Assigned", "Available", "On Trip", "2026-10-21", "08:00:00", "Wh01", "Vehicle V01 (TN-74-AA-1234, Eicher 14ft) assigned to REQ-101. Route: Valam Central Warehouse → FPS Nagercoil (S01).");
                insertOp(ps, "OP-005", "REQ-101", "S01", "delivery", "Dispatched", "Reserved", "Dispatched", "2026-10-21", "08:30:00", "Wh01", "REQ-101 dispatched from Valam Central Warehouse via Vehicle V01 to Shop S01 (FPS Nagercoil). Delivery status: Dispatched.");

                insertOp(ps, "OP-006", "REQ-102", "S02", "request", "Request Received", "—", "Pending", "2026-10-18", "11:00:00", "Shop02", "Shop S02 (FPS Marthandam) submitted commodity request REQ-102. Rice: 300 kg, Wheat: 150 kg, Sugar: 80 kg. Urgency: Normal. Warehouse: Valam North Warehouse.");
                insertOp(ps, "OP-007", "REQ-102", "S02", "request", "Request Accepted", "Pending", "Reserved", "2026-10-18", "14:20:00", "Wh02", "Warehouse staff accepted request REQ-102 from Shop S02. Status changed from Pending to Reserved.");
                insertOp(ps, "OP-008", "REQ-102", "W02", "warehouse", "Stock Updated", "Rice: 4800 kg", "Rice: 4500 kg", "2026-10-18", "14:21:00", "Wh02", "Stock reserved for REQ-102 at Valam North Warehouse (W02). Rice: 4800→4500 kg (-300), Wheat: 2950→2800 kg (-150), Sugar: 1280→1200 kg (-80).");
                insertOp(ps, "OP-009", "REQ-102", "V02", "vehicle", "Vehicle Assigned", "Available", "On Trip", "2026-10-19", "07:45:00", "Wh02", "Vehicle V02 (TN-74-BB-5678, Tata 407) assigned to REQ-102. Route: Valam North Warehouse → FPS Marthandam (S02).");
                insertOp(ps, "OP-010", "REQ-102", "S02", "delivery", "Dispatched", "Reserved", "Dispatched", "2026-10-19", "08:15:00", "Wh02", "REQ-102 dispatched from Valam North Warehouse via Vehicle V02 to Shop S02 (FPS Marthandam).");
                insertOp(ps, "OP-011", "REQ-102", "S02", "delivery", "In Transit", "Dispatched", "In Transit", "2026-10-19", "09:30:00", "System", "REQ-102 delivery is in transit. Vehicle V02 en route from Valam North Warehouse to FPS Marthandam.");
                insertOp(ps, "OP-012", "REQ-102", "S02", "delivery", "Delivered", "In Transit", "Delivered", "2026-10-19", "11:45:00", "Shop02", "REQ-102 successfully delivered to FPS Marthandam (S02). Vehicle V02 returned. Delivery confirmed by shop staff.");
                insertOp(ps, "OP-013", "REQ-102", "V02", "vehicle", "Vehicle Released", "On Trip", "Available", "2026-10-19", "12:30:00", "Wh02", "Vehicle V02 (TN-74-BB-5678) released after completing delivery of REQ-102. Status: Available / Idle.");

                insertOp(ps, "OP-014", "REQ-103", "S03", "request", "Request Received", "—", "Pending", "2026-10-25", "10:00:00", "Shop03", "Shop S03 (FPS Colachel) submitted commodity request REQ-103. Rice: 500 kg, Wheat: 400 kg, Sugar: 50 kg. Urgency: Normal. Warehouse: Valam Central Warehouse. Awaiting processing.");

                insertOp(ps, "OP-015", "REQ-104", "S04", "request", "Request Received", "—", "Pending", "2026-10-22", "09:00:00", "Shop04", "Shop S04 (FPS Kanyakumari) submitted commodity request REQ-104. Rice: 250 kg, Wheat: 200 kg, Sugar: 100 kg. Urgency: Urgent. Warehouse: Valam South Warehouse.");
                insertOp(ps, "OP-016", "REQ-104", "S04", "request", "Request Accepted", "Pending", "Reserved", "2026-10-22", "09:45:00", "Wh03", "Warehouse staff accepted urgent request REQ-104 from Shop S04 at Valam South Warehouse. Status changed from Pending to Reserved.");
                insertOp(ps, "OP-017", "REQ-104", "W03", "warehouse", "Stock Updated", "Rice: 4250 kg", "Rice: 4000 kg", "2026-10-22", "09:46:00", "Wh03", "Stock reserved for REQ-104 at Valam South Warehouse (W03). Rice: 4250→4000 kg (-250), Wheat: 2700→2500 kg (-200), Sugar: 1100→1000 kg (-100).");
                insertOp(ps, "OP-018", "REQ-104", "V03", "vehicle", "Vehicle Assigned", "Available", "On Trip", "2026-10-23", "07:30:00", "Wh03", "Vehicle V03 (TN-74-CC-9012, Ashok Leyland) assigned to REQ-104. Route: Valam South Warehouse → FPS Kanyakumari (S04).");
                insertOp(ps, "OP-019", "REQ-104", "S04", "delivery", "Dispatched", "Reserved", "Dispatched", "2026-10-23", "08:00:00", "Wh03", "REQ-104 dispatched from Valam South Warehouse via Vehicle V03 to Shop S04 (FPS Kanyakumari).");
                insertOp(ps, "OP-020", "REQ-104", "S04", "delivery", "In Transit", "Dispatched", "In Transit", "2026-10-23", "09:15:00", "System", "REQ-104 delivery is in transit. Vehicle V03 en route from Valam South Warehouse to FPS Kanyakumari.");

                insertOp(ps, "OP-021", "REQ-105", "S05", "request", "Request Received", "—", "Pending", "2026-10-26", "14:30:00", "Shop05", "Shop S05 (FPS Thuckalay) submitted commodity request REQ-105. Rice: 400 kg, Wheat: 250 kg, Sugar: 75 kg. Urgency: Normal. Warehouse: Valam Central Warehouse. Awaiting processing.");

                insertOp(ps, "OP-022", "REQ-106", "S06", "request", "Request Received", "—", "Pending", "2026-10-24", "11:00:00", "Shop01", "Shop S06 (FPS Padmanabhapuram) submitted commodity request REQ-106. Rice: 350 kg, Wheat: 200 kg, Sugar: 60 kg. Urgency: Normal. Warehouse: Valam Central Warehouse.");
                insertOp(ps, "OP-023", "REQ-106", "S06", "request", "Request Accepted", "Pending", "Reserved", "2026-10-24", "13:15:00", "Wh01", "Warehouse staff accepted request REQ-106 from Shop S06. Status changed from Pending to Reserved.");
                insertOp(ps, "OP-024", "REQ-106", "W01", "warehouse", "Stock Updated", "Rice: 5350 kg", "Rice: 5000 kg", "2026-10-24", "13:16:00", "Wh01", "Stock reserved for REQ-106 at Valam Central Warehouse (W01). Rice: 5350→5000 kg (-350), Wheat: 3400→3200 kg (-200), Sugar: 1560→1500 kg (-60).");
                insertOp(ps, "OP-025", "REQ-106", "V01", "vehicle", "Vehicle Assigned", "Available", "On Trip", "2026-10-25", "07:00:00", "Wh01", "Vehicle V01 (TN-74-AA-1234) assigned to REQ-106. Route: Valam Central Warehouse → FPS Padmanabhapuram (S06).");
                insertOp(ps, "OP-026", "REQ-106", "S06", "delivery", "Dispatched", "Reserved", "Dispatched", "2026-10-25", "07:30:00", "Wh01", "REQ-106 dispatched from Valam Central Warehouse via Vehicle V01 to Shop S06 (FPS Padmanabhapuram).");

                insertOp(ps, "OP-027", "REQ-107", "S07", "request", "Request Received", "—", "Pending", "2026-10-15", "08:30:00", "Shop01", "Shop S07 (FPS Karungal) submitted commodity request REQ-107. Rice: 200 kg, Wheat: 100 kg, Sugar: 40 kg. Urgency: Normal. Warehouse: Valam North Warehouse.");
                insertOp(ps, "OP-028", "REQ-107", "S07", "delivery", "Delivered", "In Transit", "Delivered", "2026-10-17", "16:00:00", "Shop01", "REQ-107 successfully delivered to FPS Karungal (S07). Delivery confirmed by shop staff.");

                insertOp(ps, "OP-029", "REQ-108", "S08", "request", "Request Received", "—", "Pending", "2026-10-27", "15:45:00", "Shop01", "Shop S08 (FPS Kulasekharam) submitted commodity request REQ-108. Rice: 300 kg, Wheat: 200 kg, Sugar: 120 kg. Urgency: Normal. Warehouse: Valam South Warehouse. Awaiting processing.");

                insertOp(ps, "OP-030", "REQ-108", "W03", "warehouse", "Stock Updated", "Rice: 4000 kg", "Rice: 4500 kg", "2026-10-28", "10:00:00", "Wh03", "Routine stock replenishment received at Valam South Warehouse (W03). Rice: +500 kg, Wheat: +300 kg, Sugar: +200 kg.");
            }

            conn.commit();
            System.out.println("[Database] Initial seeding completed successfully.");
        } catch (SQLException e) {
            conn.rollback();
            throw e;
        } finally {
            conn.setAutoCommit(true);
        }
    }

    private static void insertUser(Connection conn, String username, String password, String role, String entityId, String entityName) throws SQLException {
        String salt = PasswordUtil.generateSalt();
        String hash = PasswordUtil.hashPassword(password, salt);
        String sql = "INSERT INTO users (username, password_hash, salt, role, entity_id, entity_name) VALUES (?, ?, ?, ?, ?, ?);";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, role);
            ps.setString(5, entityId);
            ps.setString(6, entityName);
            ps.executeUpdate();
        }
    }

    private static void insertReq(PreparedStatement ps, String id, String shopId, String warehouseId, String warehouseName,
                                  int rice, int wheat, int sugar, String urgency, String status, String vehicle,
                                  String route, String subDate, String subTime, String procDate, String procTime,
                                  String upDate, String upTime) throws SQLException {
        ps.setString(1, id);
        ps.setString(2, shopId);
        ps.setString(3, warehouseId);
        ps.setString(4, warehouseName);
        ps.setInt(5, rice);
        ps.setInt(6, wheat);
        ps.setInt(7, sugar);
        ps.setString(8, urgency);
        ps.setString(9, status);
        ps.setString(10, vehicle);
        ps.setString(11, route);
        ps.setString(12, subDate);
        ps.setString(13, subTime);
        ps.setString(14, procDate);
        ps.setString(15, procTime);
        ps.setString(16, upDate);
        ps.setString(17, upTime);
        ps.executeUpdate();
    }

    private static void insertTimeline(PreparedStatement ps, String reqId, String title, String date, String time, String status, String by, String details, int order) throws SQLException {
        ps.setString(1, reqId);
        ps.setString(2, title);
        ps.setString(3, date);
        ps.setString(4, time);
        ps.setString(5, status);
        ps.setString(6, by);
        ps.setString(7, details);
        ps.setInt(8, order);
        ps.executeUpdate();
    }

    private static void insertStockLog(PreparedStatement ps, String updateId, String warehouseId, String commodity, int prev, int added, int deducted, int cur, String reason, String date, String time, String by) throws SQLException {
        ps.setString(1, updateId);
        ps.setString(2, warehouseId);
        ps.setString(3, commodity);
        ps.setInt(4, prev);
        ps.setInt(5, added);
        ps.setInt(6, deducted);
        ps.setInt(7, cur);
        ps.setString(8, reason);
        ps.setString(9, date);
        ps.setString(10, time);
        ps.setString(11, by);
        ps.executeUpdate();
    }

    private static void insertOp(PreparedStatement ps, String opId, String reqId, String entityId, String category, String opType, String prevStatus, String newStatus, String date, String time, String by, String details) throws SQLException {
        ps.setString(1, opId);
        ps.setString(2, reqId);
        ps.setString(3, entityId);
        ps.setString(4, category);
        ps.setString(5, opType);
        ps.setString(6, prevStatus);
        ps.setString(7, newStatus);
        ps.setString(8, date);
        ps.setString(9, time);
        ps.setString(10, by);
        ps.setString(11, details);
        ps.executeUpdate();
    }
}
