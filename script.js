// ---------------------------------------------------------------
// VALAM - Supply Management System
// Centralized Data Layer & API Client (Connected strictly to Java + SQLite)
// ---------------------------------------------------------------

// Map Wh01, Wh02, Wh03 to warehouse details for profile display
var WH_USER_MAP = {
    "Wh01": { id: "W01", name: "Valam Central Warehouse", location: "Central Area (Nagercoil)" },
    "Wh02": { id: "W02", name: "Valam North Warehouse", location: "North Area (Marthandam)" },
    "Wh03": { id: "W03", name: "Valam South Warehouse", location: "South Area (Kanyakumari)" }
};

// Map Shop01 to Shop05 to shop details for profile display
var SHOP_USER_MAP = {
    "Shop01": { id: "S01", name: "FPS Nagercoil", location: "Nagercoil Town", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "5 km" },
    "Shop02": { id: "S02", name: "FPS Marthandam", location: "Marthandam Central", nearestWh: "W02", nearestWhName: "Valam North Warehouse", distance: "8 km" },
    "Shop03": { id: "S03", name: "FPS Colachel", location: "Colachel Port", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "6 km" },
    "Shop04": { id: "S04", name: "FPS Kanyakumari", location: "Kanyakumari Beach Road", nearestWh: "W03", nearestWhName: "Valam South Warehouse", distance: "3 km" },
    "Shop05": { id: "S05", name: "FPS Thuckalay", location: "Thuckalay Market", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "7 km" }
};

// ---------------------------------------------------------------
// VALAM JAVA HTTP BACKEND API CLIENT
// ---------------------------------------------------------------
var VALAM_API_BASE = (function() {
    if (typeof window !== "undefined" && window.location) {
        if (window.location.protocol === "file:") {
            return "http://localhost:8080";
        }
        return "";
    }
    return "http://localhost:8080";
})();

async function valamApiCall(endpoint, method, data) {
    var url = VALAM_API_BASE + endpoint;
    var opts = {
        method: method || "GET",
        headers: {
            "Content-Type": "application/json"
        }
    };
    if (data && (method === "POST" || method === "PUT")) {
        opts.body = JSON.stringify(data);
    }
    try {
        var resp = await fetch(url, opts);
        var json = await resp.json();
        if (!resp.ok) {
            return { error: json.error || ("Server error: " + resp.status) };
        }
        return json;
    } catch (err) {
        console.error("[VALAM API] Network/Server Error connecting to " + endpoint + ":", err);
        return { error: "Cannot connect to Java backend. Please verify that the VALAM Java server is running on port 8080." };
    }
}

// ---------------------------------------------------------------
// PORTAL NAVIGATION & LOGIN (STRICTLY DATABASE-AUTHENTICATED)
// ---------------------------------------------------------------
var selectedRole = "";

function goToPortal(role) {

    selectedRole = role;

    var icon = document.getElementById("modalPortalIcon");
    var portalName = document.getElementById("modalPortalName");
    var loginBtn = document.getElementById("loginBtn");

    if (!icon || !portalName || !loginBtn) return;

    icon.className = "portal-icon";

    if (role === "admin") {
        icon.classList.add("admin-portal-icon");
        icon.textContent = "A";
        portalName.textContent = "Admin Portal";
        loginBtn.style.backgroundColor = "#d93829";

    } else if (role === "warehouse") {
        icon.classList.add("warehouse-portal-icon");
        icon.textContent = "W";
        portalName.textContent = "Warehouse Portal";
        loginBtn.style.backgroundColor = "#2563eb";

    } else if (role === "shop") {
        icon.classList.add("shop-portal-icon");
        icon.textContent = "S";
        portalName.textContent = "Shop Portal";
        loginBtn.style.backgroundColor = "#16a34a";
    }

    var userInput = document.getElementById("username");
    var passInput = document.getElementById("password");
    var errDiv = document.getElementById("loginError");

    if (userInput) userInput.value = "";
    if (passInput) passInput.value = "";
    if (errDiv) errDiv.style.display = "none";

    var loginModal = new bootstrap.Modal(document.getElementById("loginModal"));
    loginModal.show();
}

