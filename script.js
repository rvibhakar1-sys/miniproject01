
// ---------------------------------------------------------------
// VALAM - Supply Management System
// Centralized Data Layer and Shared Utilities
// ---------------------------------------------------------------

// ---------------------------------------------------------------
// CREDENTIALS DATABASE
// ---------------------------------------------------------------
var credentials = {
    admin: [
        { username: "Admin", password: "admin@01" }
    ],
    warehouse: [
        { username: "Wh01", password: "wh@01" },
        { username: "Wh02", password: "wh@02" },
        { username: "Wh03", password: "wh@03" }
    ],
    shop: [
        { username: "Shop01", password: "shop@01" },
        { username: "Shop02", password: "shop@02" },
        { username: "Shop03", password: "shop@03" },
        { username: "Shop04", password: "shop@04" },
        { username: "Shop05", password: "shop@05" }
    ]
};

// ---------------------------------------------------------------
// CENTRALIZED WAREHOUSE MASTER DATA (W01, W02, W03)
// ---------------------------------------------------------------
var WAREHOUSES_MASTER = [
    {
        id: "W01",
        name: "Valam Central Warehouse",
        location: "Central Area (Nagercoil)",
        distance: "5 km",
        initialStock: { rice: 5000, wheat: 3200, sugar: 1500 },
        initialReserved: { rice: 800, wheat: 400, sugar: 200 },
        lastUpdate: {
            date: "2026-10-24",
            time: "13:16:00",
            updatedBy: "Wh01 Staff",
            reason: "Request REQ-106 processed (Shop S06)"
        }
    },
    {
        id: "W02",
        name: "Valam North Warehouse",
        location: "North Area (Marthandam)",
        distance: "8 km",
        initialStock: { rice: 4500, wheat: 2800, sugar: 1200 },
        initialReserved: { rice: 550, wheat: 350, sugar: 180 },
        lastUpdate: {
            date: "2026-10-22",
            time: "09:46:00",
            updatedBy: "Wh02 Staff",
            reason: "Request REQ-102 processed (Shop S02)"
        }
    },
    {
        id: "W03",
        name: "Valam South Warehouse",
        location: "South Area (Kanyakumari)",
        distance: "3 km",
        initialStock: { rice: 4000, wheat: 2500, sugar: 1000 },
        initialReserved: { rice: 600, wheat: 300, sugar: 150 },
        lastUpdate: {
            date: "2026-10-23",
            time: "09:46:00",
            updatedBy: "Wh03 Staff",
            reason: "Request REQ-104 processed (Shop S04)"
        }
    }
];

// Map Wh01, Wh02, Wh03 to warehouse details
var WH_USER_MAP = {
    "Wh01": { id: "W01", name: "Valam Central Warehouse", location: "Central Area (Nagercoil)" },
    "Wh02": { id: "W02", name: "Valam North Warehouse", location: "North Area (Marthandam)" },
    "Wh03": { id: "W03", name: "Valam South Warehouse", location: "South Area (Kanyakumari)" }
};

// Map Shop01 to Shop05 (and S01 - S08)
var SHOP_USER_MAP = {
    "Shop01": { id: "S01", name: "FPS Nagercoil", location: "Nagercoil Town", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "5 km" },
    "Shop02": { id: "S02", name: "FPS Marthandam", location: "Marthandam Central", nearestWh: "W02", nearestWhName: "Valam North Warehouse", distance: "8 km" },
    "Shop03": { id: "S03", name: "FPS Colachel", location: "Colachel Port", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "6 km" },
    "Shop04": { id: "S04", name: "FPS Kanyakumari", location: "Kanyakumari Beach Road", nearestWh: "W03", nearestWhName: "Valam South Warehouse", distance: "3 km" },
    "Shop05": { id: "S05", name: "FPS Thuckalay", location: "Thuckalay Market", nearestWh: "W01", nearestWhName: "Valam Central Warehouse", distance: "7 km" }
};

