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

import javax.servlet.http.HttpSession;
import java.util.List;

@Controller
@RequestMapping("/admin/cms")
public class AdminController {

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private TutorialService tutorialService;

    // CMS首页
    @GetMapping("/index")
    public String cmsIndex(HttpSession session, Model model) {
        // 验证登录
        if (session.getAttribute("adminUser") == null) {
            return "redirect:/admin/login";
        }
        List<Category> categories = categoryService.findAll();
        model.addAttribute("categories", categories);
        return "admin/index";
    }

    // ========== 分类管理 ==========
    // 添加分类
    @PostMapping("/category/add")
    @ResponseBody
    public Result addCategory(@RequestBody Category category, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = categoryService.add(category);
        return count > 0 ? Result.success() : Result.error("添加失败");
    }

    // 修改分类
    @PostMapping("/category/update")
    @ResponseBody
    public Result updateCategory(@RequestBody Category category, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = categoryService.update(category);
        return count > 0 ? Result.success() : Result.error("修改失败");
    }

    // 删除分类
    @PostMapping("/category/delete/{id}")
    @ResponseBody
    public Result deleteCategory(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = categoryService.delete(id);
        return count > 0 ? Result.success() : Result.error("删除失败");
    }

    // ========== 教程管理 ==========
    // 添加教程
    @PostMapping("/tutorial/add")
    @ResponseBody
    public Result addTutorial(@RequestBody Tutorial tutorial, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = tutorialService.add(tutorial);
        return count > 0 ? Result.success() : Result.error("添加失败");
    }

    // 修改教程
    @PostMapping("/tutorial/update")
    @ResponseBody
    public Result updateTutorial(@RequestBody Tutorial tutorial, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = tutorialService.update(tutorial);
        return count > 0 ? Result.success() : Result.error("修改失败");
    }

    // 删除教程
    @PostMapping("/tutorial/delete/{id}")
    @ResponseBody
    public Result deleteTutorial(@PathVariable Long id, HttpSession session) {
        if (session.getAttribute("adminUser") == null) {
            return Result.unauth("未登录");
        }
        int count = tutorialService.delete(id);
        return count > 0 ? Result.success() : Result.error("删除失败");
    }
}