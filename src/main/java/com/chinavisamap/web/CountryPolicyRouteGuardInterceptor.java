package com.chinavisamap.web;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.StructuredDataService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component
public class CountryPolicyRouteGuardInterceptor implements HandlerInterceptor {
    private static final Set<String> NOT_UNILATERAL = new HashSet<String>(Arrays.asList("kyrgyzstan", "vietnam"));

    private final CountryCodeResolver resolver;
    private final StructuredDataService structuredDataService;

    public CountryPolicyRouteGuardInterceptor(CountryCodeResolver resolver, StructuredDataService structuredDataService) {
        this.resolver = resolver;
        this.structuredDataService = structuredDataService;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/country/")) {
            return true;
        }
        String[] parts = path.substring("/country/".length()).split("/");
        if (parts.length == 2 && "unilateral".equals(parts[1]) && NOT_UNILATERAL.contains(resolver.policyKey(parts[0]))) {
            response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
            response.setHeader("Location", "/country/" + resolver.routeCode(parts[0]) + "?lang=" + safeLang(request.getParameter("lang")));
            return false;
        }
        return true;
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
        String key = resolver.policyKey(code);
        if (!NOT_UNILATERAL.contains(key)) {
            return;
        }

        Object typesObject = modelAndView.getModel().get("availableTypes");
        if (typesObject instanceof List) {
            ((List<?>) typesObject).remove("unilateral");
        }
        Object policiesObject = modelAndView.getModel().get("availablePolicies");
        if (policiesObject instanceof List) {
            ((List<?>) policiesObject).removeIf(item -> item instanceof CountryDetail && "unilateral".equals(((CountryDetail) item).getPolicyType()));
        }

        Object typesAfter = modelAndView.getModel().get("availableTypes");
        Object detailObject = modelAndView.getModel().get("detailCountry");
        Object extraObject = modelAndView.getModel().get("countryExtraRoot");
        Object langObject = modelAndView.getModel().get("lang");
        Object canonicalObject = modelAndView.getModel().get("canonicalUrl");
        if (detailObject instanceof CountryDetail && typesAfter instanceof List && extraObject instanceof Map) {
            Map<String, CountryDetail> details = new LinkedHashMap<String, CountryDetail>();
            Object policyObject = modelAndView.getModel().get("availablePolicies");
            if (policyObject instanceof List) {
                for (Object item : (List<?>) policyObject) {
                    if (item instanceof CountryDetail) {
                        CountryDetail detail = (CountryDetail) item;
                        details.put(detail.getPolicyType(), detail);
                    }
                }
            }
            String lang = langObject == null ? "en" : String.valueOf(langObject);
            String canonical = canonicalObject == null ? "" : String.valueOf(canonicalObject);
            modelAndView.getModel().put("structuredData", structuredDataService.buildCountryHome(
                    (CountryDetail) detailObject,
                    lang,
                    canonical,
                    (Map<String, Object>) extraObject,
                    (List<String>) typesAfter,
                    details));
        }
    }

    private String safeLang(String value) {
        return "zh".equalsIgnoreCase(value) ? "zh" : "en";
    }
}
