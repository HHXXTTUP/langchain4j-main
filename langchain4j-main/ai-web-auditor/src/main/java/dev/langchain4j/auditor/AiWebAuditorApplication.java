package dev.langchain4j.auditor;

import java.nio.file.Path;

/** Entry point for the standalone MVP service. */
public final class AiWebAuditorApplication {

    private AiWebAuditorApplication() {
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(System.getenv().getOrDefault("AI_WEB_AUDITOR_PORT", "8787"));
        for (int i = 0; i < args.length - 1; i++) {
            if ("--port".equals(args[i])) {
                port = Integer.parseInt(args[++i]);
            }
        }
        Path defaultOutput = Path.of(System.getenv().getOrDefault("AI_WEB_AUDITOR_OUTPUT", "audit-results"));
        AuditorService service = new AuditorService(new PlaywrightAuditRunner(), new ReportWriter(), defaultOutput);
        AuditorHttpServer server = new AuditorHttpServer(port, service);
        server.start();
        System.out.printf("AI Web Auditor listening on http://localhost:%d%n", port);
        Runtime.getRuntime().addShutdownHook(new Thread(server::stop));
    }
}
