package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PageResult;
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
     * @param file     上传的文件
     * @param parentId 父文件夹ID，NULL表示上传到根目录
     * @return 异步任务ID
     */
    String uploadFile(MultipartFile file, Long parentId);

    /**
     * 分页查询当前登录用户指定文件夹下的正常文件/文件夹列表（不含回收站），
     * 支持按名称搜索、按名称/大小/时间排序
     *
     * @param parentId  父文件夹ID，NULL查询根目录
     * @param page      页码（从1开始）
     * @param size      每页条数
     * @param sortBy    排序字段：name / size / uploadTime
     * @param sortOrder 排序方向：asc / desc
     * @param keyword   搜索关键词（匹配 original_filename），null 表示不搜索
     * @return 分页结果
     */
    PageResult<FileVO> listFiles(Long parentId, int page, int size,
                                 String sortBy, String sortOrder, String keyword);

    /**
     * 软删除指定文件或文件夹，将文件移入回收站（仅文件所有者可操作）。
     * 文件夹删除会递归删除目录下所有子孙文件/文件夹
     *
     * @param fileId 文件/文件夹ID
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

    /**
     * 创建新文件夹
     *
     * @param folderName 文件夹名称
     * @param parentId   父文件夹ID，NULL表示根目录
     * @return 创建的文件夹信息
     */
    FileVO createFolder(String folderName, Long parentId);

    /**
     * 移动文件或文件夹到指定目录。
     * 后端正将校验不能将文件夹移动到自身或其子孙文件夹中
     *
     * @param fileId         要移动的文件/文件夹ID
     * @param targetParentId 目标父文件夹ID，NULL表示根目录
     */
    void moveFile(Long fileId, Long targetParentId);

    /**
     * 获取从根目录到指定文件夹的完整路径（面包屑导航）
     *
     * @param folderId 文件夹ID，NULL返回空列表
     * @return 从根到该文件夹的路径列表，根在前
     */
    List<FileVO> getFolderPath(Long folderId);
}