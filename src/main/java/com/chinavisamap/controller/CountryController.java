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

    /**
     * Legacy URL: /country/{code}?type=xxx&lang=xxx -> permanent redirect.
     */
    @GetMapping(value = "/country/{code}", params = "type")
    public ResponseEntity<Void> legacyCountryRedirect(
            @PathVariable String code,
            @RequestParam String type,
            @RequestParam(defaultValue = "en") String lang) {

        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) {
            return ResponseEntity.notFound().build();
        }

        String normalizedLang = seoService.normalizeLang(lang);
        String location = UriComponentsBuilder
                .fromPath("/country/" + code + "/" + type)
                .queryParam("lang", normalizedLang)
                .build()
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));
        return new ResponseEntity<>(headers, HttpStatus.MOVED_PERMANENTLY);
    }

    /** Country aggregate page. */
    @GetMapping("/country/{code}")
    public String countryHome(@PathVariable String code,
                              @RequestParam(defaultValue = "en") String lang,
                              Model model) {

        String normalizedLang = seoService.normalizeLang(lang);
        List<String> availableTypes = detectAvailableTypes(code);
        if (availableTypes.isEmpty()) {
            return "redirect:/?lang=" + normalizedLang;
        }

        CountryDetail detailCountry = getAnyCountryDetail(code);
        Map<String, Object> countryExtraRoot = countryExtraMap.get(code);

        String path = "/country/" + code;
        String canonicalUrl = seoService.canonical(path, normalizedLang);
        model.addAttribute("code", code);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("availableTypes", availableTypes);
        model.addAttribute("countryExtraRoot", countryExtraRoot);
        model.addAttribute("availablePolicies", buildAvailablePolicies(code, availableTypes));
        model.addAttribute("countryNames", buildCountryNames());
        model.addAttribute("relatedCountryCodes", buildRelatedCountryCodes(countryExtraRoot));
        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountryHome(
                detailCountry,
                normalizedLang,
                canonicalUrl,
                countryExtraRoot,
                availableTypes,
                buildPolicyDetails(code, availableTypes)
        ));

        return "country-home";
    }

    /** Policy detail page. */
    @GetMapping("/country/{code}/{type}")
    public String restCountryDetail(@PathVariable String code,
                                    @PathVariable String type,
                                    @RequestParam(defaultValue = "en") String lang,
                                    Model model) {

        String normalizedLang = seoService.normalizeLang(lang);
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) {
            return "redirect:/?lang=" + normalizedLang;
        }

        String path = "/country/" + code + "/" + type;
        String canonicalUrl = seoService.canonical(path, normalizedLang);
        Map<String, Object> countryExtra = asMap(getCountryExtraItem(code, type));
        CountryPolicy policy = buildCountryPolicy(code, type, detail, countryExtra);

        model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("hreflang", seoService.hreflang(path));
        model.addAttribute("structuredData", structuredDataService.buildCountry(
                detail,
                normalizedLang,
                canonicalUrl,
                policy,
                countryExtra
        ));
        model.addAttribute("detail", detail);
        model.addAttribute("policy", policy);
        model.addAttribute("code", code);
        model.addAttribute("type", type);
        model.addAttribute("lang", normalizedLang);
        model.addAttribute("countryExtra", countryExtra);

        return "country-detail";
    }

    private CountryPolicy buildCountryPolicy(String code,
                                             String type,
                                             CountryDetail detail,
                                             Map<String, Object> extra) {
        CountryPolicy policy = new CountryPolicy();
        policy.setDetail(detail);
        policy.setCountryCode(code);
        policy.setPolicyType(type);
        policy.setOfficialSource(firstNonBlank(
                string(extra, "officialSource"), NIA_SOURCE));
        policy.setLastVerified(firstNonBlank(
                string(extra, "lastVerified"), VERIFIED_DATE));
        policy.setPolicyExpiry(firstNonBlank(
                string(extra, "policyExpiry"), ""));

        String purposesEn = firstNonBlank(string(extra, "permittedPurposesEn"), detail.getPurpose());
        String purposesZh = firstNonBlank(string(extra, "permittedPurposesZh"), detail.getPurposeZh());
        policy.setPermittedPurposesEn(purposesEn);
        policy.setPermittedPurposesZh(purposesZh);

        if ("unilateral".equals(type)) {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"), "Valid ordinary passport"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"), "有效普通护照"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"),
                    "Passport validity must meet China's entry inspection requirements."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"),
                    "护照有效期需符合中国入境查验标准及免签政策要求。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"),
                    "Single entry permitted under China's unilateral visa-free policy."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"),
                    "适用中国单方面免签政策，仅限单次入境。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"),
                    "Return or onward ticket may be required by border inspection authorities."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"),
                    "边检机关可根据查验需求，要求出示返程或第三国联程机票。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"),
                    "Valid accommodation reservation documents may be required for entry inspection."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"),
                    "入境查验时可能需要提供有效住宿预订凭证。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"),
                    "Sufficient financial proof may be requested to cover stay expenses in China."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"),
                    "可能需要提供充足资金证明，用于覆盖在华停留期间开支。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"),
                    "Visa-free stay extension is allowed under specific conditions. Apply to local immigration authorities before stay expiration."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"),
                    "单方面免签停留可依规申请延期，需在停留期限届满前向当地出入境管理部门提交申请。"));
        } else if ("mutual".equals(type)) {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"),
                    "Valid passport or travel document specified in bilateral visa exemption agreement"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"),
                    "双边互免签证协定规定的有效护照或旅行证件"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"),
                    "Passport validity requirements comply with bilateral visa exemption agreement provisions."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"),
                    "护照有效期要求严格遵循中外双边互免签证协定条款。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"),
                    "Entry times and valid periods are subject to the signed bilateral visa exemption agreement."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"),
                    "入境次数、有效期限以正式生效的双边互免签证协定为准。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"),
                    "Onward or return ticket may be checked by border authorities based on travel purposes."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"),
                    "边检机关将根据出行目的，核查返程或联程机票凭证。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"),
                    "Accommodation registration is mandatory for in-China stay, reservation proof may be checked on entry."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"),
                    "在华住宿必须办理住宿登记，入境时可能核查住宿预订证明。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"),
                    "Financial certification may be required to prove solvency for domestic stay."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"),
                    "入境可能需要提供资金证明，证实具备在华停留消费能力。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"),
                    "Stay extension rules follow bilateral agreements and Chinese immigration administration regulations."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"),
                    "停留延期规则依据双边协定及中国出入境管理条例执行。"));
        } else {
            policy.setPassportTypeEn(firstNonBlank(string(extra, "passportTypeEn"),
                    "Valid ordinary passport of eligible countries/regions for China transit visa-free policy"));
            policy.setPassportTypeZh(firstNonBlank(string(extra, "passportTypeZh"),
                    "符合中国过境免签政策资质国家/地区的有效普通护照"));
            policy.setPassportValidityEn(firstNonBlank(string(extra, "passportValidityEn"),
                    "Passport must be valid for the entire transit period and meet border inspection standards."));
            policy.setPassportValidityZh(firstNonBlank(string(extra, "passportValidityZh"),
                    "护照需在全程过境期间有效，且符合边检查验标准。"));
            policy.setEntryCountEn(firstNonBlank(string(extra, "entryCountEn"),
                    "One-time transit entry permitted under China's 240-hour transit visa-free policy."));
            policy.setEntryCountZh(firstNonBlank(string(extra, "entryCountZh"),
                    "适用中国240小时过境免签政策，仅限单次过境入境。"));
            policy.setOnwardTicketEn(firstNonBlank(string(extra, "onwardTicketEn"),
                    "Confirmed non-stop onward ticket to a third country/region is a mandatory application condition."));
            policy.setOnwardTicketZh(firstNonBlank(string(extra, "onwardTicketZh"),
                    "必须持有前往第三国/地区的已确认联程机票，为过境免签核心硬性条件。"));
            policy.setAccommodationEn(firstNonBlank(string(extra, "accommodationEn"),
                    "Specified transit-area accommodation is required for in-city stay during transit period."));
            policy.setAccommodationZh(firstNonBlank(string(extra, "accommodationZh"),
                    "过境期间如需停留市区，需入住政策指定区域内住宿场所。"));
            policy.setFinancialProofEn(firstNonBlank(string(extra, "financialProofEn"),
                    "Basic financial proof may be requested for transit stay verification."));
            policy.setFinancialProofZh(firstNonBlank(string(extra, "financialProofZh"),
                    "过境停留核查时，可能需要提供基础资金证明。"));
            policy.setExtensionRuleEn(firstNonBlank(string(extra, "extensionRuleEn"),
                    "240-hour transit visa-free stay is non-extendable and cannot be converted to ordinary visa-free stay."));
            policy.setExtensionRuleZh(firstNonBlank(string(extra, "extensionRuleZh"),
                    "240小时过境免签停留期限不可延期，且无法转为普通免签停留资格。"));
        }

        populateFaqs(policy, detail, type, extra);
        return policy;
    }

    /**
     * 【终极去重+SEO优化】FAQ填充逻辑
     * 优化点：1. 彻底消除话术重复 2. 三类政策FAQ完全差异化 3. 精简语句、语义聚焦 4. 精准匹配用户搜索词
     * 核心逻辑：优先加载自定义FAQ，无自定义内容时才填充**唯一不重复的兜底FAQ**
     */
    private void populateFaqs(CountryPolicy policy,
                              CountryDetail detail,
                              String type,
                              Map<String, Object> extra) {
        // 加载自定义FAQ（优先展示，不重复兜底内容）
        List<CountryPolicy.PolicyFaq> en = toFaqs(extra.get("customFaqsEn"));
        List<CountryPolicy.PolicyFaq> zh = toFaqs(extra.get("customFaqsZh"));

        String name = detail.getName();
        String nameZh = detail.getNameZh();
        String stay = detail.getStayDays();
        String stayTextEn = isBlank(stay) ? "the regulated period" : stay + " days";
        String stayTextZh = isBlank(stay) ? "政策规定期限" : stay + "天";

        // ===================== 过境免签FAQ（独有特性：240小时、联程机票、区域限制、不可延期）=====================
        if ("transit".equals(type)) {
            // 无自定义FAQ时，仅填充**专属、无重复**核心问答
            if (en.isEmpty()) {
                addIfMissing(en, "Who is eligible for China 240-hour transit visa-free?",
                        name + " citizens meeting official nationality, itinerary and port requirements can enjoy China's 240-hour transit visa-free policy.");
                addIfMissing(en, "What is the maximum stay for China transit visa-free?",
                        "Eligible transit travelers can stay up to " + stayTextEn + " in designated transit areas.");
                addIfMissing(en, "Is a third-country onward ticket required for transit?",
                        "Yes. A confirmed onward ticket is a mandatory requirement for 240-hour transit visa-free entry.");
                addIfMissing(en, "Can transit visa-free stay time be extended in China?",
                        "No. The 240-hour transit visa-free duration is non-extendable under official regulations.");
            }
            if (zh.isEmpty()) {
                addIfMissing(zh, nameZh + "公民符合什么条件可享受中国240小时过境免签？",
                        "满足官方规定的适用国籍、第三国联程行程、指定入境口岸条件，即可享受过境免签政策。");
                addIfMissing(zh, "中国240小时过境免签最长可以停留多久？",
                        "符合条件人员可在指定过境区域内最长停留" + stayTextZh + "。");
                addIfMissing(zh, "过境免签必须要有第三国联程机票吗？",
                        "是的，持有已确认的第三国/地区联程机票是过境免签的硬性必备条件。");
                addIfMissing(zh, "中国过境免签停留期限可以延期吗？",
                        "不可以延期，240小时过境免签时长严格固定，无法延长或转为普通入境资格。");
            }
        }
        // ===================== 双边互免FAQ（独有特性：双边协定、多国籍适配、协议有效期）=====================
        else if ("mutual".equals(type)) {
            if (en.isEmpty()) {
                addIfMissing(en, "Do " + name + " passport holders enjoy visa-free entry to China?",
                        "Qualified passport holders can enter China visa-free per the official bilateral visa exemption agreement between China and " + name + ".");
                addIfMissing(en, "What is the visa-free stay limit for " + name + " citizens in China?",
                        "The bilateral agreement allows a maximum visa-free stay of " + stayTextEn + " per single entry.");
                addIfMissing(en, "Can visa-free entry be used for work or study in China?",
                        "Visa-free entry only applies to short-term visits, not for paid employment or formal academic study.");
                addIfMissing(en, "Is accommodation registration mandatory for foreign visitors in China?",
                        "All foreign residents must complete official accommodation registration within 24 hours of arrival.");
            }
            if (zh.isEmpty()) {
                addIfMissing(zh, nameZh + "护照可以免签入境中国吗？",
                        "符合资质的" + nameZh + "护照持有人，可依据中" + nameZh + "双边互免协定免签入境。");
                addIfMissing(zh, "双边互免签证单次入境可停留多久？",
                        "根据双边官方协定，单次入境最长可免签停留" + stayTextZh + "。");
                addIfMissing(zh, "互免签证入境可以在中国务工或留学吗？",
                        "不可以，互免签证仅适用于旅游、商务、探亲等短期访问，不支持工作和全日制学习。");
                addIfMissing(zh, "外籍人员来华需要办理住宿登记吗？",
                        "所有外籍人员入境住宿，均需在抵达后24小时内完成官方住宿登记手续。");
            }
        }
        // ===================== 单方面免签FAQ（独有特性：中国单方政策、固定入境目的、单次入境）=====================
        else {
            if (en.isEmpty()) {
                addIfMissing(en, "Does China offer unilateral visa-free entry for " + name + " citizens?",
                        "China provides unilateral visa-free access for qualified " + name + " ordinary passport holders with valid entry conditions.");
                addIfMissing(en, "How long is the unilateral visa-free stay period in China?",
                        "Eligible visitors can enjoy a maximum visa-free stay of " + stayTextEn + " per entry.");
                addIfMissing(en, "What activities are permitted under China unilateral visa-free policy?",
                        "Allowed activities include " + firstNonBlank(detail.getPurpose(), "tourism, business visits and short-term non-profit exchanges") + ".");
                addIfMissing(en, "Can I extend unilateral visa-free stay in China?",
                        "Limited extension is available for special situations via local immigration authority application.");
            }
            if (zh.isEmpty()) {
                addIfMissing(zh, "中国对" + nameZh + "是否实行单方面免签政策？",
                        "中国对符合条件的" + nameZh + "普通护照持有人实行单方面免签入境政策。");
                addIfMissing(zh, "单方面免签来华最长停留时长是多少？",
                        "依据中国官方政策，单次入境最长可免签停留" + stayTextZh + "。");
                addIfMissing(zh, "免签来华可以开展哪些活动？",
                        "仅可开展" + firstNonBlank(detail.getPurposeZh(), "旅游观光、商务拜访、短期非营利交流") + "等合规活动。");
                addIfMissing(zh, "单方面免签停留超时可以申请延期吗？",
                        "特殊情况可向当地出入境管理部门申请延期，常规短期访问不支持随意延期。");
            }
        }

        policy.setFaqsEn(en);
        policy.setFaqsZh(zh);
    }

    private void addIfMissing(List<CountryPolicy.PolicyFaq> list, String q, String a) {
        for (CountryPolicy.PolicyFaq faq : list) {
            if (q.equalsIgnoreCase(faq.getQ())) return;
        }
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
            if (!isBlank(code) && validCodes.contains(code) && !result.contains(code)) {
                result.add(code);
            }
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
            return mapper.readValue(
                    new ClassPathResource(file).getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load " + file, e);
        }
    }

    private Map<String, Map<String, Object>> loadExtraMap(ObjectMapper mapper) {
        try {
            return mapper.readValue(
                    new ClassPathResource("country-extra.json").getInputStream(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
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

    private String string(Map<String, Object> map, String key) {
        return map == null ? "" : string(map.get(key));
    }

    private String string(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String firstNonBlank(String value, String fallback) {
        return isBlank(value) ? fallback : value;
    }

    private boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
