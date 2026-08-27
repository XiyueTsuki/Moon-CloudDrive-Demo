package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 文件数据访问层，负责 tb_file 表的数据库操作
 */
@Mapper
public interface FileMapper extends BaseMapper<File> {

    /**
     * 根据文件哈希值查询文件记录，用于秒传去重判断
     *
     * @param fileHash SHA-256 哈希值
     * @return 匹配的文件记录，未找到则返回 null
     */
    @Select("SELECT * FROM tb_file WHERE file_hash = #{fileHash} LIMIT 1")
    File selectByFileHash(@Param("fileHash") String fileHash);

    /**
     * 查询指定用户的所有文件列表，按上传时间倒序排列
     *
     * @param userId 用户ID
     * @return 该用户的文件列表
     */
    @Select("SELECT * FROM tb_file WHERE user_id = #{userId} ORDER BY upload_time DESC")
    List<File> selectByUserId(@Param("userId") Long userId);

    /**
     * 根据用户ID和文件ID查询单条文件记录，确保用户只能操作自己的文件
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     * @return 匹配的文件记录，未找到则返回 null
     */
    @Select("SELECT * FROM tb_file WHERE user_id = #{userId} AND id = #{fileId} LIMIT 1")
    File selectByUserIdAndId(@Param("userId") Long userId, @Param("fileId") Long fileId);
}