# VALAM SUPPLY MANAGEMENT SYSTEM - HTML & CSS ARCHITECTURE GUIDE

> **Document:** Module 3 of 5 (Frontend User Interface & Visual Styling)  
> **Scope:** Structural analysis of all 4 HTML portals, CSS styling rules, color palettes, responsive layouts, and UI components  
> **Target Audience:** College Project Review Panel, Frontend Evaluators  
> **Date:** November 02, 2026

---

## 1. Frontend Design Principles

The VALAM frontend is built using standard **HTML5**, **CSS3**, and **Bootstrap 5.3.8**. It strictly preserves the existing website layout, font styling, and user interaction design while eliminating dependencies on complex client frameworks like React, Vue, or Angular.

### Core Design Features:
1. **Role-Based Visual Identity:** Each user role is assigned a distinct color theme to eliminate operational confusion:
   - **Admin Portal (Red `#d93829`):** Represents supervisory oversight and district authority.
   - **Warehouse Portal (Blue `#2563eb`):** Represents logistics, physical stock, and dispatch.
   - **Shop Portal (Green `#16a34a`):** Represents retail distribution, public delivery, and fulfillment.
2. **Single-Page Section Architecture:** Each dashboard page loads all necessary UI containers in a single DOM structure, toggling sections dynamically using JavaScript show/hide methods (`showSection()`). This eliminates disruptive page reloads.
3. **Glassmorphism & Micro-Interactions:** Modern translucent navigation bars with `backdrop-filter: blur(12px)`, pulsating online status indicators, and smooth card elevation hover effects.

---

## 2. Complete HTML Page Breakdown

### 2.1 `index.html` - Login Portal & Gateway

- **Metrics:** 160 lines (7.8 KB)
- **Core Sections & Structural Components:**
  - **Top Brand Navbar (`.login-navbar`):** Displays VALAM logo mark "V", system title, and an animated green pulsing indicator (`.status-dot`) signaling server availability.
  - **Hero Presentation Banner:** Left column introducing the Public Distribution Network (PDS) with clean typographic hierarchy and accent gradient text.
  - **Portal Selection Cards (`.portal-card`):** Right column presenting 3 interactive gateway options:
  -   - Admin Portal (Icon `A`, Red badge)
  -   - Shop Portal (Icon `S`, Green badge)
  -   - Warehouse Portal (Icon `W`, Blue badge)
  - **Universal Login Modal (`#loginModal`):** Bootstrap modal dialog with username and password input fields, dynamically themed to the chosen portal color upon opening.
  - **Footer:** System copyright banner.

---

### 2.2 `admin.html` - District Supply Officer - Admin Dashboard

- **Metrics:** 957 lines (48.3 KB)
- **Core Sections & Structural Components:**
  - **Admin Navigation (`.admin-navbar`):** Deep red navigation banner (`#d93829`) with officer greeting and logout trigger.
  - **Executive KPI Card Grid (`#dashboardMain`):** 6 interactive modules:
  -   1. *View Shops (`#shopsView`):* Table of all 8 district Fair Price Shops and their current supply status.
  -   2. *View Warehouses (`#warehousesView`):* Real-time stock counts for W01 Central, W02 North, and W03 South (Rice, Wheat, Sugar).
  -   3. *View Vehicles (`#vehiclesView`):* Transport fleet directory showing vehicle IDs, carrying capacities, and transit readiness.
  -   4. *View Requests (`#requestsView`):* District-wide requisition table showing shop demands and urgencies.
  -   5. *Delivery Status (`#deliveryStatusView`):* Active supply chain dispatches and estimated fulfillment.
  -   6. *Operation History (`#operationHistoryView`):* Date-wise and warehouse-filtered administrative audit log.
  - **Detail Modal (`#operationDetailModal`):** Shows complete JSON audit payload for inspected operations.

---

### 2.3 `warehouse.html` - Warehouse Supervisor - Warehouse Dashboard

