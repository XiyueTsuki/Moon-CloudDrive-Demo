package com.xiyuetsuki.moonclouddrivedemo.mapper;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.MybatisSqlSessionFactoryBuilder;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import org.apache.ibatis.datasource.pooled.PooledDataSource;
import org.apache.ibatis.logging.stdout.StdOutImpl;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ShareMapper 集成测试 —— 手动搭建 MyBatis-Plus + H2 环境
 * <p>
 * 完全不依赖 Spring 容器，直接通过 SqlSessionFactory 构建 Mapper，
 * 验证手写 SQL {@code incrementDownloadCountAndCheckLimit} 的原子更新行为。
 */
class ShareMapperTest {

    private static SqlSessionFactory sqlSessionFactory;
    private SqlSession sqlSession;
    private ShareMapper shareMapper;

    @BeforeAll
    static void initFactory() {
        PooledDataSource ds = new PooledDataSource(
                "org.h2.Driver",
                "jdbc:h2:mem:share_test;MODE=MySQL;DATABASE_TO_LOWER=TRUE",
                "sa", "");

        try (Connection conn = ds.getConnection()) {
            conn.createStatement().execute("""
                    CREATE TABLE tb_share (
                        id              BIGINT AUTO_INCREMENT PRIMARY KEY,
                        share_code      VARCHAR(255)  NOT NULL,
                        file_id         BIGINT        NOT NULL,
                        user_id         BIGINT        NOT NULL,
                        password        VARCHAR(255)  DEFAULT NULL,
                        expire_time     TIMESTAMP     NOT NULL,
                        max_downloads   INT           DEFAULT -1,
                        download_count  INT           DEFAULT 0,
                        status          INT           DEFAULT 1,
                        create_time     TIMESTAMP     DEFAULT CURRENT_TIMESTAMP
                    )""");
        } catch (Exception e) {
            throw new RuntimeException("H2 schema init failed", e);
        }

        Environment env = new Environment("test",
                new JdbcTransactionFactory(), ds);

        MybatisConfiguration config = new MybatisConfiguration(env);
        config.setMapUnderscoreToCamelCase(true);
        config.setLogImpl(StdOutImpl.class);
        config.addMapper(ShareMapper.class);

        sqlSessionFactory = new MybatisSqlSessionFactoryBuilder().build(config);
    }

    @BeforeEach
    void setUp() {
        sqlSession = sqlSessionFactory.openSession();
        shareMapper = sqlSession.getMapper(ShareMapper.class);
        shareMapper.delete(new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<>());
        sqlSession.commit();
    }

    @AfterEach
    void tearDown() {
        sqlSession.close();
    }

    // ==================== 原子下载计数测试 ====================

    @Test
    void incrementDownloadCountAndCheckLimit_shouldIncrementAndKeepStatus() {
        insertShare(1L, "code1", 1, 0, 10);

        int rows = shareMapper.incrementDownloadCountAndCheckLimit(1L);

        assertThat(rows).isEqualTo(1);
        Share updated = shareMapper.selectById(1L);
        assertThat(updated.getDownloadCount()).isEqualTo(1);
        assertThat(updated.getStatus()).isEqualTo(1);
    }

    @Test
    void incrementDownloadCountAndCheckLimit_shouldExpireWhenReachingMax() {
        insertShare(2L, "code2", 1, 4, 5);

        int rows = shareMapper.incrementDownloadCountAndCheckLimit(2L);

        assertThat(rows).isEqualTo(1);
        Share updated = shareMapper.selectById(2L);
        assertThat(updated.getDownloadCount()).isEqualTo(5);
        assertThat(updated.getStatus()).isEqualTo(0);
    }

    @Test
    void incrementDownloadCountAndCheckLimit_shouldNotUpdateWhenAlreadyExpired() {
        insertShare(3L, "code3", 0, 0, 10);

        int rows = shareMapper.incrementDownloadCountAndCheckLimit(3L);

        assertThat(rows).isEqualTo(0);
        Share unchanged = shareMapper.selectById(3L);
        assertThat(unchanged.getDownloadCount()).isEqualTo(0);
    }

    @Test
    void incrementDownloadCountAndCheckLimit_shouldWorkWithoutMaxLimit() {
        insertShare(4L, "code4", 1, 100, -1);

        int rows = shareMapper.incrementDownloadCountAndCheckLimit(4L);

        assertThat(rows).isEqualTo(1);
        Share updated = shareMapper.selectById(4L);
        assertThat(updated.getDownloadCount()).isEqualTo(101);
        assertThat(updated.getStatus()).isEqualTo(1);
    }

    // ==================== 辅助方法 ====================

    private void insertShare(Long id, String code, int status, int downloadCount, int maxDownloads) {
        Share share = new Share();
        share.setId(id);
        share.setShareCode(code);
        share.setFileId(100L);
        share.setUserId(1L);
        share.setPassword("123456");
        share.setExpireTime(LocalDateTime.now().plusDays(7));
        share.setMaxDownloads(maxDownloads);
        share.setDownloadCount(downloadCount);
        share.setStatus(status);
        share.setCreateTime(LocalDateTime.now());
        shareMapper.insert(share);
        sqlSession.commit();
    }
}