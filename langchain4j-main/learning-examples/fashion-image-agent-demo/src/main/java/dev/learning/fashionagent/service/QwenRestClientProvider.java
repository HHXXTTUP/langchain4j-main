package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.QwenProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Proxy;
import java.net.Socket;
import java.net.URI;
import java.net.SocketAddress;
import java.net.http.HttpClient;
import java.util.List;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class QwenRestClientProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenRestClientProvider.class);
    private static final int PROXY_CHECK_TIMEOUT_MILLIS = 500;
    private static final ProxySelector DIRECT_PROXY_SELECTOR = new ProxySelector() {
        @Override public List<Proxy> select(URI uri) { return List.of(Proxy.NO_PROXY); }
        @Override public void connectFailed(URI uri, SocketAddress address, IOException exception) { }
    };

    private final QwenProperties properties;
    private final RestClient directClient;
    private final RestClient proxyClient;
    private final String proxyHost;
    private final int proxyPort;

    public QwenRestClientProvider(QwenProperties properties) {
        this.properties = properties;
        this.proxyHost = properties.getProxyHost() == null ? "" : properties.getProxyHost().trim();
        this.proxyPort = properties.getProxyPort();
        this.directClient = buildClient(null);
        this.proxyClient = properties.isProxyEnabled() && !proxyHost.isBlank() && proxyPort > 0
                ? buildClient(ProxySelector.of(new InetSocketAddress(proxyHost, proxyPort)))
                : null;
        LOGGER.info("千问网络路由配置 proxyEnabled={} proxy={} configuredRoute={}",
                properties.isProxyEnabled(),
                proxyHost.isBlank() || proxyPort <= 0 ? "disabled" : proxyHost + ":" + proxyPort,
                configuredRoute());
    }

    public Selection select() {
        boolean proxyConfigured = proxyClient != null;
        if (!proxyConfigured) {
            return new Selection(directClient, "direct");
        }
        boolean proxyReachable = proxyConfigured && isReachable(proxyHost, proxyPort);
        if (proxyReachable) {
            return new Selection(proxyClient, "proxy=" + proxyHost + ":" + proxyPort);
        }
        // A direct fallback is misleading on networks where DashScope is only
        // reachable through the local VPN/proxy: it results in a TLS reset
        // after a second, indistinguishable from an API failure. Stop before
        // sending the request and tell the caller exactly what to fix.
        String message = "千问代理不可用，已停止本次请求 proxy=" + proxyHost + ":" + proxyPort
                + "；请启动代理或设置 QWEN_PROXY_HOST/QWEN_PROXY_PORT。"
                + "如需直连，请显式设置 QWEN_PROXY_ENABLED=false";
        LOGGER.error(message);
        throw new IllegalStateException(message);
    }

    public Selection selectDirect() { return new Selection(directClient, "direct"); }

    public String configuredRoute() {
        return proxyClient == null ? "direct-only" : "dynamic-proxy=" + proxyHost + ":" + proxyPort;
    }

    private RestClient buildClient(ProxySelector proxySelector) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                // Leave TLS protocol selection to the running JDK. Forcing
                // TLS 1.2 here caused the DashScope connection to be reset on
                // the current network path. Java 17 negotiates TLS 1.3/1.2
                // with the server as appropriate.
                .connectTimeout(properties.getConnectTimeout());
        // Do not inherit JVM/OS proxy settings for the direct client.
        builder.proxy(proxySelector == null ? DIRECT_PROXY_SELECTOR : proxySelector);
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(builder.build());
        factory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder().requestFactory(factory).build();
    }

    private static boolean isReachable(String host, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(host, port), PROXY_CHECK_TIMEOUT_MILLIS);
            return true;
        } catch (IOException | RuntimeException ignored) {
            return false;
        }
    }

    public record Selection(RestClient client, String route) {}
}
