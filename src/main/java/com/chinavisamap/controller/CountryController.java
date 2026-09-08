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
    private static final String VERIFIED_DATE = "2026-09-09";

    private final Map<String, CountryDetail> unilateralMap;
    private final Map<String, CountryDetail> mutualMap;
    private final Map<String, CountryDetail> transitMap;
    private final Map<String, CountryDetail> hainanMap;
    private final Map<String, Map<String, Object>> countryExtraMap;
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
        String path = "/country/" + pageCode;
        String canonical = seoService.canonical(path, normalizedLang);
        model.addAttribute("code", pageCode);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("countryFlag", flagService.flag(pageCode));
        model.addAttribute("availableTypes", types);
        model.addAttribute("countryExtraRoot", extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, extra));
        List<CountryDetail> availablePolicies = buildAvailablePolicies(code, types);
        model.addAttribute("availablePolicies", availablePolicies);
        model.addAttribute("countryNames", buildCountryNames());
        model.addAttribute("countryFlags", buildCountryFlags());
        model.addAttribute("relatedCountryCodes", buildRelatedCountryCodes(extra));
        model.addAttribute("eligibilityConfig", eligibilityService.build(pageCode, availablePolicies, extra));
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountryHome(detailCountry, normalizedLang, canonical, extra, types, buildPolicyDetails(code, types)));
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
        model.addAttribute("canonicalUrl", canonical);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountry(detailCountry, normalizedLang, canonical, policy, extra));
        model.addAttribute("detail", detailCountry);
        model.addAttribute("policy", policy);
        model.addAttribute("code", pageCode);
        model.addAttribute("type", type);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("countryFlag", flagService.flag(pageCode));
        model.addAttribute("countryExtra", extra);
        model.addAttribute("countryProfile", buildCountryProfile(pageCode, detailCountry, rootExtra));
        model.addAttribute("availableTypes", detectAvailableTypes(code));
        model.addAttribute("policyDetails", buildPolicyDetails(code, detectAvailableTypes(code)));
        return "country-detail";
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
        profile.put("homeFaqsEn", buildHomeFaqs(detailCountry, code, extra, false));
        profile.put("homeFaqsZh", buildHomeFaqs(detailCountry, code, extra, true));
        return profile;
    }

    private List<Map<String,Object>> buildHomeFaqs(CountryDetail detail, String code, Map<String,Object> extra, boolean zh) {
        String customKey = zh ? "homeCustomFaqsZh" : "homeCustomFaqsEn";
        List<Map<String,Object>> result = new ArrayList<>();
        Object custom = extra == null ? null : extra.get(customKey);
        if (custom instanceof List) {
            for (Object item : (List<?>) custom) {
                if (!(item instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) item;
                String q = string(m.get("q")); String a = string(m.get("a"));
                if (!q.isEmpty() && !a.isEmpty()) {
                    Map<String,Object> faq = new LinkedHashMap<>(); faq.put("q", q); faq.put("a", a); faq.put("first", result.isEmpty()); result.add(faq);
                }
            }
        }
        List<String> types = detectAvailableTypes(code);
        String n = zh ? detail.getNameZh() : detail.getName();
        addHomeFaq(result, zh ? n+"公民去中国需要签证吗？" : "Do "+n+" citizens need a visa for China?",
                types.contains("unilateral") ? (zh ? "符合条件的普通护照持有人可按现行单方面免签政策免签停留最长30天，具体事由和期限以出行当日有效规定为准。" : "Eligible ordinary-passport holders may use the current unilateral visa-free route for up to 30 days for covered purposes, subject to the rules in force on the travel date.") :
                types.contains("mutual") ? (zh ? "是否免签取决于适用的中外互免签证协定，需核对护照类型、事由和停留条件。" : "Visa exemption depends on the applicable China bilateral agreement, including passport, purpose and stay conditions.") :
                types.contains("transit") ? (zh ? "普通护照持有人可能适用240小时过境免签，但必须满足第三国或地区联程行程、口岸和活动区域等条件。" : "Eligible ordinary-passport holders may use the 240-hour transit route only when the onward itinerary, port and permitted-area conditions are met.") :
                (zh ? "该国目前主要适用海南区域免签路径，请按实际行程核对。" : "This country is currently covered mainly by the Hainan regional visa-free route; check the conditions for your actual itinerary."));
        addHomeFaq(result, zh ? "如果不符合本国的直接免签，过境中国24小时还可以免签吗？" : "Can I still transit China visa-free for 24 hours if my nationality has no direct visa-free route?", zh ? "可以考虑。国家移民管理局规定，中国所有对外开放口岸对世界各国人员实施24小时过境免签；但必须是经中国前往第三国或地区，持有效国际旅行证件和已确定座位的国际联程客票，并且不离开口岸限定区域。如需离开限定区域，应先申请临时入境许可。" : "Possibly. China applies a 24-hour visa-free transit policy to nationals of all countries at open ports, but you must be transiting to a third country or region with valid international travel documents and confirmed onward international travel, and remain in the port restricted area. A temporary entry permit is required if you need to leave that restricted area.");
        if (types.contains("transit")) addHomeFaq(result, zh ? "可以把240小时过境免签当作普通旅游免签吗？" : "Can I use 240-hour transit visa-free entry like a normal tourist visa?", zh ? "不可以。它要求从中国过境前往第三国或地区，并满足确定日期和行程的联程客票、适用口岸及活动区域等条件。" : "No. It is a transit arrangement to a third country or region and requires the specified onward itinerary, eligible port and permitted-area conditions.");
        if (types.contains("hainan")) addHomeFaq(result, zh ? "这个国家可以使用海南30天免签吗？" : "Can this nationality use Hainan's 30-day visa-free route?", zh ? "持普通护照且符合规定短期事由的人员可以申请适用；工作、学习等不属于该政策范围，活动范围限于海南省行政区域。" : "Eligible ordinary-passport holders may use the route for covered short-term purposes; work and study are excluded and activities are limited to Hainan Province.");
        addHomeFaq(result, zh ? "如果我要在中国工作或学习怎么办？" : "What if I plan to work or study in China?", zh ? "不要把普通免签路径用于工作或学习。请根据实际目的提前申请相应签证或向官方渠道核实。" : "Do not use an ordinary visa-free route for work or study. Apply for the appropriate visa in advance or verify with the official authorities.");
        return result.stream().limit(5).collect(Collectors.toList());
    }

    private void addHomeFaq(List<Map<String,Object>> list, String q, String a) {
        for (Map<String,Object> item : list) if (q.equals(item.get("q"))) return;
        Map<String,Object> faq = new LinkedHashMap<>(); faq.put("q", q); faq.put("a", a); faq.put("first", list.isEmpty()); list.add(faq);
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
        if ("hainan".equals(type)) {
            addIfMissing(en, "Can " + detailCountry.getName() + " citizens use the 30-day Hainan visa-free policy?", "Eligible ordinary-passport holders may enter Hainan visa-free for up to 30 days for covered short-term purposes, subject to the regional restrictions and current policy.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民可以使用海南30天免签吗？", "符合条件的普通护照持有人可按现行海南区域免签政策免签停留最长30天，但仅限海南省行政区域及规定事由。" );
        } else if ("transit".equals(type)) {
            addIfMissing(en, "How long can " + detailCountry.getName() + " citizens stay under this transit route?", "The current policy record allows up to " + stay + ", subject to the qualifying transit itinerary and entry record.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民过境免签可以停留多久？", "当前政策记录允许最长停留" + stayZh + "，具体以符合条件的过境行程和入境记录为准。");
        } else if ("mutual".equals(type)) {
            addIfMissing(en, "Do " + detailCountry.getName() + " citizens need a visa under this agreement?", "A visa may be waived when the passport and purpose meet the applicable bilateral agreement.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民按照互免协定去中国需要签证吗？", "满足适用双边互免签证协定条件时，可以免办签证入境。");
        } else {
            addIfMissing(en, "Can " + detailCountry.getName() + " citizens enter China without a visa?", "They may qualify when the ordinary passport, purpose and stay conditions of the current policy are met.");
            addIfMissing(zh, detailCountry.getNameZh() + "公民可以免签进入中国吗？", "满足当前普通护照、出行目的和停留条件时，可能适用中国免签安排。");
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
    private Map<String,Object> asMap(Object value){if(!(value instanceof Map))return new LinkedHashMap<>();Map<String,Object> r=new LinkedHashMap<>();for(Map.Entry<?,?>e:((Map<?,?>)value).entrySet())r.put(String.valueOf(e.getKey()),e.getValue());return r;}
    private String string(Map<String,Object> m,String k){return m==null?"":string(m.get(k));} private String string(Object v){return v==null?"":String.valueOf(v).trim();} private String firstNonBlank(String v,String f){return isBlank(v)?f:v;} private boolean isBlank(String v){return v==null||v.trim().isEmpty();} private int number(Object v,int f){try{return Integer.parseInt(string(v));}catch(Exception e){return f;}}
}
