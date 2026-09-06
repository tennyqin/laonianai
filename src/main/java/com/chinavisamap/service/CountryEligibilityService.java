package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Builds eligibility strictly from unilateral.json / mutual.json / transit.json. */
@Service
public class CountryEligibilityService {
    private final CountryCodeResolver resolver;

    public CountryEligibilityService(CountryCodeResolver resolver) {
        this.resolver = resolver;
    }

    public Map<String, Object> build(String countryCode, List<CountryDetail> policies, Map<String, Object> extra) {
        String normalized = resolver.policyKey(countryCode);
        List<Map<String, Object>> rules = buildPolicies(policies);
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "2026-09-06");
        root.put("countryCode", resolver.routeCode(normalized));
        root.put("policySource", "unilateral.json / mutual.json / transit.json");
        root.put("policies", rules);

        // Hainan is not represented in the country policy JSONs used by this page.
        // Never infer country eligibility from a hard-coded list.
        Map<String, Object> hainan = new LinkedHashMap<>();
        hainan.put("enabled", false);
        hainan.put("maxStayDays", null);
        hainan.put("purposes", Collections.emptyList());
        hainan.put("restrictedToHainan", true);
        hainan.put("requiresOrdinaryPassport", true);
        hainan.put("verificationRequired", true);
        hainan.put("officialSource", "https://en.nia.gov.cn/");
        root.put("hainan", hainan);

        // 240-hour transit is enabled only when this country's transit.json record exists.
        Map<String, Object> transitPolicy = findPolicy(rules, "transit");
        Map<String, Object> transit240 = new LinkedHashMap<>();
        boolean transitEnabled = transitPolicy != null;
        transit240.put("enabled", transitEnabled);
        transit240.put("maxStayDays", transitEnabled ? numericOrDefault(transitPolicy.get("stayDays"), 10) : null);
        transit240.put("requiresOrdinaryPassport", transitEnabled);
        transit240.put("requiresThirdCountryOrRegion", transitEnabled);
        transit240.put("requiresConfirmedOnwardTicket", transitEnabled);
        transit240.put("verificationRequired", !transitEnabled);
        transit240.put("officialSource", transitEnabled ? "https://en.nia.gov.cn/" : null);
        transit240.put("policyUrl", transitEnabled ? transitPolicy.get("policyUrl") : null);
        root.put("transit240", transit240);

        // General 24-hour transit is only a verification hint, never a country-level entitlement.
        Map<String, Object> transit24 = new LinkedHashMap<>();
        transit24.put("enabled", true);
        transit24.put("maxStayHours", 24);
        transit24.put("requiresValidInternationalTravelDocument", true);
        transit24.put("requiresConfirmedOnwardTicket", true);
        transit24.put("thirdCountryOrRegionRequired", true);
        transit24.put("restrictedAreaOnly", true);
        transit24.put("verificationRequired", true);
        transit24.put("officialSource", "https://en.nia.gov.cn/");
        root.put("transit24", transit24);

        root.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        return root;
    }

    private List<Map<String, Object>> buildPolicies(List<CountryDetail> policies) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (policies == null) return result;
        for (CountryDetail policy : policies) {
            if (policy == null) continue;
            String type = safe(policy.getPolicyType());
            if (type.isEmpty()) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("type", type);
            item.put("stayDays", parseSimpleDays(policy.getStayDays()));
            item.put("stayText", safe(policy.getStayDays()));
            item.put("purposes", purposeCodes(policy.getPurpose()));
            item.put("requiresOnward", "transit".equals(type));
            item.put("ordinaryPassportOnly", true);
            item.put("policyUrl", "/country/" + resolver.routeCode(policy.getCode()) + "/" + type);
            item.put("sourceRule", safe(policy.getRule()));
            item.put("sourceRuleZh", safe(policy.getRuleZh()));
            item.put("policyCode", resolver.policyKey(policy.getCode()));
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> findPolicy(List<Map<String, Object>> rules, String type) {
        for (Map<String, Object> rule : rules) if (type.equals(rule.get("type"))) return rule;
        return null;
    }

    private List<String> purposeCodes(String text) {
        String value = safe(text).toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        addIfContains(result, value, "tourism", "tourism", "tourist", "旅游", "观光");
        addIfContains(result, value, "business", "business", "commercial", "商务", "经商");
        addIfContains(result, value, "family", "family", "relative", "friend", "探亲", "访友");
        addIfContains(result, value, "exchange", "exchange", "交流");
        addIfContains(result, value, "transit", "transit", "过境");
        addIfContains(result, value, "visit", "visit", "访问");
        return result;
    }

    private void addIfContains(List<String> result, String value, String code, String... keywords) {
        for (String keyword : keywords) {
            if (value.contains(keyword.toLowerCase(Locale.ROOT))) {
                if (!result.contains(code)) result.add(code);
                return;
            }
        }
    }

    private Integer parseSimpleDays(String value) {
        String text = safe(value);
        if (text.isEmpty() || !text.matches("\\d+")) return null;
        try { int days = Integer.parseInt(text); return days > 0 ? days : null; }
        catch (NumberFormatException ex) { return null; }
    }

    private int numericOrDefault(Object value, int fallback) {
        try { int n = Integer.parseInt(safe(value)); return n > 0 ? n : fallback; }
        catch (Exception ex) { return fallback; }
    }

    private int number(Object value, int fallback) {
        try { return Integer.parseInt(safe(value)); }
        catch (Exception ex) { return fallback; }
    }

    private String safe(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}
