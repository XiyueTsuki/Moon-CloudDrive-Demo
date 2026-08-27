package com.xiyuetsuki.moonclouddrivedemo.service;

import java.time.LocalDateTime;

public interface ShareExpireManager {

    void schedule(String shareCode, LocalDateTime expireTime);
}