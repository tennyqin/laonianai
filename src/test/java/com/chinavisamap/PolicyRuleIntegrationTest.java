package com.chinavisamap;

import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class PolicyRuleIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyRuleEngine engine;
    @Autowired CountryCodeResolver resolver;
    @Test void homepageStartsWithUniqueCanonicalNationalitiesAndWorksWithoutJavascript() throws Exception {
        for(String lang:Arrays.asList("en","zh")) {
            MvcResult page=mvc.perform(get("/").param("lang",lang)).andExpect(status().isOk()).andReturn();
            List<Map<String,Object>> countries=(List<Map<String,Object>>)page.getModelAndView().getModel().get("checkerCountries");
            Set<String> codes=new HashSet<>();
            for(Map<String,Object> c:countries) {
                String code=String.valueOf(c.get("code"));
                assertTrue(codes.add(code));
                assertEquals(resolver.routeCode(resolver.policyKey(code)),code);
                mvc.perform(get("/country/"+code).param("lang",lang)).andExpect(status().isOk());
            }
            String html=page.getResponse().getContentAsString();
            assertTrue(html.contains("id=\"passport-country\""));
            assertTrue(html.contains("/transit-check?lang="+lang));
            mvc.perform(get("/").param("keyword","australia").param("lang",lang))
                    .andExpect(status().isOk()).andExpect(model().attribute("searchHasResults",true));
        }
    }
    @Test void paginationHasItsOwnCanonicalButSearchAndEmptyVariantsStayNoindex() throws Exception {
        for(String lang:Arrays.asList("en","zh")) {
            MvcResult page=mvc.perform(get("/articles").param("lang",lang).param("page","2"))
                    .andExpect(status().isOk()).andExpect(model().attribute("noIndex",false)).andReturn();
            assertEquals("https://chinavisamap.com/articles?lang="+lang+"&page=2",page.getModelAndView().getModel().get("canonicalUrl"));
            Map<?,?> links=(Map<?,?>)page.getModelAndView().getModel().get("hreflang");
            assertEquals("https://chinavisamap.com/articles?lang=en&page=2",links.get("en"));
            mvc.perform(get("/articles").param("keyword","passport").param("page","2"))
                    .andExpect(status().isOk()).andExpect(model().attribute("noIndex",true));
            mvc.perform(get("/articles").param("page","999999999"))
                    .andExpect(status().isOk()).andExpect(model().attribute("noIndex",true));
            mvc.perform(get("/about").param("lang",lang)).andExpect(status().isOk())
                    .andExpect(model().attribute("canonicalUrl","https://chinavisamap.com/about?lang="+lang));
        }
    }
    @Test void automaticRoutePrefersGeneralEntryAndKeepsTransitConditional() throws Exception {
        Map<String,Object> t=trip("auto");
        Map<String,Object> out=call("au",t,false);
        assertEquals("eligible",out.get("status"));assertEquals("direct",out.get("recommendedRoute"));
        out=call("us",t,false);assertEquals("transit240",out.get("recommendedRoute"));
        assertEquals("pending",out.get("status"));assertEquals("port_area_unverified",out.get("reason"));
        t.put("origin","");out=call("us",t,false);assertEquals(true,out.get("needsItinerary"));assertEquals("pending",out.get("status"));
        t.put("purpose","work");assertEquals("visa_purpose",call("au",t,false).get("reason"));
        t=trip("auto");assertEquals("agreement_verification",call("my",t,false).get("reason"));
    }
    @Test void departureDatesAreValidatedAndOverrideManualDays()throws Exception{
        Map<String,Object> t=trip("direct");t.put("days",999);t.put("departureDate","2026-11-19");
        Map<String,Object> out=call("au",t,false);assertEquals("eligible",out.get("status"));assertEquals("2026-11-19",out.get("plannedDeparture"));
        t.put("departureDate","2026-11-20");assertEquals("ineligible",call("au",t,false).get("status"));
        t.put("departureDate","2026-10-20");assertEquals("2026-10-20",call("au",t,false).get("plannedDeparture"));
        t.put("departureDate","2026-10-19");assertEquals("invalid_departure_date",call("au",t,false).get("reason"));
        t.put("departureDate","2026-02-30");assertEquals("invalid",call("au",t,false).get("status"));
    }
    @Test void universalTransitDoesNotRequireCountrySpecificListingOrOrdinaryPassport()throws Exception{
        Map<String,Object> t=trip("transit24");t.put("days",12);t.put("passport","other");
        Map<String,Object> out=call("IN",t,false);assertEquals("transit24_port_check",out.get("reason"));
        assertEquals("pending",out.get("status"));assertNotNull(out.get("officialSource"));
        t.put("days",25);assertEquals("ineligible",call("IN",t,false).get("status"));
        t.put("days",12);t.put("destination","US");assertEquals("not_third_country",call("IN",t,false).get("reason"));
        assertEquals("invalid",call("unknown",t,false).get("status"));
        t=trip("direct");assertEquals("invalid",call("IN",t,false).get("status"));
        for(String code:Arrays.asList("CN","HK","MO","TW"))assertEquals("invalid",call(code,trip("transit24"),false).get("status"));
    }
    @Test void hainanReferenceDateAndSpecificAgreementSourceAreExplicit()throws Exception{
        Map<String,Object> out=call("us",trip("hainan"),false);
        assertEquals("pending",out.get("status"));assertEquals("2026-11-19",out.get("indicativeLatestDeparture"));
        assertTrue(((List<?>)out.get("missingChecks")).contains("hainan_only"));
        Map<String,Object> t=trip("direct");t.put("purpose","medical");out=call("my",t,false);
        assertEquals("agreement_verification",out.get("reason"));assertTrue(String.valueOf(out.get("officialSource")).contains("my.china-embassy.gov.cn"));
        Map<String,Object> config=(Map<String,Object>)mvc.perform(get("/country/us")).andReturn().getModelAndView().getModel().get("eligibilityConfig");
        assertEquals(true,((Map<?,?>)config.get("transit240")).get("verificationRequired"));
        assertEquals(true,((Map<?,?>)config.get("hainan")).get("verificationRequired"));
    }
    @Test void sitemapIncludesEveryArticleOncePerLanguageAndRealModificationDates()throws Exception{
        String xml=mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        javax.xml.parsers.DocumentBuilderFactory factory=javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl",true);
        org.w3c.dom.Document doc=factory.newDocumentBuilder().parse(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        org.w3c.dom.NodeList urls=doc.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9","url");
        Set<String> locations=new HashSet<>();
        for(int i=0;i<urls.getLength();i++){
            org.w3c.dom.Element url=(org.w3c.dom.Element)urls.item(i);
            String loc=url.getElementsByTagNameNS("http://www.sitemaps.org/schemas/sitemap/0.9","loc").item(0).getTextContent();
            assertTrue(locations.add(loc),loc);assertEquals(3,url.getElementsByTagNameNS("http://www.w3.org/1999/xhtml","link").getLength());
            assertFalse(loc.contains("entryMode"));
        }
        List<Map<String,Object>> articles=mapper.readValue(new org.springframework.core.io.ClassPathResource("articles.json").getInputStream(),List.class);
        assertEquals(92,articles.size());
        for(Map<String,Object> a:articles)for(String lang:Arrays.asList("en","zh"))assertTrue(locations.contains("https://chinavisamap.com/articles/"+a.get("id")+"?lang="+lang));
        for(String lang:Arrays.asList("en","zh")){
            assertTrue(locations.contains("https://chinavisamap.com/transit-check?lang="+lang));
            mvc.perform(get("/transit-check").param("lang",lang)).andExpect(status().isOk());
        }
        assertTrue(xml.contains("<lastmod>2026-10-17</lastmod>"));
    }
    @Test void aliasRedirectPreservesCheckerRoute()throws Exception{
        mvc.perform(get("/country/australia").param("entryMode","transit24").param("purpose","transit"))
            .andExpect(status().isMovedPermanently()).andExpect(header().string("Location","/country/au?lang=en&entryMode=transit24&purpose=transit"));
    }
    private Map<String,Object> trip(String mode) {
        Map<String,Object> t=new LinkedHashMap<>();
        t.put("entryDate","2026-10-20");t.put("passport","ordinary");t.put("passportValid",true);
        t.put("purpose","tourism");t.put("days",7);t.put("entryMode",mode);
        t.put("origin","US");t.put("destination","JP");t.put("onward",true);return t;
    }
    private Map<String,Object> call(String code,Map<String,Object> t,boolean calc)throws Exception{
        MvcResult res=mvc.perform(post("/api/eligibility/"+code+(calc?"/calculate":""))
            .contentType(MediaType.APPLICATION_JSON).content(mapper.writeValueAsBytes(t)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andReturn();
        return mapper.readValue(res.getResponse().getContentAsByteArray(),Map.class);
    }
    @Test void relatedCountryNamesRemainLocalized() throws Exception {
        MvcResult res=mvc.perform(get("/country/au").param("lang","zh")).andExpect(status().isOk()).andReturn();
        Map names=(Map)res.getModelAndView().getModel().get("relatedCountryNames");
        assertEquals("新西兰",names.get("newzealand"));
        String rendered=res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String related=rendered.substring(rendered.indexOf("相关国家指南"));
        related=related.substring(0,related.indexOf("</section>"));
        assertTrue(related.contains("新西兰"),related);
        List<Map<String,Object>> articles=mapper.readValue(new org.springframework.core.io.ClassPathResource("articles.json").getInputStream(),List.class);
        for(Map<String,Object> article:articles) for(String lang:Arrays.asList("en","zh"))
            mvc.perform(get("/articles/"+article.get("id")).param("lang",lang)).andExpect(status().isOk());
    }
    @Test void allRecordedPolicyRoutesAndArticlesRender() throws Exception {
        for(String type:Arrays.asList("unilateral","mutual","transit","hainan")) {
            Map<String,Object> countries=mapper.readValue(new org.springframework.core.io.ClassPathResource(type+".json").getInputStream(),Map.class);
            for(String key:countries.keySet()) for(String lang:Arrays.asList("en","zh")) {
                String code=resolver.routeCode(key);
                mvc.perform(get("/country/"+code+"/"+type).param("lang",lang)).andExpect(status().isOk());
                mvc.perform(get("/country/"+code).param("lang",lang)).andExpect(status().isOk());
            }
        }
    }
    @Test void knownUnilateralPurposesAndLimit()throws Exception{
        for(String purpose:Arrays.asList("tourism","conference","sports","study_tour")){
            Map<String,Object> t=trip("direct");t.put("purpose",purpose);
            assertEquals("eligible",call("au",t,false).get("status"));
        }
        Map<String,Object> t=trip("direct");t.put("days",31);
        assertEquals("ineligible",call("au",t,false).get("status"));
        t.put("days",7);t.put("purpose","medical");assertEquals("pending",call("au",t,false).get("status"));
    }
    @Test void entryDatesDoNotAssumeExtensions()throws Exception{
        Map<String,Object> t=trip("direct");t.put("entryDate","2027-01-01");
        assertEquals("policy_date_unconfirmed",call("au",t,false).get("reason"));
        assertEquals("eligible",call("russia",t,false).get("status"));
        t.put("entryDate","2028-01-01");assertEquals("pending",call("russia",t,false).get("status"));
        t.put("entryDate","2026-09-01");assertEquals("pending",call("au",t,false).get("status"));
        t.put("entryDate","2026-02-30");assertEquals("invalid",call("au",t,false).get("status"));
    }
    @Test void calculatorHandlesEndOfYearAndLeapDay()throws Exception{
        Map<String,Object> t=trip("direct");t.put("entryDate","2026-12-31");t.put("days",30);
        Map<String,Object> out=call("au",t,true);
        assertEquals("2027-01-01",out.get("countingStart"));assertEquals("2027-01-30",out.get("latestDeparture"));
        t.put("entryDate","2028-02-28");out=call("brunei",t,true);
        assertEquals("2028-02-29",out.get("countingStart"));assertEquals("2028-03-29",out.get("latestDeparture"));
        t.put("entryDate","2026-10-20");assertEquals("pending",call("my",t,true).get("status"));
    }
    @Test void passportMustBeConfirmed()throws Exception{
        Map<String,Object> t=trip("direct");t.put("passportValid",false);
        assertEquals("pending",call("au",t,false).get("status"));
        t.put("passport","diplomatic");assertEquals("passport_type",call("au",t,false).get("reason"));
    }
    @Test void sameCountryAndMissingTransitEvidenceNeverPass()throws Exception{
        Map<String,Object> t=trip("transit240");t.put("destination","US");
        assertEquals("not_third_country",call("us",t,false).get("reason"));
        t.put("destination","");assertEquals("invalid",call("us",t,false).get("status"));
        t.put("destination","JP");t.put("onward",false);assertEquals("pending",call("us",t,false).get("status"));
    }
    @Test void hongKongAndMacaoAreDistinctRegionsButPortsRemainUnverified()throws Exception{
        Map<String,Object> t=trip("transit240");t.put("origin","HK");t.put("destination","MO");
        Map<String,Object> out=call("us",t,false);assertEquals("port_area_unverified",out.get("reason"));
        assertEquals("pending",out.get("status"));assertNotNull(out.get("missingChecks"));
        t.put("destination","HK");assertEquals("ineligible",call("us",t,false).get("status"));
    }
    @Test void twentyFourHoursIsNotTenDays()throws Exception{
        Map<String,Object> t=trip("transit24");t.put("purpose","transit");t.put("days",24.1);
        assertEquals("ineligible",call("vietnam",t,false).get("status"));
        t.put("days",0.5);assertEquals("pending",call("vietnam",t,false).get("status"));
        t.put("destination","US");assertEquals("ineligible",call("vietnam",t,false).get("status"));
    }
    @Test void malaysiaAndThailandExposeStructuredCumulativeRules()throws Exception{
        for(String code:Arrays.asList("my","thailand")){
            Map<String,Object> out=call(code,trip("direct"),false);
            assertEquals("pending",out.get("status"));assertEquals(30,out.get("perEntryDays"));
            assertEquals(90,out.get("cumulativeDays"));assertEquals(180,out.get("cumulativeWindowDays"));
        }
    }
    @Test void hainanIsNeverGeneralMainlandApproval()throws Exception{
        Map<String,Object> t=trip("hainan");assertEquals("pending",call("vietnam",t,false).get("status"));
        t.put("days",31);assertEquals("ineligible",call("vietnam",t,false).get("status"));
    }
    @Test void invalidNumbersAndUnknownCountriesAreSafe()throws Exception{
        for(Object days:Arrays.asList(0,-1,7.5,"NaN","Infinity",4000)){
            Map<String,Object> t=trip("direct");t.put("days",days);assertEquals("invalid",call("au",t,false).get("status"));
        }
        assertEquals("invalid",call("unknown",trip("direct"),false).get("status"));
    }
    @Test void canonicalAndLegacyUrlsStillWork()throws Exception{
        mvc.perform(get("/country/australia").param("lang","en")).andExpect(status().isMovedPermanently()).andExpect(header().string("Location","/country/au?lang=en"));
        mvc.perform(get("/country/usa/transit").param("lang","zh")).andExpect(status().isMovedPermanently());
        mvc.perform(get("/sitemap.xml")).andExpect(status().isOk());
        mvc.perform(get("/robots.txt")).andExpect(status().isOk());
    }
    @Test void allLegacyCountryPagesRenderAndUseMergedSources()throws Exception{
        String[] codes={"au","us","gb","my","sg","jp","de","al","pl","cz","canada"};
        for(String code:codes)for(String lang:Arrays.asList("en","zh")){
            mvc.perform(get("/country/"+code).param("lang",lang)).andExpect(status().isOk());
        }
        MvcResult result=mvc.perform(get("/country/au/unilateral").param("lang","zh")).andExpect(status().isOk()).andReturn();
        CountryPolicy p=(CountryPolicy)result.getModelAndView().getModel().get("policy");
        assertEquals("https://cs.mfa.gov.cn/lh/lhqz_149493/cjwd/",p.getOfficialSource());
        assertTrue(result.getResponse().getContentAsString().contains("#stay-calculator"));
        mvc.perform(get("/articles/calculate-visafree-stay-days-china").param("lang","en")).andExpect(status().isOk());
    }
}
