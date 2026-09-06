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
            // 建议：生产环境不要直接 ignored，至少打印日志 log.error("Failed to load data.json", ignored);
        }
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/")
    public String index(@RequestParam(required = false) String keyword,
                        @RequestParam(defaultValue = "en") String lang,
                        Model model) {

        // 1. 处理 lang，使其成为 effectively final，解决 Lambda 报错的根本原因
        final String currentLang = seoService.normalizeLang(lang);

        // 2. 注入基础 Model 属性
        model.addAllAttributes(allData);
        model.addAttribute("lang", currentLang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searched", false);
        model.addAttribute("noIndex", keyword != null && !keyword.trim().isEmpty());
        model.addAttribute("canonicalUrl", seoService.canonical("/", currentLang));
        model.addAttribute("hreflang", seoService.hreflang("/"));

        // 3. 处理 SEO 标题和描述
        String siteName = String.valueOf(allData.getOrDefault("siteName", "China Visa Free Guide 2026"));
        String title = "zh".equals(currentLang)
                ? String.valueOf(allData.getOrDefault("siteTitleZh", "2026中国免签政策指南"))
                : String.valueOf(allData.getOrDefault("siteTitle", "China Visa-Free Policy 2026"));
        String description = "zh".equals(currentLang)
                ? String.valueOf(allData.getOrDefault("siteDescZh", ""))
                : String.valueOf(allData.getOrDefault("siteDesc", ""));

        model.addAttribute("structuredData", structuredDataService.buildHome(currentLang, siteName, title, description));

        // 4. 如果没有关键词，直接返回
        if (keyword == null || keyword.trim().isEmpty()) {
            return "index";
        }

        // 5. 准备搜索变量（final）
        final String kw = keyword.trim().toLowerCase(Locale.ROOT);
        model.addAttribute("searched", true);

        // 6. 初始化默认空结果
        List<Map<String, Object>> unilateralResult = Collections.emptyList();
        List<Map<String, Object>> mutualResult = Collections.emptyList();
        List<Map<String, Object>> transitResult = Collections.emptyList();

        try {
            unilateralResult = searchAndSort((List<Map<String, Object>>) allData.get("continents"), kw, currentLang);
            mutualResult = searchAndSort((List<Map<String, Object>>) allData.get("mutualContinents"), kw, currentLang);
            transitResult = searchAndSort((List<Map<String, Object>>) allData.get("transitContinents"), kw, currentLang);
        } catch (Exception ignored) {
            // 搜索异常兜底，结果保持为空列表
        }

        model.addAttribute("unilateralResult", unilateralResult);
        model.addAttribute("mutualResult", mutualResult);
        model.addAttribute("transitResult", transitResult);
        model.addAttribute("searchResultCount", unilateralResult.size() + mutualResult.size() + transitResult.size());

        return "index";
    }

    /**
     * 抽取公共的搜索与排序逻辑，避免代码重复
     */
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
                .sorted((a, b) -> Integer.compare(score(b, kw, lang), score(a, kw, lang)))
                .collect(Collectors.toList());
    }

    private boolean match(Map<String, Object> c, String kw) {
        String n = String.valueOf(c.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
        String z = String.valueOf(c.getOrDefault("nameZh", "")).toLowerCase(Locale.ROOT);
        String code = String.valueOf(c.getOrDefault("code", "")).toLowerCase(Locale.ROOT);
        return n.contains(kw) || z.contains(kw) || code.contains(kw);
    }

    private int score(Map<String, Object> c, String kw, String lang) {
        String n = String.valueOf(c.getOrDefault("name", "")).toLowerCase(Locale.ROOT);
        String z = String.valueOf(c.getOrDefault("nameZh", "")).toLowerCase(Locale.ROOT);
        String code = String.valueOf(c.getOrDefault("code", "")).toLowerCase(Locale.ROOT);

        int s = 0;
        if (code.equals(kw)) s += 100;
        if ("en".equals(lang) && n.equals(kw)) s += 90;
        if ("zh".equals(lang) && z.equals(kw)) s += 90;
        if (n.startsWith(kw) || z.startsWith(kw)) s += 40;

        return s;
    }
}