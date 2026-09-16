package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.CountryEligibilityService;
import com.chinavisamap.service.CountryFlagService;
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
import java.util.stream.Collectors;

@Controller
public class CountryController {
    private static final String NIA_SOURCE = "https://en.nia.gov.cn/";
    private static final String VERIFIED_DATE = "2026-09-13";

    private final Map<String, CountryDetail> unilateralMap;
    private final Map<String, CountryDetail> mutualMap;
    private final Map<String, CountryDetail> transitMap;
    private final Map<String, CountryDetail> hainanMap;
    private final Map<String, Map<String, Object>> countryExtraMap;
    private final Map<String, Map<String, Object>> countryIntentMap;
    private final List<Map<String, Object>> articles;
    private final CountryCodeResolver resolver;
    private final CountryEligibilityService eligibilityService;
    private final CountryFlagService flagService;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;

    public CountryController(ObjectMapper mapper, CountryCodeResolver resolver,
                             CountryEligibilityService eligibilityService, CountryFlagService flagService,
                             SeoService seoService, StructuredDataService structuredDataService) {
        this.resolver = resolver;
        this.eligibilityService = eligibilityService;
        this.flagService = flagService;
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        this.unilateralMap = loadCountryMap(mapper, "unilateral.json");
        this.mutualMap = loadCountryMap(mapper, "mutual.json");
        this.transitMap = loadCountryMap(mapper, "transit.json");
        this.hainanMap = loadCountryMap(mapper, "hainan.json");
        this.countryExtraMap = loadExtraMap(mapper);
        this.countryIntentMap = loadIntentMap(mapper);
        this.articles = loadArticles(mapper);
    }