// ---------------------------------------------------------------
// SHARED WAREHOUSE STOCK MANAGEMENT (Independent per warehouse)
// ---------------------------------------------------------------
function initValamStockStorage() {
    if (typeof sessionStorage === "undefined") {
        return null;
    }
    var stored = sessionStorage.getItem("valam_warehouses_stock");
    if (!stored) {
        var initialStockMap = {};
        for (var i = 0; i < WAREHOUSES_MASTER.length; i++) {
            var wh = WAREHOUSES_MASTER[i];
            initialStockMap[wh.id] = {
                id: wh.id,
                name: wh.name,
                location: wh.location,
                available: { rice: wh.initialStock.rice, wheat: wh.initialStock.wheat, sugar: wh.initialStock.sugar },
                reserved: { rice: wh.initialReserved.rice, wheat: wh.initialReserved.wheat, sugar: wh.initialReserved.sugar },
                lastUpdate: wh.lastUpdate
            };
        }
        sessionStorage.setItem("valam_warehouses_stock", JSON.stringify(initialStockMap));
        return initialStockMap;
    }
    try {
        var parsed = JSON.parse(stored);
        // Make sure all 3 warehouses exist
        for (var j = 0; j < WAREHOUSES_MASTER.length; j++) {
            var mWh = WAREHOUSES_MASTER[j];
            if (!parsed[mWh.id]) {
                parsed[mWh.id] = {
                    id: mWh.id,
                    name: mWh.name,
                    location: mWh.location,
                    available: { rice: mWh.initialStock.rice, wheat: mWh.initialStock.wheat, sugar: mWh.initialStock.sugar },
                    reserved: { rice: mWh.initialReserved.rice, wheat: mWh.initialReserved.wheat, sugar: mWh.initialReserved.sugar },
                    lastUpdate: mWh.lastUpdate
                };
            }
        }
        return parsed;
    } catch (e) {
        return null;
    }
}

function getValamStockMap() {
    var map = initValamStockStorage();
    if (map) return map;
    // Fallback in-memory
    var fallback = {};
    for (var i = 0; i < WAREHOUSES_MASTER.length; i++) {
        var wh = WAREHOUSES_MASTER[i];
        fallback[wh.id] = {
            id: wh.id,
            name: wh.name,
            location: wh.location,
            available: { rice: wh.initialStock.rice, wheat: wh.initialStock.wheat, sugar: wh.initialStock.sugar },
            reserved: { rice: wh.initialReserved.rice, wheat: wh.initialReserved.wheat, sugar: wh.initialReserved.sugar },
            lastUpdate: wh.lastUpdate
        };
    }
    return fallback;
}

function getValamWarehouseStock(warehouseId) {
    var map = getValamStockMap();
    if (map[warehouseId]) {
        var item = map[warehouseId];
        return {
            id: item.id,
            name: item.name,
            location: item.location,
            available: item.available,
            reserved: item.reserved,
            rice: item.available ? item.available.rice : 0,
            wheat: item.available ? item.available.wheat : 0,
            sugar: item.available ? item.available.sugar : 0,
            lastUpdate: item.lastUpdate
        };
    }
    for (var i = 0; i < WAREHOUSES_MASTER.length; i++) {
        if (WAREHOUSES_MASTER[i].id === warehouseId) {
            var m = WAREHOUSES_MASTER[i];
            return {
                id: m.id,
                name: m.name,
                location: m.location,
                available: { rice: m.initialStock.rice, wheat: m.initialStock.wheat, sugar: m.initialStock.sugar },
                reserved: { rice: m.initialReserved.rice, wheat: m.initialReserved.wheat, sugar: m.initialReserved.sugar },
                rice: m.initialStock.rice,
                wheat: m.initialStock.wheat,
                sugar: m.initialStock.sugar,
                lastUpdate: m.lastUpdate
            };
        }
    }
    return {
        id: warehouseId,
        name: "Warehouse " + warehouseId,
        available: { rice: 0, wheat: 0, sugar: 0 },
        reserved: { rice: 0, wheat: 0, sugar: 0 },
        rice: 0,
        wheat: 0,
        sugar: 0,
        lastUpdate: null
    };
}

