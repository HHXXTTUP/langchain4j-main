package dev.learning.fashionagent.service;

import dev.learning.fashionagent.config.QwenProperties;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ProxySelector;
import java.net.Socket;
import java.net.http.HttpClient;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Component
public class QwenRestClientProvider {
    private static final Logger LOGGER = LoggerFactory.getLogger(QwenRestClientProvider.class);
    private static final int PROXY_CHECK_TIMEOUT_MILLIS = 500;

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
    }

    public Selection select() {
        boolean proxyConfigured = proxyClient != null;
        boolean proxyReachable = proxyConfigured && isReachable(proxyHost, proxyPort);
        if (proxyReachable) {
            return new Selection(proxyClient, "proxy=" + proxyHost + ":" + proxyPort);
        }
        if (proxyConfigured) {
            LOGGER.warn("千问代理不可用，本次请求将尝试直连 proxy={}:{}；若直连出现 TLS handshake/Connection reset，"
                    + "请启动该端口的代理或设置 QWEN_PROXY_HOST/QWEN_PROXY_PORT",
                    proxyHost, proxyPort);
        }
        return new Selection(directClient, "direct");
    }

    public String configuredRoute() {
        return proxyClient == null ? "direct-only" : "dynamic-proxy=" + proxyHost + ":" + proxyPort;
    }

    private RestClient buildClient(ProxySelector proxySelector) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .version(HttpClient.Version.HTTP_1_1)
                .connectTimeout(properties.getConnectTimeout());
        if (proxySelector != null) builder.proxy(proxySelector);
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
