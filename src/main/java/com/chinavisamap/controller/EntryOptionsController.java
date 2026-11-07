package com.chinavisamap.controller;

import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.PolicyRuleEngine;
import com.chinavisamap.service.SeoService;
import com.chinavisamap.web.RedirectAttribution;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import javax.servlet.http.HttpServletRequest;

/** Nationality selection works with native forms, including without JavaScript. */
@Controller
public class EntryOptionsController {
    private final CountryCodeResolver resolver;
    private final PolicyRuleEngine rules;
    private final SeoService seo;

    public EntryOptionsController(CountryCodeResolver resolver, PolicyRuleEngine rules, SeoService seo) {
        this.resolver = resolver;
        this.rules = rules;
        this.seo = seo;
    }

    @GetMapping("/entry-options")
    public ResponseEntity<Void> entryOptions(@RequestParam String country,
                                           @RequestParam(defaultValue = "country") String task,
                                           @RequestParam(defaultValue = "en") String lang,
                                           HttpServletRequest request) {
        String language = seo.normalizeLang(lang);
        String target;
        if ("other".equals(country)) {
            target = "/transit-check?lang=" + language;
        } else {
            String code = resolver.routeCode(resolver.policyKey(country));
            if (!code.matches("[a-z][a-z-]{1,24}") || !rules.countryExists(code)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose a listed nationality");
            }
            boolean dates = "dates".equals(task) && rules.policyAvailableToday(code, "unilateral");
            target = "/country/" + code + "?lang=" + language + "&entryMode=" + (dates ? "direct" : "auto")
                    + (dates ? "#stay-calculator" : "#eligibility-checker");
        }
        return ResponseEntity.status(HttpStatus.SEE_OTHER).location(URI.create(RedirectAttribution.preserve(target, request)))
                .header("X-Robots-Tag", "noindex").header("Cache-Control", "no-store").build();
    }
}
