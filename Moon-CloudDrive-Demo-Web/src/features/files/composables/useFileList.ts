/**
 * 文件列表管理 Composable
 * 负责文件列表的加载、排序、搜索和分页逻辑
 */
import { ref, watch, onMounted, onUnmounted } from 'vue'
import { getFileList, getFolderPath } from '../api/files'
import type { FileInfo } from '../types/files'
import { RefreshFileListEvent } from '../events/files'

export function useFileList() {
  /** 当前文件夹ID */
  const currentParentId = ref<number | null>(null)
  /** 面包屑路径 */
  const breadcrumbs = ref<{ id: number | null; name: string }[]>([{ id: null, name: '根目录' }])
  /** 文件列表数据 */
  const fileList = ref<FileInfo[]>([])
  /** 列表加载状态 */
  const fileListLoading = ref(false)
  /** 当前页码 */
  const currentPage = ref(1)
  /** 每页条数 */
  const pageSize = ref(20)
  /** 总条数 */
  const totalFiles = ref(0)
  /** 排序字段 */
  const sortBy = ref('uploadTime')
  /** 排序方向 */
  const sortOrder = ref<'asc' | 'desc'>('desc')
  /** 搜索关键词 */
  const searchKeyword = ref('')

  /** 加载文件列表 */
  async function loadFileList() {
    fileListLoading.value = true
    try {
      const res = await getFileList({
        parentId: currentParentId.value,
        page: currentPage.value,
        size: pageSize.value,
        sortBy: sortBy.value,
        sortOrder: sortOrder.value,
        keyword: searchKeyword.value || undefined,
      })
      const pageResult = res.data.data
      fileList.value = pageResult.records
      totalFiles.value = pageResult.total
    } catch {
      // 错误已在拦截器中统一处理
    } finally {
      fileListLoading.value = false
    }
  }

  /** 重置到第一页并刷新 */
  function refreshFileList() {
    currentPage.value = 1
    loadFileList()
  }

  /** 加载面包屑导航 */
  async function loadBreadcrumbs() {
    if (currentParentId.value == null) {
      breadcrumbs.value = [{ id: null, name: '根目录' }]
      return
    }
    try {
      const res = await getFolderPath(currentParentId.value)
      const path = res.data.data || []
      breadcrumbs.value = [
        { id: null, name: '根目录' },
        ...path.map((f: FileInfo) => ({ id: f.id, name: f.originalFilename })),
      ]
    } catch {
      breadcrumbs.value = [{ id: null, name: '根目录' }]
    }
  }

  /** 导航到指定文件夹 */
  function navigateTo(folderId: number | null) {
    currentParentId.value = folderId
  }

  /** 点击文件夹进入 */
  function handleFolderClick(folder: FileInfo) {
    currentParentId.value = folder.id
  }

  /** 排序变化回调 */
  function handleSortChange({ prop, order }: { prop: string; order: string | null }) {
    if (order) {
      sortBy.value = prop
      sortOrder.value = order === 'ascending' ? 'asc' : 'desc'
    } else {
      sortBy.value = 'uploadTime'
      sortOrder.value = 'desc'
    }
    currentPage.value = 1
    loadFileList()
  }

  /** 每页条数变化 */
  function handleSizeChange(size: number) {
    pageSize.value = size
    currentPage.value = 1
    loadFileList()
  }

  /** 页码变化 */
  function handlePageChange(page: number) {
    currentPage.value = page
    loadFileList()
  }

  /** 搜索 */
  function handleSearch(keyword?: string) {
    searchKeyword.value = keyword || ''
    currentPage.value = 1
    loadFileList()
  }

  /** 监听文件夹变化，自动加载数据 */
  watch(currentParentId, async () => {
    currentPage.value = 1
    searchKeyword.value = ''
    await Promise.all([loadBreadcrumbs(), loadFileList()])
  }, { immediate: true })

  /** 监听上传完成事件，自动刷新文件列表 */
  function onUploadDone() {
    refreshFileList()
  }
  onMounted(() => {
    window.addEventListener(RefreshFileListEvent, onUploadDone)
  })
  onUnmounted(() => {
    window.removeEventListener(RefreshFileListEvent, onUploadDone)
  })

  return {
    currentParentId,
    breadcrumbs,
    fileList,
    fileListLoading,
    currentPage,
    pageSize,
    totalFiles,
    sortBy,
    sortOrder,
    searchKeyword,
    loadFileList,
    refreshFileList,
    loadBreadcrumbs,
    navigateTo,
    handleFolderClick,
    handleSortChange,
    handleSizeChange,
    handlePageChange,
    handleSearch,
  }
}