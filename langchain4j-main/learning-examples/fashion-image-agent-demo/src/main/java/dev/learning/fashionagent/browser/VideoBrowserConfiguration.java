package dev.learning.fashionagent.browser;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(VideoBrowserProperties.class)
public class VideoBrowserConfiguration {}
