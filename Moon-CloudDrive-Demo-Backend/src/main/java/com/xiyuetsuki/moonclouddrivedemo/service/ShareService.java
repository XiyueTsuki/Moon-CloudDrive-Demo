package com.xiyuetsuki.moonclouddrivedemo.service;

import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.ShareInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;

import java.util.List;

public interface ShareService {

    Share createShare(CreateShareRequest request);

    ShareInfoResponse getShareInfo(String shareCode);

    String verifyPassword(String shareCode, String password);

    List<Share> getMyShares();

    void cancelShare(String shareCode);
}