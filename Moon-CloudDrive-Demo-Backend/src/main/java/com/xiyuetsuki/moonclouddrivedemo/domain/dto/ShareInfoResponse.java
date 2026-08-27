package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class ShareInfoResponse {

    private String shareCode;

    private String fileName;

    private Long fileSize;

    private boolean needPassword;

    private String downloadUrl;
}