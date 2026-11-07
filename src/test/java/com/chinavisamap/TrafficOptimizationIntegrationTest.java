package com.chinavisamap;

import com.chinavisamap.entity.CountryPolicy;
import com.chinavisamap.service.PolicyRuleEngine;
import com.chinavisamap.service.SiteMetadataService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class TrafficOptimizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper mapper;
    @Autowired PolicyRuleEngine rules;
    @Autowired SiteMetadataService metadata;

    @Test void nativeSelectionUsesCanonicalInternalRoutesAndLimitsDateCalculation() throws Exception {
        for (String lang : Arrays.asList("en", "zh")) {
            mvc.perform(get("/entry-options").param("country", "australia").param("lang", lang))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "/country/au?lang=" + lang + "&entryMode=auto#eligibility-checker"))
                .andExpect(header().string("X-Robots-Tag", "noindex"))
                .andExpect(header().string("Cache-Control", "no-store"));
            boolean direct = rules.policyAvailableToday("au", "unilateral");
            mvc.perform(get("/entry-options").param("country", "au").param("task", "dates").param("lang", lang))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/country/au?lang=" + lang
                    + (direct ? "&entryMode=direct#stay-calculator" : "&entryMode=auto#eligibility-checker")));
            for (String country : Arrays.asList("sg", "us", "philippines"))
                mvc.perform(get("/entry-options").param("country", country).param("task", "dates").param("lang", lang))
                    .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/country/" + country + "?lang=" + lang + "&entryMode=auto#eligibility-checker"));
            mvc.perform(get("/entry-options").param("country", "other").param("lang", lang))
                .andExpect(status().isSeeOther()).andExpect(header().string("Location", "/transit-check?lang=" + lang));
        }
        for (String country : Arrays.asList("", "unknown", "//example.com", "au?lang=zh", "../us", "au\r\nLocation: https://example.com"))
            mvc.perform(get("/entry-options").param("country", country)).andExpect(status().isBadRequest());
        mvc.perform(get("/entry-options")).andExpect(status().isBadRequest());
    }

    @Test void genericArticlesAndGuideExposeTheSameCompleteNationalityChoiceAsHome() throws Exception {
        for (String lang : Arrays.asList("en", "zh")) {
            Map<String,Object> home = model("/", lang);
            Set<String> homeCountries = codes(home.get("checkerCountries"));
            assertTrue(homeCountries.size() > 60);
            assertEquals(8, codes(home.get("featuredCountries")).size());
            for (String path : Arrays.asList("/articles/calculate-visafree-stay-days-china", "/articles/hotel-booking-proof-china-visafree", "/visa-guide")) {
                MvcResult page = mvc.perform(get(path).param("lang", lang)).andExpect(status().isOk()).andReturn();
                assertEquals(homeCountries, codes(page.getModelAndView().getModel().get("checkerCountries")), path);
                String html = page.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
                assertTrue(html.contains("action=\"/entry-options\""));
                assertTrue(html.contains("value=\"other\""));
                if (path.equals("/visa-guide")) assertTrue(html.contains("guide-faq"));
            }
        }
    }

    @Test void philippinePresentationCannotAdvertiseUnavailableTransitAndSchemaMatchesFinalSeo() throws Exception {
        for (String lang : Arrays.asList("en", "zh")) {
            for (String code : Arrays.asList("canada", "philippines", "kazakhstan")) {
                Map<String,Object> m = model("/country/" + code, lang);
                JsonNode graph = mapper.readTree(String.valueOf(m.get("structuredData"))).get("@graph");
                JsonNode web = null;
                for (JsonNode node : graph) if ("WebPage".equals(node.path("@type").asText())) web = node;
                assertNotNull(web);
                assertEquals(m.get("countrySeoTitle" + (lang.equals("en") ? "En" : "Zh")), web.path("name").asText(), code);
                assertEquals(metadata.pageLastModified("/country/" + code), web.path("dateModified").asText(), code);
                if (code.equals("philippines")) {
                    assertEquals(Collections.singletonList("hainan"), m.get("availableTypes"));
                    Map<?,?> intent = (Map<?,?>)m.get("countryIntent");
                    assertEquals(false, intent.get("transit"));
                    assertTrue(String.valueOf(m.get("countrySeoDescEn")).contains("outside the 240-hour"));
                    assertTrue(String.valueOf(intent.get("faqEn")).contains("No. The Philippines"));
                }
            }
        }
    }

    @Test void priorityFaqsCannotOverrideThaiAndMalaysianTransitExclusions() throws Exception {
        for (String lang : Arrays.asList("en", "zh")) {
            for (String code : Arrays.asList("thailand", "my")) {
                Map<String,Object> m = model("/country/" + code, lang);
                assertFalse(((List<?>)m.get("availableTypes")).contains("transit"));
                Map<?,?> intent = (Map<?,?>)m.get("countryIntent");
                assertEquals(false, intent.get("transit"));
                List<?> faqs = (List<?>)intent.get(lang.equals("en") ? "faqEn" : "faqZh");
                Map<?,?> transitFaq = faqs.stream().map(f -> (Map<?,?>)f)
                    .filter(f -> String.valueOf(f.get("q")).contains("240")).findFirst().orElseThrow();
                String answer = String.valueOf(transitFaq.get("a"));
                assertTrue(answer.startsWith(lang.equals("en") ? "No." : "不可以。"));
                JsonNode graph = mapper.readTree(String.valueOf(m.get("structuredData"))).get("@graph");
                Set<String> schemaPolicyUrls = new HashSet<>();
                for (JsonNode node : graph) if ("ItemList".equals(node.path("@type").asText())) {
                    for (JsonNode item : node.path("itemListElement")) {
                        schemaPolicyUrls.add(item.path("url").asText());
                    }
                }
                Set<String> expectedUrls = ((List<?>)m.get("availableTypes")).stream()
                    .map(type -> "https://chinavisamap.com/country/" + code + "/" + type + "?lang=" + lang)
                    .collect(Collectors.toSet());
                assertEquals(expectedUrls, schemaPolicyUrls, code + " schema must list only available policies");
            }
        }
        for (String lang : Arrays.asList("en", "zh")) {
            Map<String,Object> article = model("/articles/thailand-china-transit-2026", lang);
            assertEquals("auto", article.get("articleCheckerRoute"));
            assertFalse(String.valueOf(article.get("articleCheckerUrl")).contains("transit240"));
        }
    }

    @Test void verificationDateAndScopeFollowReviewedRulesRatherThanStaleExtras() throws Exception {
        assertEquals("2026-11-01", rules.version());
        for (String route : Arrays.asList("canada/unilateral", "thailand/mutual", "sg/mutual", "us/transit", "philippines/hainan")) {
            Map<String,Object> m = model("/country/" + route, "en");
            CountryPolicy policy = (CountryPolicy)m.get("policy");
            assertEquals(rules.version(), policy.getLastVerified());
            JsonNode graph = mapper.readTree(String.valueOf(m.get("structuredData"))).get("@graph");
            assertEquals(metadata.pageLastModified("/country/" + route), graph.get(0).path("dateModified").asText());
            if (route.equals("thailand/mutual")) {
                assertTrue(policy.getOfficialSource().contains("11234802"));
                assertEquals(true, m.get("agreementTermsVerified"));
            }
            if (route.equals("sg/mutual")) assertEquals(false, m.get("agreementTermsVerified"));
        }
    }

    private Map<String,Object> model(String path, String lang) throws Exception {
        return mvc.perform(get(path).param("lang", lang)).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
    }
    private Set<String> codes(Object raw) {
        return ((List<Map<String,Object>>)raw).stream().map(m -> String.valueOf(m.get("code"))).collect(Collectors.toSet());
    }
}
