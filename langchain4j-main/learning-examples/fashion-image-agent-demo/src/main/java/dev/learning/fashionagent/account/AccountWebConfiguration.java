package dev.learning.fashionagent.account;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class AccountWebConfiguration implements WebMvcConfigurer {
    private final AccountService accounts;
    private final ObjectMapper mapper;

    public AccountWebConfiguration(AccountService accounts, ObjectMapper mapper) {
        this.accounts = accounts; this.mapper = mapper;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new AuthenticationInterceptor(accounts, mapper))
                .addPathPatterns("/", "/index.html", "/api/**")
                .excludePathPatterns("/api/auth/login");
    }
}
