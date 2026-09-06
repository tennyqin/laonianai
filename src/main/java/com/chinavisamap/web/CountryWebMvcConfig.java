package com.chinavisamap.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CountryWebMvcConfig implements WebMvcConfigurer {
    private final CountryEligibilityModelInterceptor eligibilityInterceptor;
    private final CountryPolicyRouteGuardInterceptor routeGuardInterceptor;

    public CountryWebMvcConfig(CountryEligibilityModelInterceptor eligibilityInterceptor,
                                CountryPolicyRouteGuardInterceptor routeGuardInterceptor) {
        this.eligibilityInterceptor = eligibilityInterceptor;
        this.routeGuardInterceptor = routeGuardInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(routeGuardInterceptor).addPathPatterns("/country/**");
        registry.addInterceptor(eligibilityInterceptor).addPathPatterns("/country/*");
    }
}
