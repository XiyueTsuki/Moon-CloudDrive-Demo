package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

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
     * 统计指定 OSS 存储文件名（stored_filename）被引用的文件记录数。
     * 秒传后多个文件记录可能指向同一个 OSS 对象，彻底删除时需据此判断
     * 是否为最后一个引用，避免误删仍被其他记录引用的 OSS 文件本体。
     *
     * @param storedFilename OSS 存储文件名
     * @return 引用该 OSS 对象的文件记录数
     */
    @Select("SELECT COUNT(*) FROM tb_file WHERE stored_filename = #{storedFilename}")
    long countByStoredFilename(@Param("storedFilename") String storedFilename);

    /**
     * 统计指定用户在指定文件夹下的文件/文件夹数量，支持按文件名模糊搜索
     *
     * @param userId   用户ID
     * @param parentId 父文件夹ID，NULL 查询根目录
     * @param keyword  搜索关键词（模糊匹配 original_filename），null 表示不搜索
     * @return 符合条件的记录总数
     */
    @Select("<script>"
            + "SELECT COUNT(*) FROM tb_file"
            + " WHERE user_id = #{userId} AND (deleted IS NULL OR deleted = 0)"
            + " AND (parent_id = #{parentId} OR (#{parentId} IS NULL AND parent_id IS NULL))"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND original_filename LIKE CONCAT('%', #{keyword}, '%')"
            + "</if>"
            + "</script>")
    long countFiles(@Param("userId") Long userId, @Param("parentId") Long parentId,
                    @Param("keyword") String keyword);

    /**
     * 分页查询指定用户在指定文件夹下的文件/文件夹列表，支持排序和搜索。
     * sortColumn 和 sortOrder 由 Service 层白名单校验后传入，确保 SQL 安全
     *
     * @param userId     用户ID
     * @param parentId   父文件夹ID，NULL 查询根目录
     * @param keyword    搜索关键词，null 表示不搜索
     * @param sortColumn 排序字段（数据库列名，已校验）
     * @param sortOrder  排序方向（ASC / DESC，已校验）
     * @param offset     偏移量
     * @param size       每页条数
     * @return 文件/文件夹列表
     */
    @Select("<script>"
            + "SELECT * FROM tb_file"
            + " WHERE user_id = #{userId} AND (deleted IS NULL OR deleted = 0)"
            + " AND (parent_id = #{parentId} OR (#{parentId} IS NULL AND parent_id IS NULL))"
            + "<if test='keyword != null and keyword != \"\"'>"
            + " AND original_filename LIKE CONCAT('%', #{keyword}, '%')"
            + "</if>"
            + " ORDER BY is_folder DESC, ${sortColumn} ${sortOrder}"
            + " LIMIT #{size} OFFSET #{offset}"
            + "</script>")
    List<File> selectPage(@Param("userId") Long userId, @Param("parentId") Long parentId,
                          @Param("keyword") String keyword,
                          @Param("sortColumn") String sortColumn, @Param("sortOrder") String sortOrder,
                          @Param("offset") int offset, @Param("size") int size);

    /**
     * 查询指定用户的回收站文件列表，按删除时间倒序排列
     *
     * @param userId 用户ID
     * @return 该用户回收站中的文件列表
     */
    @Select("SELECT * FROM tb_file WHERE user_id = #{userId} AND deleted = 1 ORDER BY delete_time DESC")
    List<File> selectRecycleBinByUserId(@Param("userId") Long userId);

    /**
     * 查询回收站中删除时间超过指定天数的文件，用于定时清理任务
     *
     * @param days 天数
     * @return 待彻底删除的文件列表
     */
    @Select("SELECT * FROM tb_file WHERE deleted = 1 AND delete_time < DATE_SUB(NOW(), INTERVAL #{days} DAY)")
    List<File> selectExpiredRecycleBinFiles(@Param("days") int days);

    /**
     * 根据用户ID和文件ID查询单条文件记录，确保用户只能操作自己的文件
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     * @return 匹配的文件记录，未找到则返回 null
     */
    @Select("SELECT * FROM tb_file WHERE user_id = #{userId} AND id = #{fileId} LIMIT 1")
    File selectByUserIdAndId(@Param("userId") Long userId, @Param("fileId") Long fileId);

    /**
     * 递归查询指定文件夹 ID 的所有子孙节点（包括文件和子文件夹），
     * 使用 MySQL 8 递归 CTE 实现，用于删除/移动文件夹时获取所有受影响条目
     *
     * @param folderId 文件夹ID
     * @return 该文件夹及其下的所有子孙节点
     */
    @Select("WITH RECURSIVE cte AS ("
            + "  SELECT id FROM tb_file WHERE id = #{folderId} "
            + "  UNION ALL "
            + "  SELECT f.id FROM tb_file f INNER JOIN cte ON f.parent_id = cte.id"
            + ") SELECT * FROM tb_file WHERE id IN (SELECT id FROM cte)")
    List<File> selectAllDescendants(@Param("folderId") Long folderId);

    /**
     * 查询从根目录到指定文件夹的完整路径链（面包屑导航），
     * 使用 MySQL 8 递归 CTE 从当前节点向上追溯到根
     *
     * @param folderId 文件夹ID
     * @return 从根到该文件夹的路径列表（按深度升序，根在前）
     */
    @Select("WITH RECURSIVE cte AS ("
            + "  SELECT id, original_filename, parent_id, 0 AS depth FROM tb_file WHERE id = #{folderId} "
            + "  UNION ALL "
            + "  SELECT f.id, f.original_filename, f.parent_id, cte.depth - 1 FROM tb_file f "
            + "  INNER JOIN cte ON f.id = cte.parent_id"
            + ") SELECT id, original_filename, parent_id FROM cte ORDER BY depth ASC")
    List<File> selectFolderPath(@Param("folderId") Long folderId);

    /**
     * 查询指定用户名下、指定父文件夹下同名的文件/文件夹数量，用于重名校验
     *
     * @param userId   用户ID
     * @param parentId 父文件夹ID
     * @param filename 文件/文件夹名
     * @return 同名数量
     */
    @Select("SELECT COUNT(*) FROM tb_file WHERE user_id = #{userId} AND (deleted IS NULL OR deleted = 0) "
            + "AND (parent_id = #{parentId} OR (#{parentId} IS NULL AND parent_id IS NULL)) "
            + "AND original_filename = #{filename}")
    int countByNameAndParent(@Param("userId") Long userId, @Param("parentId") Long parentId,
                             @Param("filename") String filename);

    /**
     * 更新文件/文件夹的父文件夹ID，用于移动操作。
     * 必须用显式 SQL 而非 MyBatis-Plus 的 updateById，
     * 因为后者默认策略 NOT_NULL 会跳过 null 值（移回根目录时 parent_id 应为 NULL）
     *
     * @param fileId   文件/文件夹ID
     * @param parentId 目标父文件夹ID，可为 null 表示根目录
     */
    @Update("UPDATE tb_file SET parent_id = #{parentId} WHERE id = #{fileId}")
    int updateParentId(@Param("fileId") Long fileId, @Param("parentId") Long parentId);
}