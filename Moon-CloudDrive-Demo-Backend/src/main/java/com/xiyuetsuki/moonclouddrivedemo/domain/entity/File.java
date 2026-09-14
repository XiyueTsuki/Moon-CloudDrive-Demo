package com.xiyuetsuki.moonclouddrivedemo.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_file")
public class File {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String originalFilename;

    private String storedFilename;

    private Long fileSize;

    private String contentType;

    private String fileHash;

    private Long userId;

    private String ossUrl;

    private LocalDateTime uploadTime;

    /** 软删除标记：0-正常，1-已删除（回收站中） */
    private Integer deleted;

    /** 进入回收站的时间，用于计算30天自动清理 */
    private LocalDateTime deleteTime;

    /** 父文件夹ID，NULL表示根目录 */
    private Long parentId;

    /** 是否为文件夹：0-文件，1-文件夹 */
    private Integer isFolder;
}