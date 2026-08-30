package com.chinavisamap.entity;

import lombok.Data;

@Data
public class CountryDetail {
    private String code;
    private String name;
    private String nameZh;
    private String continent;
    private String continentZh;
    private String policyType;
    private String policyTypeZh;
    private String stayDays;
    private String validFrom;
    private String purpose;
    private String purposeZh;
    private String rule;
    private String ruleZh;
    private String notes;
    private String notesZh;
    private String ports;
    private String portsZh;
    private String seoTitle;
    private String seoTitleZh;
    private String seoDesc;
    private String seoDescZh;
}