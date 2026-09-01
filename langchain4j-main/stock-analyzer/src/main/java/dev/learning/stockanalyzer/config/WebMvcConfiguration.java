package dev.learning.stockanalyzer.config;

import dev.learning.stockanalyzer.web.PublicCleanExternalAccessInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebMvcConfiguration implements WebMvcConfigurer {

    private final PublicCleanExternalAccessInterceptor externalAccessInterceptor;

    public WebMvcConfiguration(PublicCleanExternalAccessInterceptor externalAccessInterceptor) {
        this.externalAccessInterceptor = externalAccessInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(externalAccessInterceptor).addPathPatterns("/api/**");
    }
}
