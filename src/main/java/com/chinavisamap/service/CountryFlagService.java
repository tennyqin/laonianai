package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Converts the site's country policy keys / route aliases into flag emoji.
 * The country code used by the site is often a slug (for example "japan"),
 * so it cannot be converted to a regional-indicator flag directly.
 */
@Service("countryFlagService")
public class CountryFlagService {

    private final Map<String, String> iso2 = new HashMap<>();

    public CountryFlagService() {
        add("albania", "AL"); add("argentina", "AR"); add("australia", "AU"); add("austria", "AT");
        add("belarus", "BY"); add("belgium", "BE"); add("bosnia", "BA"); add("bosniaandherzegovina", "BA");
        add("brazil", "BR"); add("brunei", "BN"); add("bulgaria", "BG"); add("canada", "CA");
        add("chile", "CL"); add("croatia", "HR"); add("cyprus", "CY"); add("czechia", "CZ"); add("czech", "CZ");
        add("denmark", "DK"); add("estonia", "EE"); add("finland", "FI"); add("france", "FR");
        add("germany", "DE"); add("greece", "GR"); add("hungary", "HU"); add("iceland", "IS");
        add("indonesia", "ID"); add("ireland", "IE"); add("italy", "IT"); add("japan", "JP");
        add("kazakhstan", "KZ"); add("kyrgyzstan", "KG"); add("latvia", "LV"); add("lithuania", "LT");
        add("luxembourg", "LU"); add("malaysia", "MY"); add("malta", "MT"); add("mexico", "MX");
        add("monaco", "MC"); add("montenegro", "ME"); add("netherlands", "NL"); add("newzealand", "NZ");
        add("northmacedonia", "MK"); add("norway", "NO"); add("philippines", "PH"); add("poland", "PL");
        add("portugal", "PT"); add("qatar", "QA"); add("korea", "KR"); add("southkorea", "KR");
        add("romania", "RO"); add("russia", "RU"); add("serbia", "RS"); add("singapore", "SG");
        add("slovakia", "SK"); add("slovenia", "SI"); add("spain", "ES"); add("sweden", "SE");
        add("switzerland", "CH"); add("thailand", "TH"); add("ukraine", "UA"); add("uae", "AE");
        add("unitedarabemirates", "AE"); add("uk", "GB"); add("unitedkingdom", "GB"); add("usa", "US");
        add("unitedstates", "US"); add("vietnam", "VN"); add("japan", "JP");

        // Existing canonical route aliases used by CountryCodeResolver.
        add("al", "AL"); add("ar", "AR"); add("au", "AU"); add("at", "AT"); add("be", "BE");
        add("br", "BR"); add("bn", "BN"); add("ca", "CA"); add("cl", "CL"); add("cz", "CZ");
        add("de", "DE"); add("dk", "DK"); add("es", "ES"); add("fi", "FI"); add("fr", "FR");
        add("gb", "GB"); add("gr", "GR"); add("id", "ID"); add("ie", "IE"); add("it", "IT");
        add("jp", "JP"); add("kg", "KG"); add("kr", "KR"); add("my", "MY"); add("nl", "NL");
        add("nz", "NZ"); add("pl", "PL"); add("pt", "PT"); add("qa", "QA"); add("ro", "RO");
        add("ru", "RU"); add("sg", "SG"); add("se", "SE"); add("si", "SI"); add("sk", "SK");
        add("th", "TH"); add("ua", "UA"); add("us", "US"); add("vn", "VN"); add("ae", "AE");
    }

    private void add(String key, String value) {
        iso2.put(key, value);
    }

    public String flag(String code) {
        if (code == null || code.trim().isEmpty()) return "🌐";
        String value = code.trim().toLowerCase(Locale.ROOT).replace(" ", "");
        String alpha2 = iso2.get(value);
        if (alpha2 == null) return "🌐";
        return toFlag(alpha2);
    }

    private String toFlag(String alpha2) {
        int first = Character.codePointAt(alpha2, 0) - 'A' + 0x1F1E6;
        int second = Character.codePointAt(alpha2, 1) - 'A' + 0x1F1E6;
        return new String(Character.toChars(first)) + new String(Character.toChars(second));
    }
}