function updateValamWarehouseStock(warehouseId, availableDelta, reservedDelta, lastUpdateInfo) {
    var map = getValamStockMap();
    if (!map[warehouseId]) {
        map[warehouseId] = {
            id: warehouseId,
            name: (WH_USER_MAP[warehouseId] ? WH_USER_MAP[warehouseId].name : "Warehouse " + warehouseId),
            available: { rice: 0, wheat: 0, sugar: 0 },
            reserved: { rice: 0, wheat: 0, sugar: 0 },
            lastUpdate: null
        };
    }
    if (availableDelta) {
        if (typeof availableDelta.rice === 'number') map[warehouseId].available.rice += availableDelta.rice;
        if (typeof availableDelta.wheat === 'number') map[warehouseId].available.wheat += availableDelta.wheat;
        if (typeof availableDelta.sugar === 'number') map[warehouseId].available.sugar += availableDelta.sugar;
    }
    if (reservedDelta) {
        if (typeof reservedDelta.rice === 'number') map[warehouseId].reserved.rice += reservedDelta.rice;
        if (typeof reservedDelta.wheat === 'number') map[warehouseId].reserved.wheat += reservedDelta.wheat;
        if (typeof reservedDelta.sugar === 'number') map[warehouseId].reserved.sugar += reservedDelta.sugar;
    }
    if (lastUpdateInfo) {
        map[warehouseId].lastUpdate = lastUpdateInfo;
    }
    if (typeof sessionStorage !== "undefined") {
        sessionStorage.setItem("valam_warehouses_stock", JSON.stringify(map));
    }
    return map[warehouseId];
}

