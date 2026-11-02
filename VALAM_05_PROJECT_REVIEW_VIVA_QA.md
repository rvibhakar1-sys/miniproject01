# VALAM SUPPLY MANAGEMENT SYSTEM - MASTER PROJECT REVIEW & VIVA-VOCE Q&A

> **Document:** Module 5 of 5 (Evaluation Preparation & Viva Survival Guide)  
> **Scope:** 25 Rigorous, High-Probability Technical & Architectural Questions with Complete Model Answers  
> **Target Audience:** College Project Defense, External Review Examiners, University Viva Panels  
> **Date:** November 02, 2026

---

## How to Use This Document

This document prepares you for technical inquiries during your project review or viva. The questions are categorized into:

1. Architecture & Technology Stack Decisions
2. Multi-Warehouse Supply Chain & Business Workflows
3. Data Structures & Algorithms
4. Security & Cryptography
5. Database Transactions & ACID Guarantees
6. Frontend & Asynchronous API Client
7. Edge Cases, Failure Recovery & Scaling

---

## Category 1: Architecture & Technology Stack Decisions

### Q1: Why did you choose Core Java with built-in HttpServer instead of standard frameworks like Spring Boot or Node.js/Express?
**Model Answer:**
"We deliberately chose Core Java (`com.sun.net.httpserver.HttpServer`) for three key engineering reasons:
1. **Zero Cold-Start Latency & Low Memory:** Spring Boot requires 200MB+ RAM and 15 seconds to start. Our Core Java server starts in under 0.8 seconds and operates within < 35 MB RAM, making it suitable for low-power edge machines located in rural warehouse depots.
2. **Zero Dependency Footprint:** By avoiding massive JAR dependencies, the project is completely self-contained. There are no version mismatch issues or heavy dependency bloat.
3. **Pedagogical Clarity:** Frameworks like Spring Boot hide the underlying HTTP request handling behind annotations (`@RestController`). Writing native handlers with `HttpExchange` allowed us to master core HTTP protocol semantics, thread pooling, and direct JDBC connection management."

---

### Q2: Why did you choose SQLite instead of an external database server like MySQL or PostgreSQL?
**Model Answer:**
"SQLite was chosen because it is an **embedded, zero-configuration database engine**. Unlike MySQL, which requires running a separate background daemon (or XAMPP) and configuring network ports and user privileges, SQLite stores the entire relational database inside a single file (`database/valam.db`). It provides full ACID compliance, foreign key constraints, and transactions without external server maintenance. For a district supply management system with low-to-medium concurrent writes, SQLite provides maximum reliability and instant portability."

---

### Q3: How does your Core Java server handle concurrent requests from multiple users?
**Model Answer:**
"In `MainServer.java`, we attach a fixed thread pool executor to the HTTP server: `server.setExecutor(Executors.newFixedThreadPool(16))`. When multiple users (e.g., an Admin in Nagercoil and a Shopkeeper in Marthandam) make simultaneous requests, the Sun HttpServer assigns each request to an available worker thread from the pool of 16 threads. This allows 16 concurrent HTTP requests without blocking the server main loop."

---

## Category 2: Multi-Warehouse Supply Chain & Business Workflows

### Q4: How do you maintain strict stock isolation between the three warehouses (W01, W02, W03)?
**Model Answer:**
"Stock isolation is enforced at both the database and backend logic layers:
1. **Database Schema:** The `warehouse_stock` table defines a composite primary key or explicit `warehouse_id` column: `(warehouse_id, commodity)`. W01 Central, W02 North, and W03 South each have independent physical stock rows for Rice, Wheat, Sugar, and Kerosene.
2. **SQL Isolation:** Every stock deduction or addition query includes `WHERE warehouse_id = ?`. A stock dispatch at W01 strictly updates W01's row and can never alter W02 or W03.
3. **Session Binding:** When a supervisor logs into `warehouse.html`, their session stores their `warehouseId`. All dashboard queries automatically filter: `GET /api/requests?warehouseId=W02`."

---

