package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Controller
public class CountryController {

    // 原始三份政策json
    private Map<String, CountryDetail> unilateralMap;
    private Map<String, CountryDetail> mutualMap;
    private Map<String, CountryDetail> transitMap;

    // 新增：国家个性化扩展配置 country‑extra.json
    private Map<String, Map<String, Object>> countryExtraMap;

    public CountryController(ObjectMapper objectMapper) {
        // 加载原始三份政策
        try {
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

        // 加载新增 country‑extra.json
        try {
            countryExtraMap = objectMapper.readValue(

                    new ClassPathResource("country‑extra.json").getInputStream(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
        } catch (Exception ignored) {
            countryExtraMap = null;
        }
    }


    /**
     * 旧接口：兼容历史访问地址 /country/{code}?type=xxx&lang=xxx
     * 不传type 则进入【国家聚合总页】
     */
    @GetMapping("/country/{code}")
    public String legacyCountryHandler(
            @PathVariable String code,
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "zh") String lang,
            Model model
    ) {
        // type参数存在，走详情页面（旧query‑param地址）
        if (StringUtils.isNotBlank(type)) {
            CountryDetail detail = getCountryDetail(code, type);
            if (detail == null) {
                return "redirect:/";
            }
            // canonical 指向新REST路径
            String canonicalUrl = "/country/" + code + "/" + type + "?lang=" + lang;
            Object countryExtra = getCountryExtraItem(code, type);

            model.addAttribute("detail", detail);
            model.addAttribute("code", code);
            model.addAttribute("type", type);
            model.addAttribute("lang", lang);
            model.addAttribute("canonicalUrl", canonicalUrl);
            model.addAttribute("countryExtra", countryExtra);
            return "country-detail";
        } else {
            // type为空，进入【国家聚合总页 country‑home】
            List<String> availableTypes = detectAvailableTypes(code);
            if (availableTypes.isEmpty()) {
                return "redirect:/";
            }
            // 取任意一份存在的detail，用于聚合页展示国家基础信息
            CountryDetail detailCountry = getAnyCountryDetail(code);
            Map<String, Object> countryExtraRoot = (countryExtraMap != null) ? countryExtraMap.get(code) : null;

            model.addAttribute("code", code);
            model.addAttribute("lang", lang);
            model.addAttribute("detailCountry", detailCountry);
            model.addAttribute("availableTypes", availableTypes);
            model.addAttribute("countryExtraRoot", countryExtraRoot);
            return "country‑home";
        }
    }


    /**
     * 新REST详情路径 /country/{code}/{type}?lang=xxx
     */
    @GetMapping("/country/{code}/{type}")
    public String restCountryDetail(
            @PathVariable String code,
            @PathVariable String type,
            @RequestParam(defaultValue = "zh") String lang,
            Model model
    ) {
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) {
            return "redirect:/";
        }
        String canonicalUrl = "/country/" + code + "/" + type + "?lang=" + lang;
        Object countryExtra = getCountryExtraItem(code, type);

        // 空Map置null，避免模板层 countryExtra != null 通过后取key得到null
        if (countryExtra instanceof Map) {
            Map<?, ?> extraMap = (Map<?, ?>) countryExtra;
            if (extraMap.isEmpty()) {
                countryExtra = null;
            }
        }

        model.addAttribute("detail", detail);
        model.addAttribute("code", code);
        model.addAttribute("type", type);
        model.addAttribute("lang", lang);
        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("countryExtra", countryExtra);
        return "country-detail";
    }


    // =====================内部工具方法=====================

    /** 根据code+type获取政策详情 */
    private CountryDetail getCountryDetail(String code, String type) {
        if ("unilateral".equals(type)) {
            return unilateralMap != null ? unilateralMap.get(code) : null;
        } else if ("mutual".equals(type)) {
            return mutualMap != null ? mutualMap.get(code) : null;
        } else if ("transit".equals(type)) {
            return transitMap != null ? transitMap.get(code) : null;
        }
        return null;
    }

    /** 获取该code+type对应的extra扩展对象 */
    private Object getCountryExtraItem(String code, String type) {
        if (countryExtraMap == null) return null;
        Map<String, Object> countryNode = countryExtraMap.get(code);
        if (countryNode == null) return null;
        return countryNode.get(type);
    }

    /**
     * 检测一个国家拥有哪些可用政策type
     * 遍历三份map，收集存在的type，返回集合给聚合页
     */
    private List<String> detectAvailableTypes(String code) {
        List<String> list = new ArrayList<>();
        if (unilateralMap != null && unilateralMap.containsKey(code)) {
            list.add("unilateral");
        }
        if (mutualMap != null && mutualMap.containsKey(code)) {
            list.add("mutual");
        }
        if (transitMap != null && transitMap.containsKey(code)) {
            list.add("transit");
        }
        return list;
    }

    /**
     * 获取该国家任意一份存在的CountryDetail，用于聚合页展示国家基础信息
     */
    private CountryDetail getAnyCountryDetail(String code) {
        if (unilateralMap != null && unilateralMap.containsKey(code)) {
            return unilateralMap.get(code);
        }
        if (mutualMap != null && mutualMap.containsKey(code)) {
            return mutualMap.get(code);
        }
        if (transitMap != null && transitMap.containsKey(code)) {
            return transitMap.get(code);
        }
        return null;
    }
}
