package com.chinavisamap.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.time.LocalDate;

/**
 * Single source for site-level verification/update metadata.
 * These dates describe the data/content version shipped with the application;
 * they are not generated from the current server date.
 */
@Service
public class SiteMetadataService {
    private final Map<String, Object> metadata;

    public SiteMetadataService(ObjectMapper mapper) {
        try {
            metadata = mapper.readValue(
                    new ClassPathResource("data.json").getInputStream(),
                    new TypeReference<Map<String, Object>>() {}
            );
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load data.json site metadata", e);
        }
    }

    public String lastVerified() {
        String value = text("siteDataLastVerified");
        return value.isEmpty() ? "2026-09-13" : value;
    }

    public String contentLastModified() {
        String value = text("siteContentLastModified");
        return value.isEmpty() ? "2026-09-24" : value;
    }

    public String sitemapBaselineLastModified() {
        String value = text("siteSitemapBaselineLastModified");
        return value.isEmpty() ? "2026-09-16" : value;
    }

    public String pageLastModified(String path) {
        Object pages = metadata.get("pageContentLastModified");
        Object value = pages instanceof Map ? ((Map<?, ?>)pages).get(path) : null;
        String date = value == null ? sitemapBaselineLastModified() : String.valueOf(value).trim();
        try {
            return LocalDate.parse(date).toString();
        } catch (RuntimeException exception) {
            throw new IllegalStateException("Invalid content modification date for " + path, exception);
        }
    }

    private String text(String key) {
        Object value = metadata.get(key);
        return value == null ? "" : String.valueOf(value).trim();
    }
}