// ---------------------------------------------------------------
// INITIAL SHARED REQUESTS (Covering W01, W02, W03)
// ---------------------------------------------------------------
var INITIAL_VALAM_REQUESTS = [
    {
        requestId: "REQ-101",
        shopId: "S01",
        rice: "200",
        wheat: "100",
        sugar: "50",
        urgency: "critical",
        warehouseId: "W01",
        warehouse: "Valam Central Warehouse",
        status: "Dispatched",
        vehicle: "V01",
        route: "Valam Central Warehouse → FPS Nagercoil",
        submittedDate: "2026-10-20",
        submittedTime: "09:15:00",
        processedDate: "2026-10-20",
        processedTime: "10:30:00",
        lastUpdatedDate: "2026-10-21",
        lastUpdatedTime: "08:30:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-20", time: "09:15:00", status: "Pending", actor: "Shop S01", details: "Commodity stock request submitted for Rice: 200 kg, Wheat: 100 kg, Sugar: 50 kg. Urgency: Critical.", updatedBy: "Shop01 Staff" },
            { stage: "Request Accepted / Processed", date: "2026-10-20", time: "10:30:00", status: "Reserved", actor: "Wh01 Staff", details: "Request accepted at Valam Central Warehouse. Commodity stock reserved.", updatedBy: "Wh01 Staff" },
            { stage: "Vehicle Assigned", date: "2026-10-21", time: "08:00:00", status: "Reserved", actor: "Wh01 Staff", details: "Delivery vehicle V01 (TN-74-AA-1234, Eicher 14ft) assigned.", updatedBy: "Wh01 Staff" },
            { stage: "Dispatched", date: "2026-10-21", time: "08:30:00", status: "Dispatched", actor: "Wh01 Staff", details: "Commodity consignment dispatched from Valam Central Warehouse to FPS Nagercoil.", updatedBy: "Wh01 Staff" }
        ]
    },
    {
        requestId: "REQ-102",
        shopId: "S02",
        rice: "300",
        wheat: "150",
        sugar: "80",
        urgency: "normal",
        warehouseId: "W02",
        warehouse: "Valam North Warehouse",
        status: "Delivered",
        vehicle: "V02",
        route: "Valam North Warehouse → FPS Marthandam",
        submittedDate: "2026-10-18",
        submittedTime: "11:00:00",
        processedDate: "2026-10-18",
        processedTime: "14:20:00",
        lastUpdatedDate: "2026-10-19",
        lastUpdatedTime: "11:45:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-18", time: "11:00:00", status: "Pending", actor: "Shop S02", details: "Commodity stock request submitted. Rice: 300 kg, Wheat: 150 kg, Sugar: 80 kg.", updatedBy: "Shop02 Staff" },
            { stage: "Request Accepted / Processed", date: "2026-10-18", time: "14:20:00", status: "Reserved", actor: "Wh02 Staff", details: "Request verified and approved. Stock reserved at Valam North Warehouse.", updatedBy: "Wh02 Staff" },
            { stage: "Vehicle Assigned", date: "2026-10-19", time: "07:45:00", status: "Reserved", actor: "Wh02 Staff", details: "Vehicle V02 (TN-74-BB-5678, Tata 407) assigned.", updatedBy: "Wh02 Staff" },
            { stage: "Dispatched", date: "2026-10-19", time: "08:15:00", status: "Dispatched", actor: "Wh02 Staff", details: "Shipment dispatched towards Marthandam Central.", updatedBy: "Wh02 Staff" },
            { stage: "In Transit", date: "2026-10-19", time: "09:30:00", status: "In Transit", actor: "Fleet GPS System", details: "Vehicle in transit. Estimated delivery 11:30 AM.", updatedBy: "Fleet GPS System" },
            { stage: "Delivered", date: "2026-10-19", time: "11:45:00", status: "Delivered", actor: "Shop02 Staff", details: "Consignment safely delivered and verified at FPS Marthandam.", updatedBy: "Shop02 Staff" }
        ]
    },
    {
        requestId: "REQ-103",
        shopId: "S03",
        rice: "500",
        wheat: "400",
        sugar: "50",
        urgency: "normal",
        warehouseId: "W01",
        warehouse: "Valam Central Warehouse",
        status: "Pending",
        vehicle: "Not Assigned",
        route: "Valam Central Warehouse → FPS Colachel",
        submittedDate: "2026-10-25",
        submittedTime: "10:00:00",
        processedDate: "—",
        processedTime: "—",
        lastUpdatedDate: "2026-10-25",
        lastUpdatedTime: "10:00:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-25", time: "10:00:00", status: "Pending", actor: "Shop S03", details: "Request submitted. Awaiting acceptance from Central Warehouse staff.", updatedBy: "Shop03 Staff" }
        ]
    },
    {
        requestId: "REQ-104",
        shopId: "S04",
        rice: "250",
        wheat: "200",
        sugar: "100",
        urgency: "urgent",
        warehouseId: "W03",
        warehouse: "Valam South Warehouse",
        status: "In Transit",
        vehicle: "V03",
        route: "Valam South Warehouse → FPS Kanyakumari",
        submittedDate: "2026-10-22",
        submittedTime: "09:00:00",
        processedDate: "2026-10-22",
        processedTime: "09:45:00",
        lastUpdatedDate: "2026-10-23",
        lastUpdatedTime: "09:15:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-22", time: "09:00:00", status: "Pending", actor: "Shop S04", details: "Urgent quota requested for Kanyakumari coastal depot.", updatedBy: "Shop04 Staff" },
            { stage: "Request Accepted / Processed", date: "2026-10-22", time: "09:45:00", status: "Reserved", actor: "Wh03 Staff", details: "Request accepted at Valam South Warehouse. Stock reserved.", updatedBy: "Wh03 Staff" },
            { stage: "Vehicle Assigned", date: "2026-10-23", time: "07:30:00", status: "Reserved", actor: "Wh03 Staff", details: "Vehicle V03 (TN-74-CC-9012, Ashok Leyland) assigned.", updatedBy: "Wh03 Staff" },
            { stage: "Dispatched", date: "2026-10-23", time: "08:00:00", status: "Dispatched", actor: "Wh03 Staff", details: "Dispatched from Valam South Warehouse to FPS Kanyakumari.", updatedBy: "Wh03 Staff" },
            { stage: "In Transit", date: "2026-10-23", time: "09:15:00", status: "In Transit", actor: "System", details: "Consignment in transit along Coastal Beach Road.", updatedBy: "System" }
        ]
    },
    {
        requestId: "REQ-105",
        shopId: "S05",
        rice: "400",
        wheat: "250",
        sugar: "75",
        urgency: "normal",
        warehouseId: "W01",
        warehouse: "Valam Central Warehouse",
        status: "Pending",
        vehicle: "Not Assigned",
        route: "Valam Central Warehouse → FPS Thuckalay",
        submittedDate: "2026-10-26",
        submittedTime: "14:30:00",
        processedDate: "—",
        processedTime: "—",
        lastUpdatedDate: "2026-10-26",
        lastUpdatedTime: "14:30:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-26", time: "14:30:00", status: "Pending", actor: "Shop S05", details: "Regular monthly commodity request submitted.", updatedBy: "Shop05 Staff" }
        ]
    },
    {
        requestId: "REQ-106",
        shopId: "S06",
        rice: "350",
        wheat: "200",
        sugar: "60",
        urgency: "normal",
        warehouseId: "W01",
        warehouse: "Valam Central Warehouse",
        status: "Dispatched",
        vehicle: "V01",
        route: "Valam Central Warehouse → FPS Padmanabhapuram",
        submittedDate: "2026-10-24",
        submittedTime: "11:00:00",
        processedDate: "2026-10-24",
        processedTime: "13:15:00",
        lastUpdatedDate: "2026-10-25",
        lastUpdatedTime: "07:30:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-24", time: "11:00:00", status: "Pending", actor: "Shop S06", details: "Commodity stock request submitted.", updatedBy: "Shop01 Staff" },
            { stage: "Request Accepted / Processed", date: "2026-10-24", time: "13:15:00", status: "Reserved", actor: "Wh01 Staff", details: "Stock reserved at Valam Central Warehouse.", updatedBy: "Wh01 Staff" },
            { stage: "Vehicle Assigned", date: "2026-10-25", time: "07:00:00", status: "Reserved", actor: "Wh01 Staff", details: "Vehicle V01 (TN-74-AA-1234) assigned.", updatedBy: "Wh01 Staff" },
            { stage: "Dispatched", date: "2026-10-25", time: "07:30:00", status: "Dispatched", actor: "Wh01 Staff", details: "Dispatched to FPS Padmanabhapuram.", updatedBy: "Wh01 Staff" }
        ]
    },
    {
        requestId: "REQ-107",
        shopId: "S07",
        rice: "200",
        wheat: "100",
        sugar: "40",
        urgency: "normal",
        warehouseId: "W02",
        warehouse: "Valam North Warehouse",
        status: "Delivered",
        vehicle: "V02",
        route: "Valam North Warehouse → FPS Karungal",
        submittedDate: "2026-10-15",
        submittedTime: "08:30:00",
        processedDate: "2026-10-15",
        processedTime: "10:00:00",
        lastUpdatedDate: "2026-10-17",
        lastUpdatedTime: "16:00:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-15", time: "08:30:00", status: "Pending", actor: "Shop S07", details: "Request submitted for Karungal town.", updatedBy: "Shop01 Staff" },
            { stage: "Request Accepted / Processed", date: "2026-10-15", time: "10:00:00", status: "Reserved", actor: "Wh02 Staff", details: "Stock reserved at Valam North Warehouse.", updatedBy: "Wh02 Staff" },
            { stage: "Vehicle Assigned", date: "2026-10-16", time: "09:00:00", status: "Reserved", actor: "Wh02 Staff", details: "Vehicle V02 assigned.", updatedBy: "Wh02 Staff" },
            { stage: "Dispatched", date: "2026-10-16", time: "09:30:00", status: "Dispatched", actor: "Wh02 Staff", details: "Dispatched towards Karungal.", updatedBy: "Wh02 Staff" },
            { stage: "Delivered", date: "2026-10-17", time: "16:00:00", status: "Delivered", actor: "Shop01 Staff", details: "Delivery verified and receipt acknowledged.", updatedBy: "Shop01 Staff" }
        ]
    },
    {
        requestId: "REQ-108",
        shopId: "S08",
        rice: "300",
        wheat: "200",
        sugar: "90",
        urgency: "normal",
        warehouseId: "W03",
        warehouse: "Valam South Warehouse",
        status: "Pending",
        vehicle: "Not Assigned",
        route: "Valam South Warehouse → FPS Kulasekharam",
        submittedDate: "2026-10-27",
        submittedTime: "15:45:00",
        processedDate: "—",
        processedTime: "—",
        lastUpdatedDate: "2026-10-27",
        lastUpdatedTime: "15:45:00",
        timeline: [
            { stage: "Request Received", date: "2026-10-27", time: "15:45:00", status: "Pending", actor: "Shop S08", details: "Commodity request submitted to Valam South Warehouse. Awaiting processing.", updatedBy: "Shop01 Staff" }
        ]
    }
];

