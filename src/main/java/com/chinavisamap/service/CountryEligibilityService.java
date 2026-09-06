package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryEligibilityService {

    private static final Set<String> HAINAN_COUNTRIES = Set.of(
            "albania","argentina","australia","austria","belarus","belgium","bosnia","brazil","brunei",
            "bulgaria","canada","chile","croatia","cyprus","czechia","denmark","estonia","finland",
            "france","germany","greece","hungary","iceland","indonesia","ireland","italy","japan",
            "kazakhstan","kyrgyzstan","latvia","lithuania","luxembourg","malaysia","malta","mexico",
            "monaco","montenegro","netherlands","newzealand","northmacedonia","norway","philippines",
            "poland","portugal","qatar","korea","romania","russia","serbia","singapore","slovakia",
            "slovenia","spain","sweden","switzerland","thailand","ukraine","uae","uk","usa","vietnam"
    );

    private static final Set<String> TRANSIT_240_COUNTRIES = Set.of(
            "albania","austria","belarus","belgium","bosnia","bulgaria","croatia","cyprus","czechia",
            "denmark","estonia","finland","france","germany","greece","hungary","iceland","ireland",
            "italy","latvia","lithuania","luxembourg","malta","monaco","montenegro","netherlands",
            "northmacedonia","norway","poland","portugal","romania","russia","serbia","slovakia",
            "slovenia","spain","sweden","switzerland","ukraine","uk","canada","usa","argentina",
            "brazil","chile","mexico","australia","newzealand","brunei","indonesia","japan","kyrgyzstan",
            "qatar","singapore","korea","uae","vietnam"
    );

    public Map<String, Object> build(String countryCode, List<CountryDetail> policies, Map<String, Object> extra) {
        Map<String, Object> root = new LinkedHashMap<>();
        String normalized = normalize(countryCode);
        root.put("version", "2026-08-20");
        root.put("passportTypes", List.of("ordinary"));
        root.put("ordinaryPassportOnly", true);
        root.put("policies", buildPolicies(policies));

        boolean hainan = HAINAN_COUNTRIES.contains(normalized);
        boolean transit240 = TRANSIT_240_COUNTRIES.contains(normalized);
        root.put("hainan", hainan ? Map.of(
                "enabled", true,
                "maxStayDays", 30,
                "purposes", List.of("tourism", "business", "family", "visit", "medical", "exhibition", "sports"),
                "restrictedToHainan", true,
                "requiresOrdinaryPassport", true,
                "officialSource", "https://en.nia.gov.cn/n147418/n147463/c180637/content.html"
        ) : Map.of("enabled", false));
        root.put("transit240", transit240 ? Map.of(
                "enabled", true,
                "maxStayDays", 10,
                "requiresOrdinaryPassport", true,
                "requiresThirdCountryOrRegion", true,
                "requiresConfirmedOnwardTicket", true,
                "designatedPorts", 65,
                "officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html"
        ) : Map.of("enabled", false));
        root.put("transit24", Map.of(
                "enabled", true,
                "maxStayHours", 24,
                "requiresConfirmedOnwardTicket", true,
                "restrictedAreaOnly", true,
                "officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html"
        ));
        root.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        return root;
    }

    private List<Map<String, Object>> buildPolicies(List<CountryDetail> policies) {
        List<Map<String, Object>> result = new ArrayList<>();
        if (policies == null) return result;
        for (CountryDetail p : policies) {
            if (p == null) continue;
            Map<String, Object> item = new LinkedHashMap<>();
            String type = safe(p.getPolicyType());
            item.put("type", type);
            item.put("stayDays", number(safe(p.getStayDays()), 0));
            item.put("purposes", purposeCodes(p.getPurpose()));
            item.put("requiresOnward", "transit".equals(type));
            item.put("ordinaryPassportOnly", true);
            item.put("sourceRule", safe(p.getRule()));
            result.add(item);
        }
        return result;
    }

    private List<String> purposeCodes(String text) {
        String value = safe(text).toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        if (value.contains("tourism")) result.add("tourism");
        if (value.contains("business")) result.add("business");
        if (value.contains("family")) result.add("family");
        if (value.contains("exchange")) result.add("exchange");
        if (value.contains("transit")) result.add("transit");
        return result;
    }

    private String normalize(String value) {
        if (value == null) return "";
        return value.toLowerCase(Locale.ROOT).replaceAll("[^a-z]", "");
    }

    private int number(Object value, int fallback) {
        try { return Integer.parseInt(safe(value)); } catch (Exception e) { return fallback; }
    }

    private String safe(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
}