package com.xiyuetsuki.moonclouddrivedemo.service;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.FileVO;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.impl.FileServiceImpl;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import com.xiyuetsuki.moonclouddrivedemo.util.ProgressTracker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FileServiceImpl 单元测试 —— 验证重命名、软删除恢复、循环引用、文件夹创建等核心逻辑
 */
@ExtendWith(MockitoExtension.class)
class FileServiceImplTest {

    @Mock
    private AsyncUploadService asyncUploadService;
    @Mock
    private ProgressTracker progressTracker;
    @Mock
    private FileMapper fileMapper;
    @Mock
    private OssUtil ossUtil;

    @InjectMocks
    private FileServiceImpl fileService;

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

    // ==================== 重命名测试 ====================

    @Test
    void renameFile_shouldUpdateName_whenFileBelongsToUser() {
        File file = buildFile(1L, 1L, "old_name.txt", 0);
        when(fileMapper.selectByUserIdAndId(1L, 1L)).thenReturn(file);

        fileService.renameFile(1L, "new_name.txt");

        assertThat(file.getOriginalFilename()).isEqualTo("new_name.txt");
        verify(fileMapper).updateById(file);
    }

    @Test
    void renameFile_shouldThrowException_whenFileNotFound() {
        when(fileMapper.selectByUserIdAndId(1L, 999L)).thenReturn(null);

        assertThatThrownBy(() -> fileService.renameFile(999L, "new.txt"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("文件不存在");
    }

    // ==================== 软删除测试 ====================

    @Test
    void deleteFile_shouldSoftDelete_whenFileFound() {
        File file = buildFile(1L, 1L, "test.txt", 0);
        when(fileMapper.selectByUserIdAndId(1L, 1L)).thenReturn(file);

        fileService.deleteFile(1L);

        assertThat(file.getDeleted()).isEqualTo(1);
        assertThat(file.getDeleteTime()).isNotNull();
        verify(fileMapper).updateById(file);
    }

    @Test
    void deleteFile_shouldDeleteFolderAndDescendants() {
        File folder = buildFolder(10L, 1L, "docs", 1);
        File child1 = buildFile(11L, 1L, "a.txt", 0);
        File child2 = buildFile(12L, 1L, "b.txt", 0);

        when(fileMapper.selectByUserIdAndId(1L, 10L)).thenReturn(folder);
        when(fileMapper.selectAllDescendants(10L)).thenReturn(List.of(child1, child2));

        fileService.deleteFile(10L);

        assertThat(child1.getDeleted()).isEqualTo(1);
        assertThat(child2.getDeleted()).isEqualTo(1);
        verify(fileMapper).updateById(child1);
        verify(fileMapper).updateById(child2);
    }

    // ==================== 恢复测试 ====================

    @Test
    void restoreFile_shouldClearDeletedFlag() {
        File file = buildFile(1L, 1L, "deleted.txt", 0);
        file.setDeleted(1);
        when(fileMapper.selectByUserIdAndId(1L, 1L)).thenReturn(file);

        fileService.restoreFile(1L);

        assertThat(file.getDeleted()).isEqualTo(0);
        assertThat(file.getDeleteTime()).isNull();
        verify(fileMapper).updateById(file);
    }

    @Test
    void restoreFile_shouldThrow_whenFileNotInRecycleBin() {
        File file = buildFile(1L, 1L, "normal.txt", 0);
        file.setDeleted(0);
        when(fileMapper.selectByUserIdAndId(1L, 1L)).thenReturn(file);

        assertThatThrownBy(() -> fileService.restoreFile(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不在回收站");
    }

    // ==================== 文件夹创建测试 ====================

    @Test
    void createFolder_shouldInsertFolder_whenNameUnique() {
        when(fileMapper.countByNameAndParent(eq(1L), eq(null), eq("myFolder"))).thenReturn(0);

        FileVO result = fileService.createFolder("myFolder", null);

        verify(fileMapper).insert(any(File.class));
        assertThat(result.getIsFolder()).isEqualTo(1);
        assertThat(result.getOriginalFilename()).isEqualTo("myFolder");
    }

    @Test
    void createFolder_shouldThrowException_whenDuplicateName() {
        when(fileMapper.countByNameAndParent(eq(1L), eq(null), eq("existing"))).thenReturn(1);

        assertThatThrownBy(() -> fileService.createFolder("existing", null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("已存在同名");
    }

    // ==================== 移动（循环引用检测）测试 ====================

    @Test
    void moveFile_shouldThrowException_whenCyclicReference() {
        File folder = buildFolder(1L, 1L, "parent", 1);
        File descendant = buildFolder(99L, 1L, "child", 1);

        when(fileMapper.selectByUserIdAndId(1L, 1L)).thenReturn(folder);
        when(fileMapper.selectAllDescendants(1L)).thenReturn(List.of(descendant));

        assertThatThrownBy(() -> fileService.moveFile(1L, 99L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能将文件夹移动到其子文件夹");
        verify(fileMapper, never()).updateParentId(anyLong(), anyLong());
    }

    // ==================== 辅助构建方法 ====================

    private File buildFile(Long id, Long userId, String filename, int deleted) {
        File file = new File();
        file.setId(id);
        file.setUserId(userId);
        file.setOriginalFilename(filename);
        file.setStoredFilename("oss/" + filename);
        file.setFileSize(1024L);
        file.setFileHash("hash_" + id);
        file.setIsFolder(0);
        file.setDeleted(deleted);
        file.setOssUrl("https://oss/" + filename);
        return file;
    }

    private File buildFolder(Long id, Long userId, String name, int deleted) {
        File folder = buildFile(id, userId, name, deleted);
        folder.setIsFolder(1);
        folder.setFileSize(0L);
        folder.setFileHash("");
        folder.setStoredFilename("");
        folder.setOssUrl("");
        return folder;
    }
}