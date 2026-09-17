package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.Data;

import java.util.List;

@Data
public class BatchOperationRequest {

    private List<Long> fileIds;

    private Long targetParentId;

    private String mode;

    private String value;
}