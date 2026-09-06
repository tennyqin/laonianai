package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.SeoService;
import com.chinavisamap.service.StructuredDataService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Controller
public class CountryController {
    private static final String NIA_SOURCE = "https://en.nia.gov.cn/";
    private static final String VERIFIED_DATE = "2026-09-06";
    private static final Set<String> CURRENT_UNILATERAL_30 = Collections.unmodifiableSet(new HashSet<String>() {{
        add("kyrgyzstan");
        add("vietnam");
    }});

    private final Map<String, CountryDetail> unilateralMap;
    private final Map<String, CountryDetail> mutualMap;
    private final Map<String, CountryDetail> transitMap;
    private final Map<String, Map<String, Object>> countryExtraMap;
    private final CountryCodeResolver resolver;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;

    public CountryController(ObjectMapper mapper, CountryCodeResolver resolver, SeoService seoService, StructuredDataService structuredDataService) {
        this.resolver = resolver;
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        this.unilateralMap = loadCountryMap(mapper, "unilateral.json");
        this.mutualMap = loadCountryMap(mapper, "mutual.json");
        this.transitMap = loadCountryMap(mapper, "transit.json");
        this.countryExtraMap = loadExtraMap(mapper);
    }

    @GetMapping(value = "/country/{code}", params = "type")
    public ResponseEntity<Void> legacyCountryRedirect(@PathVariable String code, @RequestParam String type, @RequestParam(defaultValue = "en") String lang) {
        if (getCountryDetail(code, type) == null) {
            return ResponseEntity.notFound().build();
        }
        String location = UriComponentsBuilder.fromPath("/country/" + routeCode(code) + "/" + type)
                .queryParam("lang", seoService.normalizeLang(lang)).build().encode().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }

