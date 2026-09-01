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

    private List<Map<String,Object>> articleList;

    public ArticleController(ObjectMapper objectMapper){
        try {
            articleList = objectMapper.readValue(
                    new ClassPathResource("articles.json").getInputStream(),
                    new TypeReference<List<Map<String,Object>>>() {}
            );

            DateTimeFormatter isoFormatter = DateTimeFormatter.ISO_LOCAL_DATE_TIME;
            DateTimeFormatter displayFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd");

            // 遍历每一篇文章，解析ISO时间，生成 displayPublishDate
            for (Map<String, Object> art : articleList) {
                String publishAtRaw = (String) art.get("publishAt");
                if (StringUtils.isNotBlank(publishAtRaw)) {
                    try {
                        LocalDateTime dt = LocalDateTime.parse(publishAtRaw, isoFormatter);
                        art.put("displayPublishDate", dt.format(displayFormatter));
                    } catch (Exception parseEx) {
                        // 解析失败兜底，直接输出原始字符串
                        art.put("displayPublishDate", publishAtRaw);
                    }
                } else {
                    art.put("displayPublishDate", "");
                }
            }

            // ISO8601字符串字典序倒序排序，新文章放最前面
            articleList.sort((a, b) -> {
                String t1 = (String) a.get("publishAt");
                String t2 = (String) b.get("publishAt");
                if(t1 == null) return 1;
                if(t2 == null) return -1;
                return t2.compareTo(t1);
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
            @RequestParam(defaultValue = "1") Integer page,
            @RequestParam(defaultValue = "10") Integer size,
            @RequestParam(defaultValue = "en") String lang,
            Model model
    ){
        List<Map<String,Object>> source = articleList;
        if(StringUtils.isNotBlank(keyword)){
            String kw = keyword.toLowerCase();
            source = source.stream().filter(art->{
                String titleEn = ((String)art.getOrDefault("titleEn","")).toLowerCase();
                String titleZh = ((String)art.getOrDefault("titleZh","")).toLowerCase();
                String sumEn = ((String)art.getOrDefault("summaryEn","")).toLowerCase();
                String sumZh = ((String)art.getOrDefault("summaryZh","")).toLowerCase();
                return titleEn.contains(kw) || titleZh.contains(kw)
                        || sumEn.contains(kw) || sumZh.contains(kw);
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
                .filter(a->id.equals(a.get("id"))).findFirst();
        if(!opt.isPresent()){
            return "redirect:/articles?lang="+lang;
        }
        Map<String,Object> article = opt.get();
        model.addAttribute("lang", lang);
        model.addAttribute("article", article);
        return "articles-detail";
    }

    /**
     * 接口：获取随机N篇文章，用于页面浮动组件
     */
    @GetMapping("/api/articles/random")
    @ResponseBody
    public List<Map<String,Object>> randomArticles(
            @RequestParam(defaultValue = "5") Integer limit
    ){
        List<Map<String,Object>> copy = new ArrayList<>(articleList);
        Collections.shuffle(copy);
        return copy.stream().limit(limit).collect(Collectors.toList());
    }
}

