# AI Web Auditor

`ai-web-auditor` is a small Java 17 MVP for auditing authenticated web features with Playwright. It accepts a target URL, a login form, and a list of modules, then produces screenshots and Markdown/HTML evidence reports.

## Run

Install the Playwright browser once:

```shell
mvn -pl ai-web-auditor -am install -DskipTests
mvn -pl ai-web-auditor exec:java -Dexec.mainClass=com.microsoft.playwright.CLI -Dexec.args="install chromium"
mvn -pl ai-web-auditor exec:java
```

The API listens on `http://localhost:8787` by default. Set `AI_WEB_AUDITOR_PORT` or pass `--port 9000` to change it.

Create an audit with `POST /api/audits`:

```json
{
  "baseUrl": "https://your-app.example.com",
  "login": {
    "path": "/login",
    "username": "qa@example.com",
    "password": "only-held-in-memory",
    "usernameSelector": "input[name=email]",
    "passwordSelector": "input[name=password]",
    "submitSelector": "button[type=submit]",
    "successUrlContains": "/dashboard"
  },
  "modules": [
    {"name": "订单列表", "path": "/orders", "description": "检查筛选、分页和空状态"}
  ],
  "outputDirectory": "audit-results",
  "headless": true,
  "viewportWidth": 1440,
  "viewportHeight": 900
}
```

The response contains the report paths. `GET /api/health` is available for readiness checks. Credentials are never written to the report or result JSON.

The MVP includes deterministic checks for blank titles, horizontal overflow, missing image alt text, unnamed buttons, and browser console errors. A later iteration can add a LangChain4j analysis step over the captured DOM, screenshots, and findings.
