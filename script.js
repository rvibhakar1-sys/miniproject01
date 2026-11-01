
var selectedRole = "";

function goToPortal(role) {

    selectedRole = role;

    var icon = document.getElementById("modalPortalIcon");
    var portalName = document.getElementById("modalPortalName");
    var loginBtn = document.getElementById("loginBtn");

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

    document.getElementById("username").value = "";
    document.getElementById("password").value = "";
    document.getElementById("loginError").style.display = "none";

    var loginModal = new bootstrap.Modal(
        document.getElementById("loginModal")
    );

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

    try {

        errorDiv.style.display = "none";

        const response = await fetch("http://localhost:8080/api/auth/login", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify({
                username: username,
                password: password
            })
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || "Invalid username or password");
        }

        // Save user session
        sessionStorage.setItem("valam_user", result.username);
        sessionStorage.setItem("valam_role", result.role.toLowerCase());

        // Redirect according to database role
        if (result.role === "ADMIN") {
            window.location.href = "admin.html";
        } else if (result.role === "WAREHOUSE") {
            window.location.href = "warehouse.html";
        } else if (result.role === "SHOP") {
            window.location.href = "shop.html";
        }

    } catch (error) {

        errorDiv.textContent = error.message;
        errorDiv.style.display = "block";

        console.error("Login error:", error);
    }
}
