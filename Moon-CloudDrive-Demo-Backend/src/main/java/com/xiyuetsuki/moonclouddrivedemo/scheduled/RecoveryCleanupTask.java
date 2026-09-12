package com.xiyuetsuki.moonclouddrivedemo.scheduled;

import com.xiyuetsuki.moonclouddrivedemo.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 回收站定时清理任务
 * 每天凌晨2点自动彻底删除回收站中超过30天的文件
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class RecoveryCleanupTask {

    private final FileService fileService;

    /**
     * 定时清理回收站中过期的文件
     * cron表达式：每天凌晨2点执行
     * 
     * 清理逻辑：
     * 1. 查询回收站中删除时间早于30天前的所有文件
     * 2. 逐一物理删除数据库记录和OSS上的实际文件
     * 3. 单个文件删除失败不影响其他文件的清理
     */
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupExpiredFiles() {
        log.info("========== 回收站定时清理任务开始 ==========");
        fileService.cleanupExpiredRecycleBinFiles();
        log.info("========== 回收站定时清理任务结束 ==========");
    }
}