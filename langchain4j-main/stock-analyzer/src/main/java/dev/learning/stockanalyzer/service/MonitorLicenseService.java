package dev.learning.stockanalyzer.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.AEADBadTagException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.Base64;

@Service
public class MonitorLicenseService {

    public static final String UNAVAILABLE_MESSAGE = "通道积分已耗尽，暂无法提供服务";
    private static final String TOKEN_PREFIX = "v2.";
    private static final String PURPOSE_PREFIX = "monitor|";
    private static final byte[] KEY = Base64.getDecoder()
            .decode("JUMdi4sA1YvnrEGwG2JaemUpWtJu2sAs50d29/8iDGU=");
    private static final byte[] PUBLIC_KEY = Base64.getDecoder()
            .decode("MCowBQYDK2VwAyEAmS9MjiIjce13JB8ZrORPQOo2UjiaLLy7H23o4wj+GJU=");
    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Shanghai");

    private final String token;

    public MonitorLicenseService(@Value("${stock.license.monitor-expiry-ciphertext:${stock.license.monitor-token:}}") String token) {
        this.token = normalizeCiphertext(token);
    }

    private static String normalizeCiphertext(String value) {
        String normalized = value == null ? "" : value.trim();
        while (normalized.startsWith("=")) {
            normalized = normalized.substring(1).trim();
        }
        return normalized;
    }

    public void assertMonitorAvailable() {
        if (!isMonitorAvailable(LocalDate.now(BUSINESS_ZONE))) {
            throw new FeatureUnavailableException(UNAVAILABLE_MESSAGE);
        }
    }

    boolean isMonitorAvailable(LocalDate today) {
        LocalDate expiresOn = decryptExpiry(token);
        return expiresOn != null && !today.isAfter(expiresOn);
    }

    static LocalDate decryptExpiry(String encodedToken) {
        if (encodedToken == null || !encodedToken.startsWith(TOKEN_PREFIX)) return null;
        try {
            String[] parts = encodedToken.substring(TOKEN_PREFIX.length()).split("\\.", -1);
            if (parts.length != 2) return null;
            byte[] payload = Base64.getUrlDecoder().decode(parts[0]);
            byte[] signatureBytes = Base64.getUrlDecoder().decode(parts[1]);
            Signature verifier = Signature.getInstance("Ed25519");
            verifier.initVerify(KeyFactory.getInstance("Ed25519")
                    .generatePublic(new X509EncodedKeySpec(PUBLIC_KEY)));
            verifier.update(payload);
            if (!verifier.verify(signatureBytes)) return null;
            if (payload.length <= 12) return null;
            byte[] nonce = Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, nonce));
            String value = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
            if (!value.startsWith(PURPOSE_PREFIX)) return null;
            return LocalDate.parse(value.substring(PURPOSE_PREFIX.length()));
        } catch (AEADBadTagException e) {
            return null;
        } catch (Exception e) {
            return null;
        }
    }

    static String createToken(LocalDate expiresOn, PrivateKey privateKey) {
        try {
            byte[] nonce = new byte[12];
            new SecureRandom().nextBytes(nonce);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(KEY, "AES"), new GCMParameterSpec(128, nonce));
            byte[] encrypted = cipher.doFinal(
                    (PURPOSE_PREFIX + expiresOn).getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[nonce.length + encrypted.length];
            System.arraycopy(nonce, 0, payload, 0, nonce.length);
            System.arraycopy(encrypted, 0, payload, nonce.length, encrypted.length);
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(payload);
            byte[] signature = signer.sign();
            return TOKEN_PREFIX
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(payload)
                    + "."
                    + Base64.getUrlEncoder().withoutPadding().encodeToString(signature);
        } catch (Exception e) {
            throw new IllegalStateException("无法生成盯盘授权令牌", e);
        }
    }
}
