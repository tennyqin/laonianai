package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryEligibilityService {

    private static final Set<String> HAINAN_COUNTRIES;
    private static final Set<String> TRANSIT_240_COUNTRIES;
    private static final Set<String> CURRENT_UNILATERAL_30;

    static {
        Set<String> set1 = new HashSet<>();
        set1.add("albania");
        set1.add("argentina");
        set1.add("australia");
        set1.add("austria");
        set1.add("belarus");
        set1.add("belgium");
        set1.add("bosnia");
        set1.add("brazil");
        set1.add("brunei");
        set1.add("bulgaria");
        set1.add("canada");
        set1.add("chile");
        set1.add("croatia");
        set1.add("cyprus");
        set1.add("czechia");
        set1.add("denmark");
        set1.add("estonia");
        set1.add("finland");
        set1.add("france");
        set1.add("germany");
        set1.add("greece");
        set1.add("hungary");
        set1.add("iceland");
        set1.add("indonesia");
        set1.add("ireland");
        set1.add("italy");
        set1.add("japan");
        set1.add("kazakhstan");
        set1.add("kyrgyzstan");
        set1.add("latvia");
        set1.add("lithuania");
        set1.add("luxembourg");
        set1.add("malaysia");
        set1.add("malta");
        set1.add("mexico");
        set1.add("monaco");
        set1.add("montenegro");
        set1.add("netherlands");
        set1.add("newzealand");
        set1.add("northmacedonia");
        set1.add("norway");
        set1.add("philippines");
        set1.add("poland");
        set1.add("portugal");
        set1.add("qatar");
        set1.add("korea");
        set1.add("romania");
        set1.add("russia");
        set1.add("serbia");
        set1.add("singapore");
        set1.add("slovakia");
        set1.add("slovenia");
        set1.add("spain");
        set1.add("sweden");
        set1.add("switzerland");
        set1.add("thailand");
        set1.add("ukraine");
        set1.add("uae");
        set1.add("uk");
        set1.add("usa");
        set1.add("vietnam");
        HAINAN_COUNTRIES = Collections.unmodifiableSet(set1);

        Set<String> set2 = new HashSet<>();
        set2.add("albania");
        set2.add("austria");
        set2.add("belarus");
        set2.add("belgium");
        set2.add("bosnia");
        set2.add("bulgaria");
        set2.add("croatia");
        set2.add("cyprus");
        set2.add("czechia");
        set2.add("denmark");
        set2.add("estonia");
        set2.add("finland");
        set2.add("france");
        set2.add("germany");
        set2.add("greece");
        set2.add("hungary");
        set2.add("iceland");
        set2.add("ireland");
        set2.add("italy");
        set2.add("latvia");
        set2.add("lithuania");
        set2.add("luxembourg");
        set2.add("malta");
        set2.add("monaco");
        set2.add("montenegro");
        set2.add("netherlands");
        set2.add("northmacedonia");
        set2.add("norway");
        set2.add("poland");
        set2.add("portugal");
        set2.add("romania");
        set2.add("russia");
        set2.add("serbia");
        set2.add("slovakia");
        set2.add("slovenia");
        set2.add("spain");
        set2.add("sweden");
        set2.add("switzerland");
        set2.add("ukraine");
        set2.add("uk");
        set2.add("canada");
        set2.add("usa");
        set2.add("argentina");
        set2.add("brazil");
        set2.add("chile");
        set2.add("mexico");
        set2.add("australia");
        set2.add("newzealand");
        set2.add("brunei");
        set2.add("indonesia");
        set2.add("japan");
        set2.add("kyrgyzstan");
        set2.add("qatar");
        set2.add("singapore");
        set2.add("korea");
        set2.add("uae");
        set2.add("vietnam");
        TRANSIT_240_COUNTRIES = Collections.unmodifiableSet(set2);

        Set<String> set3 = new HashSet<>();
        set3.add("kyrgyzstan");
        set3.add("vietnam");
        CURRENT_UNILATERAL_30 = Collections.unmodifiableSet(set3);
    }

    private final CountryCodeResolver resolver;

    public CountryEligibilityService(CountryCodeResolver resolver) {
        this.resolver = resolver;
    }

    public Map<String, Object> build(String countryCode, List<CountryDetail> policies, Map<String, Object> extra) {
        String normalized = resolver.policyKey(countryCode);
        List<Map<String, Object>> rules = buildPolicies(policies);

        if (CURRENT_UNILATERAL_30.contains(normalized) && rules.stream().noneMatch(p -> "unilateral".equals(p.get("type")))) {
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("type", "unilateral");
            p.put("stayDays", 30);
            List<String> purposes = new ArrayList<>();
            purposes.add("tourism");
            purposes.add("business");
            purposes.add("family");
            purposes.add("exchange");
            purposes.add("transit");
            p.put("purposes", Collections.unmodifiableList(purposes));
            p.put("requiresOnward", false);
            p.put("ordinaryPassportOnly", true);
            p.put("sourceRule", "Ordinary passport holders may enter visa‑free for business, tourism, visits to relatives and friends, exchange visits or transit, for up to 30 days.");
            rules.add(p);
        }

        Map<String, Object> root = new LinkedHashMap<>();
        root.put("version", "2026‑08‑20");
        List<String> passportTypes = new ArrayList<>();
        passportTypes.add("ordinary");
        root.put("passportTypes", Collections.unmodifiableList(passportTypes));
        root.put("ordinaryPassportOnly", true);
        root.put("policies", rules);

        if (HAINAN_COUNTRIES.contains(normalized)) {
            Map<String, Object> hainanMap = new LinkedHashMap<>();
            hainanMap.put("enabled", true);
            hainanMap.put("maxStayDays", 30);
            List<String> purpList = new ArrayList<>();
            purpList.add("tourism");
            purpList.add("business");
            purpList.add("family");
            purpList.add("visit");
            purpList.add("medical");
            purpList.add("exhibition");
            purpList.add("sports");
            hainanMap.put("purposes", Collections.unmodifiableList(purpList));
            hainanMap.put("restrictedToHainan", true);
            hainanMap.put("requiresOrdinaryPassport", true);
            hainanMap.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c180637/content.html");
            root.put("hainan", hainanMap);
        } else {
            Map<String, Object> hainanMap = new LinkedHashMap<>();
            hainanMap.put("enabled", false);
            root.put("hainan", hainanMap);
        }

        if (TRANSIT_240_COUNTRIES.contains(normalized)) {
            Map<String, Object> t240 = new LinkedHashMap<>();
            t240.put("enabled", true);
            t240.put("maxStayDays", 10);
            t240.put("requiresOrdinaryPassport", true);
            t240.put("requiresThirdCountryOrRegion", true);
            t240.put("requiresConfirmedOnwardTicket", true);
            t240.put("designatedPorts", 65);
            t240.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html");
            root.put("transit240", t240);
        } else {
            Map<String, Object> t240 = new LinkedHashMap<>();
            t240.put("enabled", false);
            root.put("transit240", t240);
        }

        Map<String, Object> transit24 = new LinkedHashMap<>();
        transit24.put("enabled", true);
        transit24.put("maxStayHours", 24);
        transit24.put("requiresConfirmedOnwardTicket", true);
        transit24.put("restrictedAreaOnly", true);
        transit24.put("officialSource", "https://en.nia.gov.cn/n147418/n147463/c183412/content.html");
        root.put("transit24", transit24);

        root.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        return root;
    }

    private List<Map<String, Object>> buildPolicies(List<CountryDetail> policies) {
        List<Map<String, Object>> r = new ArrayList<>();
        if (policies == null) {
            return r;
        }
        for (CountryDetail p : policies) {
            if (p == null) {
                continue;
            }
            Map<String, Object> x = new LinkedHashMap<>();
            String type = safe(p.getPolicyType());
            x.put("type", type);
            x.put("stayDays", number(p.getStayDays(), 0));
            x.put("purposes", purposeCodes(p.getPurpose()));
            x.put("requiresOnward", "transit".equals(type));
            x.put("ordinaryPassportOnly", true);
            x.put("sourceRule", safe(p.getRule()));
            r.add(x);
        }
        return r;
    }

    private List<String> purposeCodes(String text) {
        String v = safe(text).toLowerCase(Locale.ROOT);
        List<String> r = new ArrayList<>();
        if (v.contains("tourism")) {
            r.add("tourism");
        }
        if (v.contains("business")) {
            r.add("business");
        }
        if (v.contains("family")) {
            r.add("family");
        }
        if (v.contains("exchange")) {
            r.add("exchange");
        }
        if (v.contains("transit")) {
            r.add("transit");
        }
        if (v.contains("visit")) {
            r.add("visit");
        }
        return r;
    }

    private int number(Object v, int f) {
        try {
            return Integer.parseInt(safe(v));
        } catch (Exception e) {
            return f;
        }
    }

    private String safe(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }
}