package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.entity.CountryPolicy;
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
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import java.util.*;

@Controller
public class CountryController {

    private static final String NIA_SOURCE = "https://en.nia.gov.cn/";
    private static final String VERIFIED_DATE = "2026-09-05";

    private final Map<String, CountryDetail> unilateralMap;
    private final Map<String, CountryDetail> mutualMap;
    private final Map<String, CountryDetail> transitMap;
    private final Map<String, Map<String, Object>> countryExtraMap;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;

    public CountryController(ObjectMapper objectMapper,
                             SeoService seoService,
                             StructuredDataService structuredDataService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        this.unilateralMap = loadCountryMap(objectMapper, "unilateral.json");
        this.mutualMap = loadCountryMap(objectMapper, "mutual.json");
        this.transitMap = loadCountryMap(objectMapper, "transit.json");
        this.countryExtraMap = loadExtraMap(objectMapper);
    }

    @GetMapping(value = "/country/{code}", params = "type")
    public ResponseEntity<Void> legacyCountryRedirect(
            @PathVariable String code,
            @RequestParam String type,
            @RequestParam(defaultValue = "en") String lang) {
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) return ResponseEntity.notFound().build();
        String normalizedLang = seoService.normalizeLang(lang);
        String location = UriComponentsBuilder.fromPath("/country/" + code + "/" + type)
                .queryParam("lang", normalizedLang).build().encode().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }

    @GetMapping("/country/{code}")
    public String countryHome(@PathVariable String code,
                              @RequestParam(defaultValue = "en") String lang,
                              Model model) {
        String normalizedLang = seoService.normalizeLang(lang);
        List<String> availableTypes = detectAvailableTypes(code);
        if (availableTypes.isEmpty()) return "redirect:/?lang=" + normalizedLang;

        CountryDetail detailCountry = getAnyCountryDetail(code);
        Map<String, Object> countryExtraRoot = countryExtraMap.get(code);
        String path = "/country/" + code;
        String canonicalUrl = seoService.canonical(path, normalizedLang);

        model.addAttribute("code", code);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("availableTypes", availableTypes);
        model.addAttribute("countryExtraRoot", countryExtraRoot);
        model.addAttribute("countryProfile", buildCountryProfile(code, detailCountry, countryExtraRoot));
        model.addAttribute("availablePolicies", buildAvailablePolicies(code, availableTypes));
        model.addAttribute("countryNames", buildCountryNames());
        model.addAttribute("relatedCountryCodes", buildRelatedCountryCodes(countryExtraRoot));
        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountryHome(
                detailCountry, normalizedLang, canonicalUrl, countryExtraRoot,
                availableTypes, buildPolicyDetails(code, availableTypes)));
        return "country-home";
    }

    @GetMapping("/country/{code}/{type}")
    public String restCountryDetail(@PathVariable String code,
                                    @PathVariable String type,
                                    @RequestParam(defaultValue = "en") String lang,
                                    Model model) {
        String normalizedLang = seoService.normalizeLang(lang);
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) return "redirect:/?lang=" + normalizedLang;

        String path = "/country/" + code + "/" + type;
        String canonicalUrl = seoService.canonical(path, normalizedLang);
        Map<String, Object> countryExtra = asMap(getCountryExtraItem(code, type));
        Map<String, Object> countryRoot = countryExtraMap.get(code);
        CountryPolicy policy = buildCountryPolicy(code, type, detail, countryExtra);

        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountry(
                detail, normalizedLang, canonicalUrl, policy, countryExtra));
        model.addAttribute("detail", detail);
        model.addAttribute("policy", policy);
        model.addAttribute("code", code);
        model.addAttribute("type", type);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("countryExtra", countryExtra);
        model.addAttribute("countryProfile", buildCountryProfile(code, detail, countryRoot));
        model.addAttribute("availableTypes", detectAvailableTypes(code));
        model.addAttribute("policyDetails", buildPolicyDetails(code, detectAvailableTypes(code)));
        return "country-detail";
    }

    private Map<String, Object> buildCountryProfile(String code, CountryDetail detail, Map<String, Object> extra) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("code", code);
        profile.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        profile.put("tier", firstNonBlank(string(extra, "tier"), "C"));
        profile.put("heroQuestionEn", firstNonBlank(string(extra, "homeHeroQuestionEn"),
                "Do " + detail.getName() + " citizens need a visa for China?"));
        profile.put("heroQuestionZh", firstNonBlank(string(extra, "homeHeroQuestionZh"),
                detail.getNameZh() + "公民去中国需要签证吗？"));
        profile.put("heroAnswerEn", firstNonBlank(string(extra, "homeHeroAnswerEn"),
                "Your visa requirement depends on the passport, travel purpose, stay and the China entry policy that applies to your itinerary."));
        profile.put("heroAnswerZh", firstNonBlank(string(extra, "homeHeroAnswerZh"),
                "是否需要签证取决于护照、出行目的、停留时间以及当前适用于你行程的中国入境政策。"));
        profile.put("introEn", firstNonBlank(string(extra, "homeIntroEn"),
                "Use the eligibility checker first, then open the policy that matches your trip."));
        profile.put("introZh", firstNonBlank(string(extra, "homeIntroZh"),
                "建议先使用资格检查器，再进入与你行程相匹配的政策详情页。"));
        return profile;
    }

    private int number(Object value, int fallback) {
        if (value instanceof Number) return ((Number) value).intValue();
        try { return Integer.parseInt(string(value)); } catch (Exception ignored) { return fallback; }
    }

    private CountryPolicy buildCountryPolicy(String code, String type, CountryDetail detail, Map<String, Object> extra) {
        CountryPolicy policy = new CountryPolicy();
        policy.setDetail(detail);
        policy.setCountryCode(code);
        policy.setPolicyType(type);
        policy.setOfficialSource(firstNonBlank(string(extra, "officialSource"), NIA_SOURCE));
        policy.setLastVerified(firstNonBlank(string(extra, "lastVerified"), VERIFIED_DATE));
        policy.setPolicyExpiry(firstNonBlank(string(extra, "policyExpiry"), ""));
        policy.setPermittedPurposesEn(firstNonBlank(string(extra, "permittedPurposesEn"), detail.getPurpose()));
        policy.setPermittedPurposesZh(firstNonBlank(string(extra, "permittedPurposesZh"), detail.getPurposeZh()));

        if ("unilateral".equals(type)) {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), "Valid ordinary passport"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), "有效普通护照"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"), "Passport validity must meet China's entry inspection requirements."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"), "护照有效期需符合中国入境查验及适用免签政策要求。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"), "Entry conditions and frequency follow the applicable unilateral visa-free policy."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"), "入境条件及次数以适用的单方面免签政策为准。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"), "Check the current entry requirements for any return or onward itinerary documentation."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"), "如行程涉及返程或联程行程，请按当前入境要求准备相关凭证。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"), "Keep your accommodation information available if requested during entry inspection."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"), "如入境查验需要，请准备住宿信息或相关凭证。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"), "Prepare evidence relevant to your trip if an authority requests it."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"), "如有关机关要求，请准备与行程相关的证明材料。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"), "If you need to stay longer, contact the local immigration authority before the permitted stay expires."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"), "如需延长停留，请在允许停留期限届满前向当地出入境管理部门咨询并申请。"));
        } else if ("mutual".equals(type)) {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), "Passport or travel document covered by the applicable bilateral agreement"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), "适用双边互免签证协定的护照或旅行证件"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"), "Passport requirements follow the applicable bilateral visa-exemption agreement."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"), "护照要求以适用的双边互免签证协定为准。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"), "Entry frequency and stay limits follow the applicable bilateral agreement."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"), "入境次数及停留期限以适用双边协定为准。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"), "Travel-document checks depend on the applicable agreement and itinerary."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"), "返程或联程材料要求以适用协定及实际行程为准。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"), "Keep accommodation information available for entry and local registration requirements."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"), "请准备住宿信息，并遵守在华住宿登记要求。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"), "Prepare trip-related supporting documents if requested."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"), "如被要求，请准备与行程相关的证明材料。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"), "Extension follows the applicable bilateral agreement and Chinese immigration rules."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"), "延期以适用双边协定及中国出入境管理规定为准。"));
        } else {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), "Valid passport of an eligible country/region under China's transit visa-free policy"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), "符合中国过境免签政策条件国家/地区的有效护照"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"), "Passport must satisfy the applicable transit visa-free requirements."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"), "护照需符合适用过境免签政策要求。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"), "Transit entry conditions and permitted stay follow the current transit visa-free policy."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"), "过境入境条件及允许停留期限以当前过境免签政策为准。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"), "A confirmed onward itinerary meeting the transit policy is required."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"), "需要持有符合过境免签政策要求的已确认联程行程。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"), "Accommodation and travel-area requirements depend on the applicable transit rules."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"), "住宿及停留区域要求以适用过境规则为准。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"), "Keep trip-supporting documents available if requested during transit inspection."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"), "过境查验时如被要求，请准备与行程相关的证明材料。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"), "Transit visa-free stay is governed by the current transit policy and should not be assumed extendable."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"), "过境免签停留受当前政策约束，不应默认可以延期。"));
        }
        populateFaqs(policy, detail, type, extra);
        return policy;
    }

    private void populateFaqs(CountryPolicy policy, CountryDetail detail, String type, Map<String, Object> extra) {
        List<CountryPolicy.PolicyFaq> en = toFaqs(extra.get("customFaqsEn"));
        List<CountryPolicy.PolicyFaq> zh = toFaqs(extra.get("customFaqsZh"));
        String stay = detail.getStayDays();
        String stayTextEn = isBlank(stay) ? "the period stated by the policy" : stay + " days";
        String stayTextZh = isBlank(stay) ? "政策规定期限" : stay + "天";
        String name = detail.getName();
        String nameZh = detail.getNameZh();
        if ("transit".equals(type)) {
            addIfMissing(en, "How long can " + name + " citizens stay in China under this transit policy?", "The permitted transit stay is " + stayTextEn + ", subject to the current transit rules and entry record.");
            addIfMissing(en, "What is the key requirement for China transit visa-free entry?", "The itinerary must satisfy the applicable transit visa-free conditions, including the required onward travel arrangement.");
            addIfMissing(zh, nameZh + "公民过境免签可以在中国停留多久？", "允许停留期限为" + stayTextZh + "，具体以当前过境政策及入境记录为准。");
            addIfMissing(zh, "中国过境免签最关键的条件是什么？", "行程必须符合适用的过境免签条件，包括规定的后续行程安排。 ");
        } else if ("mutual".equals(type)) {
            addIfMissing(en, "Do " + name + " citizens need a visa for China under this agreement?", "The applicable bilateral visa-exemption agreement may waive the visa requirement when its conditions are met.");
            addIfMissing(en, "How long can " + name + " citizens stay in China?", "The maximum stay is " + stayTextEn + " under the applicable policy record.");
            addIfMissing(zh, nameZh + "公民按照互免协定去中国需要签证吗？", "满足适用双边互免签证协定条件时，可以免办签证入境。");
            addIfMissing(zh, nameZh + "公民免签来华可以停留多久？", "按照当前政策记录，最长停留为" + stayTextZh + "。");
        } else {
            addIfMissing(en, "Can " + name + " citizens enter China without a visa?", "They may qualify for the current unilateral visa-free arrangement when the published passport, purpose and stay conditions are met.");
            addIfMissing(en, "How long can " + name + " citizens stay in China without a visa?", "The current policy record allows a stay of " + stayTextEn + ".");
            addIfMissing(zh, nameZh + "公民可以免签进入中国吗？", "在符合当前公布的护照、出行目的和停留条件时，可能适用中国单方面免签安排。");
            addIfMissing(zh, nameZh + "公民免签来中国可以停留多久？", "当前政策记录显示最长停留为" + stayTextZh + "。");
        }
        policy.setFaqsEn(en);
        policy.setFaqsZh(zh);
    }

    private void addIfMissing(List<CountryPolicy.PolicyFaq> list, String q, String a) {
        for (CountryPolicy.PolicyFaq faq : list) if (q.equalsIgnoreCase(faq.getQ())) return;
        list.add(new CountryPolicy.PolicyFaq(q, a));
    }

    private List<CountryPolicy.PolicyFaq> toFaqs(Object value) {
        List<CountryPolicy.PolicyFaq> result = new ArrayList<>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            Map<?, ?> m = (Map<?, ?>) item;
            String q = string(m.get("q"));
            String a = string(m.get("a"));
            if (!isBlank(q) && !isBlank(a)) result.add(new CountryPolicy.PolicyFaq(q, a));
        }
        return result;
    }

    private Map<String, String> buildCountryNames() {
        Map<String, String> result = new HashMap<>();
        unilateralMap.forEach((k, v) -> result.putIfAbsent(k, v.getName()));
        mutualMap.forEach((k, v) -> result.putIfAbsent(k, v.getName()));
        transitMap.forEach((k, v) -> result.putIfAbsent(k, v.getName()));
        return result;
    }

    private List<String> buildRelatedCountryCodes(Map<String, Object> extra) {
        List<String> result = new ArrayList<>();
        if (extra == null) return result;
        Object value = extra.get("relatedCountryCodes");
        if (!(value instanceof List)) return result;
        Set<String> validCodes = new HashSet<>();
        validCodes.addAll(unilateralMap.keySet());
        validCodes.addAll(mutualMap.keySet());
        validCodes.addAll(transitMap.keySet());
        for (Object item : (List<?>) value) {
            String code = string(item);
            if (!isBlank(code) && validCodes.contains(code) && !result.contains(code)) result.add(code);
        }
        return result;
    }

    private List<CountryDetail> buildAvailablePolicies(String code, List<String> types) {
        List<CountryDetail> result = new ArrayList<>();
        for (String type : types) {
            CountryDetail d = getCountryDetail(code, type);
            if (d != null) result.add(d);
        }
        return result;
    }

    private Map<String, CountryDetail> buildPolicyDetails(String code, List<String> types) {
        Map<String, CountryDetail> result = new LinkedHashMap<>();
        for (String type : types) {
            CountryDetail d = getCountryDetail(code, type);
            if (d != null) result.put(type, d);
        }
        return result;
    }

    private CountryDetail getCountryDetail(String code, String type) {
        if ("unilateral".equals(type)) return unilateralMap.get(code);
        if ("mutual".equals(type)) return mutualMap.get(code);
        if ("transit".equals(type)) return transitMap.get(code);
        return null;
    }

    private Object getCountryExtraItem(String code, String type) {
        Map<String, Object> node = countryExtraMap.get(code);
        return node == null ? null : node.get(type);
    }

    private List<String> detectAvailableTypes(String code) {
        List<String> result = new ArrayList<>();
        if (unilateralMap.containsKey(code)) result.add("unilateral");
        if (mutualMap.containsKey(code)) result.add("mutual");
        if (transitMap.containsKey(code)) result.add("transit");
        return result;
    }

    private CountryDetail getAnyCountryDetail(String code) {
        CountryDetail d = unilateralMap.get(code);
        if (d != null) return d;
        d = mutualMap.get(code);
        if (d != null) return d;
        return transitMap.get(code);
    }

    private Map<String, CountryDetail> loadCountryMap(ObjectMapper mapper, String file) {
        try {
            return mapper.readValue(new ClassPathResource(file).getInputStream(), new TypeReference<Map<String, CountryDetail>>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + file, e);
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
        if (value instanceof Map) {
            Map<String, Object> result = new LinkedHashMap<>();
            ((Map<?, ?>) value).forEach((k, v) -> result.put(String.valueOf(k), v));
            return result;
        }
        return new LinkedHashMap<>();
    }

    private String string(Map<String, Object> map, String key) { return map == null ? "" : string(map.get(key)); }
    private String string(Object value) { return value == null ? "" : String.valueOf(value).trim(); }
    private String firstNonBlank(String value, String fallback) { return isBlank(value) ? fallback : value; }
    private boolean isBlank(String value) { return value == null || value.trim().isEmpty(); }
}