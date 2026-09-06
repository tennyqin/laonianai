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
        addNames("jp","Japan","日本"); addNames("de","Germany","德国"); addNames("fr","France","法国"); addNames("it","Italy","意大利");
        addNames("spain","Spain","西班牙"); addNames("gb","United Kingdom","英国"); addNames("us","United States","美国"); addNames("au","Australia","澳大利亚");
        addNames("ca","Canada","加拿大"); addNames("korea","South Korea","韩国"); addNames("sg","Singapore","新加坡"); addNames("my","Malaysia","马来西亚");
    }
    private void addNames(String code,String en,String zh){countryNames.put(code,new String[]{en,zh});}

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
                    base.putAll(priority); mav.addObject("countryExtraRoot",base);
                    Object profileObject=mav.getModel().get("countryProfile");
                    if(profileObject instanceof Map){Map<String,Object> p=new LinkedHashMap<>((Map<String,Object>)profileObject);p.put("priority",priority.getOrDefault("priority",p.get("priority")));p.put("tier",priority.getOrDefault("tier",p.get("tier")));p.put("heroQuestionEn",priority.getOrDefault("homeHeroQuestionEn",p.get("heroQuestionEn")));p.put("heroQuestionZh",priority.getOrDefault("homeHeroQuestionZh",p.get("heroQuestionZh")));p.put("heroAnswerEn",priority.getOrDefault("homeHeroAnswerEn",p.get("heroAnswerEn")));p.put("heroAnswerZh",priority.getOrDefault("homeHeroAnswerZh",p.get("heroAnswerZh")));p.put("introEn",priority.getOrDefault("homeIntroEn",p.get("introEn")));p.put("introZh",priority.getOrDefault("homeIntroZh",p.get("introZh")));mav.addObject("countryProfile",p);}
                    Object related=priority.get("relatedCountryCodes"); if(related instanceof List)mav.addObject("relatedCountryCodes",((List<?>)related).stream().map(String::valueOf).collect(Collectors.toList()));
                    Object country=mav.getModel().get("detailCountry"),types=mav.getModel().get("availableTypes"),canonical=mav.getModel().get("canonicalUrl");
                    if(country instanceof CountryDetail&&types instanceof List&&canonical!=null){Map<String,CountryDetail> details=new LinkedHashMap<>();Object policies=mav.getModel().get("availablePolicies");if(policies instanceof List)for(Object p:(List<?>)policies)if(p instanceof CountryDetail)details.put(((CountryDetail)p).getPolicyType(),(CountryDetail)p);mav.addObject("structuredData",structuredDataService.buildCountryHome((CountryDetail)country,lang,String.valueOf(canonical),base,(List<String>)types,details));}
                }
            }
        }else if(uri.startsWith("/articles/")&&partsCount(uri)==2){String id=uri.substring("/articles/".length());Map<String,Object> article=findArticle(id);List<String> codes=article==null?Collections.emptyList():relatedCountries(article);mav.addObject("relatedCountryCodes",codes);Map<String,String> names=new LinkedHashMap<>();for(String c:codes){String[] n=countryNames.get(c);names.put(c,n==null?c:("zh".equals(lang)?n[1]:n[0]));}mav.addObject("relatedCountryNames",names);mav.addObject("contentLinkLang",lang);}
    }
    private int partsCount(String uri){return(int)Arrays.stream(uri.split("/",-1)).filter(s->!s.isEmpty()).count();}
    private List<Map<String,Object>> relatedArticles(String code,String type){return articles.stream().filter(a->{Object raw=a.get("relatedCountryCodes");if(!(raw instanceof List))return false;for(Object item:(List<?>)raw)if(resolver.routeCode(resolver.policyKey(String.valueOf(item))).equals(code))return true;return false;}).sorted((a,b)->Integer.compare(score(b,type),score(a,type))).limit(6).collect(Collectors.toList());}
    private int score(Map<String,Object>a,String type){String c=String.valueOf(a.getOrDefault("categoryEn",""));if("transit".equals(type)&&(c.contains("Visa")||containsTag(a,"Transit")||containsTag(a,"240-hour")))return 20;if(("unilateral".equals(type)||"mutual".equals(type))&&c.contains("Visa-Free"))return 15;return 1;}
    private boolean containsTag(Map<String,Object>a,String k){Object t=a.get("tagsEn");if(!(t instanceof List))return false;return((List<?>)t).stream().anyMatch(v->String.valueOf(v).toLowerCase(Locale.ROOT).contains(k.toLowerCase(Locale.ROOT)));}
    private List<String> relatedCountries(Map<String,Object>a){Object raw=a.get("relatedCountryCodes");if(!(raw instanceof List))return Collections.emptyList();return((List<?>)raw).stream().map(String::valueOf).map(v->resolver.routeCode(resolver.policyKey(v))).distinct().limit(6).collect(Collectors.toList());}
    private Map<String,Object> findArticle(String id){return articles.stream().filter(a->id.equals(String.valueOf(a.get("id")))).findFirst().orElse(null);}
    private List<Map<String,Object>> loadArticles(ObjectMapper m){try{JsonNode r=m.readTree(new ClassPathResource("articles.json").getInputStream());JsonNode c=r.isObject()&&r.has("content")?r.get("content"):r;if(c.isTextual())c=m.readTree(c.asText());return m.convertValue(c,new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyList();}}
    private Map<String,Map<String,Object>> loadPriorityContent(ObjectMapper m){try{JsonNode r=m.readTree(new ClassPathResource("priority-country-content.json").getInputStream());return m.convertValue(r,new TypeReference<Map<String,Map<String,Object>>>(){});}catch(Exception e){return Collections.emptyMap();}}
}
