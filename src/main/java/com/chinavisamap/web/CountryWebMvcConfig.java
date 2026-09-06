package com.chinavisamap.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CountryWebMvcConfig implements WebMvcConfigurer {
    private final ContentLinkModelInterceptor contentLinkInterceptor;

    public CountryWebMvcConfig(ContentLinkModelInterceptor contentLinkInterceptor) {
        this.contentLinkInterceptor = contentLinkInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // CountryController is the single source of truth for country/policy models.
        // Only the deterministic internal-link interceptor is layered on top.
        registry.addInterceptor(contentLinkInterceptor).addPathPatterns("/country/**", "/articles/**");
    }
}
