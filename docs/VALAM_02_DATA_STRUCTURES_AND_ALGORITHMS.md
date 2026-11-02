# VALAM SUPPLY MANAGEMENT SYSTEM - DATA STRUCTURES & ALGORITHMS GUIDE

> **Document:** Module 2 of 5 (Computer Science & Engineering Concepts)  
> **Scope:** Deep-dive into all Data Structures, Memory Models, Time/Space Complexities, and Algorithms implemented in VALAM  
> **Target Audience:** Technical Interviewers, Project Viva Reviewers, Academic Evaluators  
> **Date:** November 02, 2026

---

## 1. Executive Summary

In academic evaluations, examiners look closely at how core Computer Science principles are applied in a practical project. In VALAM, data structures and algorithms were intentionally selected to achieve:

1. **O(1) Constant-Time Lookups** for HTTP request routing and parameter decoding.
2. **O(N) Amortized Appends & Zero Memory Reallocation Overhead** using mutable string buffers.
3. **Cryptographic Protection** against side-channel timing analysis and brute-force GPU attacks.
4. **Atomic State Transactions** ensuring stock consistency without race conditions.

---

## 2. Comprehensive Data Structures Matrix

| Data Structure | Java Type / Construct | Where Used in VALAM | Specific Purpose in the System | Time Complexity | Space Complexity |
| :--- | :--- | :--- | :--- | :---: | :---: |
| **Hash Table / Map** | `Map<String, String>`<br/>`HashMap<String, String>` | `HttpUtil.java`<br/>`JsonUtil.java`<br/>`AuthHandler.java` | 1. URL Query parameter decoding (`?warehouseId=W01`).<br/>2. Key-value payload extraction from client JSON requests. | **Lookup:** O(1)<br/>**Insert:** O(1) | O(K) where K = number of parameters |
| **Dynamic Array** | `List<Object>`<br/>`ArrayList<Object>` | `OperationHandler.java`<br/>`StockHistoryHandler.java`<br/>`DeliveryIssueHandler.java` | Dynamic SQL PreparedStatement parameter binding for multi-criteria filters without SQL injection. | **Append:** O(1) amortized<br/>**Iteration:** O(N) | O(N) contiguous memory |
| **Mutable Character Buffer** | `StringBuilder` | All 14 Java Handlers | Streaming JSON construction and dynamic SQL query assembly, avoiding expensive immutable String re-allocations. | **Append:** O(1) amortized<br/>**Total:** O(N) | O(N) expandable buffer |
| **Byte Array (Memory Buffer)** | `byte[]` | `PasswordUtil.java`<br/>`StaticFileHandler.java`<br/>`HttpUtil.java` | 1. 16-byte random salt storage for cryptographic hashing.<br/>2. 8 KB I/O stream buffer for high-speed file streaming. | **Access:** O(1)<br/>**Streaming:** O(N) | Fixed byte allocation |
| **Fixed Array** | `String[]` | `RequestHandler.java`<br/>`MainServer.java` | REST URI tokenization (`path.split("/")`) and static commodity enumeration (`{"rice", "wheat", "sugar"}`). | **Access:** O(1) | O(C) where C = segments |
| **B-Tree / B+Tree Index** | SQLite Internal Engine | `database/valam.db` (Primary & Foreign Keys) | Rapid primary key indexing on `requests(id)`, `warehouses(id)`, `shops(id)`, and timestamp ranges. | **Search:** O(log N)<br/>**Insert:** O(log N) | O(N) on-disk node tree |
| **Append-Only Event Stream (FIFO)** | Relational Log Table | `request_timeline`<br/>`stock_history`<br/>`operation_history` | Chronological audit queue preserving state change transitions with immutable timestamps. | **Append:** O(1)<br/>**Range Query:** O(log N + K) | O(E) where E = total events |

---

## 3. Deep-Dive: Why Each Data Structure Was Chosen

### 3.1 Hash Table (`Map<String, String>` & `HashMap<String, String>`)

- **File Locations:** `HttpUtil.java` (Line 60), `JsonUtil.java` (Line 15), `AuthHandler.java` (Line 38).

- **Exact Code Implementation:**
```java
public static Map<String, String> parseQueryParams(URI uri) {
    Map<String, String> queryMap = new HashMap<>();
    String query = uri.getRawQuery();
    if (query == null || query.isEmpty()) return queryMap;
    for (String param : query.split("&")) {
        String[] pair = param.split("=", 2);
        if (pair.length == 2) {
            queryMap.put(URLDecoder.decode(pair[0], StandardCharsets.UTF_8),
                         URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
    }
    return queryMap;
}
```
- **Why it is used:** When a client sends a GET request like `/api/requests?warehouseId=W01&status=Pending`, checking the query parameter must be instantaneous. Using a `HashMap` allows the backend to retrieve `queryMap.get("warehouseId")` in **O(1) constant time** rather than scanning strings in a loop.

