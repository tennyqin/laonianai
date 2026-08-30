package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

@Controller
public class CountryController {

    private Map<String, CountryDetail> unilateralMap;
    private Map<String, CountryDetail> mutualMap;
    private Map<String, CountryDetail> transitMap;

    public CountryController(ObjectMapper objectMapper) {
        try {
            // 加载三类政策，完全独立互不影响
            unilateralMap = objectMapper.readValue(
                    new ClassPathResource("unilateral.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
            mutualMap = objectMapper.readValue(
                    new ClassPathResource("mutual.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
            transitMap = objectMapper.readValue(
                    new ClassPathResource("transit.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
        } catch (Exception ignored) {
        }
    }

    @GetMapping("/country/{code}")
    public String countryDetail(
            @PathVariable String code,
            @RequestParam(defaultValue = "en") String lang,
            @RequestParam(defaultValue = "unilateral") String type,
            Model model) {

        CountryDetail detail = null;
        if ("unilateral".equals(type)) {
            detail = unilateralMap.get(code);
        } else if ("mutual".equals(type)) {
            detail = mutualMap.get(code);
        } else if ("transit".equals(type)) {
            detail = transitMap.get(code);
        }

        if (detail == null) return "redirect:/";
        model.addAttribute("detail", detail);
        model.addAttribute("lang", lang);
        model.addAttribute("type", type);
        return "country-detail";
    }
}