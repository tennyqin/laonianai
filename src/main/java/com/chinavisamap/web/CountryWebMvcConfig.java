package com.chinavisamap.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CountryWebMvcConfig implements WebMvcConfigurer {
    private final CountryPolicyRouteGuardInterceptor routeGuardInterceptor;
    private final ContentLinkModelInterceptor contentLinkInterceptor;

    public CountryWebMvcConfig(CountryPolicyRouteGuardInterceptor routeGuardInterceptor,
                               ContentLinkModelInterceptor contentLinkInterceptor) {
        this.routeGuardInterceptor = routeGuardInterceptor;
        this.contentLinkInterceptor = contentLinkInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Keep one canonical country URL per country. Legacy/alias country routes
        // are permanently redirected before the model/link interceptor runs.
        registry.addInterceptor(routeGuardInterceptor).addPathPatterns("/country/**");

        // CountryController remains the single source of truth for country/policy models.
        // The deterministic internal-link interceptor is layered on top afterwards.
        registry.addInterceptor(contentLinkInterceptor).addPathPatterns("/country/**", "/articles/**");
    }
}
