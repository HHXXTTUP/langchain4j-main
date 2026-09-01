package dev.learning.stockanalyzer;

import dev.learning.stockanalyzer.config.AiProperties;
import dev.learning.stockanalyzer.service.MonitorLicenseService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

class ExternalConfigurationTest {

    @Test
    void shouldLoadAiKeyAndLicenseFromExternalProperties() {
        System.setProperty("ZHIPU_API_KEY", "environment-key");
        try {
            try (ConfigurableApplicationContext context = new SpringApplicationBuilder(StockAnalyzerApplication.class)
                    .web(WebApplicationType.NONE)
                    .run(
                            "--spring.config.additional-location=classpath:/external-stock-lens.properties",
                            "--spring.datasource.url=jdbc:h2:mem:external-config-test;MODE=MySQL",
                            "--spring.datasource.username=sa",
                            "--spring.datasource.password=",
                            "--spring.datasource.driver-class-name=org.h2.Driver",
                            "--spring.jpa.hibernate.ddl-auto=create-drop",
                            "--stock.fundamentals.enabled=false",
                            "--stock.desktop.enabled=false")) {
                assertThat(context.getBean(AiProperties.class).getApiKey()).isEqualTo("customer-local-key");
                assertThat(context.getBean(MonitorLicenseService.class)).isNotNull();
            }
        } finally {
            System.clearProperty("ZHIPU_API_KEY");
        }
    }
}
