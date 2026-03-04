package com.laonianai.mapper;

import com.laonianai.entity.Category;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface CategoryMapper {
    // 查询所有分类
    List<Category> findAll();
    // 根据名称查询分类
    Category findByName(String name);
    // 添加分类
    int add(Category category);
    // 修改分类
    int update(Category category);
    // 删除分类
    int delete(Long id);
}