package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryCodeResolver {
    private final Map<String, String> aliases = new HashMap<>();

    public CountryCodeResolver() {
        aliases.put("sg", "singapore"); aliases.put("singapore", "singapore");
        aliases.put("us", "usa"); aliases.put("usa", "usa");
        aliases.put("de", "germany"); aliases.put("germany", "germany");
        aliases.put("al", "albania"); aliases.put("albania", "albania");
        aliases.put("pl", "poland"); aliases.put("poland", "poland");
        aliases.put("au", "australia"); aliases.put("australia", "australia");
        aliases.put("cz", "czechia"); aliases.put("czechia", "czechia"); aliases.put("czech", "czechia");
        aliases.put("gb", "uk"); aliases.put("uk", "uk");
        aliases.put("my", "malaysia"); aliases.put("malaysia", "malaysia");
        aliases.put("jp", "japan"); aliases.put("japan", "japan");
    }

    public String policyKey(String code) {
        if (code == null) return "";
        String value = code.trim().toLowerCase(Locale.ROOT);
        return aliases.getOrDefault(value, value);
    }

    public String routeCode(String policyKey) {
        if (policyKey == null) return "";
        for (Map.Entry<String,String> e : aliases.entrySet()) {
            if (e.getValue().equals(policyKey) && e.getKey().length() <= 3) return e.getKey();
        }
        return policyKey;
    }

    public Set<String> knownRouteAliases() { return Collections.unmodifiableSet(aliases.keySet()); }
}