package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface FileMapper extends BaseMapper<File> {

    @Select("SELECT * FROM tb_file WHERE file_hash = #{fileHash} LIMIT 1")
    File selectByFileHash(@Param("fileHash") String fileHash);
}