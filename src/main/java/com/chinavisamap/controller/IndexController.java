package com.chinavisamap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.chinavisamap.service.SeoService;
import java.util.*;
import java.util.stream.Collectors;
import com.chinavisamap.service.StructuredDataService;

@Controller
public class IndexController {
    private Map<String,Object> allData=new HashMap<>();
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;
    public IndexController(SeoService seoService,StructuredDataService structuredDataService){this.seoService=seoService;this.structuredDataService=structuredDataService;try{allData=new ObjectMapper().readValue(new ClassPathResource("data.json").getInputStream(),new TypeReference<Map<String,Object>>(){});}catch(Exception ignored){}}

    @SuppressWarnings("unchecked")
    @GetMapping("/")
    public String index(@RequestParam(required=false)String keyword,@RequestParam(defaultValue="en")String lang,Model model){
        lang=seoService.normalizeLang(lang); model.addAllAttributes(allData); model.addAttribute("lang",lang); model.addAttribute("keyword",keyword); model.addAttribute("searched",false); model.addAttribute("noIndex",keyword!=null&&!keyword.trim().isEmpty());
        model.addAttribute("canonicalUrl",seoService.canonical("/",lang)); model.addAttribute("hreflang",seoService.hreflang("/"));
        String siteName=String.valueOf(allData.getOrDefault("siteName","China Visa Free Guide 2026"));
        String title="zh".equals(lang)?String.valueOf(allData.getOrDefault("siteTitleZh","2026中国免签政策指南")):String.valueOf(allData.getOrDefault("siteTitle","China Visa-Free Policy 2026"));
        String description="zh".equals(lang)?String.valueOf(allData.getOrDefault("siteDescZh","")):String.valueOf(allData.getOrDefault("siteDesc",""));
        model.addAttribute("structuredData",structuredDataService.buildHome(lang,siteName,title,description));
        if(keyword==null||keyword.trim().isEmpty())return "index";
        String kw=keyword.trim().toLowerCase(Locale.ROOT); model.addAttribute("searched",true);
        try{
            List<Map<String,Object>> continents=(List<Map<String,Object>>)allData.get("continents");
            List<Map<String,Object>> unilateral=flatten(continents).stream().filter(c->match(c,kw)).sorted((a,b)->Integer.compare(score(b,kw,lang),score(a,kw,lang))).collect(Collectors.toList());
            List<Map<String,Object>> mutual=flatten((List<Map<String,Object>>)allData.get("mutualContinents")).stream().filter(c->match(c,kw)).sorted((a,b)->Integer.compare(score(b,kw,lang),score(a,kw,lang))).collect(Collectors.toList());
            List<Map<String,Object>> transit=flatten((List<Map<String,Object>>)allData.get("transitContinents")).stream().filter(c->match(c,kw)).sorted((a,b)->Integer.compare(score(b,kw,lang),score(a,kw,lang))).collect(Collectors.toList());
            model.addAttribute("unilateralResult",unilateral);model.addAttribute("mutualResult",mutual);model.addAttribute("transitResult",transit);model.addAttribute("searchResultCount",unilateral.size()+mutual.size()+transit.size());
        }catch(Exception ignored){model.addAttribute("unilateralResult",Collections.emptyList());model.addAttribute("mutualResult",Collections.emptyList());model.addAttribute("transitResult",Collections.emptyList());model.addAttribute("searchResultCount",0);}
        return "index";
    }
    @SuppressWarnings("unchecked") private List<Map<String,Object>> flatten(List<Map<String,Object>> continents){if(continents==null)return Collections.emptyList();return continents.stream().filter(Objects::nonNull).flatMap(c->{Object v=c.get("countries");return v instanceof List?((List<Map<String,Object>>)v).stream():java.util.stream.Stream.empty();}).collect(Collectors.toList());}
    private boolean match(Map<String,Object> c,String kw){String n=String.valueOf(c.getOrDefault("name","")).toLowerCase(Locale.ROOT);String z=String.valueOf(c.getOrDefault("nameZh","")).toLowerCase(Locale.ROOT);String code=String.valueOf(c.getOrDefault("code","")).toLowerCase(Locale.ROOT);return n.contains(kw)||z.contains(kw)||code.contains(kw);}
    private int score(Map<String,Object> c,String kw,String lang){String n=String.valueOf(c.getOrDefault("name","")).toLowerCase(Locale.ROOT);String z=String.valueOf(c.getOrDefault("nameZh","")).toLowerCase(Locale.ROOT);String code=String.valueOf(c.getOrDefault("code","")).toLowerCase(Locale.ROOT);int s=0;if(code.equals(kw))s+=100;if("en".equals(lang)&&n.equals(kw))s+=90;if("zh".equals(lang)&&z.equals(kw))s+=90;if(n.startsWith(kw)||z.startsWith(kw))s+=40;return s;}
}
