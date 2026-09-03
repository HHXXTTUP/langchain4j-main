package dev.learning.fashionagent.config;

import dev.learning.fashionagent.account.AccountContext;
import java.nio.charset.StandardCharsets;
import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** Configuration for the Gemini-compatible text generation endpoint. */
@ConfigurationProperties(prefix = "gemini")
public class GeminiProperties {
    private URI baseUrl = URI.create("https://api.rtoc.cc");
    private String apiKey;
    private String model = "gemini-3.7-flash";

    public URI getBaseUrl() { return baseUrl; }
    public void setBaseUrl(URI baseUrl) { this.baseUrl = baseUrl; }
    public String getApiKey() { return AccountContext.secretValue("geminiKey", apiKey); }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public String requiredApiKey() {
        String effective = getApiKey();
        if (effective == null || effective.isBlank()) {
            throw new IllegalStateException("请先在账号配置中填写 Gemini API Key");
        }
        return effective.trim();
    }

    public ApiKeyDiagnostic apiKeyDiagnostic() {
        AccountContext.Snapshot account = AccountContext.current();
        String configured = account == null ? null : account.settings().get("geminiKey");
        String effective = getApiKey();
        String source = configured != null && !configured.isBlank()
                ? "account-settings:" + account.username()
                : "environment:NEW_API_KEY";
        return new ApiKeyDiagnostic(source, fingerprint(effective), effective == null ? 0 : effective.trim().length());
    }

    private static String fingerprint(String value) {
        if (value == null || value.isBlank()) return "none";
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.trim().getBytes(StandardCharsets.UTF_8));
            StringBuilder result = new StringBuilder();
            for (int index = 0; index < 6; index++) result.append(String.format("%02x", digest[index]));
            return "sha256:" + result;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 Java 环境不支持 SHA-256", exception);
        }
    }

    public record ApiKeyDiagnostic(String source, String fingerprint, int length) {}
}
