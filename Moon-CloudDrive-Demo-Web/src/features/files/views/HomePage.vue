<!--
  HomePage.vue - 文件管理主页面
  组装面包屑导航、工具栏、文件表格和所有对话框，
  业务逻辑由各个 Composable 提供
-->
<template>
  <div class="home-container">
    <main class="home-main">
      <!-- 文件/文件夹展示区域（面包屑 + 工具栏 + 列表） -->
      <el-card class="content-card" shadow="never">
        <!-- 面包屑导航：显示当前文件夹路径，点击可返回上级 -->
        <div class="breadcrumb-bar">
          <BreadcrumbNav
            :items="breadcrumbs"
            @navigate="navigateTo"
          />
        </div>

        <!-- 工具栏：新建文件夹、上传、批量操作、搜索 -->
        <div class="toolbar">
          <FileToolbar
          :has-selection="selectedFileIds.size > 0"
          @new-folder="openNewFolderDialog"
          @upload="handleUpload"
          @batch-delete="handleBatchDelete"
          @batch-move="openBatchMoveDialog"
          @batch-rename="openBatchRenameDialog"
          @batch-download="handleBatchDownload"
          @search="handleSearch"
        />
        </div>

        <!-- 文件表格：支持排序、多选、文件夹双击进入、文件单击预览 -->
        <FileTable
          :file-list="fileList"
          :loading="fileListLoading"
          @sort-change="handleSortChange"
          @selection-change="onTableSelectionChange"
          @folder-click="handleFolderClick"
          @file-click="handlePreview"
          @download="handleDownload"
          @share="openShareDialog"
          @rename="openRenameDialog"
          @move="openMoveDialog"
          @delete="handleDelete"
        />

        <!-- 分页器 -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :page-sizes="[10, 20, 50, 100]"
            :total="totalFiles"
            layout="total, sizes, prev, pager, next, jumper"
            @size-change="handleSizeChange"
            @current-change="handlePageChange"
          />
        </div>
      </el-card>

    <!-- ============ 对话框 ============ -->

    <!-- 重命名对话框 -->
    <RenameDialog
      v-model:visible="renameDialogVisible"
      v-model:model-value="renameNewName"
      :file="renamingFile"
      @submit="handleRename"
    />

    <!-- 新建文件夹对话框 -->
    <NewFolderDialog
      v-model:visible="newFolderDialogVisible"
      v-model:name="newFolderName"
      @submit="handleCreateFolder"
    />

    <!-- 单个文件移动对话框 -->
    <MoveDialog
      v-model:visible="moveDialogVisible"
      title="移动到"
      :load-nodes="loadMoveFolderNodes"
      :selected-parent-label="moveTargetParentId != null ? '已选择文件夹' : ''"
      @node-click="handleMoveTreeNodeClick"
      @submit="handleSubmitMove"
    />

    <!-- 批量移动对话框 -->
    <MoveDialog
      v-model:visible="batchMoveDialogVisible"
      title="批量移动到"
      :load-nodes="loadBatchMoveFolderNodes"
      :selected-parent-label="batchMoveTargetParentId != null ? '已选择文件夹' : ''"
      @node-click="handleBatchMoveTreeNodeClick"
      @submit="handleSubmitBatchMove"
    />

    <!-- 批量重命名对话框 -->
    <BatchRenameDialog
      v-model:visible="batchRenameDialogVisible"
      @submit="handleSubmitBatchRename"
    />

    <!-- 打包下载进度对话框 -->
    <PackDownloadDialog
      v-model:visible="packDialogVisible"
      :status="packStatus"
      :percent="packPercent"
      :message="packMessage"
    />

    <!-- 文件预览对话框 -->
    <PreviewDialog
      v-model:visible="previewDialogVisible"
      :file="previewFile"
      :loading="previewLoading"
      :info="previewInfo"
      :text-content="previewTextContent"
      :html-content="previewTextHtml"
      :error-msg="previewErrorMsg"
      :pdf-info="pdfPreviewInfo"
      :pdf-page-urls="pdfPageUrls"
      :pdf-error="previewErrorMsg"
      @copy="copyTextContent"
    />

    <!-- 分享链接对话框 -->
    <ShareDialog
      v-model:visible="shareDialogVisible"
      :file-name="sharingFileName"
      :share-link="shareLink"
      @submit="handleShare"
    />

    <!-- 上传任务管理器（右下角浮层） -->
    <UploadTaskPanel />

    <!-- 悬浮上传按钮 -->
    <div class="upload-fab" @click="handleUpload">
      <el-icon :size="24"><Upload /></el-icon>
    </div>
  </main>
  </div>
</template>

<script setup lang="ts">
import { Upload } from '@element-plus/icons-vue'
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useFileList } from '@/features/files/composables/useFileList'
import { useFileActions } from '@/features/files/composables/useFileActions'
import { useFolderOps } from '@/features/files/composables/useFolderOps'
import { useBatchOps } from '@/features/files/composables/useBatchOps'
import { useFilePreview } from '@/features/files/composables/useFilePreview'
import { usePackDownload } from '@/features/files/composables/usePackDownload'
import { useUploadStore } from '@/features/upload/stores/upload'
import { createShare } from '@/features/share/api/share'

import BreadcrumbNav from '@/features/files/components/BreadcrumbNav.vue'
import FileToolbar from '@/features/files/components/FileToolbar.vue'
import FileTable from '@/features/files/components/FileTable.vue'
import RenameDialog from '@/features/files/components/RenameDialog.vue'
import NewFolderDialog from '@/features/files/components/NewFolderDialog.vue'
import MoveDialog from '@/features/files/components/MoveDialog.vue'
import BatchRenameDialog from '@/features/files/components/BatchRenameDialog.vue'
import PackDownloadDialog from '@/features/files/components/PackDownloadDialog.vue'
import PreviewDialog from '@/features/files/components/PreviewDialog.vue'
import UploadTaskPanel from '@/features/upload/components/UploadTaskPanel.vue'
import ShareDialog from '@/features/files/components/ShareDialog.vue'

