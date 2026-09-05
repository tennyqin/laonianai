package com.chinavisamap.service;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class SeoService {

    private static final String BASE_URL = "https://chinavisamap.com";

    /**
     * 标准化语言，只允许 en / zh
     */
    public String normalizeLang(String lang) {
        return "zh".equalsIgnoreCase(lang) ? "zh" : "en";
    }

    /**
     * 生成当前页面 canonical
     *
     * 例如：
     * /                  + en
     * -> https://chinavisamap.com/?lang=en
     *
     * /articles          + zh
     * -> https://chinavisamap.com/articles?lang=zh
     */
    public String canonical(String path, String lang) {
        return buildUrl(path, normalizeLang(lang));
    }

    /**
     * 生成 hreflang
     */
    public Map<String, String> hreflang(String path) {

        Map<String, String> result = new LinkedHashMap<>();

        result.put("en", buildUrl(path, "en"));
        result.put("zh", buildUrl(path, "zh"));

        // 默认使用英文版本
        result.put("x-default", buildUrl(path, "en"));

        return result;
    }

    /**
     * 构建带 lang 参数的完整 URL
     */
    private String buildUrl(String path, String lang) {

        if (StringUtils.isBlank(path)) {
            path = "/";
        }

        if (!path.startsWith("/")) {
            path = "/" + path;
        }

        return UriComponentsBuilder
                .fromUriString(BASE_URL)
                .path(path)
                .queryParam("lang", lang)
                .build()
                .encode()
                .toUriString();
    }

    public String baseUrl() {
        return BASE_URL;
    }
}