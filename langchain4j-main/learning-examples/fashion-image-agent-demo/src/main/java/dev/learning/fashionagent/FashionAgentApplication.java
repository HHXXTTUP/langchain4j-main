package dev.learning.fashionagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class FashionAgentApplication {

    public static void main(String[] args) {
        // Some Windows networks advertise DashScope IPv6 records but cannot route
        // them reliably. Prefer IPv4 before any HTTP client is created.
        System.setProperty("java.net.preferIPv4Stack", "true");
        System.setProperty("java.net.preferIPv6Addresses", "false");
        SpringApplication.run(FashionAgentApplication.class, args);
    }
}
