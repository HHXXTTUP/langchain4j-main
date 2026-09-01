package dev.learning.fashionagent.integration.runninghub;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.sun.net.httpserver.HttpServer;
import dev.learning.fashionagent.config.RunningHubProperties;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RunningHubClientUploadTest {

    @Test
    void shouldDownloadEncodedVideoPathWithoutDoubleEncoding() throws Exception {
        AtomicReference<String> rawPath = new AtomicReference<>();
        byte[] video = new byte[] {4, 5, 6};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            rawPath.set(exchange.getRequestURI().getRawPath());
            exchange.sendResponseHeaders(200, video.length);
            exchange.getResponseBody().write(video);
            exchange.close();
        });
        server.start();

        try {
            RunningHubProperties properties = new RunningHubProperties();
            RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);
            URI videoUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort()
                    + "/output/h264%20yuv420p_00001.mp4");

            Path directory = Files.createTempDirectory("runninghub-download-");
            Path target = directory.resolve("video.mp4");
            Path downloaded = client.downloadToFile(videoUri, target, properties.getVideoMaxDownloadBytes());

            assertArrayEquals(video, Files.readAllBytes(downloaded));
            assertEquals("/output/h264%20yuv420p_00001.mp4", rawPath.get());
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDownloadThroughPowerShellFallback() throws Exception {
        byte[] video = new byte[] {7, 8, 9};
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/video.mp4", exchange -> {
            exchange.sendResponseHeaders(200, video.length);
            exchange.getResponseBody().write(video);
            exchange.close();
        });
        server.start();

        try {
            RunningHubProperties properties = new RunningHubProperties();
            RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);
            URI videoUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/video.mp4");
            Path target = Files.createTempDirectory("runninghub-curl-download-").resolve("video.mp4");

            Path downloaded = client.downloadToFileViaPowerShell(
                    videoUri, target, properties.getVideoMaxDownloadBytes());

            assertArrayEquals(video, Files.readAllBytes(downloaded));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldDownloadLiveRunningHubVideoWhenProbeIsEnabled() throws Exception {
        assumeTrue(Boolean.getBoolean("runninghub.live-download-test"));
        String url = System.getProperty("runninghub.live-download-url");
        assumeTrue(url != null && !url.isBlank());

        RunningHubProperties properties = new RunningHubProperties();
        RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);
        Path target = Path.of("target", "runninghub-live-download", "video.mp4").toAbsolutePath();
        client.downloadToFileViaPowerShell(
                URI.create(url), target, properties.getVideoMaxDownloadBytes());
        assertTrue(Files.size(target) > 1024);
    }

    @Test
    void shouldTimeOutWhenVideoServerStopsResponding() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/slow.mp4", exchange -> {
            try {
                Thread.sleep(1_000);
                exchange.sendResponseHeaders(200, 1);
                exchange.getResponseBody().write(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();

        try {
            RunningHubProperties properties = new RunningHubProperties();
            properties.setDownloadConnectTimeout(Duration.ofMillis(100));
            properties.setDownloadReadTimeout(Duration.ofMillis(100));
            RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);
            URI videoUri = URI.create("http://127.0.0.1:" + server.getAddress().getPort() + "/slow.mp4");
            Path target = Files.createTempDirectory("runninghub-timeout-").resolve("video.mp4");

            assertTimeoutPreemptively(Duration.ofSeconds(3), () -> assertThrows(
                    RuntimeException.class,
                    () -> client.downloadToFile(videoUri, target, properties.getVideoMaxDownloadBytes())));
            assertFalse(Files.exists(target.resolveSibling("video.mp4.part")));
        } finally {
            server.stop(0);
        }
    }

    @Test
    void shouldUploadMultipartFileWithSimpleRequestFactory() throws Exception {
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> contentType = new AtomicReference<>();
        AtomicReference<byte[]> requestBody = new AtomicReference<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/openapi/v2/media/upload/binary", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
            requestBody.set(exchange.getRequestBody().readAllBytes());
            byte[] response = """
                    {"code":0,"message":"success","data":{"type":"image","fileName":"openapi/test.png","size":"13"}}
                    """.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();

        Path image = Files.createTempFile("runninghub-upload-", ".png");
        Files.writeString(image, "image-payload", StandardCharsets.UTF_8);
        try {
            RunningHubProperties properties = new RunningHubProperties();
            properties.setBaseUrl(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
            properties.setApiKey("test-key");
            properties.setUploadConnectTimeout(Duration.ofSeconds(2));
            properties.setUploadReadTimeout(Duration.ofSeconds(2));
            RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);

            String remoteFileName = client.upload(image);

            assertEquals("openapi/test.png", remoteFileName);
            assertEquals("Bearer test-key", authorization.get());
            assertTrue(contentType.get().startsWith("multipart/form-data;boundary="));
            String multipartBody = new String(requestBody.get(), StandardCharsets.ISO_8859_1);
            assertTrue(multipartBody.contains("filename=\"" + image.getFileName() + "\""));
            assertTrue(multipartBody.contains("image-payload"));
        } finally {
            Files.deleteIfExists(image);
            server.stop(0);
        }
    }

    @Test
    void shouldUploadRealFileWhenLiveProbeIsEnabled() {
        assumeTrue("true".equalsIgnoreCase(System.getenv("RUNNINGHUB_LIVE_UPLOAD_TEST")));
        String apiKey = System.getenv("RUNNINGHUB_API_KEY");
        String imagePath = System.getProperty("runninghub.live-upload-file");
        assumeTrue(apiKey != null && !apiKey.isBlank());
        assumeTrue(imagePath != null && !imagePath.isBlank());

        RunningHubProperties properties = new RunningHubProperties();
        properties.setApiKey(apiKey);
        RunningHubClient client = new RunningHubClient(RestClient.builder(), properties);

        String remoteFileName = client.upload(Path.of(imagePath));

        assertFalse(remoteFileName.isBlank());
    }
}
