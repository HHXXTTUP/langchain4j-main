package dev.learning.kidsgrowth.web;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class HealthControllerTest {

    @Test
    void reportsServiceAsReady() {
        assertThat(new HealthController().health())
                .containsEntry("status", "UP")
                .containsEntry("service", "kids-growth-learning-server");
    }
}
