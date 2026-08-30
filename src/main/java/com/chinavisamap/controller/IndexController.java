package com.chinavisamap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;
import java.util.stream.Collectors;

@Controller
public class IndexController {

    private Map<String, Object> allData;

    public IndexController() {
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            allData = objectMapper.readValue(
                    new ClassPathResource("data.json").getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception ignored) {
        }
    }

    @SuppressWarnings("unchecked")
    @GetMapping("/")
    public String index(
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "en") String lang,
            Model model) {

        model.addAllAttributes(allData);
        model.addAttribute("lang", lang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("searched", false);

        if (keyword == null || keyword.isEmpty()) {
            return "index";
        }

        try {
            String kw = keyword.toLowerCase();
            model.addAttribute("searched", true);

            // ==============================================
            // 1. 搜索：单方面免签（分类保留）
            // ==============================================
            List<Map<String, Object>> continents = (List<Map<String, Object>>) allData.get("continents");
            List<Map<String, Object>> unilateralResult = new ArrayList<>();
            if (continents != null) {
                unilateralResult = continents.stream()
                        .flatMap(c -> ((List<Map<String, Object>>) c.get("countries")).stream())
                        .filter(country -> match(country, kw))
                        .collect(Collectors.toList());
            }
            model.addAttribute("unilateralResult", unilateralResult);


            // ==============================================
            // 2. 搜索：互免签证（分类保留）
            // ==============================================
            List<Map<String, Object>> mutualContinents = (List<Map<String, Object>>) allData.get("mutualContinents");
            List<Map<String, Object>> mutualResult = new ArrayList<>();
            if (mutualContinents != null) {
                mutualResult = mutualContinents.stream()
                        .flatMap(c -> ((List<Map<String, Object>>) c.get("countries")).stream())
                        .filter(country -> match(country, kw))
                        .collect(Collectors.toList());
            }
            model.addAttribute("mutualResult", mutualResult);


            // ==============================================
            // 3. 搜索：过境免签（分类保留）
            // ==============================================
            List<Map<String, Object>> transitContinents = (List<Map<String, Object>>) allData.get("transitContinents");
            List<Map<String, Object>> transitResult = new ArrayList<>();
            if (transitContinents != null) {
                transitResult = transitContinents.stream()
                        .flatMap(c -> ((List<Map<String, Object>>) c.get("countries")).stream())
                        .filter(country -> match(country, kw))
                        .collect(Collectors.toList());
            }
            model.addAttribute("transitResult", transitResult);

        } catch (Exception ignored) {

        }

        return "index";
    }

    // 搜索匹配规则：支持 英文、中文、code
    private boolean match(Map<String, Object> country, String kw) {
        String name = country.getOrDefault("name", "").toString().toLowerCase();
        String nameZh = country.getOrDefault("nameZh", "").toString().toLowerCase();
        String code = country.getOrDefault("code", "").toString().toLowerCase();
        return name.contains(kw) || nameZh.contains(kw) || code.contains(kw);
    }
}