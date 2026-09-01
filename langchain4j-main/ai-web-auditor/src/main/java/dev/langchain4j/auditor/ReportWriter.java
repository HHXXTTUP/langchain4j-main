package dev.langchain4j.auditor;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static dev.langchain4j.auditor.AuditModels.*;

final class ReportWriter {
    ReportFiles write(AuditResult result, Path directory) throws Exception {
        Files.createDirectories(directory);
        String markdown = markdown(result);
        String html = html(result, markdown);
        Path md = directory.resolve("report.md");
        Path htmlPath = directory.resolve("report.html");
        Files.writeString(md, markdown, StandardCharsets.UTF_8);
        Files.writeString(htmlPath, html, StandardCharsets.UTF_8);
        return new ReportFiles(md.toString(), htmlPath.toString());
    }

    private String markdown(AuditResult result) {
        StringBuilder out = new StringBuilder("# AI Web Auditor Report\n\n");
        out.append("- Audit: `").append(result.auditId()).append("`\n");
        out.append("- Target: `").append(result.baseUrl()).append("`\n");
        out.append("- Started: ").append(result.startedAt()).append("\n");
        out.append("- Finished: ").append(result.finishedAt()).append("\n\n");
        for (ModuleResult module : result.modules()) {
            out.append("## ").append(module.name()).append("\n\n");
            out.append("URL: `").append(module.url()).append("`  \nTitle: ").append(module.title().isBlank() ? "(blank)" : module.title()).append("  \n");
            out.append("Duration: ").append(module.durationMs()).append(" ms  \nScreenshot: `").append(module.screenshot()).append("`\n\n");
            if (module.error() != null) {
                out.append("> **FAILED:** ").append(module.error()).append("\n\n");
            }
            if (module.findings().isEmpty()) {
                out.append("No deterministic findings.\n\n");
            } else {
                out.append("| Severity | Category | Finding | Recommendation |\n|---|---|---|---|\n");
                for (Finding finding : module.findings()) {
                    out.append("| ").append(finding.severity()).append(" | ").append(finding.category()).append(" | ")
                            .append(finding.message().replace("|", "\\|")) .append(" | ")
                            .append(finding.recommendation().replace("|", "\\|")) .append(" |\n");
                }
                out.append("\n");
            }
        }
        return out.toString();
    }

    private String html(AuditResult result, String markdown) {
        StringBuilder body = new StringBuilder();
        body.append("<h1>AI Web Auditor Report</h1><dl>")
                .append("<dt>Audit</dt><dd><code>").append(escape(result.auditId())).append("</code></dd>")
                .append("<dt>Target</dt><dd><code>").append(escape(result.baseUrl())).append("</code></dd>")
                .append("<dt>Started</dt><dd>").append(escape(result.startedAt())).append("</dd>")
                .append("<dt>Finished</dt><dd>").append(escape(result.finishedAt())).append("</dd></dl>");
        for (ModuleResult module : result.modules()) {
            body.append("<section><h2>").append(escape(module.name())).append("</h2>")
                    .append("<p><b>URL:</b> <code>").append(escape(module.url())).append("</code><br>")
                    .append("<b>Title:</b> ").append(escape(module.title().isBlank() ? "(blank)" : module.title()))
                    .append("<br><b>Duration:</b> ").append(module.durationMs()).append(" ms</p>");
            if (module.screenshot() != null && !module.screenshot().isBlank()) {
                body.append("<p><a href=\"").append(escape(module.screenshot())).append("\">Open screenshot evidence</a></p>");
            }
            if (module.error() != null) {
                body.append("<p class=\"failed\"><b>FAILED:</b> ").append(escape(module.error())).append("</p>");
            }
            if (module.findings().isEmpty()) {
                body.append("<p class=\"ok\">No deterministic findings.</p>");
            } else {
                body.append("<table><thead><tr><th>Severity</th><th>Category</th><th>Finding</th><th>Recommendation</th></tr></thead><tbody>");
                for (Finding finding : module.findings()) {
                    body.append("<tr><td class=\"").append(escape(finding.severity())).append("\">")
                            .append(escape(finding.severity())).append("</td><td>").append(escape(finding.category()))
                            .append("</td><td>").append(escape(finding.message())).append("</td><td>")
                            .append(escape(finding.recommendation())).append("</td></tr>");
                }
                body.append("</tbody></table>");
            }
            body.append("</section>");
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\"><title>AI Web Auditor Report</title>"
                + "<style>body{font:15px system-ui;max-width:1100px;margin:40px auto;padding:0 24px;color:#202124}"
                + "h1{border-bottom:2px solid #222;padding-bottom:12px}h2{margin-top:32px}section{border-top:1px solid #ddd;padding-top:8px}"
                + "dl{display:grid;grid-template-columns:100px 1fr;gap:6px 12px}dt{font-weight:700}code{background:#f1f3f4;padding:2px 4px;border-radius:3px}"
                + "table{width:100%;border-collapse:collapse;margin-top:12px}th,td{border:1px solid #ddd;padding:8px;text-align:left;vertical-align:top}"
                + "th{background:#f5f5f5}.high{color:#b42318}.medium{color:#b54708}.failed{color:#b42318}.ok{color:#067647}</style></head><body>"
                + body + "</body></html>";
    }

    private static String escape(String value) {
        return value.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    record ReportFiles(String markdown, String html) {
    }
}
