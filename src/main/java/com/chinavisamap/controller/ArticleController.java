package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import com.chinavisamap.service.SeoService;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
import com.chinavisamap.service.StructuredDataService;

@Controller
public class ArticleController {

    private List<Map<String, Object>> articleList;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;
    private final Map<String, String> countrySearchNames = new LinkedHashMap<>();

    public static final List<Map<String, String>> ALL_CATEGORIES;
    static {
        List<Map<String,String>> temp = new ArrayList<>();
        temp.add(category("Visa-Free Tips", "免签政策提示"));
        temp.add(category("China Life & Apps", "中国生活与APP"));
        temp.add(category("Travel Customs", "旅行风俗文化"));
        temp.add(category("Border-Inspection Pitfalls", "边检出入境踩坑"));
        ALL_CATEGORIES = Collections.unmodifiableList(temp);
    }

    private static Map<String,String> category(String en, String zh) {
        Map<String,String> result = new LinkedHashMap<>();
        result.put("en", en);
        result.put("zh", zh);
        return result;
    }

    public ArticleController(ObjectMapper objectMapper, SeoService seoService, StructuredDataService structuredDataService){
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        loadCountrySearchNames(objectMapper, "unilateral.json");
        loadCountrySearchNames(objectMapper, "mutual.json");
        loadCountrySearchNames(objectMapper, "transit.json");
        try {
            JsonNode root = objectMapper.readTree(new ClassPathResource("articles.json").getInputStream());
            if (root.isArray()) {
                articleList = objectMapper.convertValue(root, new TypeReference<List<Map<String,Object>>>() {});
            } else if (root.isObject() && root.has("content")) {
                JsonNode content = root.get("content");
                if (content.isTextual()) {
                    articleList = objectMapper.readValue(content.asText(), new TypeReference<List<Map<String,Object>>>() {});
                } else {
                    articleList = objectMapper.convertValue(content, new TypeReference<List<Map<String,Object>>>() {});
                }
            } else {
                articleList = Collections.emptyList();
            }
            DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Map<String, Object> art : articleList) {
                String publishAtRaw = (String) art.get("publishAt");
                if (StringUtils.isNotBlank(publishAtRaw)) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(publishAtRaw, isoFormatter);
                        art.put("displayPublishDate", dt.format(displayFormatter));
                    } catch (Exception parseEx) {
                        art.put("displayPublishDate", publishAtRaw);
                    }
                } else {
                    art.put("displayPublishDate", "");
                }
            }
            articleList.sort((a, b) -> {
                String t1 = String.valueOf(a.getOrDefault("publishAt", ""));
                String t2 = String.valueOf(b.getOrDefault("publishAt", ""));
                return t2.compareTo(t1);
            });
        } catch (Exception e) {
            articleList = Collections.emptyList();
        }
    }

    private void loadCountrySearchNames(ObjectMapper mapper, String fileName) {
        try {
            Map<String, CountryDetail> data = mapper.readValue(
                    new ClassPathResource(fileName).getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
            for (Map.Entry<String, CountryDetail> entry : data.entrySet()) {
                CountryDetail country = entry.getValue();
                if (country == null) continue;
                String code = entry.getKey();
                countrySearchNames.put(normalize(code), normalize(country.getName()));
                countrySearchNames.put(normalize(country.getName()), normalize(code));
                countrySearchNames.put(normalize(country.getNameZh()), normalize(code));
            }
        } catch (Exception ignored) {
            // Country-name search is an enhancement; article rendering must still work.
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
    ){
        final String currentLang = seoService.normalizeLang(lang);
        final int currentPage = page == null ? 1 : Math.max(page, 1);
        final int currentSize = size == null ? 10 : Math.min(Math.max(size, 1), 50);
        model.addAttribute("canonicalUrl", seoService.canonical("/articles", currentLang));
        model.addAttribute("hreflang", seoService.hreflang("/articles"));

        boolean noIndex = currentPage > 1 || StringUtils.isNotBlank(keyword) || StringUtils.isNotBlank(category) || currentSize != 10;
        model.addAttribute("noIndex", noIndex);

        List<Map<String,Object>> source = new ArrayList<>(articleList);
        if(StringUtils.isNotBlank(category)){
            final String catParam = category.trim();
            source = source.stream()
                    .filter(art -> catParam.equalsIgnoreCase(text(art, "categoryEn")) || catParam.equalsIgnoreCase(text(art, "categoryZh")))
                    .collect(Collectors.toList());
        }

        if(StringUtils.isNotBlank(keyword)){
            final String kw = normalize(keyword);
            source = source.stream()
                    .filter(art -> articleMatches(art, kw))
                    .sorted((a, b) -> {
                        int byScore = Integer.compare(articleScore(b, kw, currentLang), articleScore(a, kw, currentLang));
                        if (byScore != 0) return byScore;
                        return text(a, "titleEn").compareToIgnoreCase(text(b, "titleEn"));
                    })
                    .collect(Collectors.toList());
        }

        int total = source.size();
        int totalPages = (int)Math.ceil((double)total / currentSize);
        int offset = (currentPage - 1) * currentSize;
        List<Map<String,Object>> pageData;
        if(offset >= total){
            pageData = Collections.emptyList();
        }else {
            int end = Math.min(offset + currentSize, total);
            pageData = source.subList(offset, end);
        }

        model.addAttribute("lang", currentLang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("allCategories", ALL_CATEGORIES);
        model.addAttribute("page", currentPage);
        model.addAttribute("size", currentSize);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("articlePageList", pageData);
        return "articles-list";
    }

    private boolean articleMatches(Map<String,Object> art, String kw) {
        String titleEn = normalize(art.get("titleEn"));
        String titleZh = normalize(art.get("titleZh"));
        String sumEn = normalize(art.get("summaryEn"));
        String sumZh = normalize(art.get("summaryZh"));
        String catEn = normalize(art.get("categoryEn"));
        String catZh = normalize(art.get("categoryZh"));
        String tagsEn = normalizeList(art.get("tagsEn"));
        String tagsZh = normalizeList(art.get("tagsZh"));
        String countries = countryTerms(art.get("relatedCountryCodes"));
        return titleEn.contains(kw) || titleZh.contains(kw)
                || sumEn.contains(kw) || sumZh.contains(kw)
                || catEn.contains(kw) || catZh.contains(kw)
                || tagsEn.contains(kw) || tagsZh.contains(kw)
                || countries.contains(kw);
    }

    private int articleScore(Map<String,Object> art, String kw, String lang) {
        String titleEn = normalize(art.get("titleEn"));
        String titleZh = normalize(art.get("titleZh"));
        String sumEn = normalize(art.get("summaryEn"));
        String sumZh = normalize(art.get("summaryZh"));
        String catEn = normalize(art.get("categoryEn"));
        String catZh = normalize(art.get("categoryZh"));
        String tagsEn = normalizeList(art.get("tagsEn"));
        String tagsZh = normalizeList(art.get("tagsZh"));
        String countries = countryTerms(art.get("relatedCountryCodes"));

        int score = 0;
        score += titleScore(titleEn, kw, "en".equals(lang), 1000);
        score += titleScore(titleZh, kw, "zh".equals(lang), 1000);
        score += containsScore(tagsEn, kw, 500, 300);
        score += containsScore(tagsZh, kw, 500, 300);
        score += containsScore(sumEn, kw, 180, 80);
        score += containsScore(sumZh, kw, 180, 80);
        score += containsScore(catEn, kw, 120, 60);
        score += containsScore(catZh, kw, 120, 60);
        score += containsScore(countries, kw, 350, 180);
        return score;
    }

    private String countryTerms(Object value) {
        if (!(value instanceof List)) return normalize(value);
        StringBuilder result = new StringBuilder();
        for (Object item : (List<?>) value) {
            String code = normalize(item);
            if (result.length() > 0) result.append(' ');
            result.append(code);
            String name = countrySearchNames.get(code);
            if (name != null) result.append(' ').append(name);
            // Reverse lookup lets a user search for a country name even when the article stores only its code.
            for (Map.Entry<String,String> entry : countrySearchNames.entrySet()) {
                if (entry.getValue().equals(code)) {
                    result.append(' ').append(entry.getKey());
                }
            }
        }
        return result.toString();
    }

    private int titleScore(String value, String kw, boolean localized, int base) {
        if (value.equals(kw)) return base + (localized ? 200 : 100);
        if (value.startsWith(kw)) return base / 2 + (localized ? 80 : 40);
        if (value.contains(kw)) return base / 4;
        return 0;
    }

    private int containsScore(String value, String kw, int exact, int partial) {
        if (value.equals(kw)) return exact;
        if (value.contains(kw)) return partial;
        return 0;
    }

    private String normalizeList(Object value) {
        if (!(value instanceof List)) return normalize(value);
        return ((List<?>) value).stream().map(this::normalize).collect(Collectors.joining(" "));
    }

    private String text(Map<String,Object> map, String key) {
        return String.valueOf(map.getOrDefault(key, ""));
    }

    private String normalize(Object value) {
        return value == null ? "" : String.valueOf(value).trim().toLowerCase(Locale.ROOT);
    }

    @GetMapping("/articles/{id}")
    public String articleDetail(
            @PathVariable String id,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ){
        final String currentLang = seoService.normalizeLang(lang);
        String articlePath = "/articles/" + id;
        Optional<Map<String, Object>> opt = articleList.stream()
                .filter(a -> id.equals(a.get("id")))
                .findFirst();
        if(!opt.isPresent()){
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Article not found");
        }

        model.addAttribute("canonicalUrl", seoService.canonical(articlePath, currentLang));
        model.addAttribute("hreflang", seoService.hreflang(articlePath));
        Map<String,Object> article = opt.get();
        model.addAttribute("lang", currentLang);
        model.addAttribute("article", article);
        model.addAttribute("structuredData", structuredDataService.buildArticle(article, currentLang, seoService.canonical(articlePath, currentLang)));
        return "articles-detail";
    }

    @GetMapping("/api/articles/random")
    @ResponseBody
    public List<Map<String,Object>> randomArticles(@RequestParam(defaultValue = "5") Integer limit){
        int safeLimit = limit == null ? 5 : Math.min(Math.max(limit, 1), 20);
        List<Map<String,Object>> copy = new ArrayList<>(articleList);
        Collections.shuffle(copy);
        return copy.stream().limit(safeLimit).collect(Collectors.toList());
    }
}
