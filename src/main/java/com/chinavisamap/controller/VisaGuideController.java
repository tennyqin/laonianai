package com.chinavisamap.controller;

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

    public VisaGuideController() {
        Map<String,Object> temp = null;
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            temp = objectMapper.readValue(
                    new ClassPathResource("visa‑guide.json").getInputStream(),
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
        if (!"zh".equals(lang)) {
            lang = "en";
        }
        model.addAttribute("lang", lang);
        model.addAttribute("vg", vgData);
        return "visa-guide";
    }
}

