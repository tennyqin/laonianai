package com.chinavisamap.controller;

import com.chinavisamap.service.SeoService;
import com.chinavisamap.service.SiteMetadataService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class AboutController {
    private final SeoService seo;
    private final SiteMetadataService metadata;
    public AboutController(SeoService seo, SiteMetadataService metadata) {
        this.seo = seo;
        this.metadata = metadata;
    }
    @GetMapping("/about")
    public String about(@RequestParam(defaultValue="en") String lang, Model model) {
        String language = seo.normalizeLang(lang);
        model.addAttribute("lang", language);
        model.addAttribute("canonicalUrl", seo.canonical("/about", language));
        model.addAttribute("hreflang", seo.hreflang("/about"));
        model.addAttribute("lastVerified", metadata.lastVerified());
        return "about";
    }
}
