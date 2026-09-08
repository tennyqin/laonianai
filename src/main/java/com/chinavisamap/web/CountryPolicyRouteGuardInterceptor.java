package com.chinavisamap.web;

import com.chinavisamap.service.CountryCodeResolver;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class CountryPolicyRouteGuardInterceptor implements HandlerInterceptor {
    private final CountryCodeResolver resolver;

    public CountryPolicyRouteGuardInterceptor(CountryCodeResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String path = request.getRequestURI();
        if (path == null || !path.startsWith("/country/")) return true;
        String[] parts = path.substring("/country/".length()).split("/");
        if (parts.length == 0 || parts[0] == null || parts[0].isEmpty()) return true;
        String requestedCode = parts[0];
        String canonicalCode = resolver.routeCode(resolver.policyKey(requestedCode));
        if (requestedCode.equals(canonicalCode)) return true;
        StringBuilder location = new StringBuilder("/country/").append(canonicalCode);
        if (parts.length >= 2 && parts[1] != null && !parts[1].isEmpty()) location.append('/').append(parts[1]);
        String lang = "zh".equalsIgnoreCase(request.getParameter("lang")) ? "zh" : "en";
        location.append("?lang=").append(lang);
        response.setStatus(HttpServletResponse.SC_MOVED_PERMANENTLY);
        response.setHeader("Location", location.toString());
        return false;
    }
}
