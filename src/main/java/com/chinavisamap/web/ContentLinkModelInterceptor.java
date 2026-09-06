package com.chinavisamap.web;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.StructuredDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class ContentLinkModelInterceptor implements HandlerInterceptor {
    private final CountryCodeResolver resolver;
    private final StructuredDataService structuredDataService;
    private final List<Map<String,Object>> articles;
    private final Map<String,Map<String,Object>> priorityContent;

    public ContentLinkModelInterceptor(CountryCodeResolver resolver, StructuredDataService structuredDataService, ObjectMapper mapper) {
        this.resolver = resolver;
        this.structuredDataService = structuredDataService;
        this.articles = loadArticles(mapper);
        this.priorityContent = loadPriorityContent(mapper);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView mav) {
        if (mav == null) return;
        String uri = request.getRequestURI();
        String lang = "zh".equals(request.getParameter("lang")) ? "zh" : "en";
        if (uri.startsWith("/country/")) {
            String[] parts = uri.split("/");
            String code = parts.length > 2 ? resolver.routeCode(resolver.policyKey(parts[2])) : "";
            String type = parts.length > 3 ? parts[3] : "";
            mav.addObject("relatedArticles", relatedArticles(code, type));
            mav.addObject("contentLinkLang", lang);
            if ("country-home".equals(mav.getViewName())) {
                Map<String,Object> base = mav.getModel().get("countryExtraRoot") instanceof Map ? new LinkedHashMap<>((Map<String,Object>)mav.getModel().get("countryExtraRoot")) : new LinkedHashMap<>();
                Map<String,Object> priority = priorityContent.get(resolver.policyKey(code));
                if (priority != null) {
                    base.putAll(priority);
                    mav.addObject("countryExtraRoot", base);
                    Object profileObject = mav.getModel().get("countryProfile");
                    if (profileObject instanceof Map) {
                        Map<String,Object> profile = new LinkedHashMap<>((Map<String,Object>)profileObject);
                        profile.put("priority", priority.getOrDefault("priority", profile.get("priority")));
                        profile.put("tier", priority.getOrDefault("tier", profile.get("tier")));
                        profile.put("heroQuestionEn", priority.getOrDefault("homeHeroQuestionEn", profile.get("heroQuestionEn")));
                        profile.put("heroQuestionZh", priority.getOrDefault("homeHeroQuestionZh", profile.get("heroQuestionZh")));
                        profile.put("heroAnswerEn", priority.getOrDefault("homeHeroAnswerEn", profile.get("heroAnswerEn")));
                        profile.put("heroAnswerZh", priority.getOrDefault("homeHeroAnswerZh", profile.get("heroAnswerZh")));
                        profile.put("introEn", priority.getOrDefault("homeIntroEn", profile.get("introEn")));
                        profile.put("introZh", priority.getOrDefault("homeIntroZh", profile.get("introZh")));
                        mav.addObject("countryProfile", profile);
                    }
                    Object related = priority.get("relatedCountryCodes");
                    if (related instanceof List) mav.addObject("relatedCountryCodes", ((List<?>)related).stream().map(String::valueOf).collect(Collectors.toList()));
                    Object country = mav.getModel().get("detailCountry");
                    Object types = mav.getModel().get("availableTypes");
                    Object canonical = mav.getModel().get("canonicalUrl");
                    if (country instanceof CountryDetail && types instanceof List && canonical != null) {
                        Map<String,CountryDetail> details = new LinkedHashMap<>();
                        Object policies = mav.getModel().get("availablePolicies");
                        if (policies instanceof List) for (Object p:(List<?>)policies) if(p instanceof CountryDetail) details.put(((CountryDetail)p).getPolicyType(),(CountryDetail)p);
                        mav.addObject("structuredData", structuredDataService.buildCountryHome((CountryDetail)country,lang,String.valueOf(canonical),base,(List<String>)types,details));
                    }
                }
            }
        } else if (uri.startsWith("/articles/") && partsCount(uri) == 2) {
            String id = uri.substring("/articles/".length());
            Map<String,Object> article = findArticle(id);
            mav.addObject("relatedCountryCodes", article == null ? Collections.emptyList() : relatedCountries(article));
            mav.addObject("contentLinkLang", lang);
        }
    }

    private int partsCount(String uri){return (int)Arrays.stream(uri.split("/",-1)).filter(s->!s.isEmpty()).count();}
    private List<Map<String,Object>> relatedArticles(String code,String type){return articles.stream().filter(a->{Object raw=a.get("relatedCountryCodes");if(!(raw instanceof List))return false;for(Object item:(List<?>)raw)if(resolver.routeCode(resolver.policyKey(String.valueOf(item))).equals(code))return true;return false;}).sorted((a,b)->Integer.compare(score(b,type),score(a,type))).limit(6).collect(Collectors.toList());}
    private int score(Map<String,Object> article,String type){String cat=String.valueOf(article.getOrDefault("categoryEn",""));if("transit".equals(type)&&(cat.contains("Visa")||containsTag(article,"Transit")||containsTag(article,"240-hour")))return 20;if(("unilateral".equals(type)||"mutual".equals(type))&&cat.contains("Visa-Free"))return 15;return 1;}
    private boolean containsTag(Map<String,Object> article,String keyword){Object tags=article.get("tagsEn");if(!(tags instanceof List))return false;return ((List<?>)tags).stream().anyMatch(v->String.valueOf(v).toLowerCase(Locale.ROOT).contains(keyword.toLowerCase(Locale.ROOT)));}
    private List<String> relatedCountries(Map<String,Object> article){Object raw=article.get("relatedCountryCodes");if(!(raw instanceof List))return Collections.emptyList();return ((List<?>)raw).stream().map(String::valueOf).map(v->resolver.routeCode(resolver.policyKey(v))).distinct().limit(6).collect(Collectors.toList());}
    private Map<String,Object> findArticle(String id){return articles.stream().filter(a->id.equals(String.valueOf(a.get("id")))).findFirst().orElse(null);}
    private List<Map<String,Object>> loadArticles(ObjectMapper mapper){try{JsonNode root=mapper.readTree(new ClassPathResource("articles.json").getInputStream());JsonNode c=root.isObject()&&root.has("content")?root.get("content"):root;if(c.isTextual())c=mapper.readTree(c.asText());return mapper.convertValue(c,new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyList();}}
    private Map<String,Map<String,Object>> loadPriorityContent(ObjectMapper mapper){try{JsonNode root=mapper.readTree(new ClassPathResource("priority-country-content.json").getInputStream());return mapper.convertValue(root,new TypeReference<Map<String,Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyMap();}}
}