function getValamRequests() {
    var rawList = INITIAL_VALAM_REQUESTS;
    if (typeof sessionStorage !== "undefined") {
        var stored = sessionStorage.getItem("valam_all_requests");
        if (stored) {
            try {
                rawList = JSON.parse(stored);
            } catch (e) {
                rawList = INITIAL_VALAM_REQUESTS;
            }
        } else {
            sessionStorage.setItem("valam_all_requests", JSON.stringify(INITIAL_VALAM_REQUESTS));
        }
    }
    for (var i = 0; i < rawList.length; i++) {
        var item = rawList[i];
        if (item.requestId && !item.id) item.id = item.requestId;
        if (item.id && !item.requestId) item.requestId = item.id;
        if (!item.warehouseId) {
            if (item.warehouse === "Valam Central Warehouse") item.warehouseId = "W01";
            else if (item.warehouse === "Valam North Warehouse") item.warehouseId = "W02";
            else if (item.warehouse === "Valam South Warehouse") item.warehouseId = "W03";
        }
    }
    return rawList;
}

function saveValamRequests(requests) {
    if (typeof sessionStorage !== "undefined") {
        sessionStorage.setItem("valam_all_requests", JSON.stringify(requests));
    }
}

function addValamRequest(req) {
    var list = getValamRequests();
    if (!req.id && !req.requestId) {
        req.id = "REQ-" + (100 + list.length + 1);
        req.requestId = req.id;
    } else {
        if (req.id && !req.requestId) req.requestId = req.id;
        if (req.requestId && !req.id) req.id = req.requestId;
    }
    list.unshift(req);
    saveValamRequests(list);
    return req;
}


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
        return json;
    } catch (err) {
        console.warn("[VALAM API] Failed request to " + endpoint + ":", err);
        return null;
    }
}

