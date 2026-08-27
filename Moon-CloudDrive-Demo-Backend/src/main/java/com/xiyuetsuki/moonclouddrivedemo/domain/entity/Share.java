package com.xiyuetsuki.moonclouddrivedemo.domain.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("tb_share")
public class Share {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String shareCode;

    private Long fileId;

    private Long userId;

    private String password;

    private LocalDateTime expireTime;

    private Integer maxDownloads;

    private Integer downloadCount;

    private Integer status;

    private LocalDateTime createTime;
}