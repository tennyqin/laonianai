package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Resolves the site's country slug / ISO-2 alias to a flag emoji.
 * Keep this mapping independent from SEO route aliases: a valid country slug
 * must always render a real flag instead of a generic globe.
 */
@Service("countryFlagService")
public class CountryFlagService {

    private final Map<String, String> iso2 = new HashMap<>();

    public CountryFlagService() {
        // Canonical country slugs used by the policy JSON files.
        add("albania", "AL"); add("argentina", "AR"); add("armenia", "AM"); add("australia", "AU");
        add("austria", "AT"); add("azerbaijan", "AZ"); add("belarus", "BY"); add("belgium", "BE");
        add("bosnia", "BA"); add("bosniaandherzegovina", "BA"); add("brazil", "BR"); add("brunei", "BN");
        add("bulgaria", "BG"); add("canada", "CA"); add("chile", "CL"); add("croatia", "HR");
        add("cyprus", "CY"); add("czechia", "CZ"); add("czech", "CZ"); add("denmark", "DK");
        add("estonia", "EE"); add("finland", "FI"); add("france", "FR"); add("georgia", "GE");
        add("germany", "DE"); add("greece", "GR"); add("hungary", "HU"); add("iceland", "IS");
        add("indonesia", "ID"); add("ireland", "IE"); add("italy", "IT"); add("japan", "JP");
        add("kazakhstan", "KZ"); add("korea", "KR"); add("southkorea", "KR"); add("kyrgyzstan", "KG");
        add("latvia", "LV"); add("liechtenstein", "LI"); add("lithuania", "LT"); add("luxembourg", "LU");
        add("malaysia", "MY"); add("maldives", "MV"); add("malta", "MT"); add("mexico", "MX");
        add("monaco", "MC"); add("montenegro", "ME"); add("netherlands", "NL"); add("newzealand", "NZ");
        add("northmacedonia", "MK"); add("norway", "NO"); add("oman", "OM"); add("peru", "PE");
        add("philippines", "PH"); add("poland", "PL"); add("portugal", "PT"); add("qatar", "QA");
        add("romania", "RO"); add("russia", "RU"); add("saudiarabia", "SA"); add("saudi-arabia", "SA");
        add("sanmarino", "SM"); add("serbia", "RS"); add("singapore", "SG"); add("slovakia", "SK");
        add("slovenia", "SI"); add("spain", "ES"); add("sweden", "SE"); add("switzerland", "CH");
        add("thailand", "TH"); add("uk", "GB"); add("unitedkingdom", "GB"); add("ukraine", "UA");
        add("uae", "AE"); add("unitedarabemirates", "AE"); add("usa", "US"); add("unitedstates", "US");
        add("uruguay", "UY"); add("uzbekistan", "UZ"); add("vietnam", "VN");
        add("antiguaandbarbuda", "AG"); add("barbados", "BB"); add("bahamas", "BS"); add("dominica", "DM");
        add("grenada", "GD"); add("guyana", "GY"); add("suriname", "SR"); add("jamaica", "JM");
        add("mauritius", "MU"); add("seychelles", "SC");

        // ISO-2 / legacy URL aliases. These must point to the same flag as the canonical slug.
        add("al", "AL"); add("ar", "AR"); add("am", "AM"); add("au", "AU"); add("at", "AT");
        add("az", "AZ"); add("by", "BY"); add("be", "BE"); add("ba", "BA"); add("br", "BR");
        add("bn", "BN"); add("bg", "BG"); add("ca", "CA"); add("cl", "CL"); add("hr", "HR");
        add("cy", "CY"); add("cz", "CZ"); add("dk", "DK"); add("ee", "EE"); add("fi", "FI");
        add("fr", "FR"); add("ge", "GE"); add("de", "DE"); add("gr", "GR"); add("hu", "HU");
        add("is", "IS"); add("id", "ID"); add("ie", "IE"); add("it", "IT"); add("jp", "JP");
        add("kz", "KZ"); add("kr", "KR"); add("kg", "KG"); add("lv", "LV"); add("li", "LI");
        add("lt", "LT"); add("lu", "LU"); add("my", "MY"); add("mv", "MV"); add("mt", "MT");
        add("mx", "MX"); add("mc", "MC"); add("me", "ME"); add("nl", "NL"); add("nz", "NZ");
        add("mk", "MK"); add("no", "NO"); add("om", "OM"); add("pe", "PE"); add("ph", "PH");
        add("pl", "PL"); add("pt", "PT"); add("qa", "QA"); add("ro", "RO"); add("ru", "RU");
        add("sa", "SA"); add("sm", "SM"); add("rs", "RS"); add("sg", "SG"); add("sk", "SK");
        add("si", "SI"); add("es", "ES"); add("se", "SE"); add("ch", "CH"); add("th", "TH");
        add("gb", "GB"); add("ua", "UA"); add("ae", "AE"); add("us", "US"); add("uy", "UY");
        add("uz", "UZ"); add("vn", "VN"); add("ag", "AG"); add("bb", "BB"); add("bs", "BS");
        add("dm", "DM"); add("gd", "GD"); add("gy", "GY"); add("sr", "SR"); add("jm", "JM");
        add("mu", "MU"); add("sc", "SC");
    }

    private void add(String key, String value) {
        iso2.put(key, value);
    }

    public String flag(String code) {
        if (code == null || code.trim().isEmpty()) return "🌐";
        String value = code.trim().toLowerCase(Locale.ROOT)
                .replace(" ", "")
                .replace("_", "")
                .replace("-", "");
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
