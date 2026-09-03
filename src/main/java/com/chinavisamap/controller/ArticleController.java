package com.chinavisamap.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Controller
public class ArticleController {

    private List<Map<String, Object>> articleList;

    // JDK8 不支持 List.of，使用 Arrays.asList + HashMap 构建全部分类
    public static final List<Map<String, String>> ALL_CATEGORIES;
    static {
        List<Map<String,String>> temp = new ArrayList<>();
        Map<String,String> cat1 = new HashMap<>();
        cat1.put("en","Visa-Free Tips");
        cat1.put("zh","免签政策提示");
        temp.add(cat1);

        Map<String,String> cat2 = new HashMap<>();
        cat2.put("en","China Life & Apps");
        cat2.put("zh","中国生活与APP");
        temp.add(cat2);

        Map<String,String> cat3 = new HashMap<>();
        cat3.put("en","Travel Customs");
        cat3.put("zh","旅行风俗文化");
        temp.add(cat3);

        Map<String,String> cat4 = new HashMap<>();
        cat4.put("en","Border-Inspection Pitfalls");
        cat4.put("zh","边检出入境踩坑");
        temp.add(cat4);

        ALL_CATEGORIES = Collections.unmodifiableList(temp);
    }

    public ArticleController(ObjectMapper objectMapper){
        try {
            articleList = objectMapper.readValue(
                    new ClassPathResource("articles.json").getInputStream(),
                    new TypeReference<List<Map<String,Object>>>() {}
            );
            DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");
            for (Map<String, Object> art : articleList) {
                String publishAtRaw = (String) art.get("publishAt");
                if (StringUtils.isNotBlank(publishAtRaw)) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(publishAtRaw, isoFormatter);
                        art.put("displayPublishDate", dt.format(displayFormatter));
                    } catch (Exception parseEx) {
                        art.put("displayPublishDate", publishAtRaw);
                    }
                } else {
                    art.put("displayPublishDate", "");
                }
            }
            // 按发布时间倒序
            articleList.sort(new Comparator<Map<String, Object>>() {
                @Override
                public int compare(Map<String, Object> a, Map<String, Object> b) {
                    String t1 = (String) a.get("publishAt");
                    String t2 = (String) b.get("publishAt");
                    if(t1 == null) return 1;
                    if(t2 == null) return -1;
                    return t2.compareTo(t1);
                }
            });
        } catch (Exception e) {
            articleList = Collections.emptyList();
        }
    }

    /**
     * 文章列表页 /articles
     */
    @GetMapping("/articles")
    public String articleList(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ){
        List<Map<String,Object>> source = new ArrayList<>(articleList);

        // 分类过滤
        if(StringUtils.isNotBlank(category)){
            final String catParam = category;
            source = source.stream()
                    .filter(new java.util.function.Predicate<Map<String, Object>>() {
                        @Override
                        public boolean test(Map<String, Object> art) {
                            String catEn = (String) art.getOrDefault("categoryEn","");
                            String catZh = (String) art.getOrDefault("categoryZh","");
                            return catParam.equals(catEn) || catParam.equals(catZh);
                        }
                    })
                    .collect(Collectors.toList());
        }

        // 关键词搜索：标题/摘要/分类
        if(StringUtils.isNotBlank(keyword)){
            final String kw = keyword.toLowerCase();
            source = source.stream().filter(new java.util.function.Predicate<Map<String, Object>>() {
                @Override
                public boolean test(Map<String, Object> art) {
                    String titleEn = ((String)art.getOrDefault("titleEn","")).toLowerCase();
                    String titleZh = ((String)art.getOrDefault("titleZh","")).toLowerCase();
                    String sumEn = ((String)art.getOrDefault("summaryEn","")).toLowerCase();
                    String sumZh = ((String)art.getOrDefault("summaryZh","")).toLowerCase();
                    String catEn = ((String)art.getOrDefault("categoryEn","")).toLowerCase();
                    String catZh = ((String)art.getOrDefault("categoryZh","")).toLowerCase();
                    return titleEn.contains(kw) || titleZh.contains(kw)
                            || sumEn.contains(kw) || sumZh.contains(kw)
                            || catEn.contains(kw) || catZh.contains(kw);
                }
            }).collect(Collectors.toList());
        }

        int total = source.size();
        int totalPages = (int)Math.ceil((double)total / size);
        int offset = (page-1)*size;
        List<Map<String,Object>> pageData;
        if(offset >= total){
            pageData = Collections.emptyList();
        }else {
            int end = Math.min(offset+size, total);
            pageData = source.subList(offset, end);
        }

        model.addAttribute("lang", lang);
        model.addAttribute("keyword", keyword);
        model.addAttribute("category", category);
        model.addAttribute("allCategories", ALL_CATEGORIES);
        model.addAttribute("page", page);
        model.addAttribute("size", size);
        model.addAttribute("total", total);
        model.addAttribute("totalPages", totalPages);
        model.addAttribute("articlePageList", pageData);
        return "articles-list";
    }

    /**
     * 文章详情页 /articles/{id}
     */
    @GetMapping("/articles/{id}")
    public String articleDetail(
            @PathVariable String id,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ){
        Optional<Map<String, Object>> opt = articleList.stream()
                .filter(new java.util.function.Predicate<Map<String, Object>>() {
                    @Override
                    public boolean test(Map<String, Object> a) {
                        return id.equals(a.get("id"));
                    }
                }).findFirst();
        if(!opt.isPresent()){
            return "redirect:/articles?lang="+lang;
        }
        Map<String,Object> article = opt.get();
        model.addAttribute("lang", lang);
        model.addAttribute("article", article);
        return "articles-detail";
    }

    /**
     * 获取随机N篇文章API，用于页面推荐阅读
     */
    @GetMapping("/api/articles/random")
    @ResponseBody
    public List<Map<String,Object>> randomArticles(
            @RequestParam(defaultValue = "5") Integer limit
    ){
        List<Map<String,Object>> copy = new ArrayList<>(articleList);
        Collections.shuffle(copy);
        return copy.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }
}
