package com.chinavisamap;

import com.chinavisamap.service.SiteMetadataService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SiteAuditIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired SiteMetadataService metadata;

    @Test void articleCategoryAliasesReturnTheSameCompleteSetInBothLanguages() throws Exception {
        Set<String> expected = articleIds("en", "china-life-apps");
        assertEquals(8, expected.size());
        assertTrue(expected.contains("china-tourist-vat-tax-refund-guide-foreign-visitor"));
        assertTrue(expected.contains("china-visa-free-phone-sim-registration-rules-foreigners"));
        for (String lang : Arrays.asList("en", "zh")) {
            for (String category : Arrays.asList("china-life-apps", "China Life & Apps", "中国生活与APP", "中国生活与 APP")) {
                assertEquals(expected, articleIds(lang, category), lang + " / " + category);
                Map<String,Object> model = mvc.perform(get("/articles").param("lang", lang).param("category", category))
                        .andExpect(status().isOk()).andReturn().getModelAndView().getModel();
                assertEquals("china-life-apps", model.get("category"));
                assertEquals(lang.equals("zh") ? "中国生活与APP" : "China Life & Apps", model.get("categoryLabel"));
            }
            for (Map<String,String> category : com.chinavisamap.controller.ArticleController.ALL_CATEGORIES) {
                Set<String> ids = articleIds(lang, category.get("id"));
                assertFalse(ids.isEmpty());
                assertEquals(ids, articleIds(lang, category.get("en")));
                assertEquals(ids, articleIds(lang, category.get("zh")));
            }
            mvc.perform(get("/articles").param("lang", lang).param("category", "china-life-apps"))
                    .andExpect(model().attribute("noIndex", true))
                    .andExpect(content().string(org.hamcrest.Matchers.containsString("category=china-life-apps")));
        }
        assertTrue(articleIds("en", "unknown-category").isEmpty());
    }

    @SuppressWarnings("unchecked")
    private Set<String> articleIds(String lang, String category) throws Exception {
        List<Map<String,Object>> articles = (List<Map<String,Object>>) mvc.perform(get("/articles")
                .param("lang", lang).param("category", category).param("size", "50"))
                .andExpect(status().isOk()).andReturn().getModelAndView().getModel().get("articlePageList");
        return articles.stream().map(a -> String.valueOf(a.get("id"))).collect(Collectors.toSet());
    }

    @Test void normalizedLanguageSurvivesPresentationOverrides() throws Exception {
        for (String path : new String[]{"/country/thailand", "/country/my", "/country/korea", "/articles/thailand-china-transit-2026"}) {
            Map<String,Object> lower = mvc.perform(get(path).param("lang", "zh")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
            Map<String,Object> upper = mvc.perform(get(path).param("lang", "ZH")).andExpect(status().isOk()).andReturn().getModelAndView().getModel();
            for (String field : new String[]{"lang", "structuredData", "canonicalUrl", "articleCheckerUrl", "countryIntent"})
                assertEquals(lower.get(field), upper.get(field), path + " language mismatch: " + field);
        }
    }

    @Test void sitemapUsesPageModificationDatesSeparatelyFromPolicyVerification() throws Exception {
        byte[] bytes = mvc.perform(get("/sitemap.xml")).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        org.w3c.dom.NodeList urls = factory.newDocumentBuilder().parse(new ByteArrayInputStream(bytes)).getElementsByTagName("url");
        java.util.Map<String,String> dates = new java.util.HashMap<>();
        for (int i = 0; i < urls.getLength(); i++) {
            org.w3c.dom.Element url = (org.w3c.dom.Element)urls.item(i);
            dates.put(url.getElementsByTagName("loc").item(0).getTextContent(), url.getElementsByTagName("lastmod").item(0).getTextContent());
        }
        for (String lang : new String[]{"en", "zh"}) {
            assertEquals("2026-11-07", dates.get("https://chinavisamap.com/?lang=" + lang));
            assertEquals("2026-11-07", dates.get("https://chinavisamap.com/country/thailand?lang=" + lang));
            assertEquals(metadata.sitemapBaselineLastModified(), dates.get("https://chinavisamap.com/country/us/transit?lang=" + lang));
        }
        assertEquals("2026-11-01", metadata.lastVerified());
    }

    @Test void malformedRequiredJsonFailsInsteadOfRenderingAnEmptySite() throws Exception {
        com.fasterxml.jackson.databind.ObjectMapper mapper = org.mockito.Mockito.mock(com.fasterxml.jackson.databind.ObjectMapper.class);
        org.mockito.Mockito.when(mapper.readValue(org.mockito.ArgumentMatchers.any(java.io.InputStream.class),
                org.mockito.ArgumentMatchers.<com.fasterxml.jackson.core.type.TypeReference<Map<String,Object>>>any()))
                .thenThrow(new IllegalArgumentException("bad data"));
        assertThrows(IllegalStateException.class, () -> new com.chinavisamap.controller.IndexController(null, null, null, null, mapper));
    }

    @Test void publicSiteDoesNotExposeMetricsOrCredentialedWildcardCors() throws Exception {
        mvc.perform(get("/actuator/metrics")).andExpect(status().isNotFound());
        mvc.perform(options("/api/eligibility/au").header("Origin", "https://chinavisamap.com")
                .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "https://chinavisamap.com"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
        mvc.perform(options("/api/eligibility/au").header("Origin", "https://example.net")
                .header("Access-Control-Request-Method", "POST")).andExpect(status().isForbidden());
    }
}
