package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PackProgressResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;

import java.util.List;

/**
 * 分享服务接口，负责分享链接的创建、校验、下载及打包下载
 */
public interface ShareService {

    /** 创建分享链接 */
    Share createShare(CreateShareRequest request);

    /** 获取分享信息（仅元数据，不返回下载链接，不计次数） */
    ShareInfoResponse getShareInfo(String shareCode);

    /** 验证提取码（仅校验，不返回下载链接，不计次数） */
    void verifyPassword(String shareCode, String password);

    /** 获取下载链接（递增下载次数，返回预签名URL） */
    String getDownloadUrl(String shareCode, String password);

    /** 获取我的分享列表 */
    List<Share> getMyShares();

    /** 取消分享 */
    void cancelShare(String shareCode);

    /**
     * 提交分享文件夹的打包下载任务
     * 校验分享有效性后，递归收集文件夹下所有文件，提交异步打包任务
     *
     * @param shareCode 分享码
     * @param password  提取码（可选）
     * @return 打包任务ID，用于前端轮询进度
     */
    String prepareSharePackDownload(String shareCode, String password);

    /**
     * 查询分享文件夹打包进度
     *
     * @param shareCode 分享码
     * @param taskId    打包任务ID
     * @return 进度信息
     */
    PackProgressResponse getSharePackProgress(String shareCode, String taskId);

    /**
     * 获取分享打包完成的 ZIP 文件路径
     *
     * @param shareCode 分享码
     * @param taskId    打包任务ID
     * @return ZIP 文件绝对路径，未就绪或不存在返回 null
     */
    String getSharePackFilePath(String shareCode, String taskId);
}