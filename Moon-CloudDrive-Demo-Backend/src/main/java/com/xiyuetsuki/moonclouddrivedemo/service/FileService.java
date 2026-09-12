package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 文件服务接口，定义文件相关的业务操作
 */
public interface FileService {

    /** 回收站文件默认保留天数 */
    int RECYCLE_RETENTION_DAYS = 30;

    /**
     * 上传文件，返回异步任务ID用于进度查询
     *
     * @param file 上传的文件
     * @return 异步任务ID
     */
    String uploadFile(MultipartFile file);

    /**
     * 查询当前登录用户的正常文件列表（不含回收站）
     *
     * @return 文件信息列表
     */
    List<FileVO> listFiles();

    /**
     * 软删除指定文件，将文件移入回收站（仅文件所有者可操作）
     *
     * @param fileId 文件ID
     */
    void deleteFile(Long fileId);

    /**
     * 重命名指定文件（仅文件所有者可操作）
     *
     * @param fileId  文件ID
     * @param newName 新文件名
     */
    void renameFile(Long fileId, String newName);

    /**
     * 获取文件的OSS预签名下载URL（有效期1小时）
     *
     * @param fileId 文件ID
     * @return 预签名下载URL
     */
    String getDownloadUrl(Long fileId);

    /**
     * 查询当前登录用户的回收站文件列表
     *
     * @return 回收站文件列表
     */
    List<FileVO> listRecycleBin();

    /**
     * 从回收站恢复文件
     *
     * @param fileId 文件ID
     */
    void restoreFile(Long fileId);

    /**
     * 彻底删除回收站中的文件（物理删除 + OSS删除）
     *
     * @param fileId 文件ID
     */
    void permanentDeleteFile(Long fileId);

    /**
     * 定时清理：彻底删除回收站中超过 {@link #RECYCLE_RETENTION_DAYS} 天的文件
     */
    void cleanupExpiredRecycleBinFiles();
}