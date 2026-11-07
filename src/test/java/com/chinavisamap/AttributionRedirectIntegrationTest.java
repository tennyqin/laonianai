package com.chinavisamap;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class AttributionRedirectIntegrationTest {
    @Autowired MockMvc mvc;

    @Test void aliasRedirectPreservesLiteralCampaignValuesAndCleanSeoUrls() throws Exception {
        String campaign = "来华 + family & friends #1 / 50%";
        MvcResult redirect = mvc.perform(get("/country/australia").param("lang", "zh")
                .param("utm_source", "wechat").param("utm_medium", "social")
                .param("utm_campaign", campaign).param("gclid", "literal+click/id=1")
                .param("email", "not-forwarded@example.com").param("redirect", "https://example.com"))
                .andExpect(status().isMovedPermanently()).andReturn();
        String location = redirect.getResponse().getHeader("Location");
        assertEquals("/country/au", URI.create(location).getPath());
        Map<String, String> query = query(location);
        assertEquals(campaign, query.get("utm_campaign"));
        assertEquals("literal+click/id=1", query.get("gclid"));
        assertEquals("wechat", query.get("utm_source"));
        assertEquals("social", query.get("utm_medium"));
        assertFalse(query.containsKey("email"));
        assertFalse(query.containsKey("redirect"));
        MvcResult page = mvc.perform(get(URI.create(location))).andExpect(status().isOk())
                .andExpect(model().attribute("canonicalUrl", "https://chinavisamap.com/country/au?lang=zh"))
                .andReturn();
        Map<?, ?> hreflang = (Map<?, ?>) page.getModelAndView().getModel().get("hreflang");
        assertTrue(hreflang.values().stream().noneMatch(value -> value.toString().contains("utm_")));
    }

    @Test void legacyPolicyRedirectRetainsAttributionWithoutRetainingOldTypeParameter() throws Exception {
        MvcResult redirect = mvc.perform(get("/country/australia").param("type", "unilateral")
                .param("lang", "en").param("utm_source", "newsletter").param("utm_id", "weekly-7"))
                .andExpect(status().isMovedPermanently()).andReturn();
        String location = redirect.getResponse().getHeader("Location");
        assertEquals("/country/au/unilateral", URI.create(location).getPath());
        assertEquals("newsletter", query(location).get("utm_source"));
        assertEquals("weekly-7", query(location).get("utm_id"));
        assertFalse(query(location).containsKey("type"));
        mvc.perform(get(URI.create(location))).andExpect(status().isOk())
                .andExpect(model().attribute("canonicalUrl", "https://chinavisamap.com/country/au/unilateral?lang=en"));
    }

    @Test void retiredPolicyKeepsAttributionThroughEveryRedirectHop() throws Exception {
        MvcResult alias = mvc.perform(get("/country/usa/unilateral").param("lang", "en")
                .param("utm_source", "bing").param("msclkid", "click-123"))
                .andExpect(status().isMovedPermanently()).andReturn();
        String first = alias.getResponse().getHeader("Location");
        assertEquals("/country/us/unilateral", URI.create(first).getPath());
        MvcResult retired = mvc.perform(get(URI.create(first))).andExpect(status().isMovedPermanently()).andReturn();
        String second = retired.getResponse().getHeader("Location");
        assertEquals("/country/us", URI.create(second).getPath());
        assertEquals("bing", query(second).get("utm_source"));
        assertEquals("click-123", query(second).get("msclkid"));
        mvc.perform(get(URI.create(second))).andExpect(status().isOk());
    }

    @Test void entrySelectionPreservesCampaignBeforeFragmentAndRejectsInvalidValues() throws Exception {
        MvcResult redirect = mvc.perform(get("/entry-options").param("country", "au").param("task", "dates")
                .param("lang", "zh").param("utm_content", "button+1").param("wbraid", "test-click")
                .param("utm_source", "\r\nLocation: https://example.com").param("utm_medium", "x".repeat(513)))
                .andExpect(status().isSeeOther()).andExpect(header().string("Cache-Control", "no-store")).andReturn();
        String location = redirect.getResponse().getHeader("Location");
        assertEquals("stay-calculator", URI.create(location).getFragment());
        assertEquals("button+1", query(location).get("utm_content"));
        assertEquals("test-click", query(location).get("wbraid"));
        assertFalse(query(location).containsKey("utm_source"));
        assertFalse(query(location).containsKey("utm_medium"));
        mvc.perform(get(URI.create(location))).andExpect(status().isOk());
        MvcResult other = mvc.perform(get("/entry-options").param("country", "other").param("utm_source", "email"))
                .andExpect(status().isSeeOther()).andReturn();
        assertEquals("email", query(other.getResponse().getHeader("Location")).get("utm_source"));
    }

    private Map<String, String> query(String location) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String part : URI.create(location).getRawQuery().split("&")) {
            String[] entry = part.split("=", 2);
            result.put(URLDecoder.decode(entry[0], StandardCharsets.UTF_8),
                    URLDecoder.decode(entry.length == 2 ? entry[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
}
