package com.xiyuetsuki.moonclouddrivedemo.controller;

import com.xiyuetsuki.moonclouddrivedemo.domain.common.Response;
import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/file")
@Slf4j
@RequiredArgsConstructor
public class FileController {

    private final FileService fileService;

    @PostMapping("/upload")
    public Response<Void> uploadFile(MultipartFile file) {
        if (file.isEmpty()) {
            return Response.bad(400, "文件不能为空");
        }

        fileService.uploadFile(file);

        return Response.ok("文件上传成功");
    }
}
