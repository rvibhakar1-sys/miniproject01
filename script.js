
// ---------------------------------------------------------------
// CREDENTIALS DATABASE
// Each role has its own username and password.
// In a real app this would be checked on a server/database.
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

// Variable to track which portal the user clicked
var selectedRole = "";

// ---------------------------------------------------------------
// Portal-based navigation (opens login modal instead of redirect)
// ---------------------------------------------------------------
function goToPortal(role) {
    selectedRole = role;

    // Update modal appearance based on role
    var icon = document.getElementById("modalPortalIcon");
    var portalName = document.getElementById("modalPortalName");
    var loginBtn = document.getElementById("loginBtn");

    // Reset classes
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

    // Clear previous inputs and errors
    document.getElementById("username").value = "";
    document.getElementById("password").value = "";
    document.getElementById("loginError").style.display = "none";

    // Show the login modal
    var loginModal = new bootstrap.Modal(document.getElementById("loginModal"));
    loginModal.show();
}

// ---------------------------------------------------------------
// Handle login form submission
// Validates username & password before redirecting
// ---------------------------------------------------------------
function handleLogin(event) {
    // Prevent the form from refreshing the page
    event.preventDefault();

    // Get the values from the form
    var username = document.getElementById("username").value.trim();
    var password = document.getElementById("password").value.trim();
    var errorDiv = document.getElementById("loginError");

    // Check if username and password are entered
    if (username === "" || password === "") {
        errorDiv.textContent = "Please enter both username and password.";
        errorDiv.style.display = "block";
        return;
    }

    // Validate credentials against the stored data
    var validCredsList = credentials[selectedRole];
    var isValid = false;

    for (var i = 0; i < validCredsList.length; i++) {
        if (username === validCredsList[i].username && password === validCredsList[i].password) {
            isValid = true;
            break;
        }
    }

    if (isValid) {
        // Save current user session
        sessionStorage.setItem("valam_user", username);
        sessionStorage.setItem("valam_role", selectedRole);

        // Correct credentials — redirect to the dashboard
        if (selectedRole === "admin") {
            window.location.href = "admin.html";
        } else if (selectedRole === "warehouse") {
            window.location.href = "warehouse.html";
        } else if (selectedRole === "shop") {
            window.location.href = "shop.html";
        }
    } else {
        // Wrong credentials — show error
        errorDiv.textContent = "Invalid username or password. Please try again.";
        errorDiv.style.display = "block";
    }
}
