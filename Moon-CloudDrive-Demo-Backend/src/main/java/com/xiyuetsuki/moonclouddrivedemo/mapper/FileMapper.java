package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface FileMapper extends BaseMapper<File> {
}