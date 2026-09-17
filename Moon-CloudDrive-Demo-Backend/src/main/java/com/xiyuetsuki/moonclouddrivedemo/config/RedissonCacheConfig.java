package com.xiyuetsuki.moonclouddrivedemo.config;

import org.redisson.api.RedissonClient;
import org.redisson.spring.cache.CacheConfig;
import org.redisson.spring.cache.RedissonSpringCacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.HashMap;
import java.util.Map;

/**
 * 基于 Redisson 的 Spring Cache 配置
 *
 * <h3>缓存三大问题防护策略</h3>
 *
 * <b>1. 缓存穿透</b>——查询不存在的数据反复穿透到 DB
 * 方案：缓存空结果。{@code listFiles} 方法返回空 {@code PageResult}
 * （records=[]）时照常缓存；{@code getFolderPath(null)} 返回空列表也缓存。
 * Redisson RMapCache 不缓存 null 值，因此对不存在的文件 ID 查询（Service 层
 * 直接抛出 BusinessException）不会缓存异常结果。
 *
 * <b>2. 缓存击穿</b>——热 Key 过期瞬间大量请求涌向 DB
 * 方案：不同 Cache Region 使用不同 TTL，降低同一时刻全部过期的概率。
 * Redisson RMapCache 内部对单个 Key 的写入是原子操作，天然避免了同一
 * JVM 内多线程重复加载。
 *
 * <b>3. 缓存雪崩</b>——大量 Key 在同一时刻过期
 * 方案：为每个 Cache Region 设置不同的基础 TTL（2min / 10min），
 * 避免所有缓存同时失效。
 */
@Configuration
@EnableCaching
public class RedissonCacheConfig {

    private static final long TTL_FILE_LIST_MS   = 2 * 60 * 1000L;   // 2 分钟
    private static final long TTL_FOLDER_PATH_MS = 10 * 60 * 1000L;  // 10 分钟
    private static final long TTL_RECYCLE_BIN_MS = 2 * 60 * 1000L;   // 2 分钟

    @Bean
    public RedissonSpringCacheManager cacheManager(RedissonClient redisson) {
        Map<String, CacheConfig> configs = new HashMap<>();

        // 文件列表——高频读写，短 TTL 保障数据新鲜度
        configs.put("fileList", new CacheConfig(TTL_FILE_LIST_MS, 0));

        // 面包屑路径——递归 CTE 查询昂贵，长 TTL
        configs.put("folderPath", new CacheConfig(TTL_FOLDER_PATH_MS, 0));

        // 回收站列表——低频访问，与 fileList 相同 TTL
        configs.put("recycleBin", new CacheConfig(TTL_RECYCLE_BIN_MS, 0));

        return new RedissonSpringCacheManager(redisson, configs);
    }
}