package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryCodeResolver {
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> canonicalRoutes = new LinkedHashMap<>();

    public CountryCodeResolver() {
        add("sg", "singapore");
        add("us", "usa");
        add("de", "germany");
        add("al", "albania");
        add("pl", "poland");
        add("au", "australia");
        add("cz", "czechia");
        aliases.put("czech", "czechia");
        add("gb", "uk");
        add("my", "malaysia");
        add("jp", "japan");
    }

    private void add(String route, String policyKey) {
        aliases.put(route, policyKey);
        aliases.put(policyKey, policyKey);
        canonicalRoutes.put(policyKey, route);
    }

    public String policyKey(String code) {
        if (code == null) return "";
        String value = code.trim().toLowerCase(Locale.ROOT);
        return aliases.getOrDefault(value, value);
    }

    /**
     * Returns the single canonical route code for a policy key.
     * This must be deterministic: HashMap iteration must never decide an SEO URL.
     */
    public String routeCode(String policyKey) {
        if (policyKey == null) return "";
        String value = policyKey.trim().toLowerCase(Locale.ROOT);
        return canonicalRoutes.getOrDefault(value, value);
    }

    public Set<String> knownRouteAliases() {
        return Collections.unmodifiableSet(aliases.keySet());
    }
}