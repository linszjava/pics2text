package com.pics2text.api.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(PhotoRelayProperties.class)
public class PhotoRelayConfiguration { }
