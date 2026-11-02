# VALAM SUPPLY MANAGEMENT SYSTEM - JAVASCRIPT CLIENT & API INTEGRATION

> **Document:** Module 4 of 5 (Client Scripting, API Communication & State Management)  
> **Scope:** Deep-dive into `script.js` and inline page scripting, asynchronous Fetch API, session management, and DOM manipulation  
> **Target Audience:** Technical Examiners, Full-Stack Evaluators  
> **Date:** November 02, 2026

---

## 1. Client-Side JavaScript Architecture

JavaScript in VALAM powers asynchronous client-to-server communication, dynamic DOM manipulation, session state tracking, and UI reactivity without requiring heavy client-side frameworks.

### Key Responsibilities:
1. **Universal REST API Communication:** Encapsulates standard HTTP calls to the Java backend via `valamApiCall()`.
2. **Session Persistence:** Stores authentication tokens, active role, and assigned warehouse/shop IDs using browser `sessionStorage`.
3. **Dynamic Table & Modal Rendering:** Eliminates hardcoded data; dynamically builds HTML table rows and modal bodies from live SQLite JSON responses.
4. **Indian Standard Time (IST) Formatting:** Converts UTC and system timestamps into clean 12-hour AM/PM IST time and readable dates.
5. **Color-Coded Status Badges:** Maps requisition and delivery lifecycle states to Bootstrap badge components.

---

## 2. In-Depth Analysis of `script.js` (Central Library)

### 2.1 In-Memory Profile Mapping Dictionaries

To allow instantaneous profile rendering upon login before backend API queries return, `script.js` maintains lookup dictionaries:
```javascript
var WH_USER_MAP = {
    "Wh01": { id: "W01", name: "Valam Central Warehouse", location: "Central Area (Nagercoil)" },
    "Wh02": { id: "W02", name: "Valam North Warehouse", location: "North Area (Marthandam)" },
    "Wh03": { id: "W03", name: "Valam South Warehouse", location: "South Area (Kanyakumari)" }
};
```
Provides **O(1) dictionary lookup** for shop and warehouse metadata.

### 2.2 The Universal REST API Client (`valamApiCall`)

The backbone of all client-server interaction:
```javascript
async function valamApiCall(endpoint, method, data) {
    var url = VALAM_API_BASE + endpoint;
    var opts = {
        method: method || "GET",
        headers: { "Content-Type": "application/json" }
    };
    if (data && (method === "POST" || method === "PUT")) {
        opts.body = JSON.stringify(data);
    }
    try {
        var resp = await fetch(url, opts);
        var json = await resp.json();
        if (!resp.ok) return { error: json.error || ("Server error: " + resp.status) };
        return json;
    } catch (err) {
        return { error: "Cannot connect to Java backend on port 8080." };
    }
}
```
- **Protocol Agnostic:** Automatically prepends `http://localhost:8080` if running from `file:///` URLs or relative path if served over HTTP.
- **Asynchronous Execution:** Uses `async`/`await` (Promises) preventing the browser UI thread from freezing during database queries.
- **Graceful Degradation:** Catches network drops and returns structured error objects instead of throwing unhandled exceptions.

### 2.3 Portal Authentication & Session Dispatch (`handleLogin`)

```javascript
async function handleLogin(event) {
    event.preventDefault();
    var res = await valamApiCall("/api/auth/login", "POST", { username, password, role: selectedRole });
    if (res && res.success) {
        sessionStorage.setItem("valam_user", res.user);
        sessionStorage.setItem("valam_role", res.role);
        if (res.entityId) sessionStorage.setItem("valam_entity_id", res.entityId);
        // Redirect to role-specific dashboard
        if (selectedRole === "admin") window.location.href = "admin.html";
        else if (selectedRole === "warehouse") window.location.href = "warehouse.html";
        else if (selectedRole === "shop") window.location.href = "shop.html";
    }
}
```

### 2.4 Status & Urgency Badge Generators

Maps backend status strings to visual Bootstrap badges:
```javascript
function getStatusBadge(status) {
    if (!status) return '<span class="text-muted">—</span>';
    if (status.includes("Pending")) return '<span class="badge bg-secondary">Pending</span>';
    if (status.includes("Reserved")) return '<span class="badge bg-warning text-dark">Stock Reserved</span>';
    if (status.includes("In Transit")) return '<span class="badge bg-info text-dark">In Transit</span>';
    if (status.includes("Confirmed") || status.includes("Completed")) return '<span class="badge bg-success">Delivered / Confirmed</span>';
    if (status.includes("Issue")) return '<span class="badge bg-danger">Delivery Shortage</span>';
    return '<span class="badge bg-info text-dark">' + status + '</span>';
}
```

---

## 3. Inline Dashboard Scripting Breakdown

### 3.1 `admin.html` Scripting

- `loadDashboardData()`: Concurrently triggers API fetches (`/api/shops`, `/api/warehouses`, `/api/vehicles`, `/api/requests`, `/api/operations`).
- `populateShopsTable(shops)`: Iterates through shop objects and generates HTML table rows dynamically.
- `filterOperationHistory()`: Real-time client-side filter by selected warehouse and date.

### 3.2 `warehouse.html` Scripting

- `loadWarehouseData(warehouseId)`: Fetches data strictly filtered by logged-in warehouse ID (`?warehouseId=W01`).
- `approveRequest(reqId)`: Sends `POST /api/requests/{id}/process` to reserve physical stock.
- `dispatchVehicle(reqId)`: Prompts vehicle selection and calls `POST /api/requests/{id}/transit`.
- `openAddStockModal()` & `submitAddStock()`: Sends inbound consignment counts to `POST /api/warehouses/{id}/stock`.
- `openResolveIssueModal(issueId)`: Opens settlement form for shortage claims.

### 3.3 `shop.html` Scripting

- `submitStockRequest(e)`: Form submit handler sending Rice, Wheat, Sugar quantities to `POST /api/requests`.
- `openConfirmReceiptModal(reqId)`: Displays modal showing dispatched quantities and "Confirm Receipt" button.
- `executeConfirmReceipt(reqId)`: Sends `POST /api/requests/{id}/confirm-receipt` to credit shop stock and finalize order.
- `submitReportIssue(reqId)`: Submits discrepancy report to `POST /api/requests/{id}/report-issue`.
