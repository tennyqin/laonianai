package com.chinavisamap.web;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CountryWebMvcConfig implements WebMvcConfigurer {
    private final CountryEligibilityModelInterceptor eligibilityInterceptor;
    private final CountryPolicyRouteGuardInterceptor routeGuardInterceptor;
    private final CountryPolicyCorrectionInterceptor correctionInterceptor;
    private final ContentLinkModelInterceptor contentLinkInterceptor;

    public CountryWebMvcConfig(CountryEligibilityModelInterceptor eligibilityInterceptor,
                                CountryPolicyRouteGuardInterceptor routeGuardInterceptor,
                                CountryPolicyCorrectionInterceptor correctionInterceptor,
                                ContentLinkModelInterceptor contentLinkInterceptor) {
        this.eligibilityInterceptor = eligibilityInterceptor;
        this.routeGuardInterceptor = routeGuardInterceptor;
        this.correctionInterceptor = correctionInterceptor;
        this.contentLinkInterceptor = contentLinkInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Registered first so its postHandle runs last and can finalize localized priority content + structured data.
        registry.addInterceptor(contentLinkInterceptor).addPathPatterns("/country/**", "/articles/**");
        registry.addInterceptor(routeGuardInterceptor).addPathPatterns("/country/**");
        registry.addInterceptor(eligibilityInterceptor).addPathPatterns("/country/*");
        registry.addInterceptor(correctionInterceptor).addPathPatterns("/country/**");
    }
}
