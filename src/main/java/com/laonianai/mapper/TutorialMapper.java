package com.laonianai.mapper;

import com.laonianai.entity.Tutorial;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface TutorialMapper {
    // 根据分类ID查询教程列表
    List<Tutorial> findByCategoryId(Long categoryId);
    // 根据分类名称查询教程列表（适配前端传参）
    List<Tutorial> findByCategoryName(String categoryName);
    // 根据ID查询详情
    Tutorial findById(Long id);
    // 添加教程
    int add(Tutorial tutorial);
    // 修改教程
    int update(Tutorial tutorial);
    // 删除教程
    int delete(Long id);

    Tutorial selectByUrl(String url);

    List<Tutorial> selectByCategory(String category);
}