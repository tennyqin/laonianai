package com.chinavisamap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.chinavisamap.service.SeoService;
import com.chinavisamap.service.StructuredDataService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ArticleController {

    private List<Map<String, Object>> articleList;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;

    public static final List<Map<String, String>> ALL_CATEGORIES;

    static {
        List<Map<String, String>> temp = new ArrayList<>();
        temp.add(category("visa-free-tips", "Visa-Free Tips", "免签政策提示"));
        temp.add(category("china-life-apps", "China Life & Apps", "中国生活与APP"));
        temp.add(category("travel-customs", "Travel Customs", "旅行风俗文化"));
        temp.add(category("border-inspection-pitfalls", "Border-Inspection Pitfalls", "边检出入境踩坑"));
        temp.add(category("country-visa-guides", "Country Visa Guides", "国家签证指南"));
        ALL_CATEGORIES = Collections.unmodifiableList(temp);
    }

    private static Map<String, String> category(String id, String en, String zh) {
        Map<String, String> result = new LinkedHashMap<>();
        result.put("id", id);
        result.put("en", en);
        result.put("zh", zh);
        return result;
    }

    private static Map<String, String> findCategory(String value) {
        String key = categoryKey(value);
        if (key.isEmpty()) return null;
        for (Map<String, String> category : ALL_CATEGORIES) {
            for (String alias : category.values()) {
                if (key.equals(categoryKey(alias))) return category;
            }
        }
        return null;
    }

    private static String categoryKey(String value) {
        return StringUtils.deleteWhitespace(StringUtils.defaultString(value)).toLowerCase(Locale.ROOT);
    }

    public ArticleController(ObjectMapper objectMapper,
                             SeoService seoService,
                             StructuredDataService structuredDataService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        loadArticles(objectMapper);
    }

    private void loadArticles(ObjectMapper objectMapper) {
        try {
            JsonNode root = objectMapper.readTree(
                    new ClassPathResource("articles.json").getInputStream());

            if (root.isArray()) {
                articleList = objectMapper.convertValue(
                        root, new TypeReference<List<Map<String, Object>>>() {});
            } else if (root.isObject() && root.has("content")) {
                JsonNode content = root.get("content");
                if (content.isTextual()) {
                    articleList = objectMapper.readValue(
                            content.asText(), new TypeReference<List<Map<String, Object>>>() {});
                } else {
                    articleList = objectMapper.convertValue(
                            content, new TypeReference<List<Map<String, Object>>>() {});
                }
            } else {
                articleList = Collections.emptyList();
            }

            DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            for (Map<String, Object> art : articleList) {
                Map<String, String> articleCategory = findCategory(text(art, "categoryEn"));
                if (articleCategory == null) articleCategory = findCategory(text(art, "categoryZh"));
                art.put("categoryId", articleCategory == null ? "" : articleCategory.get("id"));
                String publishAtRaw = text(art, "publishAt");
                if (StringUtils.isNotBlank(publishAtRaw)) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(publishAtRaw, isoFormatter);
                        art.put("displayPublishDate", dt.format(displayFormatter));
                    } catch (Exception ignored) {
                        art.put("displayPublishDate", publishAtRaw);
                    }
                } else {
                    art.put("displayPublishDate", "");
                }
                String modifiedRaw = text(art, "dateModified");
                if (StringUtils.isNotBlank(modifiedRaw)) {
                    try {
                        LocalDateTime md = LocalDateTime.parse(modifiedRaw.length() == 10 ? modifiedRaw + "T00:00:00" : modifiedRaw, isoFormatter);
                        art.put("displayModifiedDate", md.format(displayFormatter));
                    } catch (Exception ignored) {
                        art.put("displayModifiedDate", modifiedRaw);
                    }
                } else {
                    art.put("displayModifiedDate", "");
                }
            }

            articleList.sort((a, b) ->
                    text(b, "publishAt").compareTo(text(a, "publishAt")));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load required articles.json", e);
        }
    }

    @GetMapping("/articles")
    public String articleList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ) {
        final String currentLang = seoService.normalizeLang(lang);
        final int currentPage = page == null ? 1 : Math.max(page, 1);
        final int currentSize = size == null ? 10 : Math.min(Math.max(size, 1), 50);
        final Map<String, String> selectedCategory = findCategory(category);
        final String currentCategory = StringUtils.isBlank(category) ? null
                : selectedCategory == null ? category.trim() : selectedCategory.get("id");

        model.addAttribute("canonicalUrl", seoService.canonical("/articles", currentLang));
        model.addAttribute("hreflang", seoService.hreflang("/articles"));

        boolean noIndex = currentPage > 1
                || StringUtils.isNotBlank(keyword)
                || StringUtils.isNotBlank(category)
                || currentSize != 10;
        model.addAttribute("noIndex", noIndex);

        List<Map<String, Object>> source = new ArrayList<>(articleList);

        if (StringUtils.isNotBlank(category)) {
            source = source.stream()
                    .filter(art -> selectedCategory != null
                            && selectedCategory.get("id").equals(text(art, "categoryId")))
                    .collect(Collectors.toList());
        }

        if (StringUtils.isNotBlank(keyword)) {
            final String kw = normalize(keyword);
            source = source.stream()
                    .filter(art -> articleMatches(art, kw))
                    .sorted((a, b) -> {
                        int byScore = Integer.compare(
                                articleScore(b, kw, currentLang),
                                articleScore(a, kw, currentLang));
                        if (byScore != 0) {
                            return byScore;
                        }
                        return text(a, currentLang.equals("zh") ? "titleZh" : "titleEn")
                                .compareToIgnoreCase(text(b, currentLang.equals("zh") ? "titleZh" : "titleEn"));
                    })
                    .collect(Collectors.toList());
        }

        int total = source.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / currentSize);
        // Real pagination contains different articles, so it is not a duplicate
        // of page one. Search/filter variants and empty pages remain noindex.
        boolean standardPage = StringUtils.isBlank(keyword) && StringUtils.isBlank(category)
                && currentSize == 10 && currentPage <= Math.max(1, totalPages);
        if (standardPage) {
            model.addAttribute("noIndex", false);
            model.addAttribute("canonicalUrl", seoService.articleListCanonical(currentLang, currentPage));
            model.addAttribute("hreflang", seoService.articleListHreflang(currentPage));
        }

        // Pagination is a list resource, not an article resource. Any page number
        // beyond the available range must still render /articles with an empty list
        // state instead of being converted into a 404. Use long arithmetic so even
        // very large user-supplied page values cannot overflow the offset.
        long offset = ((long) currentPage - 1L) * currentSize;

        List<Map<String, Object>> pageData;
        if (offset >= total) {
            pageData = Collections.emptyList();
        } else {
            int start = (int) offset;
            int end = Math.min(start + currentSize, total);
            pageData = source.subList(start, end);
        }

        model.addAttribute("outOfRangePage", total > 0 && currentPage > totalPages);

        model.addAttribute("lang", currentLang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", currentCategory);
        model.addAttribute("categoryLabel", selectedCategory == null ? currentCategory
                : selectedCategory.get(currentLang.equals("zh") ? "zh" : "en"));
        model.addAttribute("allCategories", ALL_CATEGORIES);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", currentSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("articlePageList", pageData);
        return "articles-list";
    }

    /**
     * Article search intentionally matches ONLY the article title and body content.
     * Summary/category/tags/country metadata are not searchable, so results stay
     * relevant to what the visitor actually reads on the article page.
     */
    private boolean articleMatches(Map<String, Object> art, String kw) {
        if (StringUtils.isBlank(kw)) {
            return true;
        }
        return normalize(art.get("titleEn")).contains(kw)
                || normalize(art.get("titleZh")).contains(kw)
                || normalize(art.get("contentEn")).contains(kw)
                || normalize(art.get("contentZh")).contains(kw);
    }

    /**
     * Title is intentionally weighted much higher than body content.
     * This improves usability without introducing metadata-only matches.
     */
    private int articleScore(Map<String, Object> art, String kw, String lang) {
        String title = normalize(art.get("zh".equals(lang) ? "titleZh" : "titleEn"));
        String otherTitle = normalize(art.get("zh".equals(lang) ? "titleEn" : "titleZh"));
        String content = normalize(art.get("zh".equals(lang) ? "contentZh" : "contentEn"));
        String otherContent = normalize(art.get("zh".equals(lang) ? "contentEn" : "contentZh"));

        int score = 0;
        score += titleScore(title, kw, 10000);
        score += titleScore(otherTitle, kw, 6000);
        score += contentScore(content, kw, 1000);
        score += contentScore(otherContent, kw, 500);
        return score;
    }

    private int titleScore(String value, String kw, int base) {
        if (value.equals(kw)) return base + 1000;
        if (value.startsWith(kw)) return base + 500;
        if (value.contains(kw)) return base;
        return 0;
    }

    private int contentScore(String value, String kw, int base) {
        if (!value.contains(kw)) return 0;
        int occurrences = 0;
        int from = 0;
        while ((from = value.indexOf(kw, from)) >= 0 && occurrences < 10) {
            occurrences++;
            from += Math.max(kw.length(), 1);
        }
        return base + occurrences * 20;
    }

    private String text(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value == null ? "" : String.valueOf(value);
    }

    private String normalize(Object value) {
        if (value == null) return "";
        return String.valueOf(value)
                .replaceAll("<[^>]+>", " ")
                .trim()
                .toLowerCase(Locale.ROOT);
    }

    @GetMapping("/articles/{id}")
    public String articleDetail(
            @PathVariable String id,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ) {
        final String currentLang = seoService.normalizeLang(lang);
        String articlePath = "/articles/" + id;

        Optional<Map<String, Object>> opt = articleList.stream()
                .filter(a -> id.equals(a.get("id")))
                .findFirst();

        if (!opt.isPresent()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }

        model.addAttribute("canonicalUrl", seoService.canonical(articlePath, currentLang));
        model.addAttribute("hreflang", seoService.hreflang(articlePath));

        Map<String, Object> article = opt.get();
        model.addAttribute("lang", currentLang);
        model.addAttribute("article", article);
        // Tool intent is editorial data, independent from visa eligibility rules.
        Object tool = article.get("toolIntent");
        model.addAttribute("articleToolIntent", tool == null ? "country" : String.valueOf(tool));
        model.addAttribute("structuredData", structuredDataService.buildArticle(
                article,
                currentLang,
                seoService.canonical(articlePath, currentLang)));
        return "articles-detail";
    }

    @GetMapping("/api/articles/random")
    @ResponseBody
    public List<Map<String, Object>> randomArticles(
            @RequestParam(defaultValue = "5") Integer limit) {
        int safeLimit = limit == null ? 5 : Math.min(Math.max(limit, 1), 20);
        List<Map<String, Object>> copy = new ArrayList<>(articleList);
        Collections.shuffle(copy);
        return copy.stream().limit(safeLimit).collect(Collectors.toList());
    }
}
