package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class CountryEligibilityService {

    private static final Set<String> HAINAN_COUNTRIES;
    private static final Set<String> TRANSIT_240_COUNTRIES;

    static {
        Set<String> hainan = new HashSet<String>();
        String[] hainanCountries = {
                "albania", "argentina", "australia", "austria", "belarus", "belgium", "bosnia", "brazil",
                "brunei", "bulgaria", "canada", "chile", "croatia", "cyprus", "czechia", "denmark", "estonia",
                "finland", "france", "germany", "greece", "hungary", "iceland", "indonesia", "ireland", "italy",
                "japan", "kazakhstan", "kyrgyzstan", "latvia", "lithuania", "luxembourg", "malaysia", "malta",
                "mexico", "monaco", "montenegro", "netherlands", "newzealand", "northmacedonia", "norway",
                "philippines", "poland", "portugal", "qatar", "korea", "romania", "russia", "serbia", "singapore",
                "slovakia", "slovenia", "spain", "sweden", "switzerland", "thailand", "ukraine", "uae", "uk",
                "usa", "vietnam"
        };
        Collections.addAll(hainan, hainanCountries);
        HAINAN_COUNTRIES = Collections.unmodifiableSet(hainan);

        Set<String> transit240 = new HashSet<String>();
        String[] transitCountries = {
                "albania", "austria", "belarus", "belgium", "bosnia", "bulgaria", "croatia", "cyprus", "czechia",
                "denmark", "estonia", "finland", "france", "germany", "greece", "hungary", "iceland", "ireland",
                "italy", "latvia", "lithuania", "luxembourg", "malta", "monaco", "montenegro", "netherlands",
                "northmacedonia", "norway", "poland", "portugal", "romania", "russia", "serbia", "slovakia",
                "slovenia", "spain", "sweden", "switzerland", "ukraine", "uk", "canada", "usa", "argentina", "brazil",
                "chile", "mexico", "australia", "newzealand", "brunei", "indonesia", "japan", "kyrgyzstan", "qatar",
                "singapore", "korea", "uae", "vietnam"
        };
        Collections.addAll(transit240, transitCountries);
        TRANSIT_240_COUNTRIES = Collections.unmodifiableSet(transit240);
    }

    private final CountryCodeResolver resolver;

    public CountryEligibilityService(CountryCodeResolver resolver) {
        this.resolver = resolver;
    }

    public Map<String, Object> build(String countryCode, List<CountryDetail> policies, Map<String, Object> extra) {
        String normalized = resolver.policyKey(countryCode);
        List<Map<String, Object>> rules = buildPolicies(policies);

        Map<String, Object> root = new LinkedHashMap<String, Object>();
        root.put("version", "2026-08-20");
        root.put("policies", rules);

        Map<String, Object> hainan = new LinkedHashMap<String, Object>();
        boolean hainanEnabled = HAINAN_COUNTRIES.contains(normalized);
        hainan.put("enabled", hainanEnabled);
        hainan.put("maxStayDays", hainanEnabled ? 30 : null);
        hainan.put("purposes", hainanEnabled ? hainanPurposes() : Collections.emptyList());
        hainan.put("restrictedToHainan", hainanEnabled);
        hainan.put("requiresOrdinaryPassport", hainanEnabled);
        hainan.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c180637/content.html");
        root.put("hainan", hainan);

        Map<String, Object> transit240 = new LinkedHashMap<String, Object>();
        boolean transit240Enabled = TRANSIT_240_COUNTRIES.contains(normalized);
        transit240.put("enabled", transit240Enabled);
        transit240.put("maxStayDays", transit240Enabled ? 10 : null);
        transit240.put("requiresOrdinaryPassport", transit240Enabled);
        transit240.put("requiresThirdCountryOrRegion", transit240Enabled);
        transit240.put("requiresConfirmedOnwardTicket", transit240Enabled);
        transit240.put("designatedPorts", transit240Enabled ? 65 : null);
        transit240.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html");
        root.put("transit240", transit240);

        Map<String, Object> transit24 = new LinkedHashMap<String, Object>();
        transit24.put("enabled", true);
        transit24.put("maxStayHours", 24);
        transit24.put("requiresValidInternationalTravelDocument", true);
        transit24.put("requiresConfirmedOnwardTicket", true);
        transit24.put("thirdCountryOrRegionRequired", true);
        transit24.put("restrictedAreaOnly", true);
        transit24.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html");
        root.put("transit24", transit24);

        root.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        return root;
    }

    private List<String> hainanPurposes() {
        List<String> purposes = new ArrayList<String>();
        purposes.add("tourism");
        purposes.add("business");
        purposes.add("visit");
        purposes.add("family");
        purposes.add("medical");
        purposes.add("exhibition");
        purposes.add("sports");
        return Collections.unmodifiableList(purposes);
    }

    private List<Map<String, Object>> buildPolicies(List<CountryDetail> policies) {
        List<Map<String, Object>> result = new ArrayList<Map<String, Object>>();
        if (policies == null) {
            return result;
        }
        for (CountryDetail policy : policies) {
            if (policy == null) {
                continue;
            }
            Map<String, Object> item = new LinkedHashMap<String, Object>();
            String type = safe(policy.getPolicyType());
            item.put("type", type);
            item.put("stayDays", parseSimpleDays(policy.getStayDays()));
            item.put("stayText", safe(policy.getStayDays()));
            item.put("purposes", purposeCodes(policy.getPurpose()));
            item.put("requiresOnward", "transit".equals(type));
            item.put("ordinaryPassportOnly", true);
            item.put("policyUrl", "/country/" + resolver.routeCode(policy.getCode()) + "/" + type);
            item.put("sourceRule", safe(policy.getRule()));
            result.add(item);
        }
        return result;
    }

    private List<String> purposeCodes(String text) {
        String value = safe(text).toLowerCase(Locale.ENGLISH);
        List<String> result = new ArrayList<String>();
        if (value.indexOf("tourism") >= 0 || value.indexOf("tourist") >= 0) {
            result.add("tourism");
        }
        if (value.indexOf("business") >= 0 || value.indexOf("commercial") >= 0) {
            result.add("business");
        }
        if (value.indexOf("family") >= 0 || value.indexOf("relative") >= 0 || value.indexOf("friend") >= 0) {
            result.add("family");
        }
        if (value.indexOf("exchange") >= 0) {
            result.add("exchange");
        }
        if (value.indexOf("transit") >= 0) {
            result.add("transit");
        }
        if (value.indexOf("visit") >= 0 && !result.contains("family")) {
            result.add("visit");
        }
        return result;
    }

    private Integer parseSimpleDays(String value) {
        String text = safe(value);
        if (text.length() == 0 || !text.matches("\\d+")) {
            return null;
        }
        try {
            int days = Integer.parseInt(text);
            return days > 0 ? days : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private int number(Object value, int fallback) {
        try {
            return Integer.parseInt(safe(value));
        } catch (Exception ex) {
            return fallback;
        }
    }

    private String safe(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
