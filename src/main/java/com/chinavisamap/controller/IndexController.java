package com.chinavisamap.controller;

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

    public IndexController(SeoService seoService, StructuredDataService structuredDataService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
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
        try {
            unilateralResult = searchAndSort((List<Map<String, Object>>) allData.get("continents"), kw, currentLang);
            mutualResult = searchAndSort((List<Map<String, Object>>) allData.get("mutualContinents"), kw, currentLang);
            transitResult = searchAndSort((List<Map<String, Object>>) allData.get("transitContinents"), kw, currentLang);
        } catch (Exception ignored) {
            // Keep empty results as the safe fallback.
        }

        model.addAttribute("unilateralResult", unilateralResult);
        model.addAttribute("mutualResult", mutualResult);
        model.addAttribute("transitResult", transitResult);
        model.addAttribute("searchResultCount", unilateralResult.size() + mutualResult.size() + transitResult.size());
        return "index";
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> searchAndSort(List<Map<String, Object>> continents, String kw, String lang) {
        if (continents == null) {
            return Collections.emptyList();
        }
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

        // Exact country code is the strongest intent signal.
        if (code.equals(kw)) s += 1000;
        // Exact localized name should beat partial matches.
        if ("en".equals(lang) && n.equals(kw)) s += 900;
        if ("zh".equals(lang) && z.equals(kw)) s += 900;
        // Exact name in the other language still deserves a strong score.
        if (n.equals(kw)) s += 700;
        if (z.equals(kw)) s += 700;
        // Prefix matches normally indicate a country-name search.
        if (n.startsWith(kw)) s += 400;
        if (z.startsWith(kw)) s += 400;
        // Code prefix is useful but weaker than a real country name.
        if (code.startsWith(kw)) s += 250;
        // Contains is the broad fallback.
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