### 3.2 Dynamic Array (`ArrayList<Object>`)

- **File Locations:** `StockHistoryHandler.java`, `OperationHandler.java`, `DeliveryIssueHandler.java`.

- **Why it is used:** When filtering database records by warehouse, commodity, or date range, the number of SQL `WHERE` clauses is unknown at compile time. An `ArrayList` dynamically grows to collect bound parameters:
```java
List<Object> params = new ArrayList<>();
StringBuilder sql = new StringBuilder("SELECT * FROM stock_history WHERE 1=1");
if (warehouseId != null) {
    sql.append(" AND warehouse_id = ?");
    params.add(warehouseId);
}
```
- **Complexity:** Appending is **amortized O(1)**. Memory is allocated in contiguous blocks, optimizing CPU cache lines during iteration.

### 3.3 Mutable Character Buffer (`StringBuilder` vs Immutable `String`)

- **Why this matters for Reviewers:** In Java, `String` is immutable. If you concatenate strings in a loop using `str += item`, Java allocates a new String object and copies every character on every iteration, leading to **O(N^2) quadratic time** and heavy Garbage Collection pressure.
- **VALAM Optimization:** Handlers use `StringBuilder` which maintains a resizable character array buffer:
```java
StringBuilder json = new StringBuilder("[");
boolean first = true;
while (rs.next()) {
    if (!first) json.append(",");
    appendRowJson(json, rs);
    first = false;
}
json.append("]");
```
- **Result:** Reduces serialization time from O(N^2) to **O(N) linear time**.

### 3.4 Primitive Byte Array (`byte[]` Buffer)

- **Used In:** `StaticFileHandler.java` (Line 52) and `PasswordUtil.java`.
- **Why:** When serving static files (like `admin.html` or `style.css`), reading single bytes is inefficient. We allocate a **8,192-byte (8 KB) buffer**:
```java
byte[] buffer = new byte[8192];
int bytesRead;
while ((bytesRead = fis.read(buffer)) != -1) {
    os.write(buffer, 0, bytesRead);
}
```
This matches the underlying OS disk block size, minimizing Kernel-to-User space context switches.

---

## 4. Key Algorithms Implemented in VALAM

### Algorithm 1: PBKDF2WithHmacSHA256 (Password Cryptography)

- **Class:** `PasswordUtil.java`

- **Theoretical Background:** Traditional hashes like MD5 and SHA-256 are fast, making them easily cracked using GPU rainbow tables (billions of hashes/sec). PBKDF2 (RFC 2898) introduces **computational work stretching**.
- **Implementation Specifications:**
  - **Salt Length:** 16 bytes (128 bits) generated via `SecureRandom` (cryptographically strong pseudo-random number generator).
  - **Iteration Count:** `65,536 iterations` of HMAC-SHA256.
  - **Key Length:** 256 bits.
- **Mathematical Formula:**
  $$DK = \text{PBKDF2}(PRF, Password, Salt, c, dkLen)$$
  Where $PRF = \text{HMAC-SHA256}$, $c = 65536$, and $dkLen = 256$.

### Algorithm 2: Constant-Time Comparison (Timing Attack Prevention)

- **Class:** `PasswordUtil.java` (Line 36)

- **The Vulnerability:** Standard string equality (`str1.equals(str2)`) terminates early at the first mismatched byte. An attacker can measure request response times with microsecond precision to deduce characters one by one.
- **Constant-Time Algorithm:**
```java
int diff = a.length ^ b.length;
for (int i = 0; i < a.length && i < b.length; i++) {
    diff |= (a[i] ^ b[i]);
}
return diff == 0;
```
- **Complexity:** Always executes exactly $N$ iterations regardless of where the mismatch occurs, completely neutralizing side-channel timing attacks.

### Algorithm 3: Lexical State-Machine JSON Parser

- **Class:** `JsonUtil.java`

- **Algorithm:** Single-pass lexical scanner that identifies tokens (`{`, `}`, `"`, `:`, `,`) and extracts key-value pairs without constructing a heavy Abstract Syntax Tree (AST). Operates in **O(N) linear time** with **O(1) auxiliary space**.

### Algorithm 4: Atomic Two-Phase Supply Reservation & Deduction

- **Class:** `RequestHandler.java`

- **Workflow:**
  1. **Phase 1 (Verification & Reservation):** When warehouse clicks Approve, verify available physical stock $\ge$ requested quantity. Atomically increment `reserved_stock`.
  2. **Phase 2 (Transit Dispatch & Deduction):** When vehicle is dispatched, atomically deduct physical stock: `physical_stock = physical_stock - reserved_stock; reserved_stock = 0;`.
  3. **Phase 3 (Receipt Confirmation):** When shopkeeper confirms receipt, atomically credit `shop_stock`.
- **Concurrency Protection:** Handled via database transaction isolation with rollback on failure (`conn.setAutoCommit(false)` and `conn.commit()`).