    @GetMapping("/country/{code}")
    public String countryHome(@PathVariable String code, @RequestParam(defaultValue = "en") String lang, Model model) {
        String normalizedLang = seoService.normalizeLang(lang);
        List<String> types = detectAvailableTypes(code);
        if (types.isEmpty()) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Country not found");
        String pageCode = routeCode(code);
        CountryDetail detailCountry = getAnyCountryDetail(code);
        Map<String, Object> extra = getCountryExtraRoot(code);
        String path = "/country/" + pageCode;
        String canonical = seoService.canonical(path, normalizedLang);
        model.addAttribute("code", pageCode);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("availableTypes", types);
        model.addAttribute("countryExtraRoot", extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, extra));
        model.addAttribute("availablePolicies", buildAvailablePolicies(code, types));
        model.addAttribute("countryNames", buildCountryNames());
        model.addAttribute("relatedCountryCodes", buildRelatedCountryCodes(extra));
        model.addAttribute("eligibilityConfig", buildEligibilityConfig(code, types));
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountryHome(detailCountry, normalizedLang, canonical, extra, types, buildPolicyDetails(code, types)));
        return "country-home";
    }

    @GetMapping("/country/{code}/{type}")
    public String restCountryDetail(@PathVariable String code, @PathVariable String type, @RequestParam(defaultValue = "en") String lang, Model model) {
        String normalizedLang = seoService.normalizeLang(lang);
        CountryDetail detailCountry = getCountryDetail(code, type);
        if (detailCountry == null) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Country policy not found");
        String pageCode = routeCode(code);
        String path = "/country/" + pageCode + "/" + type;
        String canonical = seoService.canonical(path, normalizedLang);
        Map<String, Object> extra = asMap(getCountryExtraItem(code, type));
        Map<String, Object> rootExtra = getCountryExtraRoot(code);
        CountryPolicy policy = buildCountryPolicy(pageCode, type, detailCountry, extra);
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountry(detailCountry, normalizedLang, canonical, policy, extra));
        model.addAttribute("detail", detailCountry);
        model.addAttribute("policy", policy);
        model.addAttribute("code", pageCode);
        model.addAttribute("type", type);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("countryExtra", extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, rootExtra));
        model.addAttribute("availableTypes", detectAvailableTypes(code));
        model.addAttribute("policyDetails", buildPolicyDetails(code, detectAvailableTypes(code)));
        return "country-detail";
    }

    private Map<String, Object> buildEligibilityConfig(String code, List<String> types) {
        Map<String, Object> config = new LinkedHashMap<>();
        List<Map<String, Object>> policies = new ArrayList<>();
        for (String type : types) {
            CountryDetail detail = getCountryDetail(code, type);
            if (detail == null) continue;
            Map<String, Object> rule = new LinkedHashMap<>();
            rule.put("type", type);
            rule.put("stayDays", parseSimpleStayDays(detail.getStayDays()));
            rule.put("stayText", safe(detail.getStayDays()));
            rule.put("purposes", parsePurposes(detail.getPurpose()));
            rule.put("policyUrl", "/country/" + routeCode(code) + "/" + type);
            policies.add(rule);
        }
        config.put("policies", policies);

        Map<String, Object> transit = new LinkedHashMap<>();
        boolean transitEnabled = types.contains("transit") && transitMap.containsKey(resolver.policyKey(code));
        transit.put("enabled", transitEnabled);
        transit.put("maxStayDays", transitEnabled ? 10 : null);
        transit.put("policyUrl", "/country/" + routeCode(code) + "/transit");
        config.put("transit240", transit);

        Map<String, Object> hainan = new LinkedHashMap<>();
        hainan.put("enabled", false);
        hainan.put("maxStayDays", null);
        hainan.put("purposes", Collections.emptyList());
        config.put("hainan", hainan);
        return config;
    }

    private Integer parseSimpleStayDays(String value) {
        if (isBlank(value) || !value.trim().matches("\\d+")) return null;
        try {
            int days = Integer.parseInt(value.trim());
            return days > 0 ? days : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private List<String> parsePurposes(String value) {
        List<String> purposes = new ArrayList<>();
        if (isBlank(value)) return purposes;
        String v = value.toLowerCase(Locale.ENGLISH);
        if (v.contains("tourism") || v.contains("tourist")) purposes.add("tourism");
        if (v.contains("business") || v.contains("commercial")) purposes.add("business");
        if (v.contains("family")) purposes.add("family");
        if (v.contains("exchange")) purposes.add("exchange");
        if (v.contains("transit")) purposes.add("transit");
        return purposes;
    }

    private Map<String, Object> buildCountryProfile(String code, CountryDetail detailCountry, Map<String, Object> extra) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("code", code);
        profile.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        profile.put("tier", firstNonBlank(string(extra, "tier"), "C"));
        profile.put("heroQuestionEn", firstNonBlank(string(extra, "homeHeroQuestionEn"), "Do " + detailCountry.getName() + " citizens need a visa for China?"));
        profile.put("heroQuestionZh", firstNonBlank(string(extra, "homeHeroQuestionZh"), detailCountry.getNameZh() + "公民去中国需要签证吗？"));
        profile.put("heroAnswerEn", firstNonBlank(string(extra, "homeHeroAnswerEn"), "Your visa requirement depends on your passport, purpose, stay and the China entry policy that applies to your itinerary."));
        profile.put("heroAnswerZh", firstNonBlank(string(extra, "homeHeroAnswerZh"), "是否需要签证取决于护照、出行目的、停留时间以及适用于你行程的中国入境政策。"));
        profile.put("introEn", firstNonBlank(string(extra, "homeIntroEn"), "Use the eligibility checker first, then open the policy that matches your trip."));
        profile.put("introZh", firstNonBlank(string(extra, "homeIntroZh"), "建议先使用资格检查器，再进入与你行程相匹配的政策详情页。"));
        return profile;
    }

    private CountryPolicy buildCountryPolicy(String code, String type, CountryDetail detailCountry, Map<String, Object> extra) {
        CountryPolicy policy = new CountryPolicy();
        policy.setDetail(detailCountry);
        policy.setCountryCode(code);
        policy.setPolicyType(type);
        policy.setOfficialSource(firstNonBlank(string(extra, "officialSource"), NIA_SOURCE));
        policy.setLastVerified(firstNonBlank(string(extra, "lastVerified"), VERIFIED_DATE));
        policy.setPolicyExpiry(string(extra, "policyExpiry"));
        policy.setPermittedPurposesEn(firstNonBlank(string(extra, "permittedPurposesEn"), detailCountry.getPurpose()));
        policy.setPermittedPurposesZh(firstNonBlank(string(extra, "permittedPurposesZh"), detailCountry.getPurposeZh()));
        boolean transit = "transit".equals(type);
        boolean mutual = "mutual".equals(type);
        policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), transit ? "Valid ordinary passport of an eligible country/region" : mutual ? "Passport covered by the applicable bilateral agreement" : "Valid ordinary passport"));
        policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), transit ? "符合过境免签条件国家/地区的有效普通护照" : mutual ? "适用双边互免签证协定的护照" : "有效普通护照"));
        policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"), transit ? "Passport and transit-document requirements must meet the current transit policy." : "Passport requirements follow the applicable entry policy."));
        policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"), transit ? "护照及过境旅行证件要求须符合当前过境政策。" : "护照要求以适用入境政策为准。"));
        policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"), transit ? "Transit conditions and permitted stay follow the current 240-hour policy." : mutual ? "Entry frequency and stay limits follow the applicable bilateral agreement." : "Entry conditions and stay limits follow the current unilateral policy."));
        policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"), transit ? "过境条件及停留期限以当前240小时过境免签政策为准。" : mutual ? "入境次数及停留期限以适用双边协定为准。" : "入境条件及停留期限以当前单方面免签政策为准。"));
        policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"), transit ? "A confirmed onward itinerary to a third country or region is required." : "Check the current return or onward travel requirements for your itinerary."));
        policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"), transit ? "需要前往第三国或地区的已确认联程行程。" : "请根据实际行程核对返程或后续行程材料要求。"));
        policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"), "Keep accommodation information available if requested at entry inspection."));
        policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"), "如入境查验需要，请准备住宿信息。"));
        policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"), "Prepare trip-related supporting documents if requested."));
        policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"), "如被要求，请准备与行程相关的证明材料。"));
        policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"), "Do not assume a visa-free stay can be extended; contact the local immigration authority before expiry."));
        policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"), "不要默认免签停留可以延期；如需延长，应在期限届满前咨询当地出入境管理部门。"));
        populateFaqs(policy, detailCountry, type, extra);
        return policy;
    }

    private void populateFaqs(CountryPolicy policy, CountryDetail detailCountry, String type, Map<String, Object> extra) {
        List<CountryPolicy.PolicyFaq> en = toFaqs(extra.get("customFaqsEn"));
        List<CountryPolicy.PolicyFaq> zh = toFaqs(extra.get("customFaqsZh"));
        String stay = isBlank(detailCountry.getStayDays()) ? "the period stated by the policy" : detailCountry.getStayDays() + " days";
        String stayZh = isBlank(detailCountry.getStayDays()) ? "政策规定期限" : detailCountry.getStayDays() + "天";
        if ("transit".equals(type)) {
            addIfMissing(en, "How long can " + detailCountry.getName() + " citizens stay under this transit route?", "The current policy record allows up to " + stay + ", subject to the qualifying transit itinerary and entry record.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民过境免签可以停留多久？", "当前政策记录允许最长停留" + stayZh + "，具体以符合条件的过境行程和入境记录为准。");
        } else if ("mutual".equals(type)) {
            addIfMissing(en, "Do " + detailCountry.getName() + " citizens need a visa under this agreement?", "A visa may be waived when the passport and purpose meet the applicable bilateral agreement.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民按照互免协定去中国需要签证吗？", "满足适用双边互免签证协定条件时，可以免办签证入境。");
        } else {
            addIfMissing(en, "Can " + detailCountry.getName() + " citizens enter China without a visa?", "They may qualify when the ordinary passport, purpose and stay conditions of the current policy are met.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民可以免签进入中国吗？", "满足当前普通护照、出行目的和停留条件时，可能适用中国免签安排。");
        }
        policy.setFaqsEn(en);
        policy.setFaqsZh(zh);
    }

    private void addIfMissing(List<CountryPolicy.PolicyFaq> list, String question, String answer) {
        for (CountryPolicy.PolicyFaq faq : list) if (question.equalsIgnoreCase(faq.getQ())) return;
        list.add(new CountryPolicy.PolicyFaq(question, answer));
    }

    private List<CountryPolicy.PolicyFaq> toFaqs(Object value) {
        List<CountryPolicy.PolicyFaq> result = new ArrayList<>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> map = (Map<?, ?>) item;
            String question = string(map.get("q"));
            String answer = string(map.get("a"));
            if (!isBlank(question) && !isBlank(answer)) result.add(new CountryPolicy.PolicyFaq(question, answer));
        }
        return result;
    }

    private CountryDetail syntheticUnilateral(String key) {
        if (!CURRENT_UNILATERAL_30.contains(key)) return null;
        CountryDetail detail = new CountryDetail();
        detail.setCode(key);
        if ("kyrgyzstan".equals(key)) {
            detail.setName("Kyrgyzstan");
            detail.setNameZh("吉尔吉斯斯坦");
        } else {
            detail.setName("Vietnam");
            detail.setNameZh("越南");
        }
        detail.setContinent("Asia");
        detail.setContinentZh("亚洲");
        detail.setPolicyType("unilateral");
        detail.setPolicyTypeZh("单方面免签（中国给予）");
        detail.setStayDays("30");
        detail.setValidFrom("2026-08-20");
        detail.setPurpose("Business, tourism, family visit, exchange, transit");
        detail.setPurposeZh("经商、旅游观光、探亲访友、交流访问、过境");
        detail.setRule("Stay no more than 30 days. Period starts from 00:00 next day.");
        detail.setRuleZh("停留不超过30日，停留时间自入境次日零时起算");
        detail.setPorts("All open ports of China");
        detail.setPortsZh("全国所有对外开放口岸");
        detail.setSeoTitle(detail.getName() + " to China Visa-Free Policy 2026 | 30 Days Unilateral");
        detail.setSeoTitleZh(detail.getNameZh() + "来华免签政策2026 | 单方面30天免签");
        return detail;
    }

    private List<String> detectAvailableTypes(String code) {
        String key = resolver.policyKey(code);
        List<String> result = new ArrayList<>();
        if (unilateralMap.containsKey(key) || CURRENT_UNILATERAL_30.contains(key)) result.add("unilateral");
        if (mutualMap.containsKey(key)) result.add("mutual");
        if (transitMap.containsKey(key)) result.add("transit");
        return result;
    }

    private CountryDetail getCountryDetail(String code, String type) {
        String key = resolver.policyKey(code);
        if ("unilateral".equals(type)) {
            CountryDetail detail = unilateralMap.get(key);
            return detail != null ? detail : syntheticUnilateral(key);
        }
        if ("mutual".equals(type)) return mutualMap.get(key);
        if ("transit".equals(type)) return transitMap.get(key);
        return null;
    }

    private CountryDetail getAnyCountryDetail(String code) {
        String key = resolver.policyKey(code);
        CountryDetail detail = unilateralMap.get(key);
        if (detail != null) return detail;
        detail = mutualMap.get(key);
        if (detail != null) return detail;
        detail = transitMap.get(key);
        return detail != null ? detail : syntheticUnilateral(key);
    }

    private String routeCode(String code) { return resolver.routeCode(resolver.policyKey(code)); }

    private Map<String, Object> getCountryExtraRoot(String code) {
        Map<String, Object> map = countryExtraMap.get(code);
        return map != null ? map : countryExtraMap.get(routeCode(code));
    }

    private Object getCountryExtraItem(String code, String type) {
        Map<String, Object> map = getCountryExtraRoot(code);
        return map == null ? null : map.get(type);
    }

    private List<CountryDetail> buildAvailablePolicies(String code, List<String> types) {
        List<CountryDetail> result = new ArrayList<>();
        for (String type : types) {
            CountryDetail detail = getCountryDetail(code, type);
            if (detail != null) result.add(detail);
        }
        return result;
    }

    private Map<String, CountryDetail> buildPolicyDetails(String code, List<String> types) {
        Map<String, CountryDetail> result = new LinkedHashMap<>();
        for (String type : types) {
            CountryDetail detail = getCountryDetail(code, type);
            if (detail != null) result.put(type, detail);
        }
        return result;
    }

    private Map<String, String> buildCountryNames() {
        Map<String, String> result = new LinkedHashMap<>();
        addNames(result, unilateralMap);
        addNames(result, mutualMap);
        addNames(result, transitMap);
        return result;
    }

    private void addNames(Map<String, String> result, Map<String, CountryDetail> map) {
        for (Map.Entry<String, CountryDetail> entry : map.entrySet()) result.putIfAbsent(routeCode(entry.getKey()), entry.getValue().getName());
    }

    private List<String> buildRelatedCountryCodes(Map<String, Object> extra) {
        List<String> result = new ArrayList<>();
        if (extra == null) return result;
        Object value = extra.get("relatedCountryCodes");
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value) {
            String countryCode = string(item);
            if (!isBlank(countryCode) && getAnyCountryDetail(countryCode) != null && !result.contains(countryCode)) result.add(countryCode);
        }
        return result;
    }

    private Map<String, CountryDetail> loadCountryMap(ObjectMapper mapper, String fileName) {
        try {
            return mapper.readValue(new ClassPathResource(fileName).getInputStream(), new TypeReference<Map<String, CountryDetail>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + fileName, e);
        }
    }

    private Map<String, Map<String, Object>> loadExtraMap(ObjectMapper mapper) {
        try {
            return mapper.readValue(new ClassPathResource("country-extra.json").getInputStream(), new TypeReference<Map<String, Map<String, Object>>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load country-extra.json", e);
        }
    }

    private Map<String, Object> asMap(Object value) {
        if (!(value instanceof Map)) return new LinkedHashMap<>();
        Map<String, Object> result = new LinkedHashMap<>();
        Map<?, ?> source = (Map<?, ?>) value;
        for (Map.Entry<?, ?> entry : source.entrySet()) result.put(String.valueOf(entry.getKey()), entry.getValue());
        return result;
    }

    private String string(Map<String, Object> map, String key) { return map == null ? "" : string(map.get(key)); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String safe(String value) { return value == null ? "" : value.trim(); }
    private String firstNonBlank(String value, String fallback) { return isBlank(value) ? fallback : value; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
    private int number(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(string(value)); } catch (Exception e) { return fallback; }
    }
}