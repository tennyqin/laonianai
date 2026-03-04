package com.laonianai.service.impl;

import com.laonianai.entity.Category;
import com.laonianai.mapper.CategoryMapper;
import com.laonianai.service.CategoryService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoryServiceImpl implements CategoryService {

    @Autowired
    private CategoryMapper categoryMapper;

    @Override
    public List<Category> findAll() {
        return categoryMapper.findAll();
    }

    @Override
    public Category findByName(String name) {
        return categoryMapper.findByName(name);
    }

    @Override
    public int add(Category category) {
        return categoryMapper.add(category);
    }

    @Override
    public int update(Category category) {
        return categoryMapper.update(category);
    }

    @Override
    public int delete(Long id) {
        return categoryMapper.delete(id);
    }
}
