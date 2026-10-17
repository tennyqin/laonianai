package com.chinavisamap.service;

import com.chinavisamap.entity.CountryDetail;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import java.time.*;
import java.util.*;

/** Shared, conservative screening rules. Display text is never parsed as legal rules. */
@Service
public class PolicyRuleEngine {
    private final CountryCodeResolver resolver;
    private final Map<String,Object> data;
    private final Map<String,Map<String,CountryDetail>> policies = new LinkedHashMap<>();
    public PolicyRuleEngine(CountryCodeResolver resolver, ObjectMapper mapper) {
        this.resolver=resolver;
        try {
            data=mapper.readValue(new ClassPathResource("policy-rules.json").getInputStream(),new TypeReference<Map<String,Object>>(){});
            LocalDate.parse(text(data.get("verifiedAsOf")));
            for(String type:Arrays.asList("unilateral","mutual","transit","hainan")) {
                policies.put(type,mapper.readValue(new ClassPathResource(type+".json").getInputStream(),new TypeReference<Map<String,CountryDetail>>(){}));
            }
        } catch(Exception e) { throw new IllegalStateException("Cannot load required policy screening data",e); }
    }
    public String today() { return LocalDate.now(ZoneId.of("Asia/Shanghai")).toString(); }
    public boolean countryExists(String code) { String k=resolver.policyKey(code); return policies.values().stream().anyMatch(m->m.containsKey(k)); }
    public Map<String,Object> rule(CountryDetail p) {
        Map<String,Object> r=new LinkedHashMap<>(map(map(data.get("defaults")).get(p.getPolicyType())));
        r.putAll(map(map(map(data.get("countries")).get(resolver.policyKey(p.getCode()))).get(p.getPolicyType())));
        r.putIfAbsent("verifiedAsOf",data.get("verifiedAsOf"));
        r.put("knownFrom",empty(p.getValidFrom())?data.getOrDefault("knownFrom",data.get("verifiedAsOf")):p.getValidFrom());
        r.put("policyExpiry",text(p.getPolicyExpiry()));
        if(!r.containsKey("perEntryDays")) r.put("perEntryDays",positive(p.getStayDays()));
        return r;
    }
    public boolean policyAvailableToday(String code, String type) {
        Map<String,CountryDetail> group=policies.get(type);
        CountryDetail p=group==null?null:group.get(resolver.policyKey(code));
        return p!=null && available(rule(p),LocalDate.parse(today()));
    }
    public boolean currentDirectRoute(String code) {
        for(String type:Arrays.asList("unilateral","mutual")) {
            CountryDetail p=policies.get(type).get(resolver.policyKey(code));
            if(p!=null && available(rule(p),LocalDate.parse(today()))) return true;
        }
        return false;
    }
    public Map<String,Object> evaluate(String code,Map<String,Object> input,boolean calculate) {
        if(input==null) return result("invalid","input_required","Enter your trip details.","请填写行程。",null);
        if("auto".equals(text(input.get("entryMode")))) {
            Map<String,Object> direct=new LinkedHashMap<>(input); direct.put("entryMode","direct");
            Map<String,Object> answer=evaluate(code,direct,calculate);
            answer.put("recommendedRoute","direct");
            // Do not bypass missing passport/date/purpose checks or an agreement's cumulative rules.
            if(!Arrays.asList("no_direct_rule","duration_exceeded").contains(answer.get("reason")) || calculate) return answer;
            Map<String,Object> transit=new LinkedHashMap<>(input); transit.put("entryMode","transit240");
            if(text(input.get("origin")).isEmpty()||text(input.get("destination")).isEmpty()) {
                if("duration_exceeded".equals(answer.get("reason"))) return answer;
                answer.put("status","pending"); answer.put("needsItinerary",true); return answer;
            }
            Map<String,Object> alternative=evaluate(code,transit,false); alternative.put("recommendedRoute","transit240");
            return alternative;
        }
        boolean generalTransit="transit24".equals(text(input.get("entryMode"))) && Arrays.asList(Locale.getISOCountries()).contains(text(code).toUpperCase(Locale.ROOT)) && !Arrays.asList("CN","HK","MO","TW").contains(text(code).toUpperCase(Locale.ROOT));
        if(!countryExists(code)&&!generalTransit) return result("invalid","unknown_country","No policy data is available for this country.","没有该国籍的政策数据。",null);
        LocalDate entry;
        try { entry=LocalDate.parse(text(input.get("entryDate"))); if(entry.getYear()<1900||entry.getYear()>2100) throw new IllegalArgumentException(); }
        catch(Exception e) { return result("invalid","entry_date_required","Enter a valid planned entry date.","请输入有效的计划入境日期。",null); }
        String mode=text(input.get("entryMode"));
        if(!Arrays.asList("direct","transit240","transit24","hainan").contains(mode)) return result("invalid","invalid_route","Select an entry route.","请选择入境路径。",null);
        double days;
        try { days=Double.parseDouble(text(input.get("days"))); }
        catch(Exception e) { days=Double.NaN; }
        boolean datedDeparture=false;
        if(!"transit24".equals(mode)&&!text(input.get("departureDate")).isEmpty()) {
            try {
                LocalDate departure=LocalDate.parse(text(input.get("departureDate")));
                if(departure.isBefore(entry)||departure.getYear()>2100) throw new IllegalArgumentException();
                days=java.time.temporal.ChronoUnit.DAYS.between(entry,departure); datedDeparture=true;
            } catch(Exception e) {return result("invalid","invalid_departure_date","Departure must be on or after arrival, using China local dates.","离境日期不得早于入境日期，请使用中国当地日期。",null);}
        }
        if(!Double.isFinite(days)||(datedDeparture?days<0:days<=0)||days>3650||(!"transit24".equals(mode)&&days!=Math.floor(days))) return result("invalid","invalid_duration","Enter a positive whole number of days (hours for 24-hour transit).","请输入有效的整数天数；24小时过境按小时填写。",null);
        String purpose=text(input.get("purpose"));
        if(!Arrays.asList("tourism","business","family","exchange","medical","conference","sports","study_tour","transit","study","work","other","visit").contains(purpose)) return result("invalid","invalid_purpose","Select a valid purpose.","请选择有效出行事由。",null);
        if(!"transit24".equals(mode) && !"ordinary".equals(text(input.get("passport")))) return result("pending","passport_type","This checker cannot verify diplomatic, official or other travel documents for this route. The general 24-hour transit facility has separate travel-document rules.","本路径不能核验外交、公务或其他旅行证件；一般24小时过境适用独立旅行证件规则。",null);
        if("work".equals(purpose)||"study".equals(purpose)) return result("pending","visa_purpose","Work and formal study normally require the corresponding visa or authorization. Short study tours are a separate purpose.","工作、正式留学通常须相应签证或许可；符合条件的短期研学是另一个事由。",null);
        if(calculate && !"direct".equals(mode)) return result("pending","calculator_scope","This calculator only supports verified unilateral 30-day entry; it does not calculate transit or bilateral stay limits.","本计算器仅支持已核实的单方面30天规则，不计算过境或双边协定期限。",null);
        String key=resolver.policyKey(code);
        if("transit24".equals(mode)) {
            if(days>24) return result("ineligible","duration_exceeded","The stated transit exceeds 24 hours.","所填过境时间超过24小时。",null);
            Map<String,Object> routeError=checkItinerary(input); if(routeError!=null)return routeError;
            Map<String,Object> out=result("pending","transit24_port_check","The itinerary screen passed. Valid documents and port conditions still require verification. Leaving the restricted area requires a temporary entry permit; it is not automatic.","行程基础检查通过；仍须核验有效旅行证件及口岸条件。离开限定区域须申请临时入境许可，并非自动获准。",map(map(data.get("defaults")).get("transit")));
            out.put("missingChecks",Arrays.asList("travel_documents","entry_exit_ports","temporary_entry_permission")); return out;
        }
        if("transit240".equals(mode)) {
            CountryDetail p=policies.get("transit").get(key);
            if(p==null) return result("pending","transit_not_listed","This nationality is not in the site's 240-hour list. Check current official eligibility and other routes.","本站240小时名单未列该国籍，请核对官方现行资格及其他路径。",null);
            Map<String,Object> r=rule(p); if(!available(r,entry)) return datePending(r);
            if(!purposes(r).contains(purpose))return result("pending","purpose_not_verified","The selected purpose has not been verified for this route.","所选事由尚未核实适用这条路径。",r);
            if(days>10) return result("ineligible","duration_exceeded","The stated stay exceeds the listed 240-hour (10-day) limit. Verify the admitted timing with immigration.","所填停留超过记录的240小时（10天）上限，具体计时须核对入境许可。",r);
            Map<String,Object> routeError=checkItinerary(input); if(routeError!=null)return routeError;
            Map<String,Object> out=result("pending","port_area_unverified","Nationality, purpose and A→China→C itinerary screening passed. The entry/exit ports, permitted areas and document conditions have NOT been automatically verified. Check the official port list before booking.","国籍、事由及A→中国→C行程基础检查通过，但入出境口岸、活动区域和证件条件尚未自动核实，请按官方名单核对后再安排出行。",r);
            out.put("missingChecks",Arrays.asList("entry_exit_ports","permitted_areas","travel_documents","admitted_timing")); return out;
        }
        if("hainan".equals(mode)) {
            CountryDetail p=policies.get("hainan").get(key);
            if(p==null)return result("pending","hainan_not_listed","This nationality is not in the site's Hainan list; verify current official eligibility.","本站海南名单未列该国籍，请核对官方现行资格。",null);
            Map<String,Object> r=rule(p); if(!available(r,entry))return datePending(r);
            if(days>30)return result("ineligible","duration_exceeded","The stated stay exceeds the listed Hainan 30-day limit.","所填停留超过海南30天上限。",r);
            if(!purposes(r).contains(purpose))return result("pending","purpose_not_verified","The selected purpose needs separate verification.","该事由须单独核验。",r);
            Map<String,Object> out=result("pending","hainan_conditions","The basic nationality, purpose and duration match the listed Hainan route. Verify the open Hainan port, passport and admitted stay period; activities must remain within Hainan Province.","国籍、事由和时长符合记录的海南基础条件；仍须核对海南开放口岸、护照及获准期限，活动范围限海南省内。",r);
            out.put("missingChecks",Arrays.asList("hainan_ports","hainan_only","travel_documents"));
            if("next_day_calendar".equals(r.get("countingMethod")))out.put("indicativeLatestDeparture",entry.plusDays(30).toString());
            return out;
        }
        Map<String,Object> deferred=null;
        for(String type:Arrays.asList("unilateral","mutual")) {
            CountryDetail p=policies.get(type).get(key); if(p==null)continue;
            Map<String,Object> r=rule(p);
            if(!available(r,entry)){deferred=datePending(r);continue;}
            if("mutual".equals(type)) {
                if(calculate){deferred=result("pending","calculator_scope","Bilateral counting and cumulative rules are not supported by this calculator.","本计算器不计算双边协定的计时和累计规则。",r);continue;}
                if(datedDeparture && !Boolean.TRUE.equals(r.get("limitsVerified")))return result("pending","agreement_verification","The bilateral counting rule is not verified. Check the agreement before treating your arrival and departure dates as an allowed stay.","尚未核实本双边协定的计时规则，不能仅凭入出境日期判断获准停留。",r);
                if(!purposes(r).isEmpty()&&!purposes(r).contains(purpose))return result("pending","purpose_not_verified","Check whether the bilateral agreement covers your actual activity.","请核对双边协定是否覆盖你的实际活动。",r);
                if(Boolean.TRUE.equals(r.get("limitsVerified"))&&number(r.get("perEntryDays"))>0&&days>number(r.get("perEntryDays")))return result("ineligible","duration_exceeded","The stated stay exceeds the verified per-entry limit.","所填停留超过已核实的单次上限。",r);
                Map<String,Object> out=result("pending","agreement_verification","A bilateral route is listed. Verify the exact passport, purpose, counting and cumulative rules; previous stays have not been checked.","名单列有双边互免路径；须核验护照、事由、计时及累计条款，本次未检查历史停留。",r);
                out.put("perEntryDays",r.get("perEntryDays"));out.put("cumulativeDays",r.get("cumulativeDays"));out.put("cumulativeWindowDays",r.get("cumulativeWindowDays"));out.put("missingChecks",Arrays.asList("agreement_conditions","previous_stays","travel_documents"));return out;
            }
            if(!purposes(r).contains(purpose)){deferred=result("pending","purpose_not_verified","The selected purpose is not verified under this unilateral rule.","所选事由尚未核实属于该单方面免签规则。",r);continue;}
            if(days>number(r.get("perEntryDays")))return result("ineligible","duration_exceeded","The stated stay exceeds the unilateral 30-day limit; check the appropriate visa route.","所填停留超过单方面30天上限，请核对相应签证路径。",r);
            if(!Boolean.TRUE.equals(input.get("passportValid")))return result("pending","passport_validity","Confirm that your ordinary passport remains valid for your intended stay.","请确认普通护照有效期覆盖拟在华停留时间。",r);
            Map<String,Object> out=result("eligible","unilateral_basic_match","The entered date, purpose and duration match the recorded unilateral rule, based on your passport confirmation. This is a screening result, not guaranteed admission; recheck the official source before departure.","按你的护照确认，所填日期、事由和时长符合记录的单方面规则。此为基础筛查，不保证获准入境，出发前须再次核对官方信息。",r);
            if("next_day_calendar".equals(r.get("countingMethod"))) {
                out.put("countingStart",entry.plusDays(1).toString());out.put("latestDeparture",entry.plusDays(number(r.get("perEntryDays"))).toString());
                out.put("plannedDeparture",entry.plusDays((int)days).toString());out.put("timezone","Asia/Shanghai");
                out.put("departureNoteEn","Complete exit before the permitted final day ends in China time. Policy availability expiry is not the individual's departure deadline; follow any period specified by immigration.");
                out.put("departureNoteZh","在中国时间最后获准停留日结束前完成出境；政策可适用截止日不等于个人离境截止日，并以主管机关注明的期限为准。");
            }
            return out;
        }
        return deferred!=null?deferred:result("pending","no_direct_rule","No verified general direct route is available in this dataset for the trip. Check other routes or the visa guide.","当前数据未确认该行程的一般直接免签路径，请核对其他路径或签证指南。",null);
    }
    private Map<String,Object> checkItinerary(Map<String,Object> input) {
        String a=text(input.get("origin")),c=text(input.get("destination"));
        if(a.isEmpty()||c.isEmpty())return result("invalid","itinerary_required","Select the country/region immediately before China and the next destination after China.","请选择紧邻中国行程的前段出发及后续目的国家或地区。",null);
        // ISO-2 values only. Select options are populated using Java's ISO country list.
        Set<String> codes=new HashSet<>(Arrays.asList(Locale.getISOCountries()));
        if(!codes.contains(a)||!codes.contains(c))return result("invalid","invalid_itinerary","Select recognized countries/regions.","请选择列表中的国家或地区。",null);
        if(a.equals(c)||"CN".equals(a)||"CN".equals(c))return result("ineligible","not_third_country","The entered itinerary does not meet A→China→C with different preceding and onward countries/regions. Hong Kong and Macao are separate region choices.","所填路线不满足前后国家或地区不同的A→中国→C行程；香港、澳门按单独地区选择。",null);
        if(!Boolean.TRUE.equals(input.get("onward")))return result("pending","onward_required","A confirmed onward international ticket specifying the date and seat is required.","须有日期和座位已确定的国际联程客票。",null);
        return null;
    }
    private boolean available(Map<String,Object> r,LocalDate date) {
        try {
            if(date.isBefore(LocalDate.parse(text(r.get("knownFrom")))))return false;
            String end=text(r.get("policyExpiry"));
            return end.isEmpty()?Boolean.TRUE.equals(r.get("noPublishedExpiry")):!date.isAfter(LocalDate.parse(end));
        } catch(Exception e) { return false; }
    }
    private Map<String,Object> datePending(Map<String,Object> r) { return result("pending","policy_date_unconfirmed","This dataset does not confirm the policy for the selected entry date. An unknown start or extension is not treated as permission.","本数据未确认所选入境日期适用的政策，未知起始日期或续期不视为已获准。",r); }
    private Map<String,Object> result(String status,String code,String en,String zh,Map<String,Object> rule) {
        Map<String,Object> out=new LinkedHashMap<>();out.put("status",status);out.put("reason",code);out.put("messageEn",en);out.put("messageZh",zh);
        if(rule!=null){out.put("officialSource",rule.get("source"));out.put("verifiedAsOf",rule.getOrDefault("verifiedAsOf",data.get("verifiedAsOf")));}return out;
    }
    @SuppressWarnings("unchecked") private Map<String,Object> map(Object o) { return o instanceof Map?(Map<String,Object>)o:Collections.emptyMap(); }
    @SuppressWarnings("unchecked") private List<String> purposes(Map<String,Object> r) { return r.get("purposes") instanceof List?(List<String>)r.get("purposes"):Collections.emptyList(); }
    private boolean empty(String s){return s==null||s.trim().isEmpty();}
    private String text(Object o){return o==null?"":String.valueOf(o).trim();}
    private Integer positive(String s){try{int n=Integer.parseInt(s);return n>0?n:null;}catch(Exception e){return null;}}
    private int number(Object o){try{return Integer.parseInt(text(o));}catch(Exception e){return 0;}}
}
