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
    private final Map<String,String[]> countryNames = new LinkedHashMap<>();

    public ContentLinkModelInterceptor(CountryCodeResolver resolver, StructuredDataService structuredDataService, ObjectMapper mapper) {
        this.resolver = resolver;
        this.structuredDataService = structuredDataService;
        this.articles = loadArticles(mapper);
        this.priorityContent = loadPriorityContent(mapper);
        loadCountryNames(mapper, "unilateral.json");
        loadCountryNames(mapper, "mutual.json");
        loadCountryNames(mapper, "transit.json");
    }

    private void loadCountryNames(ObjectMapper mapper, String fileName) {
        try {
            Map<String,CountryDetail> data = mapper.readValue(new ClassPathResource(fileName).getInputStream(), new TypeReference<Map<String,CountryDetail>>() {});
            for (Map.Entry<String,CountryDetail> entry : data.entrySet()) {
                CountryDetail c = entry.getValue(); if (c == null) continue;
                String code = resolver.routeCode(resolver.policyKey(entry.getKey()));
                countryNames.put(code, new String[]{safe(c.getName()), safe(c.getNameZh())});
            }
        } catch (Exception ignored) { }
    }

    private String safe(String value) { return value == null ? "" : value.trim(); }

    @Override
    @SuppressWarnings("unchecked")
    public void postHandle(HttpServletRequest request,HttpServletResponse response,Object handler,ModelAndView mav){
        if(mav==null)return;
        String uri=request.getRequestURI(); String lang="zh".equals(request.getParameter("lang"))?"zh":"en";
        if(uri.startsWith("/country/")){
            String[] parts=uri.split("/"); String code=parts.length>2?resolver.routeCode(resolver.policyKey(parts[2])):""; String type=parts.length>3?parts[3]:"";
            mav.addObject("relatedArticles",relatedArticles(code,type)); mav.addObject("contentLinkLang",lang);
            if("country-home".equals(mav.getViewName())){
                Map<String,Object> base=mav.getModel().get("countryExtraRoot") instanceof Map?new LinkedHashMap<>((Map<String,Object>)mav.getModel().get("countryExtraRoot")):new LinkedHashMap<>();
                Map<String,Object> priority=priorityContent.get(resolver.policyKey(code));
                if(priority!=null){
                    mergePriorityPresentation(base, priority);
                    mav.addObject("countryExtraRoot",base);
                    Object profileObject=mav.getModel().get("countryProfile");
                    if(profileObject instanceof Map){
                        Map<String,Object> p=new LinkedHashMap<>((Map<String,Object>)profileObject);
                        // priority/tier stay sourced from country-extra.json. The priority-content
                        // file is presentation copy, not a second authority for country metadata.
                        p.put("heroQuestionEn",priority.getOrDefault("homeHeroQuestionEn",p.get("heroQuestionEn")));
                        p.put("heroQuestionZh",priority.getOrDefault("homeHeroQuestionZh",p.get("heroQuestionZh")));
                        p.put("heroAnswerEn",priority.getOrDefault("homeHeroAnswerEn",p.get("heroAnswerEn")));
                        p.put("heroAnswerZh",priority.getOrDefault("homeHeroAnswerZh",p.get("heroAnswerZh")));
                        p.put("introEn",priority.getOrDefault("homeIntroEn",p.get("introEn")));
                        p.put("introZh",priority.getOrDefault("homeIntroZh",p.get("introZh")));
                        mav.addObject("countryProfile",p);
                    }
                    Object related=base.get("relatedCountryCodes");
                    if(!(related instanceof List))related=priority.get("relatedCountryCodes");
                    if(related instanceof List)mav.addObject("relatedCountryCodes",normalizeRelatedCodes((List<?>)related,code));
                    rebuildStructuredData(mav,base,lang);
                } else {
                    Object related=base.get("relatedCountryCodes");
                    if(related instanceof List)mav.addObject("relatedCountryCodes",normalizeRelatedCodes((List<?>)related,code));
                }
            }
        }else if(uri.startsWith("/articles/")&&partsCount(uri)==2){
            String id=uri.substring("/articles/".length()); Map<String,Object> article=findArticle(id);
            List<String> codes=article==null?Collections.emptyList():relatedCountries(article);
            mav.addObject("relatedCountryCodes",codes); Map<String,String> names=new LinkedHashMap<>();
            for(String c:codes){String[] n=countryNames.get(c);names.put(c,n==null?c:("zh".equals(lang)?n[1]:n[0]));}
            mav.addObject("relatedCountryNames",names); mav.addObject("contentLinkLang",lang);
        }
    }

    private void mergePriorityPresentation(Map<String,Object> base,Map<String,Object> priority){
        String[] presentationKeys={"homeSeoTitleEn","homeSeoDescEn","homeSeoTitleZh","homeSeoDescZh","homeHeroQuestionEn","homeHeroQuestionZh","homeHeroAnswerEn","homeHeroAnswerZh","homeIntroEn","homeIntroZh","homeImportantNotesEn","homeImportantNotesZh","directVisaFree"};
        for(String key:presentationKeys)if(priority.containsKey(key))base.put(key,priority.get(key));
        // Do not overwrite priority/tier/route identity or the canonical recommendation list.
        // Those remain owned by country-extra.json when present.
    }

    @SuppressWarnings("unchecked")
    private void rebuildStructuredData(ModelAndView mav,Map<String,Object> base,String lang){
        Object country=mav.getModel().get("detailCountry"),types=mav.getModel().get("availableTypes"),canonical=mav.getModel().get("canonicalUrl");
        if(!(country instanceof CountryDetail)||!(types instanceof List)||canonical==null)return;
        Map<String,CountryDetail> details=new LinkedHashMap<>(); Object policies=mav.getModel().get("availablePolicies");
        if(policies instanceof List)for(Object p:(List<?>)policies)if(p instanceof CountryDetail)details.put(((CountryDetail)p).getPolicyType(),(CountryDetail)p);
        mav.addObject("structuredData",structuredDataService.buildCountryHome((CountryDetail)country,lang,String.valueOf(canonical),base,(List<String>)types,details));
    }

    private List<String> normalizeRelatedCodes(List<?> raw,String currentCode){
        List<String> result=new ArrayList<>();
        for(Object item:raw){String value=String.valueOf(item).trim();if(value.isEmpty())continue;String canonical=resolver.routeCode(resolver.policyKey(value));if(canonical.isEmpty()||canonical.equals(currentCode)||!countryNames.containsKey(canonical))continue;if(!result.contains(canonical))result.add(canonical);if(result.size()>=6)break;}
        return result;
    }

    private int partsCount(String uri){return(int)Arrays.stream(uri.split("/",-1)).filter(s->!s.isEmpty()).count();}
    private List<Map<String,Object>> relatedArticles(String code,String type){return articles.stream().filter(a->belongsToCountry(a,code)).sorted((a,b)->{int byScore=Integer.compare(score(b,code,type),score(a,code,type));if(byScore!=0)return byScore;String bt=String.valueOf(b.getOrDefault("publishAt","")),at=String.valueOf(a.getOrDefault("publishAt",""));int byDate=bt.compareTo(at);if(byDate!=0)return byDate;return String.valueOf(a.getOrDefault("id","")).compareTo(String.valueOf(b.getOrDefault("id","")));}).limit(6).collect(Collectors.toList());}
    private boolean belongsToCountry(Map<String,Object> article,String code){Object raw=article.get("relatedCountryCodes");if(!(raw instanceof List))return false;for(Object item:(List<?>)raw)if(resolver.routeCode(resolver.policyKey(String.valueOf(item))).equals(code))return true;return false;}
    private int score(Map<String,Object>a,String code,String type){int score=0;String category=String.valueOf(a.getOrDefault("categoryEn",""));String text=(String.valueOf(a.getOrDefault("titleEn",""))+" "+String.valueOf(a.getOrDefault("titleZh",""))+" "+String.valueOf(a.getOrDefault("summaryEn",""))+" "+String.valueOf(a.getOrDefault("summaryZh",""))).toLowerCase(Locale.ROOT);if("transit".equals(type)){if(category.toLowerCase(Locale.ROOT).contains("visa"))score+=30;if(containsTag(a,"Transit"))score+=35;if(containsTag(a,"240-hour"))score+=30;if(text.contains("transit")||text.contains("过境"))score+=20;}else if("unilateral".equals(type)||"mutual".equals(type)){if(category.toLowerCase(Locale.ROOT).contains("visa-free"))score+=20;if(containsTag(a,"Visa-Free"))score+=25;if(text.contains("visa-free")||text.contains("免签"))score+=15;}Map<String,Object> priority=priorityContent.get(resolver.policyKey(code));if(priority!=null)score+=Math.max(0,20-number(priority.get("priority"),999)/10);return score;}
    private int number(Object value,int fallback){try{return Integer.parseInt(String.valueOf(value));}catch(Exception e){return fallback;}}
    private boolean containsTag(Map<String,Object>a,String k){Object t=a.get("tagsEn");if(!(t instanceof List))return false;return((List<?>)t).stream().anyMatch(v->String.valueOf(v).toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT)));}
    private List<String> relatedCountries(Map<String,Object>a){Object raw=a.get("relatedCountryCodes");if(!(raw instanceof List))return Collections.emptyList();return normalizeRelatedCodes((List<?>)raw,"");}
    private Map<String,Object> findArticle(String id){return articles.stream().filter(a->id.equals(String.valueOf(a.get("id")))).findFirst().orElse(null);}
    private List<Map<String,Object>> loadArticles(ObjectMapper m){try{JsonNode r=m.readTree(new ClassPathResource("articles.json").getInputStream());JsonNode c=r.isObject()&&r.has("content")?r.get("content"):r;if(c.isTextual())c=m.readTree(c.asText());return m.convertValue(c,new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyList();}}
    private Map<String,Map<String,Object>> loadPriorityContent(ObjectMapper m){try{JsonNode r=m.readTree(new ClassPathResource("priority-country-content.json").getInputStream());return m.convertValue(r,new TypeReference<Map<String,Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyMap();}}
}
