package com.xiyuetsuki.moonclouddrivedemo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.CreateShareRequest;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.Share;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.mapper.ShareMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.impl.ShareServiceImpl;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ShareServiceImpl 单元测试 —— 验证分享创建、下载计数原子更新、失效逻辑
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ShareServiceImplTest {

    @Mock
    private ShareMapper shareMapper;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private OssUtil ossUtil;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ShareExpireManager shareExpireManager;

    @InjectMocks
    private ShareServiceImpl shareService;

    private MockedStatic<StpUtil> stpUtilMock;

    @BeforeEach
    void setUp() {
        stpUtilMock = org.mockito.Mockito.mockStatic(StpUtil.class);
        stpUtilMock.when(StpUtil::getLoginIdAsLong).thenReturn(1L);
    }

    @AfterEach
    void tearDown() {
        stpUtilMock.close();
    }

    // ==================== 创建分享测试 ====================

    @Test
    void createShare_shouldInsertShare_whenFileOwnedByUser() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(100L);

        File file = buildFile(100L, 1L, "test.pdf");
        when(fileMapper.selectById(100L)).thenReturn(file);

        shareService.createShare(req);

        ArgumentCaptor<Share> captor = ArgumentCaptor.forClass(Share.class);
        verify(shareMapper).insert(captor.capture());
        Share saved = captor.getValue();
        assertThat(saved.getFileId()).isEqualTo(100L);
        assertThat(saved.getUserId()).isEqualTo(1L);
        assertThat(saved.getStatus()).isEqualTo(1);
        assertThat(saved.getDownloadCount()).isEqualTo(0);
        assertThat(saved.getShareCode()).isNotNull();
        verify(shareExpireManager).schedule(anyString(), any());
    }

    @Test
    void createShare_shouldThrow_whenFileNotBelongsToUser() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(100L);

        File file = buildFile(100L, 999L, "secret.pdf");
        when(fileMapper.selectById(100L)).thenReturn(file);

        assertThatThrownBy(() -> shareService.createShare(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("无权分享");
        verify(shareMapper, never()).insert(any(Share.class));
    }

    @Test
    void createShare_shouldThrow_whenFileNotFound() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(404L);
        when(fileMapper.selectById(404L)).thenReturn(null);

        assertThatThrownBy(() -> shareService.createShare(req))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不存在");
    }

    @Test
    void createShare_shouldEncodePassword_whenPasswordProvided() {
        CreateShareRequest req = new CreateShareRequest();
        req.setFileId(100L);
        req.setPassword("1234");

        File file = buildFile(100L, 1L, "test.pdf");
        when(fileMapper.selectById(100L)).thenReturn(file);
        when(passwordEncoder.encode("1234")).thenReturn("encoded_1234");

        shareService.createShare(req);

        ArgumentCaptor<Share> captor = ArgumentCaptor.forClass(Share.class);
        verify(shareMapper).insert(captor.capture());
        assertThat(captor.getValue().getPassword()).isEqualTo("encoded_1234");
    }

    // ==================== 下载计数测试（并发安全验证） ====================

    @Test
    void getDownloadUrl_shouldUseAtomicIncrement() {
        Share share = buildShare(10L, "code123", 100L, 0, 10, 1);
        File file = buildFile(100L, 1L, "test.pdf");

        when(shareMapper.selectByShareCode("code123")).thenReturn(share);
        when(fileMapper.selectById(100L)).thenReturn(file);
        when(ossUtil.generatePresignedUrl(anyString(), anyString())).thenReturn("https://oss/presigned");
        when(shareMapper.incrementDownloadCountAndCheckLimit(10L)).thenReturn(1);

        String url = shareService.getDownloadUrl("code123", null);

        assertThat(url).isEqualTo("https://oss/presigned");
        verify(shareMapper).incrementDownloadCountAndCheckLimit(10L);
    }

    @Test
    void getDownloadUrl_shouldThrowWhenDownloadLimitReached() {
        Share share = buildShare(10L, "code123", 100L, 5, 5, 1);
        File file = buildFile(100L, 1L, "test.pdf");

        when(shareMapper.selectByShareCode("code123")).thenReturn(share);
        when(fileMapper.selectById(100L)).thenReturn(file);
        when(ossUtil.generatePresignedUrl(anyString(), anyString())).thenReturn("https://oss/presigned");
        when(shareMapper.incrementDownloadCountAndCheckLimit(10L)).thenReturn(0);
        Share latestAfter = buildShare(10L, "code123", 100L, 5, 5, 0);
        when(shareMapper.selectById(10L)).thenReturn(latestAfter);

        assertThatThrownBy(() -> shareService.getDownloadUrl("code123", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已失效");
    }

    // ==================== 取消分享测试 ====================

    @Test
    void cancelShare_shouldSetStatusToZero() {
        Share share = buildShare(10L, "code123", 100L, 0, -1, 1);
        when(shareMapper.selectByShareCode("code123")).thenReturn(share);

        shareService.cancelShare("code123");

        assertThat(share.getStatus()).isEqualTo(0);
        verify(shareMapper).updateById(share);
    }

    // ==================== 辅助构建方法 ====================

    private File buildFile(Long id, Long userId, String filename) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setOriginalFilename(filename);
        file.setStoredFilename("oss/" + filename);
        file.setFileSize(1024L);
        return file;
    }

    private Share buildShare(Long id, String code, Long fileId,
                             int downloadCount, int maxDownloads, int status) {
        Share share = new Share();
        share.setId(id);
        share.setShareCode(code);
        share.setFileId(fileId);
        share.setUserId(1L);
        share.setDownloadCount(downloadCount);
        share.setMaxDownloads(maxDownloads);
        share.setStatus(status);
        share.setExpireTime(LocalDateTime.now().plusDays(7));
        share.setCreateTime(LocalDateTime.now());
        return share;
    }
}