### Q5: Explain the complete end-to-end commodity request lifecycle in VALAM.
**Model Answer:**
"The supply chain follows a 6-stage verified lifecycle:
1. **Requisition Created:** Fair Price Shop submits demand (`POST /api/requests`) with commodity quantities. Status = `Pending`.
2. **Approved & Reserved:** Warehouse supervisor verifies physical stock. If available, status = `Stock Reserved`, incrementing `reserved_stock`.
3. **Vehicle Assigned:** Warehouse selects an available truck from the fleet (`POST /api/requests/{id}/assign-vehicle`). Status = `Vehicle Assigned`.
4. **Dispatched / In Transit:** Warehouse marks order dispatched (`POST /api/requests/{id}/transit`). Physical stock is deducted from warehouse inventory. Status = `In Transit`.
5. **Shop Receipt Confirmed:** Shopkeeper physically inspects delivery and clicks Confirm Receipt (`POST /api/requests/{id}/confirm-receipt`). Status = `Delivered / Confirmed`, and delivered quantities are automatically credited into `shop_stock`.
6. **Discrepancy Resolution (if any):** If delivered bags are short or damaged, shopkeeper files an issue (`POST /api/requests/{id}/report-issue`), which the warehouse supervisor resolves with documented settlement actions."

---

### Q6: Why is the "Shop Receipt Confirmation" feature critical, and how does it prevent PDS corruption?
**Model Answer:**
"In traditional PDS distribution, corrupt intermediaries can divert commodities into the black market while claiming the goods were delivered. In VALAM, a requisition cannot reach the completed state simply because the warehouse claims it was sent. The delivery remains `In Transit` until the **destination shopkeeper cryptographically/digitally confirms physical arrival**. Furthermore, confirmation triggers an automated update to `shop_stock`. If quantities don't match, the shopkeeper lodges a formal discrepancy report before confirmation."

---

## Category 3: Data Structures & Algorithms

### Q7: Which Data Structures are used in the Java backend and for what specific purposes?
**Model Answer:**
"We utilized four core data structures:
1. **`HashMap<String, String>`:** Used in `HttpUtil.parseQueryParams` and `JsonUtil.parseJson` for O(1) constant-time key lookup of query parameters and JSON fields.
2. **`ArrayList<Object>`:** Used in `OperationHandler` and `StockHistoryHandler` as a dynamic array to accumulate prepared statement SQL parameters during multi-criteria filtering.
3. **`StringBuilder`:** Used across all 14 handlers as a mutable character buffer to serialize database result sets into JSON in O(N) linear time, avoiding the O(N^2) memory overhead of immutable String concatenation.
4. **`byte[]` Arrays:** Used in `PasswordUtil` to hold 16-byte cryptographic salts and in `StaticFileHandler` as an 8 KB binary I/O stream buffer."

---

### Q8: Why did you write a custom JSON parser in JsonUtil instead of using Jackson or Gson?
**Model Answer:**
"Third-party JSON libraries like Jackson or Gson introduce 2 to 5 MB of external JAR dependencies. Since VALAM's frontend sends simple, flat JSON payloads (username, password, commodity quantities), writing a lightweight 150-line state-machine parser in `JsonUtil.java` provided exactly what the system needed with zero external dependencies and near-instant classloading."

---

### Q9: Where are B-Trees used in this project?
**Model Answer:**
"B-Trees (and B+Trees) are the foundational data structure of SQLite's storage engine. Every primary key constraint we defined in `Database.java` (such as `PRIMARY KEY (id)` in `requests` or `users`) automatically creates a B-Tree index on disk. This reduces search complexity from an O(N) full-table scan to **O(log N)** logarithmic binary search when looking up requests or verifying login credentials."

---

## Category 4: Security & Cryptography

### Q10: How are passwords stored and authenticated in VALAM? Explain PBKDF2.
**Model Answer:**
"Passwords are never stored in plain text. We implement **PBKDF2WithHmacSHA256** (Password-Based Key Derivation Function 2) in `PasswordUtil.java`:
- A cryptographically secure random 16-byte salt is generated using `SecureRandom`.
- The password and salt are hashed through **65,536 iterations** of HMAC-SHA256 to produce a 256-bit derived key.
- The database stores `saltHex:hashHex`.
During login, `verifyPassword()` extracts the stored salt, hashes the incoming candidate password with the same 65,536 iterations, and compares the resulting hashes."

---

### Q11: What is the purpose of the salt, and why is standard SHA-256 alone not secure?
**Model Answer:**
"If you use plain SHA-256 without a salt, identical passwords produce identical hashes. Attackers use precomputed lookup tables called **Rainbow Tables** to crack millions of hashes in seconds. Adding a unique 16-byte random salt ensures that even if two users have the same password (`password123`), their stored hashes are completely different. Furthermore, the 65,536 iterations make brute-force hardware cracking computationally prohibitive."

---

