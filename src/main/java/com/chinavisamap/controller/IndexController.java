package com.chinavisamap.controller;

import com.chinavisamap.service.CountryFlagService;
import com.chinavisamap.service.SeoService;
import com.chinavisamap.service.StructuredDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Controller
public class IndexController {

    private final Map<String, Object> allData = new HashMap<>();
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;
    private final CountryFlagService countryFlagService;

    public IndexController(SeoService seoService, StructuredDataService structuredDataService,
                           CountryFlagService countryFlagService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        this.countryFlagService = countryFlagService;
        try {
            allData.putAll(new ObjectMapper().readValue(
                    new ClassPathResource("data.json").getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            ));
        } catch (Exception ignored) {
            // Keep the application available even if the static data cannot be loaded.
        }
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/")
    public String index(@RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "en") String lang,
                        Model model) {
        final String currentLang = seoService.normalizeLang(lang);

        model.addAllAttributes(allData);
        model.addAttribute("lang", currentLang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searched", false);
        model.addAttribute("noIndex", keyword != null && !keyword.trim().isEmpty());
        model.addAttribute("canonicalUrl", seoService.canonical("/", currentLang));
        model.addAttribute("hreflang", seoService.hreflang("/"));
        model.addAttribute("countryFlags", buildCountryFlags());

        String siteName = String.valueOf(allData.getOrDefault("siteName", "China Visa Free Guide 2026"));
        String title = "zh".equals(currentLang)
                ? String.valueOf(allData.getOrDefault("siteTitleZh", "2026中国免签政策指南"))
                : String.valueOf(allData.getOrDefault("siteTitle", "China Visa-Free Policy 2026"));
        String description = "zh".equals(currentLang)
                ? String.valueOf(allData.getOrDefault("siteDescZh", ""))
                : String.valueOf(allData.getOrDefault("siteDesc", ""));
        model.addAttribute("structuredData", structuredDataService.buildHome(currentLang, siteName, title, description));

        if (keyword == null || keyword.trim().isEmpty()) {
            return "index";
        }

        final String kw = normalize(keyword);
        model.addAttribute("searched", true);

        List<Map<String, Object>> unilateralResult = Collections.emptyList();
        List<Map<String, Object>> mutualResult = Collections.emptyList();
        List<Map<String, Object>> transitResult = Collections.emptyList();
        List<Map<String, Object>> hainanResult = Collections.emptyList();
        try {
            unilateralResult = searchAndSort((List<Map<String, Object>>) allData.get("continents"), kw, currentLang);
            mutualResult = searchAndSort((List<Map<String, Object>>) allData.get("mutualContinents"), kw, currentLang);
            transitResult = searchAndSort((List<Map<String, Object>>) allData.get("transitContinents"), kw, currentLang);
            hainanResult = searchAndSort((List<Map<String, Object>>) allData.get("hainanContinents"), kw, currentLang);
        } catch (Exception ignored) {
            // Keep empty results as the safe fallback.
        }

        model.addAttribute("unilateralResult", unilateralResult);
        model.addAttribute("mutualResult", mutualResult);
        model.addAttribute("transitResult", transitResult);
        model.addAttribute("hainanResult", hainanResult);

        // Present search as a country decision hub rather than four duplicate policy lists.
        // This keeps the journey: search -> country overview -> checker -> exact policy.
        List<Map<String, Object>> searchCountryResults = mergeSearchCountries(
                unilateralResult, mutualResult, transitResult, hainanResult);
        model.addAttribute("searchCountryResults", searchCountryResults);
        model.addAttribute("searchResultCount", searchCountryResults.size());
        model.addAttribute("searchHasResults", !searchCountryResults.isEmpty());
        return "index";
    }


    private List<Map<String, Object>> mergeSearchCountries(List<Map<String, Object>>... groups) {
        Map<String, Map<String, Object>> merged = new LinkedHashMap<>();
        String[] typeNames = {"unilateral", "mutual", "transit", "hainan"};
        for (int i = 0; i < groups.length; i++) {
            List<Map<String, Object>> group = groups[i];
            if (group == null) continue;
            for (Map<String, Object> country : group) {
                String code = normalize(country.get("code"));
                if (code.isEmpty()) continue;
                Map<String, Object> item = merged.computeIfAbsent(code, k -> {
                    Map<String, Object> copy = new LinkedHashMap<>();
                    copy.put("code", country.get("code"));
                    copy.put("name", country.get("name"));
                    copy.put("nameZh", country.get("nameZh"));
                    copy.put("flag", country.get("flag"));
                    copy.put("routeTypes", new ArrayList<String>());
                    return copy;
                });
                @SuppressWarnings("unchecked")
                List<String> routeTypes = (List<String>) item.get("routeTypes");
                if (!routeTypes.contains(typeNames[i])) routeTypes.add(typeNames[i]);
            }
        }
        return new ArrayList<>(merged.values());
    }

    private Map<String, String> buildCountryFlags() {
        Map<String, String> result = new HashMap<>();
        addFlagsFromContinents(result, allData.get("continents"));
        addFlagsFromContinents(result, allData.get("mutualContinents"));
        addFlagsFromContinents(result, allData.get("transitContinents"));
        addFlagsFromContinents(result, allData.get("hainanContinents"));
        return result;
    }

    @SuppressWarnings("unchecked")
    private void addFlagsFromContinents(Map<String, String> result, Object value) {
        if (!(value instanceof List)) return;
        for (Object continent : (List<?>) value) {
            if (!(continent instanceof Map)) continue;
            Object countries = ((Map<String, Object>) continent).get("countries");
            if (!(countries instanceof List)) continue;
            for (Object item : (List<?>) countries) {
                if (!(item instanceof Map)) continue;
                Map<String, Object> country = (Map<String, Object>) item;
                Object code = country.get("code");
                if (code == null) continue;
                String key = String.valueOf(code);
                String flag = countryFlagService.flag(key);
                result.put(key, flag);
                // Keep a per-country value as a second, template-safe source. This avoids
                // a missing Map lookup ever rendering an empty flag in Thymeleaf.
                country.put("flag", flag);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchAndSort(List<Map<String, Object>> continents, String kw, String lang) {
        if (continents == null) return Collections.emptyList();
        return continents.stream()
                .filter(Objects::nonNull)
                .flatMap(c -> {
                    Object v = c.get("countries");
                    return v instanceof List ? ((List<Map<String, Object>>) v).stream() : Stream.empty();
                })
                .filter(c -> match(c, kw))
                .sorted((a, b) -> {
                    int byScore = Integer.compare(score(b, kw, lang), score(a, kw, lang));
                    if (byScore != 0) return byScore;
                    return displayName(a, lang).compareToIgnoreCase(displayName(b, lang));
                })
                .collect(Collectors.toList());
    }

    private boolean match(Map<String, Object> c, String kw) {
        String n = normalize(c.get("name"));
        String z = normalize(c.get("nameZh"));
        String code = normalize(c.get("code"));
        return n.contains(kw) || z.contains(kw) || code.contains(kw);
    }

    private int score(Map<String, Object> c, String kw, String lang) {
        String n = normalize(c.get("name"));
        String z = normalize(c.get("nameZh"));
        String code = normalize(c.get("code"));
        int s = 0;
        if (code.equals(kw)) s += 1000;
        if ("en".equals(lang) && n.equals(kw)) s += 900;
        if ("zh".equals(lang) && z.equals(kw)) s += 900;
        if (n.equals(kw)) s += 700;
        if (z.equals(kw)) s += 700;
        if (n.startsWith(kw)) s += 400;
        if (z.startsWith(kw)) s += 400;
        if (code.startsWith(kw)) s += 250;
        if (n.contains(kw)) s += 120;
        if (z.contains(kw)) s += 120;
        if (code.contains(kw)) s += 80;
        return s;
    }

    private String displayName(Map<String, Object> c, String lang) {
        return "zh".equals(lang)
                ? String.valueOf(c.getOrDefault("nameZh", c.getOrDefault("name", "")))
                : String.valueOf(c.getOrDefault("name", c.getOrDefault("nameZh", "")));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }
}
