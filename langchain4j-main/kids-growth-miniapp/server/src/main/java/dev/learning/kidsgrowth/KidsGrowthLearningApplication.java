package dev.learning.kidsgrowth;

import dev.learning.kidsgrowth.config.ChildAiProperties;
import dev.learning.kidsgrowth.config.EdgeTtsProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@EnableConfigurationProperties({ChildAiProperties.class, EdgeTtsProperties.class})
public class KidsGrowthLearningApplication {

    public static void main(String[] args) {
        SpringApplication.run(KidsGrowthLearningApplication.class, args);
    }
}
