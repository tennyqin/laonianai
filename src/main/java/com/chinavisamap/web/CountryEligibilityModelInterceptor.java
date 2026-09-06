package com.chinavisamap.web;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.CountryEligibilityService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@Component
public class CountryEligibilityModelInterceptor implements HandlerInterceptor {
    private final CountryEligibilityService eligibilityService;
    private final CountryCodeResolver resolver;

    public CountryEligibilityModelInterceptor(CountryEligibilityService eligibilityService, CountryCodeResolver resolver) {
        this.eligibilityService = eligibilityService;
        this.resolver = resolver;
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView == null || !"country-home".equals(modelAndView.getViewName())) {
            return;
        }
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/country/")) {
            return;
        }
        String code = path.substring("/country/".length());
        if (code.length() == 0 || code.indexOf('/') >= 0) {
            return;
        }

        Object policyObject = modelAndView.getModel().get("availablePolicies");
        List<CountryDetail> policies = new ArrayList<CountryDetail>();
        if (policyObject instanceof List) {
            for (Object item : (List<?>) policyObject) {
                if (item instanceof CountryDetail) {
                    policies.add((CountryDetail) item);
                }
            }
        }
        Object extraObject = modelAndView.getModel().get("countryExtraRoot");
        Map<String, Object> extra = extraObject instanceof Map
                ? (Map<String, Object>) extraObject
                : Collections.<String, Object>emptyMap();

        modelAndView.getModel().put("eligibilityConfig",
                eligibilityService.build(resolver.routeCode(code), policies, extra));
    }
}
