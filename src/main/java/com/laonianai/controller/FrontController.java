package com.laonianai.controller;

import com.laonianai.entity.Category;
import com.laonianai.entity.Tutorial;
import com.laonianai.service.CategoryService;
import com.laonianai.service.TutorialService;
import com.laonianai.util.Result;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Controller
@RequestMapping("/tutorial")
public class FrontController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TutorialService tutorialService;

    // 首页（返回静态页面）
    @GetMapping("/index")
    public String index(Model model) {
        // 传递所有分类到前端
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);
        return "forward:/index.html";
    }

    // 1. 分类列表页（适配老人+SEO）
    @GetMapping("/list")
    public String listByCategory(
            @RequestParam(value = "category", required = true) String category,
            Model model) {
        // 查询该分类下的10条教程（按更新时间排序，SEO友好）
        List<Tutorial> tutorials = tutorialService.findByCategory(category);
        // 适配老人：页面参数放大、简化
        model.addAttribute("category", category);
        model.addAttribute("tutorials", tutorials);
        model.addAttribute("title", category + " - 老年AI助手"); // SEO标题
        return "tutorial/list"; // 对应list.html模板
    }

    // 2. 教程详情页（适配老人+SEO）
    @GetMapping("/detail/{url}")
    public String detailByUrl(
            @PathVariable String url,
            Model model) {
        // 按URL查询详情（URL语义化，SEO友好）
        Tutorial tutorial = tutorialService.findByUrl(url);
        if (tutorial == null) {
            // 404适配：返回友好提示，不是空白页
            model.addAttribute("msg", "没找到这个教程哦，换个分类看看吧");
            return "tutorial/404";
        }
        // 适配老人：内容放大、步骤分行
        model.addAttribute("categoryName", tutorial.getCategoryName());
        model.addAttribute("tutorial", tutorial);
        model.addAttribute("title", tutorial.getTitle() + " - 老年AI助手"); // SEO标题
        return "tutorial/detail"; // 对应detail.html模板
    }


    // AI问答接口（适配前端doAsk方法）
    @PostMapping("/ai/ask")
    @ResponseBody
    public Result aiAsk(@RequestParam String question) {
        // 限制32字符
        if (question == null || question.trim().isEmpty()) {
            return Result.error("提问内容不能为空");
        }
        if (question.length() > 32) {
            question = question.substring(0, 32);
        }
        // 模拟AI回答（实际可对接GPT/讯飞等AI接口）
        String answer = "已收到您的提问：" + question + "，AI正在处理，请稍等...";
        return Result.success(answer);
    }

    // 获取所有分类（供前端异步调用）
    @GetMapping("/categories")
    @ResponseBody
    public Result getCategories() {
        List<Category> categories = categoryService.findAll();
        return Result.success(categories);
    }
}