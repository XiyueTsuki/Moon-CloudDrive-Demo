package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

@Data
public class CreateShareRequest {

    private Long fileId;

    private String password;

    private Integer expireHours;

    private Integer maxDownloads;
}