package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("SELECT * FROM tb_user WHERE email = #{email} LIMIT 1")
    User selectByEmail(@Param("email") String email);
}