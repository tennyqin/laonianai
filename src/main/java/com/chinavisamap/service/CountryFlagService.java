package com.chinavisamap.service;

import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Resolves country slugs and ISO-2 aliases to ISO-2 country codes. */
@Service("countryFlagService")
public class CountryFlagService {
    private final Map<String,String> iso2 = new HashMap<>();

    public CountryFlagService() {
        add("albania","AL"); add("andorra","AD"); add("argentina","AR"); add("armenia","AM"); add("australia","AU"); add("austria","AT");
        add("azerbaijan","AZ"); add("belarus","BY"); add("belgium","BE"); add("bosnia","BA"); add("bosniaandherzegovina","BA");
        add("brazil","BR"); add("brunei","BN"); add("bulgaria","BG"); add("canada","CA"); add("chile","CL");
        add("croatia","HR"); add("cyprus","CY"); add("czechia","CZ"); add("czech","CZ"); add("denmark","DK");
        add("estonia","EE"); add("finland","FI"); add("france","FR"); add("georgia","GE"); add("germany","DE");
        add("greece","GR"); add("hungary","HU"); add("iceland","IS"); add("indonesia","ID"); add("ireland","IE");
        add("italy","IT"); add("japan","JP"); add("kazakhstan","KZ"); add("korea","KR"); add("southkorea","KR");
        add("kyrgyzstan","KG"); add("kuwait","KW"); add("latvia","LV"); add("liechtenstein","LI"); add("lithuania","LT");
        add("luxembourg","LU"); add("malaysia","MY"); add("maldives","MV"); add("malta","MT"); add("mexico","MX");
        add("monaco","MC"); add("montenegro","ME"); add("netherlands","NL"); add("newzealand","NZ"); add("northmacedonia","MK");
        add("norway","NO"); add("oman","OM"); add("peru","PE"); add("philippines","PH"); add("poland","PL");
        add("portugal","PT"); add("qatar","QA"); add("romania","RO"); add("russia","RU"); add("saudi","SA"); add("saudiarabia","SA");
        add("sanmarino","SM"); add("serbia","RS"); add("singapore","SG"); add("slovakia","SK"); add("slovenia","SI");
        add("spain","ES"); add("sweden","SE"); add("switzerland","CH"); add("thailand","TH"); add("uk","GB");
        add("unitedkingdom","GB"); add("ukraine","UA"); add("uae","AE"); add("unitedarabemirates","AE"); add("usa","US");
        add("unitedstates","US"); add("uruguay","UY"); add("uzbekistan","UZ"); add("vietnam","VN"); add("bahrain","BH");
        add("antigua","AG"); add("antiguaandbarbuda","AG"); add("barbados","BB"); add("bahamas","BS"); add("dominica","DM");
        add("ecuador","EC"); add("fiji","FJ"); add("grenada","GD"); add("mauritius","MU"); add("samoa","WS");
        add("seychelles","SC"); add("solomon","SB"); add("solomonislands","SB"); add("suriname","SR"); add("tonga","TO");
        add("guyana","GY"); add("jamaica","JM");

        String[][] aliases = {
            {"ad","AD"},{"al","AL"},{"ar","AR"},{"am","AM"},{"au","AU"},{"at","AT"},{"az","AZ"},{"by","BY"},{"be","BE"},{"ba","BA"},{"br","BR"},
            {"bn","BN"},{"bg","BG"},{"ca","CA"},{"cl","CL"},{"hr","HR"},{"cy","CY"},{"cz","CZ"},{"dk","DK"},{"ee","EE"},{"fi","FI"},
            {"fr","FR"},{"ge","GE"},{"de","DE"},{"gr","GR"},{"hu","HU"},{"is","IS"},{"id","ID"},{"ie","IE"},{"it","IT"},{"jp","JP"},
            {"kz","KZ"},{"kr","KR"},{"kg","KG"},{"kw","KW"},{"lv","LV"},{"li","LI"},{"lt","LT"},{"lu","LU"},{"my","MY"},{"mv","MV"},
            {"mt","MT"},{"mx","MX"},{"mc","MC"},{"me","ME"},{"nl","NL"},{"nz","NZ"},{"mk","MK"},{"no","NO"},{"om","OM"},{"pe","PE"},
            {"ph","PH"},{"pl","PL"},{"pt","PT"},{"qa","QA"},{"ro","RO"},{"ru","RU"},{"sa","SA"},{"sm","SM"},{"rs","RS"},{"sg","SG"},
            {"sk","SK"},{"si","SI"},{"es","ES"},{"se","SE"},{"ch","CH"},{"th","TH"},{"gb","GB"},{"ua","UA"},{"ae","AE"},{"us","US"},
            {"uy","UY"},{"uz","UZ"},{"vn","VN"},{"bh","BH"},{"ag","AG"},{"bb","BB"},{"bs","BS"},{"dm","DM"},{"ec","EC"},{"fj","FJ"},
            {"gd","GD"},{"mu","MU"},{"ws","WS"},{"sc","SC"},{"sb","SB"},{"sr","SR"},{"to","TO"},{"gy","GY"},{"jm","JM"}
        };
        for(String[] a:aliases)add(a[0],a[1]);
    }

    private void add(String key,String value){iso2.put(key,value);}

    public String iso2Code(String code){
        if(code==null||code.trim().isEmpty())return "xx";
        String value=code.trim().toLowerCase(Locale.ROOT).replace(" ","").replace("_","").replace("-","");
        return iso2.getOrDefault(value,"XX").toLowerCase(Locale.ROOT);
    }

    public String flag(String code){
        String alpha2=iso2Code(code).toUpperCase(Locale.ROOT);
        if("XX".equals(alpha2))return "🌐";
        int first=Character.codePointAt(alpha2,0)-'A'+0x1F1E6;
        int second=Character.codePointAt(alpha2,1)-'A'+0x1F1E6;
        return new String(Character.toChars(first))+new String(Character.toChars(second));
    }
}
