package com.chinavisamap.web;

import com.chinavisamap.entity.CountryDetail;
import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.CountryCodeResolver;
import com.chinavisamap.service.StructuredDataService;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class CountryPolicyCorrectionInterceptor implements HandlerInterceptor {
    private static final String VERIFIED = "2026-09-09";
    private final CountryCodeResolver resolver;
    private final StructuredDataService structuredDataService;

    public CountryPolicyCorrectionInterceptor(CountryCodeResolver resolver, StructuredDataService structuredDataService) {
        this.resolver = resolver;
        this.structuredDataService = structuredDataService;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, ModelAndView modelAndView) {
        if (modelAndView == null) return;
        Object policyObject = modelAndView.getModel().get("policy");
        Object policyDetailObject = modelAndView.getModel().get("detail");
        if (policyObject instanceof CountryPolicy && policyDetailObject instanceof CountryDetail) {
            CountryPolicy policy = (CountryPolicy) policyObject;
            CountryDetail detail = (CountryDetail) policyDetailObject;
            applyCorrection(resolver.policyKey(detail.getCode()), detail, policy);
            policy.setLastVerified(VERIFIED);
            String lang = String.valueOf(modelAndView.getModel().get("lang"));
            String canonical = String.valueOf(modelAndView.getModel().get("canonicalUrl"));
            Object extraObject = modelAndView.getModel().get("countryExtra");
            Map<String, Object> extra = extraObject instanceof Map ? (Map<String, Object>) extraObject : null;
            modelAndView.getModel().put("structuredData", structuredDataService.buildCountry(detail, lang, canonical, policy, extra));
            return;
        }
        if (!"country-home".equals(modelAndView.getViewName())) return;
        Object policiesObject = modelAndView.getModel().get("availablePolicies");
        if (!(policiesObject instanceof List)) return;
        for (Object item : (List<?>) policiesObject) {
            if (!(item instanceof CountryDetail)) continue;
            CountryDetail detail = (CountryDetail) item;
            applyCorrection(resolver.policyKey(detail.getCode()), detail, null);
        }
        Object countryObject = modelAndView.getModel().get("detailCountry");
        Object extraRootObject = modelAndView.getModel().get("countryExtraRoot");
        Object typesObject = modelAndView.getModel().get("availableTypes");
        if (!(countryObject instanceof CountryDetail) || !(extraRootObject instanceof Map) || !(typesObject instanceof List)) return;
        Map<String, CountryDetail> details = new LinkedHashMap<>();
        for (Object item : (List<?>) policiesObject) {
            if (!(item instanceof CountryDetail)) continue;
            CountryDetail detail = (CountryDetail) item;
            if (detail.getPolicyType() != null) details.put(detail.getPolicyType(), detail);
        }
        String lang = String.valueOf(modelAndView.getModel().get("lang"));
        String canonical = String.valueOf(modelAndView.getModel().get("canonicalUrl"));
        modelAndView.getModel().put("structuredData", structuredDataService.buildCountryHome((CountryDetail) countryObject, lang, canonical, (Map<String, Object>) extraRootObject, (List<String>) typesObject, details));
    }

    private void applyCorrection(String key, CountryDetail detail, CountryPolicy policy) {
        if ("transit".equals(detail.getPolicyType())) {
            detail.setStayDays("10");
            if ("vietnam".equals(key) || "kyrgyzstan".equals(key)) detail.setValidFrom("2026-08-20");
            detail.setPolicyTypeZh("240小时过境免签");
            detail.setPurpose("Transit to a third country or region; short-term tourism, business, visits or family visits during the permitted transit stay");
            detail.setPurposeZh("过境前往第三国或地区；停留期间可从事旅游、商务、访问、探亲等短期活动");
            detail.setRule("Stay in the permitted areas for no more than 240 hours (10 days) when entering through an eligible port with a confirmed onward itinerary to a third country or region.");
            detail.setRuleZh("从适用口岸入境并满足前往第三国或地区的确定日期及行程联程客票等条件时，可在规定区域停留不超过240小时（10天）。");
            detail.setNotes("A valid ordinary passport is required; the current transit policy also requires the international travel document to meet its validity requirement and a confirmed onward itinerary. Work, study and news reporting require the appropriate visa.");
            detail.setNotesZh("须持有效普通护照和确定日期及行程的联程客票。工作、学习、新闻采访等需事先批准的活动仍应办理相应签证。");
            detail.setPorts("65 designated open ports in 24 provinces, autonomous regions and municipalities under the current 240-hour policy");
            detail.setPortsZh("当前240小时过境免签覆盖24个省、自治区、直辖市的65个适用口岸");
            if (policy != null) {
                policy.setPermittedPurposesEn(detail.getPurpose());
                policy.setPermittedPurposesZh(detail.getPurposeZh());
                policy.setPassportTypeEn("Valid ordinary passport of a country covered by the current 240-hour transit policy.");
                policy.setPassportTypeZh("当前240小时过境免签适用国家的有效普通护照。");
                policy.setPassportValidityEn("Use a valid ordinary passport; the current transit policy requires the travel document to meet its validity requirement (currently at least 3 months)." );
                policy.setPassportValidityZh("须持有效普通护照，并符合当前过境旅行证件要求。");
                policy.setEntryCountEn("The traveler must enter through an eligible port and satisfy the required transit itinerary conditions.");
                policy.setEntryCountZh("须通过适用口岸入境，并满足规定的过境联程行程条件。");
                policy.setOnwardTicketEn("A confirmed onward ticket with a fixed date and itinerary to a third country or region is required.");
                policy.setOnwardTicketZh("需要前往第三国或地区、日期和行程已确定的联程客票。");
                policy.setAccommodationEn("Keep accommodation details for the permitted stay area available if requested.");
                policy.setAccommodationZh("如被查验，请准备规定停留区域内的住宿信息。");
            }
            return;
        }
        if ("hainan".equals(detail.getPolicyType())) {
            detail.setStayDays("30");
            detail.setValidFrom("2026-08-20");
            detail.setPolicyTypeZh("海南30天入境免签");
            detail.setPurpose("Tourism, business, visits, family visits, medical treatment, conferences and exhibitions, sports competitions and other short-term activities");
            detail.setPurposeZh("旅游、商贸、访问、探亲、就医、会展、体育竞技等短期活动");
            detail.setRule("Ordinary passport holders may enter Hainan through its open ports and stay within Hainan Province for no more than 30 days for covered short-term purposes. Work and study are excluded.");
            detail.setRuleZh("持普通护照可从海南省对外开放口岸免签入境，在海南省行政区域内停留不超过30天，适用于规定短期事由；工作、学习不适用。");
            detail.setNotes("This is a regional Hainan policy. It does not provide visa-free entry to mainland China outside Hainan.");
            detail.setNotesZh("这是海南区域性免签政策，不等于可免签前往海南以外的中国大陆地区。");
            detail.setPorts("All open ports of entry in Hainan Province");
            detail.setPortsZh("海南省所有对外开放口岸");
            if (policy != null) {
                policy.setPassportTypeEn("Valid ordinary passport of an eligible country");
                policy.setPassportTypeZh("适用国家的有效普通护照");
                policy.setPassportValidityEn("Use a valid ordinary passport and meet the current Hainan policy requirements.");
                policy.setPassportValidityZh("须持有效普通护照并符合当前海南区域免签政策要求。");
                policy.setEntryCountEn("Stay is limited to the administrative region of Hainan Province for no more than 30 days.");
                policy.setEntryCountZh("仅可在海南省行政区域内免签停留不超过30天。");
                policy.setOnwardTicketEn("No third-country transit itinerary is required for this Hainan regional route; keep itinerary and purpose evidence available if requested.");
                policy.setOnwardTicketZh("该海南区域免签路径不要求第三国过境行程；如被查验，请准备与行程和事由相符的材料。");
            }
            return;
        }
        if ("malaysia".equals(key)) {
            detail.setValidFrom("2025-07-17");
            detail.setStayDays("30");
            detail.setPurpose("Vacation/tour, family and friends visit, business, exchange, private affairs, medical treatment, international traffic (crew members)");
            detail.setPurposeZh("休闲旅游、探亲访友、商务、交流访问、私人事务、医疗、国际运输（机组人员）");
            detail.setRule("Each visa-free stay must not exceed 30 days. The cumulative visa-free stay must not exceed 90 days within any 180-day period. Work, study, settlement and news reporting are not covered.");
            detail.setRuleZh("单次免签停留不超过30日，每180日累计免签停留不超过90日。定居、工作、学习、新闻报道等不属于免签范围。");
            detail.setNotes("Malaysian ordinary passport valid for at least 6 months.");
            detail.setNotesZh("马来西亚普通护照有效期不少于6个月。");
            detail.setSeoTitle("Malaysia to China Mutual Visa Exemption 2026 | 30 Days / 90 in 180");
            detail.setSeoTitleZh("马来西亚来华互免签证2026 | 单次30天、180天累计90天");
            detail.setSeoDesc("Malaysian ordinary passport holders may enter China visa-free for eligible short-term purposes for up to 30 days per entry, subject to the 90-in-180-day cumulative limit.");
            detail.setSeoDescZh("马来西亚普通护照可按中马互免签证协定来华免签，单次最多30天，且每180日累计不超过90天。");
            if (policy != null) {
                policy.setPassportValidityEn("Malaysian ordinary passport valid for at least 6 months.");
                policy.setPassportValidityZh("马来西亚普通护照有效期不少于6个月。");
                policy.setEntryCountEn("No fixed entry-count limit, provided each stay is no more than 30 days and the cumulative visa-free stay is no more than 90 days in any 180-day period.");
                policy.setEntryCountZh("免签入境次数没有固定上限，但每次不超过30日，且每180日累计不超过90日。");
                policy.setPermittedPurposesEn(detail.getPurpose());
                policy.setPermittedPurposesZh(detail.getPurposeZh());
            }
        } else if ("albania".equals(key)) {
            detail.setStayDays("90");
            detail.setRule("Ordinary passport holders may stay visa-free for no more than 90 days within every 180-day period under the applicable bilateral agreement.");
            detail.setRuleZh("普通护照持有人根据适用双边互免签证协定，每180日内免签停留累计不超过90日。");
            if (policy != null) {
                policy.setEntryCountEn("Up to 90 days within every 180-day period under the applicable agreement.");
                policy.setEntryCountZh("根据适用协定，每180日内累计免签停留不超过90日。");
            }
        } else if ("singapore".equals(key)) {
            detail.setStayDays("30");
            detail.setRule("Ordinary passport holders may stay visa-free for up to 30 days under the China-Singapore mutual visa exemption arrangement.");
            detail.setRuleZh("普通护照持有人根据中新互免签证安排，免签停留最长30日。");
        }
    }
}
