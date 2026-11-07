package com.chinavisamap.web;

import org.springframework.web.util.UriComponentsBuilder;

import javax.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Preserve incoming campaign attribution across internal redirects, not ordinary links. */
public final class RedirectAttribution {
    private static final List<String> PARAMETERS = Arrays.asList(
            "utm_source", "utm_medium", "utm_campaign", "utm_id", "utm_term", "utm_content",
            "utm_source_platform", "utm_creative_format", "utm_marketing_tactic",
            "gclid", "dclid", "gbraid", "wbraid", "msclkid", "fbclid");

    private RedirectAttribution() {}

    public static String preserve(String target, HttpServletRequest request) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(target);
        Map<String, Object> values = new LinkedHashMap<>();
        for (String name : PARAMETERS) {
            String value = request.getParameter(name);
            if (value == null || value.trim().isEmpty() || value.length() > 512
                    || value.chars().anyMatch(Character::isISOControl)) continue;
            String variable = "attribution" + values.size();
            builder.queryParam(name, "{" + variable + "}");
            values.put(variable, value);
        }
        // Expand after template encoding so literal +, &, # and Unicode survive one decode.
        return values.isEmpty() ? target : builder.encode().buildAndExpand(values).toUriString();
    }
}
