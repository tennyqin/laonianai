package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.service.CountryCodeResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.util.*;

@RestController
public class SitemapController {

    private static final String BASE_URL = "https://chinavisamap.com";
    private static final String SITE_UPDATE = "2026-09-16";

    private final Map<String, CountryDetail> unilateralMap;
    private final Map<String, CountryDetail> mutualMap;
    private final Map<String, CountryDetail> transitMap;
    private final Map<String, CountryDetail> hainanMap;
    private final List<Map<String, Object>> articles;
    private final CountryCodeResolver resolver;

    public SitemapController(ObjectMapper objectMapper, CountryCodeResolver resolver) {
        this.resolver = resolver;
        unilateralMap = loadCountryMap(objectMapper, "unilateral.json");
        mutualMap = loadCountryMap(objectMapper, "mutual.json");
        transitMap = loadCountryMap(objectMapper, "transit.json");
        hainanMap = loadCountryMap(objectMapper, "hainan.json");
        articles = loadArticles(objectMapper);
    }

    @GetMapping(value = "/sitemap.xml", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> sitemap() {
        StringBuilder xml = new StringBuilder();
        Set<String> emittedUrls = new LinkedHashSet<>();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        xml.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\">");

        addMultilingualUrl(xml, emittedUrls, "/", "homepage", SITE_UPDATE);

        Set<String> countryCodes = new TreeSet<>();
        addCanonicalCodes(countryCodes, unilateralMap.keySet());
        addCanonicalCodes(countryCodes, mutualMap.keySet());
        addCanonicalCodes(countryCodes, transitMap.keySet());
        addCanonicalCodes(countryCodes, hainanMap.keySet());
        for (String code : countryCodes) {
            addMultilingualUrl(xml, emittedUrls, "/country/" + code, "country", SITE_UPDATE);
        }

        addCountryTypeUrls(xml, emittedUrls, countryCodes, unilateralMap, "unilateral");
        addCountryTypeUrls(xml, emittedUrls, countryCodes, mutualMap, "mutual");
        addCountryTypeUrls(xml, emittedUrls, countryCodes, transitMap, "transit");
        addCountryTypeUrls(xml, emittedUrls, countryCodes, hainanMap, "hainan");

        addMultilingualUrl(xml, emittedUrls, "/articles", "articles", SITE_UPDATE);
        for (Map<String, Object> article : articles) {
            Object id = article.get("id");
            if (id != null) if (id != null && !String.valueOf(id).trim().isEmpty()) addMultilingualUrl(xml, emittedUrls, "/articles/" + id, "article", articleLastMod(article));
        }
        addMultilingualUrl(xml, emittedUrls, "/visa-guide", "visa-guide", SITE_UPDATE);

        xml.append("</urlset>");
        return ResponseEntity.ok()
                .contentType(new MediaType(MediaType.APPLICATION_XML, StandardCharsets.UTF_8))
                .body(xml.toString());
    }

    private void addCanonicalCodes(Set<String> target, Set<String> source) {
        for (String code : source) target.add(resolver.routeCode(resolver.policyKey(code)));
    }

    private void addCountryTypeUrls(StringBuilder xml, Set<String> emittedUrls, Set<String> validCountryCodes, Map<String, CountryDetail> map, String type) {
        if (map == null || map.isEmpty()) return;
        for (String code : map.keySet()) {
            String canonicalCode = resolver.routeCode(resolver.policyKey(code));
            if (validCountryCodes.contains(canonicalCode)) addMultilingualUrl(xml, emittedUrls, "/country/" + canonicalCode + "/" + type, "country-detail", SITE_UPDATE);
        }
    }

    private void addMultilingualUrl(StringBuilder xml, Set<String> emittedUrls, String path, String type, String lastmod) {
        String en = BASE_URL + path + "?lang=en";
        String zh = BASE_URL + path + "?lang=zh";
        addUrl(xml, emittedUrls, en, "en", zh, lastmod);
        addUrl(xml, emittedUrls, zh, "zh", en, lastmod);
    }

    private void addUrl(StringBuilder xml, Set<String> emittedUrls, String loc, String lang, String alternate, String lastmod) {
        if (!emittedUrls.add(loc)) return;
        xml.append("<url>");
        xml.append("<loc>").append(xmlEscape(loc)).append("</loc>");
        if (lastmod != null && !lastmod.trim().isEmpty()) {
            xml.append("<lastmod>").append(xmlEscape(lastmod.trim())).append("</lastmod>");
        }
        xml.append("<xhtml:link rel=\"alternate\" hreflang=\"").append(lang).append("\" href=\"").append(xmlEscape(loc)).append("\"/>");
        xml.append("<xhtml:link rel=\"alternate\" hreflang=\"").append("zh".equals(lang) ? "en" : "zh").append("\" href=\"").append(xmlEscape(alternate)).append("\"/>");
        xml.append("<xhtml:link rel=\"alternate\" hreflang=\"x-default\" href=\"").append(xmlEscape("en".equals(lang) ? loc : alternate)).append("\"/>");
        xml.append("</url>");
    }

    private String articleLastMod(Map<String,Object> article) {
        String modified = String.valueOf(article.getOrDefault("dateModified", "")).trim();
        if (!modified.isEmpty()) return modified.length() >= 10 ? modified.substring(0,10) : modified;
        String published = String.valueOf(article.getOrDefault("publishAt", "")).trim();
        if (!published.isEmpty()) return published.length() >= 10 ? published.substring(0,10) : published;
        return null;
    }

    private Map<String, CountryDetail> loadCountryMap(ObjectMapper objectMapper, String file) {
        try {
            return objectMapper.readValue(new ClassPathResource(file).getInputStream(), new TypeReference<Map<String, CountryDetail>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load required sitemap resource: " + file, e);
        }
    }

    private List<Map<String, Object>> loadArticles(ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("articles.json").getInputStream());
            if (root.isArray()) return objectMapper.convertValue(root, new TypeReference<List<Map<String, Object>>>() {});
            if (root.isObject() && root.has("content")) {
                JsonNode content = root.get("content");
                if (content.isTextual()) return objectMapper.readValue(content.asText(), new TypeReference<List<Map<String, Object>>>() {});
                return objectMapper.convertValue(content, new TypeReference<List<Map<String, Object>>>() {});
            }
            throw new IllegalStateException("Failed to load required sitemap resource: articles.json");
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String xmlEscape(String value) {
        if (value == null) return "";
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