- **Metrics:** 1692 lines (87.5 KB)
- **Core Sections & Structural Components:**
  - **Warehouse Navigation (`.warehouse-navbar`):** Royal blue navigation banner (`#2563eb`) displaying the active warehouse name (W01, W02, or W03).
  - **Warehouse Profile Summary Card:** Highlights assigned warehouse code, name, and geographic area.
  - **Operational Card Grid (8 Modules):**
  -   1. *Shop Requests:* Real-time incoming requisitions targeted at this warehouse.
  -   2. *Process Request:* Approval interface allowing stock reservation.
  -   3. *Warehouse Stock:* Live physical vs reserved inventory table with an "Add Stock" trigger.
  -   4. *Delivery / Vehicle:* Fleet allocation form and "Mark Dispatched / In Transit" action.
  -   5. *Request History & Timeline:* Comprehensive requisition history with interactive visual timeline modal (`#whTimelineModal`).
  -   6. *Stock Update History:* Complete transaction ledger of inbound consignments and outbound dispatches.
  -   7. *Warehouse Operation History:* Date-filtered operational audit trail.
  -   8. *Delivery Issues:* Discrepancy management dashboard with issue resolution modal (`#resolveIssueModal`).

---

### 2.4 `shop.html` - Fair Price Shopkeeper - Shop Dashboard

- **Metrics:** 1169 lines (62.3 KB)
- **Core Sections & Structural Components:**
  - **Shop Navigation (`.shop-navbar`):** Forest green navigation banner (`#16a34a`) showing shop name and taluk.
  - **Shop Profile Card:** Displays Shop ID (S01–S05), physical address, nearest warehouse, and transport distance.
  - **Shop Operations Grid (5 Modules):**
  -   1. *Stock Request:* Demand generation form with inputs for Rice, Wheat, Sugar (kg), and priority urgency dropdown.
  -   2. *Warehouse Stock & Location:* Read-only inspection table showing available inventory across W01, W02, and W03.
  -   3. *Request Status:* Live tracking table displaying whether orders are Pending, Reserved, or Dispatched.
  -   4. *Delivery Status & Confirm Receipt:* Dispatched order view with prominent "Confirm Receipt" and "Report Discrepancy" action buttons.
  -   5. *My Request History:* Historical log with timeline modal (`#shopTimelineModal`).
  - **Confirm Receipt Modal (`#confirmReceiptModal`):** Prompts shopkeeper to verify delivered commodity bags and confirm arrival, atomically updating shop stock.
  - **Report Issue Modal (`#reportIssueModal`):** Allows shopkeeper to lodge shortage or damage claims.

---

## 3. CSS Architecture & Theme System (`style.css`)

The stylesheet `style.css` (449 lines) controls the visual styling, card micro-interactions, responsive grids, and role color palettes.

### 3.1 Color Theme Definitions

| Theme Role | CSS Class Prefix | Primary Color | Hover / Dark Tone | Light Accent / Icon Background |
| :--- | :--- | :---: | :---: | :---: |
| **Admin** | `.admin-*` | `#d93829` (Red) | `#b91c1c` | `#fdf1ee` |
| **Warehouse** | `.warehouse-*` | `#2563eb` (Blue) | `#1d4ed8` | `#edf4fe` |
| **Shop** | `.shop-*` | `#16a34a` (Green) | `#15803d` | `#ecf8f0` |

### 3.2 Key CSS Design Patterns

1. **Interactive Dashboard Card Elevation:**
```css
.dashboard-card {
    border-radius: 10px;
    border: 1px solid #dee2e6;
    transition: box-shadow 0.2s, transform 0.2s;
}
.dashboard-card:hover {
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.08);
    transform: translateY(-2px);
}
```
2. **Pulsing Online Indicator:**
```css
@keyframes pulse-dot {
    0%, 100% { opacity: 1; transform: scale(1); }
    50% { opacity: 0.4; transform: scale(0.85); }
}
.status-dot {
    width: 8px; height: 8px;
    background-color: #34c759;
    border-radius: 50%;
    animation: pulse-dot 2s infinite;
}
```
3. **Responsive Breakpoints:**
   - `@media (max-width: 991px)`: Adapts hero banner typography and switches multi-column card rows into 2-column or stacked layouts.
   - `@media (max-width: 575px)`: Optimizes modal padding and full-width buttons for mobile devices.
