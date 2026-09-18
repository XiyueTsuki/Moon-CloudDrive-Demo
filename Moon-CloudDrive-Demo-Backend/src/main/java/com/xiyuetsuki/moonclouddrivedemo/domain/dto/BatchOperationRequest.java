package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

@Data
public class BatchOperationRequest {

    @NotEmpty(message = "文件列表不能为空")
    private List<Long> fileIds;

    private Long targetParentId;

    private String mode;

    private String value;
}