// ── 文件列表管理 ──
const {
  currentParentId,
  breadcrumbs,
  fileList,
  fileListLoading,
  currentPage,
  pageSize,
  totalFiles,
  refreshFileList,
  navigateTo,
  handleFolderClick,
  handleSortChange,
  handleSizeChange,
  handlePageChange,
  handleSearch,
} = useFileList()

// ── 单个文件操作 ──
const onFileActionSuccess = () => refreshFileList()

const {
  renameDialogVisible,
  renamingFile,
  renameNewName,
  handleDownload,
  handleDelete,
  openRenameDialog,
  handleRename,
} = useFileActions(onFileActionSuccess)

// ── 文件夹操作 ──
const {
  newFolderDialogVisible,
  newFolderName,
  moveDialogVisible,
  moveTargetParentId,
  openNewFolderDialog,
  handleCreateFolder,
  openMoveDialog,
  loadMoveFolderNodes,
  handleMoveTreeNodeClick,
  handleSubmitMove,
} = useFolderOps(() => currentParentId.value, onFileActionSuccess)

// ── 批量操作 ──
const {
  selectedFileIds,
  batchMoveDialogVisible,
  batchMoveTargetParentId,
  batchRenameDialogVisible,
  handleBatchDelete,
  openBatchMoveDialog,
  loadBatchMoveFolderNodes,
  handleBatchMoveTreeNodeClick,
  handleSubmitBatchMove,
  openBatchRenameDialog,
  handleSubmitBatchRename,
  clearSelection,
} = useBatchOps(() => fileList.value, onFileActionSuccess)

// ── 文件预览 ──
const {
  previewDialogVisible,
  previewLoading,
  previewInfo,
  previewTextContent,
  previewTextHtml,
  previewErrorMsg,
  previewFile,
  pdfPreviewInfo,
  pdfPageUrls,
  handlePreview,
  copyTextContent,
} = useFilePreview()

// ── 打包下载 ──
const {
  packDialogVisible,
  packStatus,
  packPercent,
  packMessage,
  handleBatchDownload,
} = usePackDownload(() => selectedFileIds.value, () => clearSelection())

// ── 分享链接 ──
const shareDialogVisible = ref(false)
const sharingFileId = ref(0)
const sharingFileName = ref('')
const shareLink = ref('')

/** 打开分享对话框 */
function openShareDialog(file: { id: number; originalFilename: string }) {
  sharingFileId.value = file.id
  sharingFileName.value = file.originalFilename || ''
  shareLink.value = ''
  shareDialogVisible.value = true
}

/** 生成分享链接 */
async function handleShare(payload: { days: number; password: string; maxDownloads: number }) {
  try {
    const params: any = {
      fileId: sharingFileId.value,
      expireHours: payload.days * 24,
    }
    if (payload.password) {
      params.password = payload.password
    }
    if (payload.maxDownloads > 0) {
      params.maxDownloads = payload.maxDownloads
    }
    const res = await createShare(params)
    const data = res.data.data
    // hash 模式路由格式：/#/share/{shareCode}
    shareLink.value = `${window.location.origin}/#/share/${data.shareCode}`
    ElMessage.success('分享链接已生成')
  } catch (e: any) {
    ElMessage.error(e?.response?.data?.msg || '生成分享链接失败')
  }
}

/** 处理表格的选择变化事件 */
function onTableSelectionChange(selection: any[]) {
  // 清空后根据 selection 重建
  const newSet = new Set<number>()
  selection.forEach((row) => {
    if (row.isFolder !== 1) {
      newSet.add(row.id)
    }
  })
  selectedFileIds.value = newSet
}

// ── 上传任务管理 ──
const uploadStore = useUploadStore()

/** 上传按钮：触发原生文件选择并加入上传队列 */
function handleUpload() {
  const input = document.createElement('input')
  input.type = 'file'
  input.multiple = true
  input.click()
  input.addEventListener('change', () => {
    const files = input.files
    if (!files) return
    for (let i = 0; i < files.length; i++) {
      uploadStore.addTask(files[i], currentParentId.value)
    }
  })
}
</script>

<style scoped>
/* ==================== 页面整体容器 ==================== */
.home-container {
  min-height: calc(100vh - 60px);
  background: #f5f7fa;
}

.home-main {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

/* ==================== 文件/文件夹展示卡片 ==================== */
.content-card {
  border-radius: 8px;
}

/* 面包屑 */
.breadcrumb-bar {
  margin-bottom: 14px;
  padding-bottom: 12px;
  border-bottom: 1px solid #ebeef5;
}

/* 工具栏 */
.toolbar {
  margin-bottom: 14px;
  display: flex;
  align-items: center;
  gap: 8px;
}

/* ==================== 悬浮上传按钮 ==================== */
.upload-fab {
  position: fixed;
  right: 32px;
  bottom: 32px;
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #409eff;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: center;
  cursor: pointer;
  box-shadow: 0 4px 14px rgba(64, 158, 255, 0.4);
  transition: transform 0.2s, box-shadow 0.2s;
  z-index: 50;
}

.upload-fab:hover {
  transform: scale(1.08);
  box-shadow: 0 6px 20px rgba(64, 158, 255, 0.55);
}

.upload-fab:active {
  transform: scale(0.95);
}

/* ==================== 分页器（居中） ==================== */
.pagination-bar {
  display: flex;
  justify-content: center;
  margin-top: 16px;
}
</style>