// Global server sync helper
async function syncValamSessionFromServer() {
    try {
        var whs = await valamApiCall("/api/warehouses", "GET");
        if (whs && Array.isArray(whs)) {
            var whMap = {};
            for (var i = 0; i < whs.length; i++) {
                whMap[whs[i].id] = whs[i];
            }
            if (typeof sessionStorage !== "undefined") {
                sessionStorage.setItem("valam_warehouses_stock", JSON.stringify(whMap));
            }
        }
        var reqs = await valamApiCall("/api/requests", "GET");
        if (reqs && Array.isArray(reqs)) {
            if (typeof sessionStorage !== "undefined") {
                sessionStorage.setItem("valam_all_requests", JSON.stringify(reqs));
            }
        }
    } catch (e) {
        console.warn("[VALAM] Could not sync with server:", e);
    }
}

// Background sync on script load
if (typeof window !== "undefined") {
    syncValamSessionFromServer();
}

// ---------------------------------------------------------------
// PORTAL NAVIGATION & LOGIN
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
    var password = document.getElementById("password").value.trim();
    var errorDiv = document.getElementById("loginError");

    if (username === "" || password === "") {
        errorDiv.textContent = "Please enter both username and password.";
        errorDiv.style.display = "block";
        return;
    }

    // Call Java Backend Authentication API
    try {
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

            // Sync warehouse & request data before redirect
            await syncValamSessionFromServer();

            if (selectedRole === "admin") {
                window.location.href = "admin.html";
            } else if (selectedRole === "warehouse") {
                window.location.href = "warehouse.html";
            } else if (selectedRole === "shop") {
                window.location.href = "shop.html";
            }
            return;
        } else if (res && res.error) {
            errorDiv.textContent = res.error;
            errorDiv.style.display = "block";
            return;
        }
    } catch (err) {
        console.warn("[Login] API call error, falling back to local credentials:", err);
    }

    // Fallback if backend is not reachable
    var validCredsList = credentials[selectedRole];
    var isValid = false;
    if (validCredsList) {
        for (var i = 0; i < validCredsList.length; i++) {
            if (username === validCredsList[i].username && password === validCredsList[i].password) {
                isValid = true;
                break;
            }
        }
    }

    if (isValid) {
        if (typeof sessionStorage !== "undefined") {
            sessionStorage.setItem("valam_user", username);
            sessionStorage.setItem("valam_role", selectedRole);
        }
        if (selectedRole === "admin") {
            window.location.href = "admin.html";
        } else if (selectedRole === "warehouse") {
            window.location.href = "warehouse.html";
        } else if (selectedRole === "shop") {
            window.location.href = "shop.html";
        }
    } else {
        errorDiv.textContent = "Invalid username or password. Please try again.";
        errorDiv.style.display = "block";
    }
}
