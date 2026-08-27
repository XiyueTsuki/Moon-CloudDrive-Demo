package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface ShareMapper extends BaseMapper<Share> {

    @Select("SELECT * FROM tb_share WHERE share_code = #{shareCode} LIMIT 1")
    Share selectByShareCode(@Param("shareCode") String shareCode);

    @Select("SELECT * FROM tb_share WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Share> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM tb_share WHERE status = 1 AND expire_time > NOW()")
    List<Share> selectUnexpired();
}