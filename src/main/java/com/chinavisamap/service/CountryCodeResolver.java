package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryCodeResolver {
    private final Map<String, String> aliases = new LinkedHashMap<>();
    private final Map<String, String> canonicalRoutes = new LinkedHashMap<>();

    public CountryCodeResolver() {
        // Keep the existing short aliases used by already-indexed URLs.
        add("sg", "singapore"); add("us", "usa"); add("de", "germany");
        add("al", "albania"); add("pl", "poland"); add("au", "australia");
        add("cz", "czechia"); aliases.put("czech", "czechia");
        add("gb", "uk"); add("my", "malaysia"); add("jp", "japan");

        // Full policy slugs are the canonical route for countries without a
        // legacy short route.
        String[] canonicalPolicies = {
                "andorra","argentina","armenia","australia","austria","azerbaijan","belarus","belgium","bosnia","brazil",
                "brunei","bulgaria","canada","chile","croatia","cyprus","denmark","estonia","finland","france","georgia",
                "greece","hungary","iceland","indonesia","ireland","italy","kazakhstan","korea","kyrgyzstan","kuwait",
                "latvia","liechtenstein","lithuania","luxembourg","maldives","malta","mexico","monaco","montenegro","netherlands",
                "newzealand","northmacedonia","norway","oman","peru","philippines","portugal","qatar","romania","russia",
                "saudi","sanmarino","serbia","slovakia","slovenia","spain","sweden","switzerland","thailand","ukraine",
                "uae","uruguay","uzbekistan","vietnam","bahrain","antigua","barbados","bahamas","dominica","ecuador",
                "fiji","grenada","mauritius","samoa","seychelles","solomon","suriname","tonga","guyana","jamaica","czechia"
        };
        for (String policy : canonicalPolicies) canonicalRoutes.putIfAbsent(policy, policy);

        // ISO-2 aliases are also used by related-country/article metadata.
        // They resolve to the same canonical route instead of creating 404 links.
        add("ad", "andorra"); add("ar", "argentina"); add("am", "armenia");
        add("at", "austria"); add("az", "azerbaijan"); add("by", "belarus");
        add("be", "belgium"); add("ba", "bosnia"); add("br", "brazil");
        add("bn", "brunei"); add("bg", "bulgaria"); add("ca", "canada");
        add("cl", "chile"); add("hr", "croatia"); add("cy", "cyprus");
        add("dk", "denmark"); add("ee", "estonia"); add("fi", "finland");
        add("fr", "france"); add("ge", "georgia"); add("gr", "greece");
        add("hu", "hungary"); add("is", "iceland"); add("id", "indonesia");
        add("ie", "ireland"); add("it", "italy"); add("kz", "kazakhstan");
        add("kr", "korea"); add("kg", "kyrgyzstan"); add("kw", "kuwait");
        add("lv", "latvia"); add("li", "liechtenstein"); add("lt", "lithuania");
        add("lu", "luxembourg"); add("mv", "maldives"); add("mt", "malta");
        add("mx", "mexico"); add("mc", "monaco"); add("me", "montenegro");
        add("nl", "netherlands"); add("nz", "newzealand"); aliases.put("new-zealand", "newzealand"); add("mk", "northmacedonia");
        add("no", "norway"); add("om", "oman"); add("pe", "peru");
        add("ph", "philippines"); add("pt", "portugal"); add("qa", "qatar");
        add("ro", "romania"); add("ru", "russia"); add("sa", "saudi");
        add("sm", "sanmarino"); add("rs", "serbia"); add("sk", "slovakia");
        add("si", "slovenia"); add("es", "spain"); add("se", "sweden");
        add("ch", "switzerland"); add("th", "thailand"); add("ua", "ukraine");
        add("ae", "uae"); add("uy", "uruguay"); add("uz", "uzbekistan");
        add("vn", "vietnam"); add("bh", "bahrain"); add("ag", "antigua");
        add("bb", "barbados"); add("bs", "bahamas"); add("dm", "dominica");
        add("ec", "ecuador"); add("fj", "fiji"); add("gd", "grenada");
        add("mu", "mauritius"); add("ws", "samoa"); add("sc", "seychelles");
        add("sb", "solomon"); add("sr", "suriname"); add("to", "tonga");
        add("gy", "guyana"); add("jm", "jamaica");
    }

    private void add(String route, String policyKey) {
        aliases.put(route, policyKey);
        aliases.put(policyKey, policyKey);
        canonicalRoutes.putIfAbsent(policyKey, route);
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