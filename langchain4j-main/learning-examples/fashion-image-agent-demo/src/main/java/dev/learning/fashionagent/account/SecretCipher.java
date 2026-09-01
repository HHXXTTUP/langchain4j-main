package dev.learning.fashionagent.account;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

@Component
public class SecretCipher {
    private static final SecureRandom RANDOM = new SecureRandom();
    private final SecretKey key;

    public SecretCipher() {
        this.key = loadOrCreateKey(Path.of("data", "application-secret.key").toAbsolutePath().normalize());
    }

    public String encrypt(String plainText) {
        if (plainText == null || plainText.isBlank()) return null;
        try {
            byte[] iv = new byte[12]; RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));
            byte[] payload = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, payload, 0, iv.length);
            System.arraycopy(encrypted, 0, payload, iv.length, encrypted.length);
            return "enc:" + Base64.getEncoder().encodeToString(payload);
        } catch (Exception e) { throw new IllegalStateException("敏感配置加密失败", e); }
    }

    public String decrypt(String value) {
        if (value == null || value.isBlank()) return null;
        if (!value.startsWith("enc:")) return value;
        try {
            byte[] payload = Base64.getDecoder().decode(value.substring(4));
            byte[] iv = java.util.Arrays.copyOfRange(payload, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(payload, 12, payload.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (Exception e) { throw new IllegalStateException("敏感配置解密失败", e); }
    }

    private static SecretKey loadOrCreateKey(Path path) {
        try {
            Files.createDirectories(path.getParent());
            if (Files.isRegularFile(path)) return new SecretKeySpec(Base64.getDecoder().decode(Files.readString(path).trim()), "AES");
            KeyGenerator generator = KeyGenerator.getInstance("AES"); generator.init(256);
            SecretKey key = generator.generateKey();
            Files.writeString(path, Base64.getEncoder().encodeToString(key.getEncoded()), StandardOpenOption.CREATE_NEW);
            return key;
        } catch (Exception e) { throw new IllegalStateException("无法初始化本地配置加密密钥", e); }
    }
}
