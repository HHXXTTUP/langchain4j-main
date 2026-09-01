const form = document.querySelector("#login-form");
const submit = document.querySelector("#login-submit");
const message = document.querySelector("#login-message");

fetch("/api/auth/session", {cache: "no-store"}).then(response => { if (response.ok) location.replace("/"); });
form.addEventListener("submit", async event => {
    event.preventDefault(); submit.disabled = true; message.textContent = "正在验证账号…";
    try {
        const response = await fetch("/api/auth/login", {method: "POST", headers: {"Content-Type": "application/json"}, body: JSON.stringify({username: document.querySelector("#login-username").value.trim(), password: document.querySelector("#login-password").value})});
        const payload = await response.json().catch(() => ({}));
        if (!response.ok) throw new Error(payload.message || "登录失败");
        location.replace("/");
    } catch (error) { message.textContent = error.message || "登录失败"; submit.disabled = false; }
});