    @GetMapping(value = "/country/{code}", params = "type")
    public ResponseEntity<Void> legacyCountryRedirect(@PathVariable String code, @RequestParam String type,
                                                       @RequestParam(defaultValue = "en") String lang) {
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) {
            // Preserve old indexed unilateral URLs for countries whose old synthetic
            // policy record has been removed: redirect to the real country page.
            if ("unilateral".equalsIgnoreCase(type) && getAnyCountryDetail(code) != null) {
                return redirectToCountryHome(code, lang);
            }
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
        Map<String, Object> intentExtra = countryIntentMap.get(pageCode);
        Map<String, Object> countryContent = mergeContent(extra, intentExtra);
        String path = "/country/" + pageCode;
        String canonical = seoService.canonical(path, normalizedLang);
        model.addAttribute("code", pageCode);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("countryFlag", flagService.flag(pageCode));
        model.addAttribute("availableTypes", types);
        model.addAttribute("countryExtraRoot", countryContent);
        Map<String, Object> countryIntent = buildCountryIntent(pageCode, detailCountry, types, availablePoliciesFor(code, types), extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, extra));
        model.addAttribute("countryIntent", countryIntent);
        model.addAttribute("lastVerified", latestVerified(code, types, countryContent));
        model.addAttribute("countrySeoTitleEn", countryIntent.get("seoTitleEn"));
        model.addAttribute("countrySeoTitleZh", countryIntent.get("seoTitleZh"));
        model.addAttribute("countrySeoDescEn", countryIntent.get("seoDescEn"));
        model.addAttribute("countrySeoDescZh", countryIntent.get("seoDescZh"));
        List<CountryDetail> availablePolicies = buildAvailablePolicies(code, types);
        model.addAttribute("relatedArticles", findRelatedArticles(detailCountry, pageCode, normalizedLang));
        model.addAttribute("availablePolicies", availablePolicies);
        model.addAttribute("routeChecks", buildRouteChecks(pageCode, detailCountry, types, normalizedLang));
        model.addAttribute("routeCountText", buildRouteCountText(detailCountry, types, normalizedLang));
        model.addAttribute("directDecisionYes", types.contains("unilateral") || types.contains("mutual"));
        model.addAttribute("countryDecisionTitle", buildCountryDecisionTitle(detailCountry, types, normalizedLang));
        model.addAttribute("countryDecisionText", buildCountryDecisionText(detailCountry, types, normalizedLang));
        model.addAttribute("countryNames", buildCountryNames());
        model.addAttribute("countryFlags", buildCountryFlags());
        model.addAttribute("relatedCountryCodes", buildRelatedCountryCodes(countryContent));
        model.addAttribute("eligibilityConfig", eligibilityService.build(pageCode, availablePolicies, extra));
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        Map<String,Object> schemaExtra = new LinkedHashMap<>(); if(countryContent!=null) schemaExtra.putAll(countryContent);
        schemaExtra.put(normalizedLang.equals("en")?"homeCustomFaqsEn":"homeCustomFaqsZh", countryIntent.get(normalizedLang.equals("en")?"faqEn":"faqZh"));
        schemaExtra.put("lastVerified", latestVerified(code, types, countryContent));
        model.addAttribute("structuredData", structuredDataService.buildCountryHome(detailCountry, normalizedLang, canonical, schemaExtra, types, buildPolicyDetails(code, types)));
        return "country-home";
    }

    @GetMapping("/country/{code}/{type}")
    public Object restCountryDetail(@PathVariable String code, @PathVariable String type,
                                    @RequestParam(defaultValue = "en") String lang, Model model) {
        String normalizedLang = seoService.normalizeLang(lang);
        CountryDetail detailCountry = getCountryDetail(code, type);
        if (detailCountry == null) {
            // A legacy synthetic unilateral route must never be presented as a real policy.
            // Keep the URL alive with a permanent redirect to the country decision page.
            if ("unilateral".equalsIgnoreCase(type) && getAnyCountryDetail(code) != null) {
                return redirectView(code, normalizedLang);
            }
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Country policy not found");
        }
        String pageCode = routeCode(code);
        String path = "/country/" + pageCode + "/" + type;
        String canonical = seoService.canonical(path, normalizedLang);
        Map<String, Object> extra = asMap(getCountryExtraItem(code, type));
        Map<String, Object> rootExtra = getCountryExtraRoot(code);
        CountryPolicy policy = buildCountryPolicy(pageCode, type, detailCountry, extra);
        Map<String,Object> policyIntent = buildPolicyIntent(pageCode, detailCountry, type, policy, rootExtra);
        Map<String,Object> schemaExtra = new LinkedHashMap<>(extra);
        schemaExtra.put("policySeoTitleEn", policyIntent.get("seoTitleEn"));
        schemaExtra.put("policySeoTitleZh", policyIntent.get("seoTitleZh"));
        schemaExtra.put("policySeoDescEn", policyIntent.get("seoDescEn"));
        schemaExtra.put("policySeoDescZh", policyIntent.get("seoDescZh"));
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountry(detailCountry, normalizedLang, canonical, policy, schemaExtra));
        model.addAttribute("detail", detailCountry);
        model.addAttribute("policy", policy);
        model.addAttribute("code", pageCode);
        model.addAttribute("type", type);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("countryFlag", flagService.flag(pageCode));
        model.addAttribute("countryExtra", extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, rootExtra));
        model.addAttribute("countryIntent", policyIntent);
        model.addAttribute("relatedArticles", findRelatedArticles(detailCountry, pageCode, normalizedLang));
        List<String> availableTypes = detectAvailableTypes(code);
        model.addAttribute("availableTypes", availableTypes);
        model.addAttribute("policyDetails", buildPolicyDetails(code, availableTypes));
        model.addAttribute("policyNavItems", buildPolicyNavItems(pageCode, availableTypes, normalizedLang));
        model.addAttribute("checkerUrl", "/country/" + pageCode + "?lang=" + normalizedLang + "#eligibility-checker");
        model.addAttribute("policyDecisionEn", policyDecision(detailCountry, type, policy, false));
        model.addAttribute("policyDecisionZh", policyDecision(detailCountry, type, policy, true));
        model.addAttribute("policyDecisionTone", policyDecisionTone(type));
        List<Map<String,Object>> otherRoutes = buildOtherRoutes(pageCode, detailCountry, availableTypes, type, normalizedLang);
        model.addAttribute("otherRoutes", otherRoutes);
        model.addAttribute("otherRoutesTitle", buildOtherRoutesTitle(detailCountry, otherRoutes.size(), normalizedLang));
        model.addAttribute("otherRoutesIntro", buildOtherRoutesIntro(detailCountry, otherRoutes.size(), normalizedLang));
        model.addAttribute("countryOverviewUrl", "/country/" + pageCode + "?lang=" + normalizedLang);
        model.addAttribute("visaGuideUrl", "/visa-guide?lang=" + normalizedLang);
        model.addAttribute("homeUrl", "/?lang=" + normalizedLang);
        model.addAttribute("policyExplainText", normalizedLang.equals("en") ? string(extra, "policyExplainEn") : string(extra, "policyExplainZh"));
        model.addAttribute("hasPolicyExplain", !isBlank(normalizedLang.equals("en") ? string(extra, "policyExplainEn") : string(extra, "policyExplainZh")));
        model.addAttribute("policyFaqList", normalizedLang.equals("en") ? policy.getFaqsEn() : policy.getFaqsZh());
        model.addAttribute("policyFaqsAvailable", normalizedLang.equals("en") ? policy.getFaqsEn() != null && !policy.getFaqsEn().isEmpty() : policy.getFaqsZh() != null && !policy.getFaqsZh().isEmpty());
        model.addAttribute("decisionLabel", "YES");
        model.addAttribute("decisionClass", "yes-direct".equals(policyDecisionTone(type)) ? "pd-answer-direct" : "pd-answer-conditional");
        model.addAttribute("decisionNote", "yes-direct".equals(policyDecisionTone(type))
                ? (normalizedLang.equals("en") ? "This is the direct visa-free route listed for this passport." : "这是当前该护照对应的直接免签路径。")
                : (normalizedLang.equals("en") ? "This route exists, but you must meet the specific conditions below." : "这条路径确实存在，但需要满足下方列出的具体条件。"));
        model.addAttribute("detailPolicyTypeText", normalizedLang.equals("en") ? detailCountry.getPolicyType() : detailCountry.getPolicyTypeZh());
        model.addAttribute("detailPurposeText", normalizedLang.equals("en") ? detailCountry.getPurpose() : detailCountry.getPurposeZh());
        model.addAttribute("detailStayText", "mutual".equals(type) ? (normalizedLang.equals("en") ? "See agreement" : "以协定为准") : String.valueOf(detailCountry.getStayDays()) + (normalizedLang.equals("en") ? " days" : " 天"));
        model.addAttribute("detailRuleText", normalizedLang.equals("en") ? detailCountry.getRule() : detailCountry.getRuleZh());
        String notesText = normalizedLang.equals("en") ? detailCountry.getNotes() : detailCountry.getNotesZh();
        String portsText = normalizedLang.equals("en") ? detailCountry.getPorts() : detailCountry.getPortsZh();
        model.addAttribute("detailNotesText", notesText);
        model.addAttribute("hasDetailNotes", !isBlank(notesText));
        model.addAttribute("detailPortsText", portsText);
        model.addAttribute("hasDetailPorts", !isBlank(portsText));
        return "country-detail";
    }


    private List<Map<String,Object>> buildPolicyNavItems(String code, List<String> types, String lang) {
        List<Map<String,Object>> result = new ArrayList<>();
        for (String t : types) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("type", t);
            item.put("url", "/country/" + code + "/" + t + "?lang=" + lang);
            if ("unilateral".equals(t)) item.put("label", "en".equals(lang) ? "Direct visa-free" : "直接免签");
            else if ("mutual".equals(t)) item.put("label", "en".equals(lang) ? "Mutual exemption" : "互免签证");
            else if ("hainan".equals(t)) item.put("label", "en".equals(lang) ? "Hainan 30-day visa-free" : "海南30天免签");
            else item.put("label", "en".equals(lang) ? "240-hour transit" : "240小时过境免签");
            result.add(item);
        }
        return result;
    }

    private String buildCountryDecisionTitle(CountryDetail country, List<String> types, String lang) {
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        boolean direct = types.contains("unilateral") || types.contains("mutual");
        return "en".equals(lang)
                ? (direct ? n + " passport holders can enter China visa-free" : n + " passport holders: no general direct visa-free entry")
                : (direct ? n + "护照可以免签进入中国" : n + "护照目前不能直接免签进入中国");
    }

    private String buildCountryDecisionText(CountryDetail country, List<String> types, String lang) {
        int count = types.size();
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        if ("en".equals(lang)) {
            if (types.contains("unilateral") || types.contains("mutual")) return "A direct visa-free route is listed for " + n + ". " + count + " route" + (count == 1 ? " is" : "s are") + " currently available to check below.";
            return "There is no general direct visa-free route listed for " + n + ". " + count + " other route" + (count == 1 ? " is" : "s are") + " currently available to check below.";
        }
        if (types.contains("unilateral") || types.contains("mutual")) return n + "目前有直接免签政策记录。下面列出 " + count + " 种可核对的入境路径。";
        return n + "目前没有一般性的直接免签政策记录，但下面仍有 " + count + " 种可核对的入境路径。";
    }

    private String policyDecision(CountryDetail country, String type, CountryPolicy policy, boolean zh) {
        String n = zh ? country.getNameZh() : country.getName();
        if ("unilateral".equals(type)) {
            return zh ? "符合当前单方面免签条件的普通护照持有人，可以按这一路径免签入境中国。"
                      : "Eligible ordinary-passport holders can use this unilateral visa-free route under the current conditions.";
        }
        if ("mutual".equals(type)) {
            return zh ? "符合中外互免签证协定条件的护照持有人，可以按协定免签入境。"
                      : "Passport holders covered by the bilateral agreement can enter visa-free when the agreement conditions are met.";
        }
        if ("hainan".equals(type)) {
            return zh ? "符合条件的" + n + "普通护照持有人可以使用海南30天区域免签，但该路径仅适用于海南。"
                      : "Eligible " + n + " ordinary-passport holders can use the 30-day Hainan regional visa-free route, which is limited to Hainan.";
        }
        return zh ? "符合条件的" + n + "护照持有人可以使用240小时过境免签，但必须满足规定的过境联程行程条件。"
                  : "Eligible " + n + " passport holders can use the 240-hour transit route if the required onward transit itinerary conditions are met.";
    }

    private String policyDecisionTone(String type) {
        return "unilateral".equals(type) || "mutual".equals(type) ? "yes-direct" : "yes-conditional";
    }

    private List<Map<String,Object>> buildRouteChecks(String code, CountryDetail country, List<String> types, String lang) {
        List<Map<String,Object>> result = new ArrayList<>();
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        for (String t : types) {
            Map<String,Object> item = new LinkedHashMap<>();
            item.put("type", t);
            item.put("url", "/country/" + code + "/" + t + "?lang=" + lang);
            if ("unilateral".equals(t)) {
                item.put("label", "en".equals(lang) ? "Direct visa-free entry" : "直接免签入境");
            } else if ("mutual".equals(t)) {
                item.put("label", "en".equals(lang) ? "Bilateral visa exemption" : "双边互免签证");
            } else if ("transit".equals(t)) {
                item.put("label", "en".equals(lang) ? "240-hour transit visa-free" : "240小时过境免签");
            } else {
                item.put("label", "en".equals(lang) ? "Hainan 30-day regional visa-free" : "海南30天区域免签");
            }
            result.add(item);
        }
        return result;
    }

    private String buildRouteCountText(CountryDetail country, List<String> types, String lang) {
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        int count = types.size();
        return "en".equals(lang)
                ? n + " passport holders currently have " + count + " route" + (count == 1 ? "" : "s") + " to check:"
                : n + "护照目前有 " + count + " 种可以核对的入境路径：";
    }

    private String buildOtherRoutesTitle(CountryDetail country, int count, String lang) {
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        if (count <= 0) return "en".equals(lang) ? "This is the route for your current question" : "当前问题对应的就是这条路径";
        if (count == 1) return "en".equals(lang) ? n + " passport holders also have another route to check" : n + "护照还有另一条路径可以核对";
        return "en".equals(lang) ? n + " passport holders also have " + count + " other routes to check" : n + "护照还有 " + count + " 条路径可以核对";
    }

    private String buildOtherRoutesIntro(CountryDetail country, int count, String lang) {
        if (count <= 0) return "";
        return "en".equals(lang)
                ? "If this route does not match your itinerary, these are the other routes currently listed for this passport."
                : "如果当前这条路径和你的行程不完全匹配，下面就是这个护照目前还能核对的其他路径。";
    }

    private List<Map<String,Object>> buildOtherRoutes(String code, CountryDetail country, List<String> types,
                                                       String currentType, String lang) {
        List<Map<String,Object>> result = new ArrayList<>();
        String n = "en".equals(lang) ? country.getName() : country.getNameZh();
        for (String t : types) {
            if (t.equals(currentType)) continue;
            Map<String,Object> route = new LinkedHashMap<>();
            route.put("type", t);
            route.put("url", "/country/" + code + "/" + t + "?lang=" + lang);
            if ("unilateral".equals(t)) {
                route.put("title", "en".equals(lang) ? "Direct visa-free entry" : "直接免签入境");
                route.put("desc", "en".equals(lang) ? "Up to " + safeDays(countryForType(code, t)) + " days, subject to the listed purposes and conditions."
                                                     : "最长" + safeDays(countryForType(code, t)) + "天，具体以该政策的事由和条件为准。");
            } else if ("mutual".equals(t)) {
                route.put("title", "en".equals(lang) ? "Bilateral visa exemption" : "双边互免签证");
                route.put("desc", "en".equals(lang) ? "Visa-free entry under the applicable China–" + n + " agreement; check passport and purpose conditions."
                                                     : "按中国与" + n + "适用的互免签证协定执行，需核对护照类别和出行事由。");
            } else if ("transit".equals(t)) {
                route.put("title", "en".equals(lang) ? "240-hour transit visa-free" : "240小时过境免签");
                route.put("desc", "en".equals(lang) ? "Up to 10 days when the qualifying onward itinerary and port conditions are met."
                                                     : "符合规定的后续前往第三国或地区行程及口岸条件时，最长10天。");
            } else if ("hainan".equals(t)) {
                route.put("title", "en".equals(lang) ? "Hainan 30-day visa-free" : "海南30天区域免签");
                route.put("desc", "en".equals(lang) ? "A separate regional route for eligible ordinary-passport holders; it does not cover mainland China outside Hainan."
                                                     : "符合条件的普通护照可使用独立的海南区域免签；不等于海南以外中国大陆地区也可免签。");
            }
            result.add(route);
        }
        return result;
    }

    private String safeDays(CountryDetail d) {
        return d == null || isBlank(d.getStayDays()) ? "the listed" : d.getStayDays();
    }

    private CountryDetail countryForType(String code, String type) {
        return getCountryDetail(code, type);
    }

    private String latestVerified(String code, List<String> types, Map<String,Object> fallback) {
        String latest = string(fallback, "lastVerified");
        for (String type : types) {
            Map<String,Object> item = asMap(getCountryExtraItem(code, type));
            String value = string(item, "lastVerified");
            if (!isBlank(value) && value.compareTo(latest) > 0) latest = value;
        }
        return isBlank(latest) ? VERIFIED_DATE : latest;
    }

    private ResponseEntity<Void> redirectToCountryHome(String code, String lang) {
        String location = UriComponentsBuilder.fromPath("/country/" + routeCode(code))
                .queryParam("lang", seoService.normalizeLang(lang)).build().encode().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }

    private ResponseEntity<Void> redirectView(String code, String lang) {
        String location = UriComponentsBuilder.fromPath("/country/" + routeCode(code))
                .queryParam("lang", lang).build().encode().toUriString();
        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }

    private List<CountryDetail> availablePoliciesFor(String code, List<String> types) {
        return buildAvailablePolicies(code, types);
    }

    private Map<String,Object> buildCountryIntent(String code, CountryDetail country, List<String> types,
                                                   List<CountryDetail> policies, Map<String,Object> extra) {
        Map<String,Object> m = new LinkedHashMap<>();
        Map<String,Object> custom = countryIntentMap.get(routeCode(code));
        if (custom == null) custom = countryIntentMap.get(resolver.policyKey(code));
        if (custom != null) m.putAll(custom);
        String enName=country.getName(), zhName=country.getNameZh();
        boolean direct=types.contains("unilateral")||types.contains("mutual"), transit=types.contains("transit"), hainan=types.contains("hainan");
        String routeEn = routeSummaryEn(types), routeZh = routeSummaryZh(types);
        m.put("seoTitleEn", firstNonBlank(string(m,"seoTitleEn"), firstNonBlank(string(extra,"homeSeoTitleEn"), enName+" to China Visa Guide 2026 | Visa-Free & Entry Rules")));
        m.put("seoTitleZh", firstNonBlank(string(m,"seoTitleZh"), firstNonBlank(string(extra,"homeSeoTitleZh"), zhName+"来华签证与免签指南 2026 | 入境政策与资格查询")));
        m.put("seoDescEn", firstNonBlank(string(m,"seoDescEn"), firstNonBlank(string(extra,"homeSeoDescEn"), enName+" citizens: check whether a China visa is required, available visa-free routes, stay limits, permitted purposes and transit options.")));
        m.put("seoDescZh", firstNonBlank(string(m,"seoDescZh"), firstNonBlank(string(extra,"homeSeoDescZh"), zhName+"公民来华查询是否需要签证、可用免签路径、最长停留、允许事由及过境政策。")));
        m.put("intentSummaryEn", firstNonBlank(string(m,"intentSummaryEn"), "For "+enName+" passport holders, the main question is which China entry route matches the actual itinerary. "+routeEn));
        m.put("intentSummaryZh", firstNonBlank(string(m,"intentSummaryZh"), "对于"+zhName+"护照持有人，最重要的是先判断实际行程对应哪一条中国入境路径。"+routeZh));
        m.put("direct", direct); m.put("transit", transit); m.put("hainan", hainan);
        m.put("homeHeroQuestionEn", firstNonBlank(string(m,"homeHeroQuestionEn"), enName + " passport: can I enter China without a visa?"));
        m.put("homeHeroQuestionZh", firstNonBlank(string(m,"homeHeroQuestionZh"), zhName + "护照来中国免签吗？"));
        m.put("homeHeroAnswerEn", buildHomeDecisionAnswer(enName, types, false));
        m.put("homeHeroAnswerZh", buildHomeDecisionAnswer(zhName, types, true));
        // One country-home FAQ source only. Prefer curated country-intent/extra content;
        // generate intent FAQs only when no curated list exists. This prevents the old
        // "Country-specific questions" + "Common questions" duplication.
        if (!(m.get("faqEn") instanceof List)) {
            List<Map<String,Object>> curated = toFaqObjects(extra == null ? null : extra.get("homeCustomFaqsEn"));
            m.put("faqEn", curated.isEmpty() ? buildIntentFaqs(country, types, policies, false) : curated);
        }
        if (!(m.get("faqZh") instanceof List)) {
            List<Map<String,Object>> curated = toFaqObjects(extra == null ? null : extra.get("homeCustomFaqsZh"));
            m.put("faqZh", curated.isEmpty() ? buildIntentFaqs(country, types, policies, true) : curated);
        }
        m.put("faqEn", dedupeFaqs(faqObjects(m.get("faqEn")), 5));
        m.put("faqZh", dedupeFaqs(faqObjects(m.get("faqZh")), 5));
        m.put("checksEn", buildTravelChecks(country, types, policies, false));
        m.put("checksZh", buildTravelChecks(country, types, policies, true));
        return m;
    }

    private Map<String,Object> buildPolicyIntent(String code, CountryDetail country, String type, CountryPolicy policy, Map<String,Object> rootExtra) {
        Map<String,Object> m=new LinkedHashMap<>();
        String n=country.getName(), z=country.getNameZh();
        m.put("intentSummaryEn", policyIntentSummary(country,type,policy,false));
        m.put("intentSummaryZh", policyIntentSummary(country,type,policy,true));
        m.put("faqEn", dedupeFaqs(faqObjects(policy.getFaqsEn()), 6)); m.put("faqZh", dedupeFaqs(faqObjects(policy.getFaqsZh()), 6));
        String defaultTitleEn;
        String defaultTitleZh;
        String defaultDescEn;
        String defaultDescZh;
        if ("transit".equals(type)) {
            defaultTitleEn = n + " China 240-Hour Transit Visa-Free | Rules & Requirements";
            defaultTitleZh = z + "中国240小时过境免签 | 规则与入境要求";
            defaultDescEn = n + " citizens: check China 240-hour transit visa-free eligibility, onward itinerary, passport, eligible ports and stay conditions.";
            defaultDescZh = z + "公民查询中国240小时过境免签资格、后续行程、护照、适用口岸及停留条件。";
        } else if ("hainan".equals(type)) {
            defaultTitleEn = n + " to Hainan Visa-Free | 30-Day Rules & Requirements";
            defaultTitleZh = z + "赴海南免签 | 30天政策规则与入境要求";
            defaultDescEn = n + " citizens: check the 30-day Hainan regional visa-free route, permitted purposes, passport and activity restrictions.";
            defaultDescZh = z + "公民查询海南30天区域免签、允许事由、护照要求及活动范围限制。";
        } else if ("mutual".equals(type)) {
            defaultTitleEn = n + " China Visa-Free Agreement 2026 | Stay & Entry Rules";
            defaultTitleZh = z + "中国互免签证协定 2026 | 停留与入境规则";
            defaultDescEn = n + " citizens: check the China visa-exemption agreement, passport type, permitted purpose, stay limit and entry conditions.";
            defaultDescZh = z + "公民查询中外互免签证协定、护照类型、允许事由、停留期限及入境条件。";
        } else {
            defaultTitleEn = n + " to China Visa-Free 2026 | Stay, Purpose & Entry Rules";
            defaultTitleZh = z + "来华免签 2026 | 停留、事由与入境规则";
            defaultDescEn = n + " citizens: check China visa-free eligibility, permitted purposes, maximum stay and entry conditions for this route.";
            defaultDescZh = z + "公民查询本项来华免签资格、允许事由、最长停留期限及入境条件。";
        }
        Map<String,Object> priority = countryIntentMap.get(resolver.policyKey(code));
        Object policySeo = priority == null ? null : priority.get("policySeo");
        String priorityTitleEn = "", priorityTitleZh = "";
        if (policySeo instanceof Map) {
            Object item = ((Map<?,?>) policySeo).get(type);
            if (item instanceof Map) {
                priorityTitleEn = String.valueOf(((Map<?,?>) item).get("titleEn") == null ? "" : ((Map<?,?>) item).get("titleEn"));
                priorityTitleZh = String.valueOf(((Map<?,?>) item).get("titleZh") == null ? "" : ((Map<?,?>) item).get("titleZh"));
            }
        }
        m.put("seoTitleEn", firstNonBlank(priorityTitleEn, firstNonBlank(string(rootExtra,"policySeoTitleEn"), defaultTitleEn)));
        m.put("seoTitleZh", firstNonBlank(priorityTitleZh, firstNonBlank(string(rootExtra,"policySeoTitleZh"), defaultTitleZh)));
        // Policy pages need their own search intent. Do not reuse the country-home
        // description because direct entry, bilateral exemption, transit and Hainan
        // answer materially different queries.
        m.put("seoDescEn", policySeoDescription(country, type, policy, false));
        m.put("seoDescZh", policySeoDescription(country, type, policy, true));
        m.put("policyHeadingEn", policyHeading(country, type, false));
        m.put("policyHeadingZh", policyHeading(country, type, true));
        return m;
    }

    private String policyHeading(CountryDetail c, String type, boolean zh) {
        String n = zh ? c.getNameZh() : c.getName();
        if ("transit".equals(type)) return zh ? n + "护照中国240小时过境免签 | 行程与入境条件" : n + " to China 240-Hour Transit Visa-Free | Itinerary & Entry Rules";
        if ("hainan".equals(type)) return zh ? n + "赴海南30天免签 | 区域入境规则" : n + " to Hainan Visa-Free | 30-Day Regional Entry Rules";
        if ("mutual".equals(type)) return zh ? n + "与中国互免签证 | 护照、事由与停留规则" : n + "–China Mutual Visa Exemption | Passport, Purpose & Stay Rules";
        String stay = isBlank(c.getStayDays()) ? (zh ? "政策规定期限" : "the listed stay period") : c.getStayDays() + (zh ? "天" : " days");
        return zh ? n + "护照来华" + stay + "免签 | 入境条件与停留规则" : n + " to China " + stay + " Visa-Free Entry | Eligibility & Stay Rules";
    }

    private String policySeoDescription(CountryDetail c, String type, CountryPolicy p, boolean zh) {
        String n = zh ? c.getNameZh() : c.getName();
        if ("transit".equals(type)) {
            return zh ? n + "公民查询中国240小时过境免签的联程行程、普通护照、适用口岸、停留期限及可从事活动等条件。"
                    : n + " citizens: check China 240-hour transit visa-free eligibility, onward itinerary, ordinary passport, eligible ports, stay limits and permitted activities.";
        }
        if ("hainan".equals(type)) {
            return zh ? n + "普通护照持有人查询海南30天区域免签的适用事由、活动范围、入境口岸和停留条件；该政策仅适用于海南。"
                    : n + " ordinary-passport holders: check the 30-day Hainan regional visa-free route, permitted purposes, activity area, entry ports and stay conditions.";
        }
        if ("mutual".equals(type)) {
            return zh ? "中国与" + n + "的互免签证协定页面：核对适用护照类别、出行事由、停留期限、入境次数及协定条件。"
                    : "China–" + n + " mutual visa exemption: check the covered passport category, permitted purpose, stay limits, entry conditions and agreement terms.";
        }
        String stay = isBlank(c.getStayDays()) ? (zh ? "政策规定期限" : "the listed stay period") : c.getStayDays() + (zh ? "天" : " days");
        return zh ? n + "普通护照来华" + stay + "单方面免签政策：查询允许事由、护照要求、停留期限及入境条件。"
                : n + " ordinary-passport holders: check the China unilateral visa-free route, permitted purposes, maximum stay of " + stay + ", passport and entry conditions.";
    }

    private String policyIntentSummary(CountryDetail c,String type,CountryPolicy p,boolean zh){
        String n=zh?c.getNameZh():c.getName(); String stay=zh?(isBlank(c.getStayDays())?"政策规定期限":c.getStayDays()+"天"):(isBlank(c.getStayDays())?"the period stated by the policy":c.getStayDays()+" days");
        if("unilateral".equals(type)) return zh?"这是一条针对"+n+"普通护照持有人的直接免签路径。页面重点回答是否免签、最长停留、允许事由、入境条件以及出发前需要核对什么。":"This is a direct visa-free route for eligible "+n+" ordinary-passport holders. The page focuses on visa-free eligibility, the maximum stay of "+stay+", permitted purposes and practical entry conditions.";
        if("mutual".equals(type)) return zh?"这是一条基于中外互免签证协定的路径。不要只看“免签”三个字，还应核对护照类型、事由、停留期限、累计停留和入境次数等协定条件。":"This route is based on a bilateral visa-exemption agreement. Check the passport type, purpose, stay limits, cumulative-stay and entry conditions in the applicable agreement rather than treating it as a generic visa waiver.";
        if("transit".equals(type)) return zh?"这是一条过境免签路径，核心不是“去中国旅游是否免签”，而是你的中国行程是否符合前往第三国或地区的过境条件。":"This is a transit route. The key question is not whether "+n+" citizens have a general tourist waiver, but whether the itinerary through China qualifies for the listed transit conditions.";
        return zh?"这是海南区域免签路径，只适用于符合条件的海南行程，不应理解为可免签前往海南以外的中国大陆地区。":"This is a Hainan regional visa-free route. It applies only to qualifying Hainan itineraries and should not be treated as a general visa waiver for mainland China outside Hainan.";
    }

    private List<Map<String,String>> buildIntentFaqs(CountryDetail c,List<String> types,List<CountryDetail> policies,boolean zh){
        List<Map<String,String>> r=new ArrayList<>(); String n=zh?c.getNameZh():c.getName();
        if(types.contains("unilateral")){String stay=stayText(c,zh); addFaq(r,zh?n+"公民去中国需要签证吗？":"Do "+n+" citizens need a visa for China?",zh?"符合条件的普通护照持有人可按当前单方面免签政策入境，最长停留"+stay+"，但仍需满足适用事由及其他入境条件。":"Eligible ordinary-passport holders may use the current unilateral visa-free route for up to "+stay+", subject to the permitted-purpose and entry conditions.");
            addFaq(r,zh?n+"护照去中国免签可以停留多久？":"How long can "+n+" passport holders stay in China visa-free?",zh?"最长停留以当前政策记录为准，目前为"+stay+"。":"The maximum stay follows the current policy record and is "+stay+".");
            addFaq(r,zh?n+"公民来中国旅游可以免签吗？":"Can "+n+" citizens visit China for tourism without a visa?",zh?"如果旅游属于当前政策允许的出行目的，并满足护照、停留和其他条件，可以按该免签路径出行。":"If tourism is a permitted purpose under the current policy and the passport, stay and other conditions are met, the visa-free route may be used.");
        } else if(types.contains("mutual")){addFaq(r,zh?n+"公民去中国免签吗？":"Can "+n+" citizens enter China visa-free?",zh?"是否免签取决于适用的中外互免签证协定以及护照、事由和停留条件。":"Visa exemption depends on the applicable bilateral agreement and its passport, purpose and stay conditions.");
            addFaq(r,zh?"互免签证可以停留多久？":"How long can the mutual visa exemption be used?",zh?"停留期限以适用的中外互免签证协定和具体护照条件为准。":"The stay limit follows the applicable China bilateral visa-exemption agreement and passport conditions.");
            addFaq(r,zh?n+"公民去中国旅游需要申请签证吗？":"Do "+n+" citizens need a visa for tourism in China?",zh?"先核对适用协定是否覆盖旅游目的，以及护照类型和停留条件。":"Check whether the applicable agreement covers tourism and whether the passport and stay conditions are met.");
        } else {addFaq(r,zh?n+"公民去中国需要签证吗？":"Do "+n+" citizens need a visa for China?",zh?"不能只根据国籍下结论，应根据你的实际目的、停留时间和行程判断可用的免签、过境或签证路径。":"Do not decide from nationality alone; check the actual purpose, stay and itinerary to determine whether a visa-free, transit or regular visa route applies.");}
        if(types.contains("transit")){addFaq(r,zh?n+"公民可以使用240小时过境免签吗？":"Can "+n+" citizens use China's 240-hour transit visa-free route?",zh?"如果你的行程符合前往第三国或地区的过境要求，并满足适用口岸、旅行证件和停留条件，可以进一步核对该路径。":"If your itinerary qualifies as transit to a third country or region and the passport, port and stay conditions are met, this route can be considered.");}
        if(types.contains("hainan")){addFaq(r,zh?n+"公民可以使用海南30天免签吗？":"Can "+n+" citizens use the 30-day Hainan visa-free route?",zh?"符合当前海南区域免签国籍、护照、事由和停留条件时，可以进一步核对该路径；它是区域政策。":"If the nationality, passport, purpose and stay meet the current Hainan regional policy, this route can be considered; it is a regional policy.");}
        addFaq(r,zh?"如果不符合直接免签，还能24小时过境吗？":"What if I do not qualify for a direct visa-free route?",zh?"如果实际行程是经中国前往第三国或地区，可以进一步检查24小时过境免签的旅行证件、联程客票和口岸限定区域条件。":"If the trip is genuinely transit to a third country or region, check the general 24-hour transit conditions for travel documents, confirmed onward travel and the port restricted area.");
        return r.stream().limit(5).collect(Collectors.toList());
    }

    private void addFaq(List<Map<String,String>> r,String q,String a){Map<String,String> x=new LinkedHashMap<>();x.put("q",q);x.put("a",a);r.add(x);}
    private String stayText(CountryDetail c,boolean zh){return isBlank(c.getStayDays())?(zh?"政策规定期限":"the period stated by the policy"):c.getStayDays()+(zh?"天":" days");}
    private String buildHomeDecisionAnswer(String name, List<String> types, boolean zh) {
        boolean direct = types.contains("unilateral") || types.contains("mutual");
        int count = types.size();
        String routeWord = zh ? "种路径" : (count == 1 ? "route" : "routes");
        if (direct) {
            return zh
                    ? "YES — " + name + "普通护照符合条件时可以直接免签入境中国；目前还可以核对 " + count + " " + routeWord + "。"
                    : "YES — Eligible " + name + " ordinary-passport holders can enter China directly without a visa; " + count + " " + routeWord + " are currently listed to check.";
        }
        return zh
                ? "NO — " + name + "普通护照目前不能按一般直接免签入境中国；但仍有 " + count + " " + routeWord + " 可以根据实际行程核对。"
                : "NO — " + name + " ordinary-passport holders do not have a general direct visa-free route into China; " + count + " specific " + routeWord + " may still apply depending on the itinerary.";
    }

    private String routeSummaryEn(List<String> t){List<String> r=new ArrayList<>();if(t.contains("unilateral"))r.add("A direct unilateral visa-free route is listed.");if(t.contains("mutual"))r.add("A bilateral visa-exemption route is listed.");if(t.contains("transit"))r.add("A 240-hour transit route is listed.");if(t.contains("hainan"))r.add("A separate Hainan regional route is listed.");return String.join(" ",r);}
    private String routeSummaryZh(List<String> t){List<String> r=new ArrayList<>();if(t.contains("unilateral"))r.add("当前有直接单方面免签路径。");if(t.contains("mutual"))r.add("当前有双边互免签证路径。");if(t.contains("transit"))r.add("当前有240小时过境免签路径。");if(t.contains("hainan"))r.add("另外有独立的海南区域免签路径。");return String.join("",r);}
    private String policyLabel(String type,boolean zh){if("unilateral".equals(type))return zh?"单方面免签政策":"Unilateral Visa-Free Policy";if("mutual".equals(type))return zh?"互免签证政策":"Mutual Visa Exemption";if("transit".equals(type))return zh?"240小时过境免签政策":"240-Hour Transit Visa-Free Policy";return zh?"海南30天区域免签政策":"30-Day Hainan Visa-Free Policy";}
    private List<Map<String,String>> buildTravelChecks(CountryDetail c,List<String> types,List<CountryDetail> p,boolean zh){List<Map<String,String>> r=new ArrayList<>();addCheck(r,zh?"护照":"Passport",zh?"确认使用的是适用的普通护照及符合政策要求的旅行证件。":"Confirm that your passport and travel document meet the applicable route requirements.");addCheck(r,zh?"出行目的":"Purpose",zh?"确认旅游、商务、探亲等目的属于对应政策允许范围。":"Confirm that the purpose of travel is covered by the selected route.");addCheck(r,zh?"停留时间":"Stay",zh?"不要超过对应政策记录的最长停留期限。":"Do not exceed the maximum stay listed for the selected route.");if(types.contains("transit"))addCheck(r,zh?"后续行程":"Onward itinerary",zh?"如果使用过境免签，确认前往第三国或地区的联程行程及适用口岸条件。":"For transit, confirm the onward itinerary to a third country or region and eligible port conditions.");return r;}
    private void addCheck(List<Map<String,String>> r,String k,String v){Map<String,String>x=new LinkedHashMap<>();x.put("k",k);x.put("v",v);r.add(x);}

    private List<Map<String,Object>> findRelatedArticles(CountryDetail country,String code,String lang){
        List<Map<String,Object>> out=new ArrayList<>(); String route=routeCode(code);
        for(Map<String,Object> a:articles){
            Object related=a.get("relatedCountryCodes"); boolean hit=false;
            if(related instanceof List){for(Object x:(List<?>)related){if(route.equals(routeCode(string(x)))){hit=true;break;}}}
            if(!hit){String text=string(a.get("titleEn"))+" "+string(a.get("titleZh"))+" "+string(a.get("summaryEn"))+" "+string(a.get("summaryZh"));String[] needles={country.getName(),country.getNameZh()};for(String n:needles)if(!isBlank(n)&&text.toLowerCase(Locale.ROOT).contains(n.toLowerCase(Locale.ROOT))){hit=true;break;}}
            if(hit){Map<String,Object> copy=new LinkedHashMap<>(a);copy.put("url", "/articles/" + string(a.get("id")) + "?lang=" + lang);out.add(copy);if(out.size()>=4)break;}
        }
        if(out.isEmpty()) for(Map<String,Object> a:articles){String cat=string(a.get("categoryEn"))+" "+string(a.get("categoryZh"));if(cat.toLowerCase(Locale.ROOT).contains("visa")||cat.contains("免签")){out.add(a);if(out.size()>=2)break;}}
        return out;
    }
    private List<Map<String,Object>> loadArticles(ObjectMapper mapper){try{return mapper.readValue(new ClassPathResource("articles.json").getInputStream(),new TypeReference<List<Map<String,Object>>>(){});}catch(Exception e){return new ArrayList<>();}}

    private Map<String, Object> buildCountryProfile(String code, CountryDetail detailCountry, Map<String, Object> extra) {
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("code", code);
        profile.put("priority", extra == null ? 999 : number(extra.get("priority"), 999));
        profile.put("tier", firstNonBlank(string(extra, "tier"), "C"));
        profile.put("heroQuestionEn", firstNonBlank(string(extra, "homeHeroQuestionEn"), "Do " + detailCountry.getName() + " citizens need a visa for China?"));
        profile.put("heroQuestionZh", firstNonBlank(string(extra, "homeHeroQuestionZh"), detailCountry.getNameZh() + "公民去中国需要签证吗？"));
        profile.put("heroAnswerEn", firstNonBlank(string(extra, "homeHeroAnswerEn"), defaultHeroAnswer(detailCountry, code, false)));
        profile.put("heroAnswerZh", firstNonBlank(string(extra, "homeHeroAnswerZh"), defaultHeroAnswer(detailCountry, code, true)));
        profile.put("introEn", firstNonBlank(string(extra, "homeIntroEn"), defaultIntro(detailCountry, code, false)));
        profile.put("introZh", firstNonBlank(string(extra, "homeIntroZh"), defaultIntro(detailCountry, code, true)));
        return profile;
    }

    private List<Map<String,Object>> toFaqObjects(Object value) {
        List<Map<String,Object>> result = new ArrayList<>();
        if (!(value instanceof List)) return result;
        for (Object item : (List<?>) value) {
            if (!(item instanceof Map)) continue;
            Map<?,?> raw = (Map<?,?>) item;
            String q = string(raw.get("q"));
            String a = string(raw.get("a"));
            if (isBlank(q) || isBlank(a)) continue;
            Map<String,Object> faq = new LinkedHashMap<>();
            faq.put("q", q.trim()); faq.put("a", a.trim());
            result.add(faq);
        }
        return result;
    }

    private List<Map<String,Object>> faqObjects(Object value) {
        if (value instanceof List) return toFaqObjects(value);
        return new ArrayList<>();
    }

    private List<Map<String,Object>> dedupeFaqs(List<Map<String,Object>> input, int limit) {
        List<Map<String,Object>> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Map<String,Object> item : input) {
            String key = normalizeFaqQuestion(string(item.get("q")));
            if (key.isEmpty() || !seen.add(key)) continue;
            Map<String,Object> faq = new LinkedHashMap<>(item);
            faq.put("first", result.isEmpty());
            result.add(faq);
            if (result.size() >= limit) break;
        }
        return result;
    }

    private String normalizeFaqQuestion(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
                .replaceAll("[\\p{Punct}\\p{P}\\p{S}]", "")
                .replaceAll("\\s+", "").trim();
    }

    private String defaultHeroAnswer(CountryDetail detail, String code, boolean zh) {
        List<String> types = detectAvailableTypes(code);
        String n = zh ? detail.getNameZh() : detail.getName();
        if (types.contains("unilateral") && types.contains("transit")) return zh ? "符合条件的"+n+"普通护照持有人可能适用30天直接免签，也可能适用240小时过境免签；两条路径条件不同，请按实际行程判断。" : "Eligible "+n+" ordinary-passport holders may have both a direct 30-day visa-free route and a separate 240-hour transit route; the correct route depends on the itinerary.";
        if (types.contains("unilateral")) return zh ? "符合条件的"+n+"普通护照持有人可按现行单方面免签政策来华最长30天，具体以出行当日规定为准。" : "Eligible "+n+" ordinary-passport holders may enter China visa-free for up to 30 days for covered short-term purposes, subject to the current policy.";
        if (types.contains("mutual")) return zh ? n+"公民应根据适用的双边互免签证协定核对护照、事由和停留条件。" : n+" citizens should use the bilateral visa-exemption route only when their passport, purpose and stay meet the applicable agreement.";
        if (types.contains("transit")) return zh ? "符合条件的"+n+"普通护照持有人仅在经中国前往第三国或地区且满足全部过境条件时，才可能适用240小时过境免签。" : "Eligible "+n+" ordinary-passport holders may use 240-hour transit only when traveling through China to a third country or region and meeting all transit conditions.";
        return zh ? n+"公民符合普通护照及事由条件时，可使用海南30天区域免签路径。" : n+" citizens may use the 30-day Hainan regional visa-free route when the ordinary-passport and purpose conditions are met.";
    }

    private String defaultIntro(CountryDetail detail, String code, boolean zh) {
        String n = zh ? detail.getNameZh() : detail.getName();
        return zh ? "本页面汇总目前与"+n+"护照持有人相关的中国入境路径。建议先使用资格检查器，再进入与实际行程相符的政策详情。" : "This page brings together the China entry routes currently relevant to "+n+" passport holders. Start with the checker, then open the route matching your itinerary.";
    }

    private CountryPolicy buildCountryPolicy(String code, String type, CountryDetail detailCountry, Map<String, Object> extra) {
        CountryPolicy policy = new CountryPolicy();
        policy.setDetail(detailCountry); policy.setCountryCode(code); policy.setPolicyType(type);
        policy.setOfficialSource(firstNonBlank(string(extra, "officialSource"), NIA_SOURCE));
        policy.setLastVerified(firstNonBlank(string(extra, "lastVerified"), VERIFIED_DATE));
        policy.setPolicyExpiry(firstNonBlank(string(extra, "policyExpiry"), detailCountry.getPolicyExpiry()));
        policy.setPermittedPurposesEn(firstNonBlank(string(extra, "permittedPurposesEn"), detailCountry.getPurpose()));
        policy.setPermittedPurposesZh(firstNonBlank(string(extra, "permittedPurposesZh"), detailCountry.getPurposeZh()));
        boolean transit = "transit".equals(type); boolean mutual = "mutual".equals(type); boolean hainan = "hainan".equals(type);
        policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), transit ? "Valid ordinary passport of an eligible country/region" : mutual ? "Passport covered by the applicable bilateral agreement" : "Valid ordinary passport"));
        policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), transit ? "符合过境免签条件国家/地区的有效普通护照" : mutual ? "适用双边互免签证协定的护照" : "有效普通护照"));
        policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"), transit ? "Use a valid ordinary passport and meet the current transit-document requirements." : hainan ? "Use a valid ordinary passport and meet the Hainan policy requirements." : "Passport requirements follow the applicable entry policy."));
        policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"), transit ? "须持有效普通护照，并符合当前过境旅行证件要求。" : hainan ? "须持有效普通护照并符合海南区域免签政策要求。" : "护照要求以适用入境政策为准。"));
        policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"), transit ? "Enter through an eligible port and satisfy the required transit itinerary conditions." : mutual ? "Entry frequency and stay limits follow the applicable bilateral agreement." : hainan ? "Entry is limited to the Hainan regional policy and its permitted stay and activity conditions." : "Entry conditions and stay limits follow the current unilateral policy."));
        policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"), transit ? "须通过适用口岸入境，并满足规定的过境联程行程条件。" : mutual ? "入境次数及停留期限以适用双边协定为准。" : hainan ? "仅适用于海南区域免签政策规定的停留和活动范围。" : "入境条件及停留期限以当前单方面免签政策为准。"));
        policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"), transit ? "A confirmed onward itinerary to a third country or region is required." : "Check the itinerary and supporting-document requirements for your route."));
        policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"), transit ? "需要前往第三国或地区、日期和行程已确定的联程客票。" : "请根据实际行程核对行程及相关证明材料要求。"));
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
        // Policy-detail FAQs must answer route-specific implementation questions.
        // The country-home page already answers the high-level "do I need a visa?" intent.
        if ("hainan".equals(type)) {
            addIfMissing(en, "What purposes are allowed under the 30-day Hainan visa-free route?",
                    "Check the permitted purposes recorded for this route and the current Hainan regional rules before departure.");
            addIfMissing(zh, "海南30天免签可以用于哪些出行目的？",
                    "请按本页面记录的允许事由以及出行当日有效的海南区域规定核对，工作、学习等长期目的不能直接按普通旅游免签理解。");
        } else if ("transit".equals(type)) {
            addIfMissing(en, "What onward itinerary is required for this transit route?",
                    "You must meet the qualifying transit itinerary to a third country or region, together with the applicable passport, port and stay conditions.");
            addIfMissing(zh, "使用这条过境免签需要什么后续行程？",
                    "需要满足前往第三国或地区的符合条件的过境联程行程，并同时满足适用护照、口岸和停留条件。");
        } else if ("mutual".equals(type)) {
            addIfMissing(en, "Which passport and purpose conditions apply to this visa-exemption agreement?",
                    "Use the passport type, permitted purpose and stay conditions recorded for this agreement; do not treat the exemption as a general waiver for every trip.");
            addIfMissing(zh, "这项互免签证协定有哪些护照和出行目的限制？",
                    "请按本协定对应的护照类型、允许事由和停留条件核对，不要把协定理解成适用于所有行程的普遍免签。");
        } else {
            addIfMissing(en, "What purposes are covered by this visa-free route?",
                    "Check the permitted purposes and maximum stay recorded for this route, and verify the rules in force on the travel date.");
            addIfMissing(zh, "这项免签政策允许哪些出行目的？",
                    "请按本页面记录的允许事由和最长停留期限核对，并以出行当日有效规定为准。");
        }
        policy.setFaqsEn(en); policy.setFaqsZh(zh);
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
            String question = string(map.get("q")); String answer = string(map.get("a"));
            if (!isBlank(question) && !isBlank(answer)) result.add(new CountryPolicy.PolicyFaq(question, answer));
        }
        return result;
    }

    private List<String> detectAvailableTypes(String code) {
        String key = resolver.policyKey(code); List<String> result = new ArrayList<>();
        if (unilateralMap.containsKey(key)) result.add("unilateral");
        if (mutualMap.containsKey(key)) result.add("mutual");
        if (transitMap.containsKey(key)) result.add("transit");
        if (hainanMap.containsKey(key)) result.add("hainan");
        return result;
    }

    private CountryDetail getCountryDetail(String code, String type) {
        String key = resolver.policyKey(code);
        if ("unilateral".equals(type)) return unilateralMap.get(key);
        if ("mutual".equals(type)) return mutualMap.get(key);
        if ("transit".equals(type)) return transitMap.get(key);
        if ("hainan".equals(type)) return hainanMap.get(key);
        return null;
    }

    private CountryDetail getAnyCountryDetail(String code) {
        String key = resolver.policyKey(code);
        CountryDetail d = unilateralMap.get(key); if (d != null) return d;
        d = mutualMap.get(key); if (d != null) return d;
        d = transitMap.get(key); if (d != null) return d;
        // Hainan-only countries (for example the Philippines) still need a
        // country decision page and checker. Never assume every country has a
        // mainland unilateral/mutual/transit record.
        return hainanMap.get(key);
    }

    private String routeCode(String code) { return resolver.routeCode(resolver.policyKey(code)); }
    private Map<String,Object> getCountryExtraRoot(String code) { Map<String,Object> m=countryExtraMap.get(code); return m!=null?m:countryExtraMap.get(routeCode(code)); }
    private Object getCountryExtraItem(String code,String type) { Map<String,Object> m=getCountryExtraRoot(code); return m==null?null:m.get(type); }
    private List<CountryDetail> buildAvailablePolicies(String code,List<String> types) { List<CountryDetail> r=new ArrayList<>(); for(String type:types){CountryDetail d=getCountryDetail(code,type);if(d!=null)r.add(d);}return r; }
    private Map<String,CountryDetail> buildPolicyDetails(String code,List<String> types){Map<String,CountryDetail> r=new LinkedHashMap<>();for(String type:types){CountryDetail d=getCountryDetail(code,type);if(d!=null)r.put(type,d);}return r;}
    private Map<String,String> buildCountryNames(){Map<String,String> r=new LinkedHashMap<>();addNames(r,unilateralMap);addNames(r,mutualMap);addNames(r,transitMap);addNames(r,hainanMap);return r;}
    private void addNames(Map<String,String> r,Map<String,CountryDetail> m){for(Map.Entry<String,CountryDetail> e:m.entrySet())r.putIfAbsent(routeCode(e.getKey()),e.getValue().getName());}
    private Map<String,String> buildCountryFlags(){Map<String,String> r=new LinkedHashMap<>();for(String code:buildCountryNames().keySet())r.put(code,flagService.flag(code));return r;}
    private List<String> buildRelatedCountryCodes(Map<String,Object> extra){List<String> r=new ArrayList<>();if(extra==null)return r;Object v=extra.get("relatedCountryCodes");if(!(v instanceof List))return r;for(Object item:(List<?>)v){String c=string(item);String canonical=routeCode(c);if(!isBlank(c)&&getAnyCountryDetail(c)!=null&&!r.contains(canonical))r.add(canonical);}return r;}
    private Map<String,CountryDetail> loadCountryMap(ObjectMapper mapper,String fileName){try{return mapper.readValue(new ClassPathResource(fileName).getInputStream(),new TypeReference<Map<String,CountryDetail>>(){});}catch(Exception e){throw new IllegalStateException("Failed to load "+fileName,e);}}
    private Map<String,Map<String,Object>> loadExtraMap(ObjectMapper mapper){try{return mapper.readValue(new ClassPathResource("country-extra.json").getInputStream(),new TypeReference<Map<String,Map<String,Object>>>(){});}catch(Exception e){throw new IllegalStateException("Failed to load country-extra.json",e);}}
    private Map<String,Map<String,Object>> loadIntentMap(ObjectMapper mapper){try{return mapper.readValue(new ClassPathResource("country-intent.json").getInputStream(),new TypeReference<Map<String,Map<String,Object>>>(){});}catch(Exception e){throw new IllegalStateException("Failed to load country-intent.json",e);}}
    private Map<String,Object> mergeContent(Map<String,Object> base, Map<String,Object> overlay){Map<String,Object> r=new LinkedHashMap<>();if(base!=null)r.putAll(base);if(overlay!=null)for(Map.Entry<String,Object> e:overlay.entrySet())r.put(e.getKey(),e.getValue());return r;}
    private Map<String,Object> asMap(Object value){if(!(value instanceof Map))return new LinkedHashMap<>();Map<String,Object> r=new LinkedHashMap<>();for(Map.Entry<?,?>e:((Map<?,?>)value).entrySet())r.put(String.valueOf(e.getKey()),e.getValue());return r;}
    private String string(Map<String,Object> m,String k){return m==null?"":string(m.get(k));} private String string(Object v){return v==null?"":String.valueOf(v).trim();} private String firstNonBlank(String v,String f){return isBlank(v)?f:v;} private boolean isBlank(String v){return v==null||v.trim().isEmpty();} private int number(Object v,int f){try{return Integer.parseInt(string(v));}catch(Exception e){return f;}}
}
