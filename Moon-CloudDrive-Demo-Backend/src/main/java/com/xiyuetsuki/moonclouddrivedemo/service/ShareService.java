package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;

import java.util.List;

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
}