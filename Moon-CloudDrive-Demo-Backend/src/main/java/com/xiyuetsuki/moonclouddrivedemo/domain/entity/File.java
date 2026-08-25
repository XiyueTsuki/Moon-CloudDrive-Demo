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

    private String ossUrl;

    private LocalDateTime uploadTime;
}