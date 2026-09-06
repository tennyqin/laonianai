package com.chinavisamap.web;

import com.chinavisamap.service.CountryCodeResolver;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.ui.Model;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Builds server-rendered internal-link modules so country/policy pages and articles form a real SEO/content cluster.
 * No URL migration is performed here; every generated country URL uses the canonical route resolver.
 */
@Component
public class ContentLinkModelInterceptor implements HandlerInterceptor {
    private final CountryCodeResolver resolver;
    private final List<Map<String,Object>> articles;

    public ContentLinkModelInterceptor(CountryCodeResolver resolver, ObjectMapper mapper) {
        this.resolver = resolver;
        this.articles = loadArticles(mapper);
    }

    @Override
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) {
        if (mav == null || mav.getModelMap() == null) return;
        String uri = request.getRequestURI();
        String lang = "zh".equals(request.getParameter("lang")) ? "zh" : "en";
        if (uri.startsWith("/country/")) {
            String[] parts = uri.split("/");
            String code = parts.length > 2 ? resolver.routeCode(resolver.policyKey(parts[2])) : "";
            String type = parts.length > 3 ? parts[3] : "";
            mav.addObject("relatedArticles", relatedArticles(code, type));
            mav.addObject("contentLinkLang", lang);
        } else if (uri.startsWith("/articles/") && partsCount(uri) == 3) {
            String id = uri.substring("/articles/".length());
            Map<String,Object> article = findArticle(id);
            mav.addObject("relatedCountryCodes", article == null ? Collections.emptyList() : relatedCountries(article));
            mav.addObject("contentLinkLang", lang);
        }
    }

    private int partsCount(String uri) {
        return (int) Arrays.stream(uri.split("/", -1)).filter(s -> !s.isEmpty()).count();
    }

    private List<Map<String,Object>> relatedArticles(String code, String type) {
        String policy = type == null ? "" : type;
        return articles.stream()
                .filter(a -> {
                    Object raw = a.get("relatedCountryCodes");
                    if (!(raw instanceof List)) return false;
                    for (Object item : (List<?>) raw) {
                        if (resolver.routeCode(resolver.policyKey(String.valueOf(item))).equals(code)
                                || resolver.policyKey(String.valueOf(item)).equals(resolver.policyKey(code))) return true;
                    }
                    return false;
                })
                .sorted((a,b) -> {
                    int pa = score(a, policy), pb = score(b, policy);
                    if (pa != pb) return Integer.compare(pb, pa);
                    return String.valueOf(b.getOrDefault("publishAt", "")).compareTo(String.valueOf(a.getOrDefault("publishAt", "")));
                })
                .limit(6)
                .collect(Collectors.toList());
    }

    private int score(Map<String,Object> article, String type) {
        String cat = String.valueOf(article.getOrDefault("categoryEn", ""));
        if ("transit".equals(type) && (cat.contains("Visa") || containsTag(article, "240-hour") || containsTag(article, "Transit"))) return 20;
        if (("unilateral".equals(type) || "mutual".equals(type)) && cat.contains("Visa-Free")) return 15;
        return 1;
    }

    private boolean containsTag(Map<String,Object> article, String keyword) {
        Object tags = article.get("tagsEn");
        if (!(tags instanceof List)) return false;
        return ((List<?>) tags).stream().anyMatch(v -> String.valueOf(v).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));
    }

    private List<String> relatedCountries(Map<String,Object> article) {
        Object raw = article.get("relatedCountryCodes");
        if (!(raw instanceof List)) return Collections.emptyList();
        return ((List<?>) raw).stream()
                .map(String::valueOf)
                .map(v -> resolver.routeCode(resolver.policyKey(v)))
                .distinct().limit(6).collect(Collectors.toList());
    }

    private Map<String,Object> findArticle(String id) {
        return articles.stream().filter(a -> id.equals(String.valueOf(a.get("id")))).findFirst().orElse(null);
    }

    private List<Map<String,Object>> loadArticles(ObjectMapper mapper) {
        try {
            JsonNode root = mapper.readTree(new ClassPathResource("articles.json").getInputStream());
            JsonNode content = root.isObject() && root.has("content") ? root.get("content") : root;
            if (content.isTextual()) content = mapper.readTree(content.asText());
            return mapper.convertValue(content, new TypeReference<List<Map<String,Object>>>() {});
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }
}
