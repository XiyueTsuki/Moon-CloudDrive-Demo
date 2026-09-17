package com.xiyuetsuki.moonclouddrivedemo.domain.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class BatchOperationResult {

    private int successCount;

    private int failCount;

    private List<String> failReasons = new ArrayList<>();

    public void addFail(String reason) {
        failReasons.add(reason);
        failCount++;
    }

    public void addSuccess() {
        successCount++;
    }

    public static BatchOperationResult empty() {
        return new BatchOperationResult(0, 0, new ArrayList<>());
    }
}