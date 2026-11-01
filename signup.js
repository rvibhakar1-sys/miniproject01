
const signupForm = document.getElementById("signupForm");

const roleSelect = document.getElementById("signupRole");
const messageBox = document.getElementById("signupMessage");

signupForm.addEventListener("submit", async function (event) {

    event.preventDefault();

    const username = document.getElementById("signupUsername").value.trim();
    const password = document.getElementById("signupPassword").value;
    const role = roleSelect.value;

    const data = {
        username: username,
        password: password,
        role: role
    };

    try {

        messageBox.style.display = "none";

        const response = await fetch("http://localhost:8080/api/auth/signup", {
            method: "POST",
            headers: {
                "Content-Type": "application/json"
            },
            body: JSON.stringify(data)
        });

        const result = await response.json();

        if (!response.ok) {
            throw new Error(result.message || "Signup failed");
        }

        messageBox.className = "alert alert-success";
        messageBox.textContent = result.message;
        messageBox.style.display = "block";

        signupForm.reset();

    } catch (error) {

        messageBox.className = "alert alert-danger";
        messageBox.textContent = error.message;
        messageBox.style.display = "block";

        console.error("Signup error:", error);
    }
});
