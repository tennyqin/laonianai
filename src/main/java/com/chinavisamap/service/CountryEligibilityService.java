package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class CountryEligibilityService {
    private static final Set<String> HAINAN_COUNTRIES = Set.of(
            "albania","argentina","australia","austria","belarus","belgium","bosnia","brazil","brunei","bulgaria","canada","chile","croatia","cyprus","czechia","denmark","estonia","finland","france","germany","greece","hungary","iceland","indonesia","ireland","italy","japan","kazakhstan","kyrgyzstan","latvia","lithuania","luxembourg","malaysia","malta","mexico","monaco","montenegro","netherlands","newzealand","northmacedonia","norway","philippines","poland","portugal","qatar","korea","romania","russia","serbia","singapore","slovakia","slovenia","spain","sweden","switzerland","thailand","ukraine","uae","uk","usa","vietnam"
    );
    private static final Set<String> TRANSIT_240_COUNTRIES = Set.of(
            "albania","austria","belarus","belgium","bosnia","bulgaria","croatia","cyprus","czechia","denmark","estonia","finland","france","germany","greece","hungary","iceland","ireland","italy","latvia","lithuania","luxembourg","malta","monaco","montenegro","netherlands","northmacedonia","norway","poland","portugal","romania","russia","serbia","slovakia","slovenia","spain","sweden","switzerland","ukraine","uk","canada","usa","argentina","brazil","chile","mexico","australia","newzealand","brunei","indonesia","japan","kyrgyzstan","qatar","singapore","korea","uae","vietnam"
    );
    private static final Set<String> CURRENT_UNILATERAL_30 = Set.of("kyrgyzstan","vietnam");

    public Map<String,Object> build(String countryCode,List<CountryDetail> policies,Map<String,Object> extra){
        String normalized=normalize(countryCode);List<Map<String,Object>> policyRules=buildPolicies(policies);
        if(CURRENT_UNILATERAL_30.contains(normalized) && policyRules.stream().noneMatch(p->"unilateral".equals(p.get("type")))){
            Map<String,Object> p=new LinkedHashMap<>();p.put("type","unilateral");p.put("stayDays",30);p.put("purposes",List.of("tourism","business","family","exchange","transit"));p.put("requiresOnward",false);p.put("ordinaryPassportOnly",true);p.put("sourceRule","Ordinary passport holders may enter visa-free for business, tourism, visits to relatives and friends, exchange visits or transit, for up to 30 days.");policyRules.add(p);
        }
        Map<String,Object> root=new LinkedHashMap<>();root.put("version","2026-08-20");root.put("passportTypes",List.of("ordinary"));root.put("ordinaryPassportOnly",true);root.put("policies",policyRules);
        root.put("hainan",HAINAN_COUNTRIES.contains(normalized)?Map.of("enabled",true,"maxStayDays",30,"purposes",List.of("tourism","business","family","visit","medical","exhibition","sports"),"restrictedToHainan",true,"requiresOrdinaryPassport",true,"officialSource","https://en.nia.gov.cn/n147418/n147463/c180637/content.html"):Map.of("enabled",false));
        root.put("transit240",TRANSIT_240_COUNTRIES.contains(normalized)?Map.of("enabled",true,"maxStayDays",10,"requiresOrdinaryPassport",true,"requiresThirdCountryOrRegion",true,"requiresConfirmedOnwardTicket",true,"designatedPorts",65,"officialSource","https://en.nia.gov.cn/n147418/n147463/c183412/content.html"):Map.of("enabled",false));
        root.put("transit24",Map.of("enabled",true,"maxStayHours",24,"requiresConfirmedOnwardTicket",true,"restrictedAreaOnly",true,"officialSource","https://en.nia.gov.cn/n147418/n147463/c183412/content.html"));
        root.put("priority",extra==null?999:number(extra.get("priority"),999));return root;
    }

    private List<Map<String,Object>> buildPolicies(List<CountryDetail> policies){List<Map<String,Object>>r=new ArrayList<>();if(policies==null)return r;for(CountryDetail p:policies){if(p==null)continue;Map<String,Object>x=new LinkedHashMap<>();String type=safe(p.getPolicyType());x.put("type",type);x.put("stayDays",number(p.getStayDays(),0));x.put("purposes",purposeCodes(p.getPurpose()));x.put("requiresOnward","transit".equals(type));x.put("ordinaryPassportOnly",true);x.put("sourceRule",safe(p.getRule()));r.add(x);}return r;}
    private List<String>purposeCodes(String text){String v=safe(text).toLowerCase(Locale.ROOT);List<String>r=new ArrayList<>();if(v.contains("tourism"))r.add("tourism");if(v.contains("business"))r.add("business");if(v.contains("family"))r.add("family");if(v.contains("exchange"))r.add("exchange");if(v.contains("transit"))r.add("transit");if(v.contains("visit"))r.add("visit");return r;}
    private String normalize(String v){return v==null?"":v.toLowerCase(Locale.ROOT).replaceAll("[^a-z]","");}
    private int number(Object v,int f){try{return Integer.parseInt(safe(v));}catch(Exception e){return f;}}
    private String safe(Object v){return v==null?"":String.valueOf(v).trim();}
}