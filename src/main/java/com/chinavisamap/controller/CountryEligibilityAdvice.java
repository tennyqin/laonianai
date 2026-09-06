package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.CountryEligibilityService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

import java.util.*;

@ControllerAdvice(assignableTypes = CountryController.class)
public class CountryEligibilityAdvice {
    private final Map<String, CountryDetail> unilateral;
    private final Map<String, CountryDetail> mutual;
    private final Map<String, CountryDetail> transit;
    private final Map<String, Map<String,Object>> extras;
    private final CountryCodeResolver resolver;
    private final CountryEligibilityService eligibilityService;

    public CountryEligibilityAdvice(ObjectMapper mapper, CountryCodeResolver resolver, CountryEligibilityService eligibilityService) {
        this.unilateral = load(mapper, "unilateral.json");
        this.mutual = load(mapper, "mutual.json");
        this.transit = load(mapper, "transit.json");
        this.extras = loadExtras(mapper);
        this.resolver = resolver;
        this.eligibilityService = eligibilityService;
    }

    @ModelAttribute
    public void addEligibilityConfig(Model model) {
        Object codeObj = model.getAttribute("code");
        if (codeObj == null) return;
        String code = String.valueOf(codeObj);
        String key = resolver.policyKey(code);
        List<CountryDetail> policies = new ArrayList<>();
        if (unilateral.containsKey(key)) policies.add(unilateral.get(key));
        if (mutual.containsKey(key)) policies.add(mutual.get(key));
        if (transit.containsKey(key)) policies.add(transit.get(key));
        model.addAttribute("eligibilityConfig", eligibilityService.build(code, policies, extras.get(code)));
    }

    private Map<String,CountryDetail> load(ObjectMapper mapper, String file) {
        try { return mapper.readValue(new ClassPathResource(file).getInputStream(), new TypeReference<Map<String,CountryDetail>>(){}); }
        catch (Exception e) { throw new IllegalStateException("Failed to load " + file, e); }
    }
    private Map<String,Map<String,Object>> loadExtras(ObjectMapper mapper) {
        try { return mapper.readValue(new ClassPathResource("country-extra.json").getInputStream(), new TypeReference<Map<String,Map<String,Object>>>(){}); }
        catch (Exception e) { throw new IllegalStateException("Failed to load country-extra.json", e); }
    }
}