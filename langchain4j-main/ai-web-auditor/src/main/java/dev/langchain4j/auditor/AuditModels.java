package dev.langchain4j.auditor;

import java.util.ArrayList;
import java.util.List;

final class AuditModels {
    private AuditModels() {
    }

    public static class AuditRequest {
        public String baseUrl;
        public LoginConfig login = new LoginConfig();
        public List<ModuleConfig> modules = new ArrayList<>();
        public String outputDirectory;
        public Boolean headless = true;
        public Integer viewportWidth = 1440;
        public Integer viewportHeight = 900;
    }

    public static class LoginConfig {
        public String path = "/login";
        public String username;
        public String password;
        public String usernameSelector = "input[name='username'], input[type='email']";
        public String passwordSelector = "input[name='password'], input[type='password']";
        public String submitSelector = "button[type='submit'], input[type='submit']";
        public String successUrlContains;
    }

    public static class ModuleConfig {
        public String name;
        public String path = "/";
        public String description = "";
    }

    record Finding(String severity, String category, String message, String recommendation) {
    }

    record ModuleResult(String name, String url, String title, String screenshot, long durationMs,
                        List<Finding> findings, String error) {
    }

    record AuditResult(String auditId, String startedAt, String finishedAt, String baseUrl,
                       List<ModuleResult> modules, String markdownReport, String htmlReport) {
    }
}
