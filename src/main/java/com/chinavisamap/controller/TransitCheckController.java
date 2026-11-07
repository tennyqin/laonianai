package com.chinavisamap.controller;

import com.chinavisamap.service.PolicyRuleEngine;
import com.chinavisamap.service.SeoService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.*;

/** General transit is not limited to countries with a country-specific policy record. */
@Controller
public class TransitCheckController {
    private final PolicyRuleEngine rules;
    private final SeoService seo;
    public TransitCheckController(PolicyRuleEngine rules, SeoService seo){this.rules=rules;this.seo=seo;}
    @GetMapping("/transit-check")
    public String page(@RequestParam(defaultValue="en") String lang, Model model){
        lang=seo.normalizeLang(lang);
        List<Map<String,String>> places=new ArrayList<>();
        for(String code:Locale.getISOCountries()){
            if("CN".equals(code))continue;
            Map<String,String> place=new LinkedHashMap<>();place.put("code",code);
            place.put("name",new Locale("",code).getDisplayCountry("zh".equals(lang)?Locale.SIMPLIFIED_CHINESE:Locale.ENGLISH));places.add(place);
        }
        places.sort(Comparator.comparing(p->p.get("name")));
        model.addAttribute("places",places);model.addAttribute("lang",lang);model.addAttribute("todayChina",rules.today());
        model.addAttribute("lastVerified",rules.version());
        model.addAttribute("canonicalUrl",seo.canonical("/transit-check",lang));model.addAttribute("hreflang",seo.hreflang("/transit-check"));
        return "transit-check";
    }
}
