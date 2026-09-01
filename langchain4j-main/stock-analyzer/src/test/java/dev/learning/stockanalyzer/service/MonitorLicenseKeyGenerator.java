package dev.learning.stockanalyzer.service;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.KeyPairGenerator;
import java.util.Base64;

public final class MonitorLicenseKeyGenerator {

    private MonitorLicenseKeyGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: MonitorLicenseKeyGenerator private-key-output-file");
        }
        var keyPair = KeyPairGenerator.getInstance("Ed25519").generateKeyPair();
        Path privateKeyFile = Path.of(args[0]).toAbsolutePath().normalize();
        Files.createDirectories(privateKeyFile.getParent());
        Files.writeString(privateKeyFile,
                Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded()),
                StandardOpenOption.CREATE_NEW);
        System.out.println("PUBLIC_KEY=" + Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded()));
        System.out.println("PRIVATE_KEY_FILE=" + privateKeyFile);
    }
}
