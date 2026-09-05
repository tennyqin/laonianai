package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class StructuredDataService {

    private static final String BASE_URL = "https://chinavisamap.com";

    private final ObjectMapper objectMapper;

    public StructuredDataService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * 首页 JSON-LD
     *
     * WebSite + Organization
     */
    public String buildHome(String lang,
                            String siteName,
                            String title,
                            String description) {

        Map<String, Object> graph = new LinkedHashMap<>();

        List<Map<String, Object>> items = new ArrayList<>();

        // =========================
        // WebSite
        // =========================
        Map<String, Object> website = new LinkedHashMap<>();
        website.put("@type", "WebSite");
        website.put("@id", BASE_URL + "/#website");
        website.put("url", BASE_URL + "/?lang=" + lang);
        website.put("name", siteName);
        website.put("description", description);
        website.put("inLanguage", lang);

        items.add(website);

        // =========================
        // Organization
        // =========================
        Map<String, Object> organization = new LinkedHashMap<>();
        organization.put("@type", "Organization");
        organization.put("@id", BASE_URL + "/#organization");
        organization.put("name", "China Visa Free Guide");
        organization.put("url", BASE_URL + "/?lang=" + lang);

        items.add(organization);

        // =========================
        // WebPage
        // =========================
        Map<String, Object> webpage = new LinkedHashMap<>();
        webpage.put("@type", "WebPage");
        webpage.put("@id", BASE_URL + "/?lang=" + lang + "#webpage");
        webpage.put("url", BASE_URL + "/?lang=" + lang);
        webpage.put("name", title);
        webpage.put("description", description);
        webpage.put("inLanguage", lang);
        webpage.put("isPartOf", Collections.singletonMap(
                "@id", BASE_URL + "/#website"
        ));

        items.add(webpage);

        graph.put("@context", "https://schema.org");
        graph.put("@graph", items);

        return toJson(graph);
    }

    /**
     * 国家政策详情页
     */
    public String buildCountry(CountryDetail detail,
                               String lang,
                               String canonicalUrl) {

        String name = "zh".equals(lang)
                ? detail.getNameZh()
                : detail.getName();

        String description = "zh".equals(lang)
                ? detail.getSeoDescZh()
                : detail.getSeoDesc();

        String title = "zh".equals(lang)
                ? detail.getSeoTitleZh()
                : detail.getSeoTitle();

        if (isBlank(title)) {
            title = name + " China Visa-Free Policy 2026";
        }

        if (isBlank(description)) {
            description = name + " China visa-free policy, stay duration, requirements and entry information.";
        }

        List<Map<String, Object>> graph = new ArrayList<>();

        // =========================
        // WebPage
        // =========================
        Map<String, Object> webpage = new LinkedHashMap<>();

        webpage.put("@type", "WebPage");
        webpage.put("@id", canonicalUrl + "#webpage");
        webpage.put("url", canonicalUrl);
        webpage.put("name", title);
        webpage.put("description", description);
        webpage.put("inLanguage", lang);

        Map<String, Object> about = new LinkedHashMap<>();
        about.put("@type", "Country");
        about.put("name", name);

        webpage.put("about", about);

        graph.add(webpage);

        // =========================
        // BreadcrumbList
        // =========================
        Map<String, Object> breadcrumb = new LinkedHashMap<>();
        breadcrumb.put("@type", "BreadcrumbList");

        List<Map<String, Object>> elements = new ArrayList<>();

        elements.add(breadcrumbItem(
                1,
                "zh".equals(lang) ? "首页" : "Home",
                BASE_URL + "/?lang=" + lang
        ));

        elements.add(breadcrumbItem(
                2,
                name,
                BASE_URL + "/country/" + detail.getCode() + "?lang=" + lang
        ));

        elements.add(breadcrumbItem(
                3,
                title,
                canonicalUrl
        ));

        breadcrumb.put("itemListElement", elements);

        graph.add(breadcrumb);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("@context", "https://schema.org");
        result.put("@graph", graph);

        return toJson(result);
    }

    /**
     * 文章详情页
     */
    public String buildArticle(Map<String, Object> article,
                               String lang,
                               String canonicalUrl) {

        String title = "zh".equals(lang)
                ? stringValue(article.get("titleZh"))
                : stringValue(article.get("titleEn"));

        String description = "zh".equals(lang)
                ? stringValue(article.get("summaryZh"))
                : stringValue(article.get("summaryEn"));

        String publishAt = stringValue(article.get("publishAt"));

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("@context", "https://schema.org");
        data.put("@type", "Article");

        data.put("headline", title);
        data.put("description", description);
        data.put("url", canonicalUrl);
        data.put("inLanguage", lang);

        if (!isBlank(publishAt)) {
            data.put("datePublished", publishAt);
        }

        Map<String, Object> publisher = new LinkedHashMap<>();
        publisher.put("@type", "Organization");
        publisher.put("name", "China Visa Free Guide");
        publisher.put("url", BASE_URL);

        data.put("publisher", publisher);

        Map<String, Object> mainEntity = new LinkedHashMap<>();
        mainEntity.put("@type", "WebPage");
        mainEntity.put("@id", canonicalUrl);

        data.put("mainEntityOfPage", mainEntity);

        return toJson(data);
    }

    /**
     * Visa Guide
     */
    public String buildVisaGuide(String lang,
                                 String title,
                                 String description,
                                 String canonicalUrl) {

        Map<String, Object> data = new LinkedHashMap<>();

        data.put("@context", "https://schema.org");
        data.put("@type", "WebPage");

        data.put("@id", canonicalUrl + "#webpage");
        data.put("url", canonicalUrl);
        data.put("name", title);
        data.put("description", description);
        data.put("inLanguage", lang);

        return toJson(data);
    }

    private Map<String, Object> breadcrumbItem(
            int position,
            String name,
            String url
    ) {
        Map<String, Object> item = new LinkedHashMap<>();

        item.put("@type", "ListItem");
        item.put("position", position);
        item.put("name", name);
        item.put("item", url);

        return item;
    }

    private String toJson(Object object) {
        try {
            return objectMapper.writeValueAsString(object);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Failed to generate structured data",
                    e
            );
        }
    }

    private String stringValue(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}