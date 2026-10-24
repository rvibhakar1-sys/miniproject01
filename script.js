

function handleLogin(event) {
    // Prevent the form from refreshing the page
    event.preventDefault();

    // Get the values from the form
    var username = document.getElementById("username").value;
    var password = document.getElementById("password").value;
    var role = document.getElementById("role").value;

    // Check if username and password are entered
    if (username === "" || password === "") {
        alert("Please enter both username and password.");
        return;
    }

    // Redirect to the correct page based on the selected role
    if (role === "admin") {
        // Go to Admin Dashboard
        window.location.href = "admin.html";

    } else if (role === "warehouse") {
        // Go to Warehouse Dashboard
        window.location.href = "warehouse.html";

    } else if (role === "shop") {
        // Go to Shop Dashboard
        window.location.href = "shop.html";
    }
}
