package com.chinavisamap.controller;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.SeoService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

import java.net.URI;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import com.chinavisamap.service.StructuredDataService;

@Controller
public class CountryController {

    // 原始三份政策json
    private Map<String, CountryDetail> unilateralMap;
    private Map<String, CountryDetail> mutualMap;
    private Map<String, CountryDetail> transitMap;
    private final SeoService seoService;
    private final StructuredDataService structuredDataService;


    // 新增：国家个性化扩展配置 country-extra.json
    private Map<String, Map<String, Object>> countryExtraMap;

    public CountryController(ObjectMapper objectMapper, SeoService seoService, StructuredDataService structuredDataService) {
        this.seoService = seoService;
        this.structuredDataService = structuredDataService;
        // 加载原始三份政策
        try {
            unilateralMap = objectMapper.readValue(
                    new ClassPathResource("unilateral.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
            mutualMap = objectMapper.readValue(
                    new ClassPathResource("mutual.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
            transitMap = objectMapper.readValue(
                    new ClassPathResource("transit.json").getInputStream(),
                    new TypeReference<Map<String, CountryDetail>>() {}
            );
        } catch (Exception ignored) {
        }

        // 加载新增 country-extra.json
        try {
            countryExtraMap = objectMapper.readValue(

                    new ClassPathResource("country-extra.json").getInputStream(),
                    new TypeReference<Map<String, Map<String, Object>>>() {}
            );
        } catch (Exception ignored) {
            countryExtraMap = null;
        }
    }


    /**
     * 旧接口：兼容历史访问地址 /country/{code}?type=xxx&lang=xxx
     * 不传type 则进入【国家聚合总页】
     */
    @GetMapping(
            value = "/country/{code}",
            params = "type"
    )
    public ResponseEntity<Void> legacyCountryRedirect(
            @PathVariable String code,
            @RequestParam String type,
            @RequestParam(defaultValue = "zh") String lang
    ) {

        CountryDetail detail = getCountryDetail(code, type);

        if (detail == null) {
            return ResponseEntity.notFound().build();
        }

        String location = UriComponentsBuilder
                .fromPath("/country/" + code + "/" + type)
                .queryParam("lang", lang)
                .build()
                .encode()
                .toUriString();

        HttpHeaders headers = new HttpHeaders();
        headers.setLocation(URI.create(location));

        return new ResponseEntity<>(
                headers,
                HttpStatus.MOVED_PERMANENTLY
        );
    }


    @GetMapping("/country/{code}")
    public String countryHome(
            @PathVariable String code,
            @RequestParam(defaultValue = "zh") String lang,
            Model model
    ) {

        List<String> availableTypes = detectAvailableTypes(code);

        if (availableTypes.isEmpty()) {
            return "redirect:/";
        }

        CountryDetail detailCountry =
                getAnyCountryDetail(code);

        Map<String, Object> countryExtraRoot =
                countryExtraMap != null
                        ? countryExtraMap.get(code)
                        : null;

        model.addAttribute("code", code);
        model.addAttribute("lang", lang);
        model.addAttribute("detailCountry", detailCountry);
        model.addAttribute("availableTypes", availableTypes);
        model.addAttribute("countryExtraRoot", countryExtraRoot);

        return "country-home";
    }


    /**
     * 新REST详情路径 /country/{code}/{type}?lang=xxx
     */
    @GetMapping("/country/{code}/{type}")
    public String restCountryDetail(
            @PathVariable String code,
            @PathVariable String type,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ) {
        CountryDetail detail = getCountryDetail(code, type);
        if (detail == null) {
            return "redirect:/";
        }
        String path = "/country/" + code + "/" + type;

        model.addAttribute(
                "canonicalUrl",
                seoService.canonical(path, lang)
        );

        model.addAttribute(
                "hreflang",
                seoService.hreflang(path)
        );

        model.addAttribute(
                "structuredData",
                structuredDataService.buildCountry(
                        detail,
                        lang,
                        seoService.canonical(path, lang)
                )
        );

        Object countryExtra = getCountryExtraItem(code, type);

        // 空Map置null，避免模板层 countryExtra != null 通过后取key得到null
        if (countryExtra instanceof Map) {
            Map<?, ?> extraMap = (Map<?, ?>) countryExtra;
            if (extraMap.isEmpty()) {
                countryExtra = null;
            }
        }
        CountryPolicy policy = buildCountryPolicy(code, type, detail);
        model.addAttribute("detail", detail);
        model.addAttribute("policy", policy);
        model.addAttribute("code", code);
        model.addAttribute("type", type);
        model.addAttribute("lang", lang);
       // model.addAttribute("canonicalUrl", canonicalUrl);
        model.addAttribute("countryExtra", countryExtra);
        return "country-detail";
    }


    // =====================内部工具方法=====================

    /** 根据code+type获取政策详情 */
    private CountryDetail getCountryDetail(String code, String type) {
        if ("unilateral".equals(type)) {
            return unilateralMap != null ? unilateralMap.get(code) : null;
        } else if ("mutual".equals(type)) {
            return mutualMap != null ? mutualMap.get(code) : null;
        } else if ("transit".equals(type)) {
            return transitMap != null ? transitMap.get(code) : null;
        }
        return null;
    }

    /** 获取该code+type对应的extra扩展对象 */
    private Object getCountryExtraItem(String code, String type) {
        if (countryExtraMap == null) return null;
        Map<String, Object> countryNode = countryExtraMap.get(code);
        if (countryNode == null) return null;
        return countryNode.get(type);
    }

    /**
     * 检测一个国家拥有哪些可用政策type
     * 遍历三份map，收集存在的type，返回集合给聚合页
     */
    private List<String> detectAvailableTypes(String code) {
        List<String> list = new ArrayList<>();
        if (unilateralMap != null && unilateralMap.containsKey(code)) {
            list.add("unilateral");
        }
        if (mutualMap != null && mutualMap.containsKey(code)) {
            list.add("mutual");
        }
        if (transitMap != null && transitMap.containsKey(code)) {
            list.add("transit");
        }
        return list;
    }

    /**
     * 获取该国家任意一份存在的CountryDetail，用于聚合页展示国家基础信息
     */
    private CountryDetail getAnyCountryDetail(String code) {
        if (unilateralMap != null && unilateralMap.containsKey(code)) {
            return unilateralMap.get(code);
        }
        if (mutualMap != null && mutualMap.containsKey(code)) {
            return mutualMap.get(code);
        }
        if (transitMap != null && transitMap.containsKey(code)) {
            return transitMap.get(code);
        }
        return null;
    }

    private CountryPolicy buildCountryPolicy(
            String code,
            String type,
            CountryDetail detail
    ) {
        CountryPolicy policy = new CountryPolicy();

        policy.setDetail(detail);
        policy.setCountryCode(code);
        policy.setPolicyType(type);

        policy.setOfficialSource(
                "https://en.nia.gov.cn/"
        );

        /*
         * 这里暂时使用统一核验日期。
         *
         * 后面建议改成 country-extra.json 中每条政策自己的
         * lastVerified 字段。
         */
        policy.setLastVerified("2026-09-05");

        if ("unilateral".equals(type)) {

            policy.setPassportTypeEn(
                    "Valid ordinary passport"
            );
            policy.setPassportTypeZh(
                    "有效普通护照"
            );

            policy.setPassportValidityEn(
                    "Passport validity should meet the applicable entry requirements."
            );
            policy.setPassportValidityZh(
                    "护照有效期应符合实际入境要求。"
            );

            policy.setEntryCountEn(
                    "Multiple entries may be possible during the policy validity period, subject to applicable rules."
            );
            policy.setEntryCountZh(
                    "在政策有效期内是否可以多次入境，以适用政策及边检实际执行为准。"
            );

            policy.setPermittedPurposesEn(
                    detail.getPurpose()
            );
            policy.setPermittedPurposesZh(
                    detail.getPurposeZh()
            );

            policy.setOnwardTicketEn(
                    "A return or onward ticket is recommended."
            );
            policy.setOnwardTicketZh(
                    "建议准备返程或联程机票。"
            );

            policy.setAccommodationEn(
                    "Hotel booking or accommodation information may be requested."
            );
            policy.setAccommodationZh(
                    "可能需要提供酒店订单或住宿信息。"
            );

            policy.setFinancialProofEn(
                    "Financial proof may be requested when appropriate."
            );
            policy.setFinancialProofZh(
                    "视实际情况可能需要提供资金证明。"
            );

            policy.setExtensionRuleEn(
                    "If an extension is necessary, contact the local immigration administration before the permitted stay expires."
            );
            policy.setExtensionRuleZh(
                    "如确有延期需要，应在允许停留期限届满前向当地公安机关出入境管理机构咨询并申请。"
            );

            buildUnilateralFaq(policy, detail);

        } else if ("mutual".equals(type)) {

            policy.setPassportTypeEn(
                    "Passport or travel document specified by the applicable bilateral agreement"
            );
            policy.setPassportTypeZh(
                    "适用双边协定规定的护照或旅行证件"
            );

            policy.setPassportValidityEn(
                    "Passport requirements are subject to the applicable bilateral agreement."
            );
            policy.setPassportValidityZh(
                    "护照有效期要求以适用的双边协定为准。"
            );

            policy.setEntryCountEn(
                    "Entry conditions and number of entries are subject to the bilateral agreement."
            );
            policy.setEntryCountZh(
                    "入境次数及相关条件以双边协定规定为准。"
            );

            policy.setPermittedPurposesEn(
                    detail.getPurpose()
            );
            policy.setPermittedPurposesZh(
                    detail.getPurposeZh()
            );

            policy.setOnwardTicketEn(
                    "A return or onward ticket is recommended."
            );
            policy.setOnwardTicketZh(
                    "建议准备返程或联程机票。"
            );

            policy.setAccommodationEn(
                    "Accommodation information may be requested."
            );
            policy.setAccommodationZh(
                    "视实际情况可能需要提供住宿信息。"
            );

            policy.setFinancialProofEn(
                    "Financial proof may be requested when appropriate."
            );
            policy.setFinancialProofZh(
                    "视实际情况可能需要提供资金证明。"
            );

            policy.setExtensionRuleEn(
                    "Extension rules depend on the applicable bilateral agreement and Chinese immigration regulations."
            );
            policy.setExtensionRuleZh(
                    "延期规则以适用双边协定及中国出入境管理规定为准。"
            );

            buildMutualFaq(policy, detail);

        } else if ("transit".equals(type)) {

            policy.setPassportTypeEn(
                    "Valid passport of an eligible nationality"
            );
            policy.setPassportTypeZh(
                    "符合条件国家公民持有效护照"
            );

            policy.setPassportValidityEn(
                    "Passport must satisfy the applicable transit-entry requirements."
            );
            policy.setPassportValidityZh(
                    "护照有效期应符合适用的过境入境要求。"
            );

            policy.setEntryCountEn(
                    "Transit entry under the applicable 240-hour visa-free policy."
            );
            policy.setEntryCountZh(
                    "适用240小时过境免签政策的过境入境。"
            );

            policy.setPermittedPurposesEn(
                    "Transit to a third country or region, subject to applicable visa-free transit rules."
            );
            policy.setPermittedPurposesZh(
                    "须符合前往第三国或地区的过境免签条件。"
            );

            policy.setOnwardTicketEn(
                    "A confirmed onward ticket to a third country or region is required."
            );
            policy.setOnwardTicketZh(
                    "需要持有前往第三国或地区的已确定联程客票。"
            );

            policy.setAccommodationEn(
                    "Accommodation information may be requested."
            );
            policy.setAccommodationZh(
                    "视实际情况可能需要提供住宿信息。"
            );

            policy.setFinancialProofEn(
                    "Financial proof may be requested when appropriate."
            );
            policy.setFinancialProofZh(
                    "视实际情况可能需要提供资金证明。"
            );

            policy.setExtensionRuleEn(
                    "The applicable transit visa-free stay cannot be extended as an ordinary visa-free stay."
            );
            policy.setExtensionRuleZh(
                    "过境免签停留期限不能按照普通免签停留方式办理延期。"
            );

            buildTransitFaq(policy);
        }

        return policy;
    }


    private void buildUnilateralFaq(
            CountryPolicy policy,
            CountryDetail detail
    ) {

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "How is the visa-free stay period calculated?",
                        "The permitted stay is calculated according to the applicable visa-free policy and Chinese immigration rules."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "免签停留期限如何计算？",
                        "停留期限按照适用免签政策及中国出入境管理规定计算。"
                )
        );

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "Do I need accommodation registration?",
                        "Foreign visitors staying in China generally need to complete accommodation registration according to applicable regulations."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "需要办理住宿登记吗？",
                        "外国人在中国境内住宿，一般需要按照相关规定办理住宿登记。"
                )
        );

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "Can I work or study during a visa-free stay?",
                        "Visa-free entry does not automatically authorize employment or study. Check the applicable policy before engaging in such activities."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "免签期间可以工作或者学习吗？",
                        "免签入境并不当然赋予工作或学习资格，开展相关活动前应确认适用规定。"
                )
        );
    }

    private void buildMutualFaq(
            CountryPolicy policy,
            CountryDetail detail
    ) {

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "What passport can be used?",
                        "Eligible passport types depend on the applicable bilateral visa exemption agreement."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "哪些护照可以享受互免签证？",
                        "具体适用护照种类以相关双边互免签证协定为准。"
                )
        );

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "How long can I stay in China?",
                        "The permitted stay is determined by the applicable bilateral agreement."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "可以在中国停留多久？",
                        "具体允许停留期限以相关双边协定规定为准。"
                )
        );
    }

    private void buildTransitFaq(
            CountryPolicy policy
    ) {

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "What is the 240-hour transit visa-free policy?",
                        "Eligible travelers from participating countries may transit through designated areas of China without a visa for up to 240 hours, subject to applicable conditions."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "什么是240小时过境免签？",
                        "符合条件的国家公民在满足相关条件的情况下，可以在中国指定区域享受最长240小时过境免签停留。"
                )
        );

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "Do I need an onward ticket?",
                        "Yes. The traveler must satisfy the applicable transit requirement for travel to a third country or region."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "需要联程机票吗？",
                        "需要满足前往第三国或地区的过境条件，并持有符合要求的联程客票。"
                )
        );

        policy.getFaqsEn().add(
                new CountryPolicy.PolicyFaq(
                        "Can I travel outside the permitted area?",
                        "Travel is limited by the applicable designated regions and ports of the transit visa-free policy."
                )
        );

        policy.getFaqsZh().add(
                new CountryPolicy.PolicyFaq(
                        "可以离开规定区域旅行吗？",
                        "活动范围受到过境免签政策规定的适用区域和口岸限制。"
                )
        );
    }
}