async function handleLogin(event) {
    event.preventDefault();

    var username = document.getElementById("username").value.trim();
    var password = document.getElementById("password").value;
    var errorDiv = document.getElementById("loginError");

    if (username === "" || password === "") {
        errorDiv.textContent = "Please enter both username and password.";
        errorDiv.style.display = "block";
        return;
    }

    // Call Java Backend Authentication API (SQLite database)
    var res = await valamApiCall("/api/auth/login", "POST", {
        username: username,
        password: password,
        role: selectedRole
    });

    if (res && res.success) {
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.setItem("valam_user", res.user);
            sessionStorage.setItem("valam_role", res.role);
            if (res.entityId) sessionStorage.setItem("valam_entity_id", res.entityId);
            if (res.entityName) sessionStorage.setItem("valam_entity_name", res.entityName);
        }

        if (selectedRole === "admin") {
            window.location.href = "admin.html";
        } else if (result.role === "WAREHOUSE") {
            window.location.href = "warehouse.html";
        } else if (result.role === "SHOP") {
            window.location.href = "shop.html";
        }
    } else {
        errorDiv.textContent = (res && res.error) ? res.error : "Authentication failed. Please verify credentials or server status.";
        errorDiv.style.display = "block";

        console.error("Login error:", error);
    }
}

// ---------------------------------------------------------------
// TIME & DATE FORMATTING UTILITIES (IST)
// ---------------------------------------------------------------
function getCurrentDateIST() {
    var now = new Date();
    var y = now.getFullYear();
    var m = String(now.getMonth() + 1).padStart(2, '0');
    var d = String(now.getDate()).padStart(2, '0');
    return y + "-" + m + "-" + d;
}

function getCurrentTimeIST() {
    var now = new Date();
    var h = String(now.getHours()).padStart(2, '0');
    var m = String(now.getMinutes()).padStart(2, '0');
    var s = String(now.getSeconds()).padStart(2, '0');
    return h + ":" + m + ":" + s;
}

function formatTimeIST(timeStr) {
    if (!timeStr || timeStr === "—") return "—";
    var parts = timeStr.split(":");
    var hours = parseInt(parts[0]);
    var minutes = parts[1];
    var seconds = parts[2] || "00";
    var ampm = hours >= 12 ? "PM" : "AM";
    var displayHours = hours % 12;
    if (displayHours === 0) displayHours = 12;
    return displayHours + ":" + minutes + ":" + seconds + " " + ampm + " IST";
}

function formatDateReadable(dateStr) {
    if (!dateStr || dateStr === "—") return "—";
    var d = new Date(dateStr + "T00:00:00");
    var months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
    var days = ["Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat"];
    return days[d.getDay()] + ", " + d.getDate() + " " + months[d.getMonth()] + " " + d.getFullYear();
}

function getStatusBadge(status) {
    if (!status || status === "—" || status === "") return '<span class="text-muted">—</span>';
    if (status.indexOf("Pending") !== -1) return '<span class="badge bg-secondary">' + status + '</span>';
    if (status.indexOf("Reserved") !== -1) return '<span class="badge bg-warning text-dark">' + status + '</span>';
    if (status.indexOf("Dispatched") !== -1) return '<span class="badge bg-info text-dark">' + status + '</span>';
    if (status.indexOf("In Transit") !== -1) return '<span class="badge bg-warning text-dark">' + status + '</span>';
    if (status.indexOf("Awaiting") !== -1) return '<span class="badge bg-warning text-dark">' + status + '</span>';
    if (status.indexOf("Delivery Issue") !== -1 || status.indexOf("Issue") !== -1) return '<span class="badge bg-danger">' + status + '</span>';
    if (status.indexOf("Completed") !== -1 || status.indexOf("Delivered") !== -1 || status.indexOf("Confirmed") !== -1) return '<span class="badge bg-success">' + status + '</span>';
    if (status.indexOf("Available") !== -1) return '<span class="badge bg-success">' + status + '</span>';
    if (status.indexOf("On Trip") !== -1) return '<span class="badge bg-warning text-dark">' + status + '</span>';
    return '<span class="badge bg-info text-dark" style="font-size:0.75rem;">' + status + '</span>';
}

function getUrgencyBadge(urgency) {
    if (!urgency) return '<span class="badge bg-primary">Normal</span>';
    if (urgency.toLowerCase() === "critical") return '<span class="badge bg-danger">Critical</span>';
    if (urgency.toLowerCase() === "urgent") return '<span class="badge bg-warning text-dark">Urgent</span>';
    return '<span class="badge bg-primary">Normal</span>';
}
