package com.chinavisamap.entity;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 国家签证政策标准化模型。
 *
 * CountryDetail：
 *     保留原始政策数据。
 *
 * CountryPolicy：
 *     给页面、SEO、JSON-LD使用的标准化政策数据。
 *
 * 这样可以避免把政策规则硬编码在 Thymeleaf 模板中。
 */
@Data
public class CountryPolicy {

    private CountryDetail detail;

    /**
     * 国家代码
     */
    private String countryCode;

    /**
     * unilateral / mutual / transit
     */
    private String policyType;

    /**
     * 官方来源
     */
    private String officialSource;

    /**
     * 最近核验日期
     */
    private String lastVerified;

    /**
     * 政策有效期
     */
    private String policyExpiry;

    /**
     * 护照类型
     */
    private String passportTypeEn;
    private String passportTypeZh;

    /**
     * 护照有效期要求
     */
    private String passportValidityEn;
    private String passportValidityZh;

    /**
     * 入境次数
     */
    private String entryCountEn;
    private String entryCountZh;

    /**
     * 允许的入境目的
     */
    private String permittedPurposesEn;
    private String permittedPurposesZh;

    /**
     * 联程/返程机票要求
     */
    private String onwardTicketEn;
    private String onwardTicketZh;

    /**
     * 住宿证明
     */
    private String accommodationEn;
    private String accommodationZh;

    /**
     * 资金证明
     */
    private String financialProofEn;
    private String financialProofZh;

    /**
     * 延期规则
     */
    private String extensionRuleEn;
    private String extensionRuleZh;

    /**
     * FAQ
     */
    private List<PolicyFaq> faqsEn = new ArrayList<>();

    private List<PolicyFaq> faqsZh = new ArrayList<>();

    @Data
    public static class PolicyFaq {

        private String q;

        private String a;

        public PolicyFaq() {
        }

        public PolicyFaq(String q, String a) {
            this.q = q;
            this.a = a;
        }
    }
}