package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface ShareMapper extends BaseMapper<Share> {

    @Select("SELECT * FROM tb_share WHERE share_code = #{shareCode} LIMIT 1")
    Share selectByShareCode(@Param("shareCode") String shareCode);

    @Select("SELECT * FROM tb_share WHERE user_id = #{userId} ORDER BY create_time DESC")
    List<Share> selectByUserId(@Param("userId") Long userId);

    @Select("SELECT * FROM tb_share WHERE status = 1 AND expire_time > NOW()")
    List<Share> selectUnexpired();

    /**
     * 原子递增下载次数，同时判断是否达到最大下载次数并自动失效
     * <p>
     * 使用数据库行级锁保证并发安全，WHERE 条件确保：
     * 1. 分享处于有效状态(status=1)
     * 2. 未达到最大下载限制(max_downloads=0 表示无限制)
     * <p>
     * 返回值 &gt; 0 表示更新成功（行受影响），返回 0 表示分享已失效或达到上限，
     * 此时应重新查询数据库获取实际的 downloadCount 来判断失败原因
     *
     * @param id 分享记录主键
     * @return 受影响行数
     */
    @Update("UPDATE tb_share SET download_count = download_count + 1, "
            + "status = CASE WHEN max_downloads > 0 AND download_count + 1 >= max_downloads THEN 0 ELSE status END "
            + "WHERE id = #{id} AND status = 1 "
            + "AND (max_downloads = 0 OR download_count < max_downloads)")
    int incrementDownloadCountAndCheckLimit(@Param("id") Long id);
}