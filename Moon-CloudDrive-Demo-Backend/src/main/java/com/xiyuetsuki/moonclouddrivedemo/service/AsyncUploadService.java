package com.xiyuetsuki.moonclouddrivedemo.service;

public interface AsyncUploadService {

    void execute(String taskId, long userId, String originalFilename,
            byte[] fileBytes, long fileSize, String contentType);
}