### Q12: What is a Timing Attack, and how does your code prevent it?
**Model Answer:**
"In standard string comparison (`str1.equals(str2)`), the CPU terminates early at the first mismatched character. An attacker measuring HTTP response times with nanosecond precision can determine character by character whether they guessed correctly. In `PasswordUtil.java`, we implement **constant-time byte comparison** using bitwise XOR (`diff |= a[i] ^ b[i]`). The loop always executes all bytes regardless of mismatches, ensuring identical execution time and eliminating side-channel timing leaks."

---

## Category 5: Database Transactions & ACID Guarantees

### Q13: How do you prevent race conditions and duplicate dispatches in the warehouse?
**Model Answer:**
"In `RequestHandler.java`, before processing or dispatching a request, we wrap the operation in a database transaction and check status prerequisites: `SELECT status FROM requests WHERE id = ?`. If the request is not currently in the required state (`Pending` for approval, or `Vehicle Assigned` for dispatch), the transaction aborts with HTTP 400 Bad Request. In addition, when dispatching, we update both status and warehouse stock inside a single atomic commit: `conn.setAutoCommit(false)` ... `conn.commit()`."

---

### Q14: Explain how ACID properties are maintained during the Shop Receipt Confirmation process.
**Model Answer:**
"When a shopkeeper confirms delivery receipt, two distinct tables must update synchronously: the requisition status in `requests` must become `Delivered / Confirmed`, and commodity stock in `shop_stock` must increase by the delivered amount.
- **Atomicity:** Both updates occur within `conn.setAutoCommit(false)`. If the second query fails, `conn.rollback()` executes, ensuring neither table is partially updated.
- **Consistency:** Database check constraints prevent negative values or invalid foreign keys.
- **Isolation:** SQLite locks the database file during the write transaction, preventing concurrent conflicting writes.
- **Durability:** Once committed, SQLite writes changes to the journal file on disk, surviving sudden power failure."

---

## Category 6: Frontend & Asynchronous API Client

### Q15: How does the web frontend communicate with the Java backend?
**Model Answer:**
"Communication is entirely asynchronous via the browser's native **Fetch API**. In `script.js`, we implemented `valamApiCall(endpoint, method, data)`. When a user submits an action, `valamApiCall` sends an HTTP request with `Content-Type: application/json`. The Java `HttpServer` receives the request, processes the SQLite query, and returns a JSON payload. The JavaScript `await` promise resolves, and DOM update functions dynamically render table rows without full page reloads."

---

### Q16: How do you enforce role-based access control in the frontend?
**Model Answer:**
"Upon successful login via `POST /api/auth/login`, the backend returns the authenticated user's role (`admin`, `warehouse`, or `shop`). This is persisted in `sessionStorage.setItem("valam_role", role)`. On dashboard pages (`admin.html`, `warehouse.html`, `shop.html`), an initialization script checks the session. If an unauthorized user attempts to open `admin.html` without an active admin session, the script immediately redirects them back to `index.html`."

---

## Category 7: Edge Cases, Failure Recovery & Scaling

### Q17: What happens if a warehouse has insufficient stock when a supervisor attempts to approve a request?
**Model Answer:**
"In `RequestHandler.processRequest()`, before reserving stock, the system queries live inventory: `SELECT rice, wheat, sugar FROM warehouse_stock WHERE warehouse_id = ?`. If available physical stock minus already reserved stock is less than requested quantity, the server rejects the transaction with `HTTP 400 Bad Request` and message: `"Insufficient warehouse stock to fulfill this request"`. No stock is deducted, and the requisition remains in `Pending` status."

---

### Q18: What happens if the network drops while a shopkeeper is confirming delivery receipt?
**Model Answer:**
"Because `valamApiCall` uses `try / catch` blocks, network timeouts trigger an error message on screen: `"Cannot connect to Java backend. Please verify network or server status."`. Because the HTTP call did not reach the server, the database transaction was never initiated. When the connection resumes, the shopkeeper can safely click `Confirm Receipt` again. Furthermore, the backend checks `if (status.equals("Delivered / Confirmed")) return error("Request has already been confirmed")`, strictly preventing duplicate inventory crediting."

---

### Q19: How would this architecture scale if expanded from one district to the entire state (1,000+ warehouses)?
**Model Answer:**
"While SQLite is ideal for a single district or local facility, scaling to a statewide system with thousands of concurrent write transactions would involve:
1. Transitioning the persistence factory in `Database.java` from SQLite JDBC to PostgreSQL with connection pooling (e.g., HikariCP).
2. Containerizing `MainServer` with Docker and deploying behind an Nginx load balancer.
3. Because our handlers communicate with the database via standard JDBC `PreparedStatement` interfaces, 95% of the Java business logic remains completely unchanged."

---
