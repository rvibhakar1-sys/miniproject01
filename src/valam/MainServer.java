package valam;

import com.sun.net.httpserver.HttpServer;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Executors;

public class MainServer {
    private static final int DEFAULT_PORT = 8080;

    public static void main(String[] args) {
        int port = DEFAULT_PORT;
        if (args.length > 0) {
            try {
                port = Integer.parseInt(args[0]);
            } catch (NumberFormatException ignored) {}
        }

        System.out.println("=============================================================");
        System.out.println("  VALAM Supply Management System - Core Java + SQLite Backend");
        System.out.println("=============================================================");

        // 1. Initialize SQLite Database & Tables
        Database.init();

        // 2. Start HTTP Server
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);

            // API Endpoints
            server.createContext("/api/auth", new AuthHandler());
            server.createContext("/api/shops", new ShopHandler());
            server.createContext("/api/warehouses", new WarehouseHandler());
            server.createContext("/api/vehicles", new VehicleHandler());
            server.createContext("/api/requests", new RequestHandler());
            server.createContext("/api/stock-history", new StockHistoryHandler());
            server.createContext("/api/operations", new OperationHandler());
            server.createContext("/api/delivery-issues", new DeliveryIssueHandler());

            // Static Files
            File webRoot = new File(".").getCanonicalFile();
            server.createContext("/", new StaticFileHandler(webRoot));

            // Thread pool for concurrent requests
            server.setExecutor(Executors.newFixedThreadPool(10));
            server.start();

            System.out.println("\n[Server] VALAM Java HTTP Server started successfully!");
            System.out.println("[Server] Listening on: http://localhost:" + port);
            System.out.println("\n  Available Portals:");
            System.out.println("    - Login Portal:     http://localhost:" + port + "/");
            System.out.println("    - Admin Portal:     http://localhost:" + port + "/admin.html");
            System.out.println("    - Warehouse Portal: http://localhost:" + port + "/warehouse.html");
            System.out.println("    - Shop Portal:      http://localhost:" + port + "/shop.html");
            System.out.println("\n  Database: database/valam.db");
            System.out.println("=============================================================\n");

        } catch (IOException e) {
            System.err.println("[Server] Failed to start HTTP server: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

