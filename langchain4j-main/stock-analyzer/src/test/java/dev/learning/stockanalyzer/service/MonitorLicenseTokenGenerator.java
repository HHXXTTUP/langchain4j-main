package dev.learning.stockanalyzer.service;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyFactory;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Base64;

public final class MonitorLicenseTokenGenerator {

    private MonitorLicenseTokenGenerator() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2) {
            throw new IllegalArgumentException("Usage: MonitorLicenseTokenGenerator yyyyMMdd private-key-file");
        }
        LocalDate expiresOn = LocalDate.parse(args[0], DateTimeFormatter.BASIC_ISO_DATE);
        byte[] privateKeyBytes = Base64.getDecoder().decode(Files.readString(Path.of(args[1])).trim());
        var privateKey = KeyFactory.getInstance("Ed25519")
                .generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
        System.out.println("stock.license.monitor-expiry-ciphertext="
                + MonitorLicenseService.createToken(expiresOn, privateKey));
    }
}
