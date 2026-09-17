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

import java.util.Map;

@Controller
public class VisaGuideController {

    private final Map<String, Object> vgData;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;


    public VisaGuideController(SeoService seoService, StructuredDataService structuredDataService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        Map<String,Object> temp = new java.util.HashMap<>();
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            temp = objectMapper.readValue(
                    new ClassPathResource("visa-guide.json").getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception ignored) {
            // 加载失败返回空map，模板做容错
        }
        this.vgData = temp;
    }

    @GetMapping("/visa-guide")
    public String visaGuide(
            @RequestParam(value = "lang", required = false, defaultValue = "en") String lang,
            Model model
    ) {
        lang = seoService.normalizeLang(lang);
        String path = "/visa-guide";

        model.addAttribute(
                "canonicalUrl",
                seoService.canonical(path, lang)
        );

        model.addAttribute(
                "hreflang",
                seoService.hreflang(path)
        );

        @SuppressWarnings("unchecked")
        Map<String, Object> meta = vgData.get("meta") instanceof Map
                ? (Map<String, Object>) vgData.get("meta")
                : java.util.Collections.emptyMap();
        String title = "zh".equals(lang)
                ? String.valueOf(meta.getOrDefault("pageTitleZh", "中国签证指南 2026"))
                : String.valueOf(meta.getOrDefault("pageTitleEn", "China Visa Guide 2026"));
        String description = "zh".equals(lang)
                ? String.valueOf(meta.getOrDefault("pageDescZh", "中国签证、免签、过境免签及入境政策完整指南。"))
                : String.valueOf(meta.getOrDefault("pageDescEn", "Complete guide to China visas, visa-free entry, transit visa-free policies and entry requirements."));

        model.addAttribute(
                "structuredData",
                structuredDataService.buildVisaGuide(
                        lang,
                        title,
                        description,
                        seoService.canonical(path, lang)
                )
        );

        model.addAttribute("lang", lang);
        model.addAttribute("vg", vgData);
        return "visa-guide";
    }
}

