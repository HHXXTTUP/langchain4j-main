package dev.langchain4j.auditor;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;

final class AuditorHttpServer {
    private final HttpServer server;
    private final AuditorService service;

    AuditorHttpServer(int port, AuditorService service) throws IOException {
        this.server = HttpServer.create(new InetSocketAddress("localhost", port), 0);
        this.service = service;
        server.createContext("/api/health", this::health);
        server.createContext("/api/audits", this::audits);
        // Keep the MVP compatible with the repository's Java 17 baseline.
        server.setExecutor(Executors.newCachedThreadPool());
    }

    void start() { server.start(); }
    void stop() { server.stop(1); }

    private void health(HttpExchange exchange) throws IOException {
        respond(exchange, 200, "{\"status\":\"ok\"}");
    }

    private void audits(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            respond(exchange, 405, "{\"error\":\"method not allowed\"}");
            return;
        }
        try (InputStream body = exchange.getRequestBody()) {
            String json = new String(body.readAllBytes(), StandardCharsets.UTF_8);
            respond(exchange, 200, service.toJson(service.run(service.fromJson(json))));
        } catch (Exception e) {
            respond(exchange, 400, errorJson(safeMessage(e)));
        }
    }

    private static String errorJson(String message) {
        return "{\"error\":\"" + message.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private static String safeMessage(Exception e) {
        String message = e.getMessage();
        return message == null ? e.getClass().getSimpleName() : message.replaceAll("(?i)(password|token|secret)\\s*[:=]\\s*[^,} ]+", "$1=[REDACTED]");
    }

    private static void respond(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) { output.write(bytes); }
    }
}
