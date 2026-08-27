<script setup lang="ts">
import { ref, onMounted } from 'vue'
import { useRouter } from 'vue-router'
import { useUserStore } from '@/stores/user'
import { getMyShares, cancelShare, createShare } from '@/api/share'
import type { ShareInfo } from '@/types/api'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'

const router = useRouter()
const userStore = useUserStore()

const shares = ref<ShareInfo[]>([])
const loading = ref(false)
const createDialogVisible = ref(false)
const creating = ref(false)

const createForm = ref({
  fileId: null as number | null,
  password: '',
  expireHours: 168,
  maxDownloads: -1,
})

const columns = [
  { prop: 'shareCode', label: '分享码', minWidth: 120 },
  { prop: 'fileId', label: '文件ID', width: 100 },
  { prop: 'downloadCount', label: '下载次数', width: 100 },
  { prop: 'maxDownloads', label: '最大下载次数', width: 120 },
  { prop: 'expireTime', label: '过期时间', minWidth: 160 },
  { prop: 'createTime', label: '创建时间', minWidth: 160 },
  { prop: 'status', label: '状态', width: 80 },
]

function formatTime(time: string) {
  if (!time) return '-'
  return time.replace('T', ' ')
}

function getStatusText(status: number) {
  return status === 1 ? '有效' : '已取消'
}

function getStatusType(status: number) {
  return status === 1 ? 'success' : 'info'
}

function copyShareLink(shareCode: string) {
  const link = `${window.location.origin}/#/s/${shareCode}`
  navigator.clipboard.writeText(link).then(() => {
    ElMessage.success('分享链接已复制到剪贴板')
  })
}

async function handleCancelShare(share: ShareInfo) {
  try {
    await ElMessageBox.confirm(
      `确定要取消分享码为 ${share.shareCode} 的分享链接吗？`,
      '确认取消',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning',
      },
    )
    await cancelShare(share.shareCode)
    ElMessage.success('分享已取消')
    fetchShares()
  } catch {
    // 取消操作
  }
}

async function handleCreateShare() {
  if (!createForm.value.fileId) {
    ElMessage.warning('请输入文件ID')
    return
  }
  creating.value = true
  try {
    await createShare({
      fileId: createForm.value.fileId,
      password: createForm.value.password || undefined,
      expireHours: createForm.value.expireHours,
      maxDownloads: createForm.value.maxDownloads <= 0 ? undefined : createForm.value.maxDownloads,
    })
    ElMessage.success('分享链接创建成功')
    createDialogVisible.value = false
    createForm.value = { fileId: null, password: '', expireHours: 168, maxDownloads: -1 }
    fetchShares()
  } catch {
    // 错误已在拦截器中处理
  } finally {
    creating.value = false
  }
}

async function fetchShares() {
  loading.value = true
  try {
    const res = await getMyShares()
    shares.value = res.data.data
  } catch {
    // 错误已在拦截器中处理
  } finally {
    loading.value = false
  }
}

function handleLogout() {
  userStore.logout()
  router.push('/login')
}

onMounted(() => {
  fetchShares()
})
</script>

<template>
  <div class="share-container">
    <header class="share-header">
      <div class="header-left">
        <h1>Moon 云盘</h1>
      </div>
      <div class="header-right">
        <el-button text @click="router.push('/')">文件上传</el-button>
        <el-dropdown>
          <span class="user-info">
            <el-avatar :size="32" icon="UserFilled" />
            <span class="username">{{ userStore.username }}</span>
          </span>
          <template #dropdown>
            <el-dropdown-menu>
              <el-dropdown-item>
                <span>{{ userStore.email }}</span>
              </el-dropdown-item>
              <el-dropdown-item divided @click="handleLogout">
                <span style="color: #f56c6c">退出登录</span>
              </el-dropdown-item>
            </el-dropdown-menu>
          </template>
        </el-dropdown>
      </div>
    </header>

    <main class="share-main">
      <div class="share-toolbar">
        <h2>我的分享</h2>
        <el-button type="primary" :icon="Plus" @click="createDialogVisible = true">
          创建分享
        </el-button>
      </div>

      <el-card>
        <el-table
          :data="shares"
          :loading="loading"
          stripe
          empty-text="暂无分享记录"
          style="width: 100%"
        >
          <el-table-column
            v-for="col in columns"
            :key="col.prop"
            :prop="col.prop"
            :label="col.label"
            :min-width="col.minWidth"
            :width="col.width"
          >
            <template #default="{ row }">
              <template v-if="col.prop === 'shareCode'">
                <el-link type="primary" :underline="false" @click="copyShareLink(row.shareCode)">
                  {{ row.shareCode }}
                </el-link>
              </template>
              <template v-else-if="col.prop === 'maxDownloads'">
                {{ row.maxDownloads === -1 ? '不限' : row.maxDownloads }}
              </template>
              <template v-else-if="col.prop === 'expireTime' || col.prop === 'createTime'">
                {{ formatTime(row[col.prop]) }}
              </template>
              <template v-else-if="col.prop === 'status'">
                <el-tag :type="getStatusType(row.status)" size="small">
                  {{ getStatusText(row.status) }}
                </el-tag>
              </template>
              <template v-else>
                {{ row[col.prop] }}
              </template>
            </template>
          </el-table-column>

          <el-table-column label="操作" width="160" fixed="right">
            <template #default="{ row }">
              <el-button
                text
                type="primary"
                size="small"
                @click="copyShareLink(row.shareCode)"
              >
                复制链接
              </el-button>
              <el-button
                v-if="row.status === 1"
                text
                type="danger"
                size="small"
                @click="handleCancelShare(row)"
              >
                取消分享
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </el-card>
    </main>

    <el-dialog
      v-model="createDialogVisible"
      title="创建分享链接"
      width="480px"
      :close-on-click-modal="false"
    >
      <el-form :model="createForm" label-width="100px">
        <el-form-item label="文件ID" required>
          <el-input v-model="createForm.fileId" placeholder="请输入文件ID" type="number" />
        </el-form-item>
        <el-form-item label="提取码">
          <el-input
            v-model="createForm.password"
            placeholder="留空则无需提取码"
            maxlength="20"
          />
        </el-form-item>
        <el-form-item label="有效期(小时)">
          <el-input-number
            v-model="createForm.expireHours"
            :min="1"
            :max="720"
            placeholder="默认168小时(7天)"
          />
        </el-form-item>
        <el-form-item label="最大下载次数">
          <el-input-number
            v-model="createForm.maxDownloads"
            :min="-1"
            :max="9999"
            placeholder="-1表示不限"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="createDialogVisible = false">取消</el-button>
        <el-button type="primary" :loading="creating" @click="handleCreateShare">
          创建
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.share-container {
  min-height: 100vh;
  background: #f5f7fa;
}

.share-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 24px;
  height: 60px;
  background: #fff;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.08);
}

.header-left h1 {
  font-size: 20px;
  color: #303133;
  margin: 0;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 8px;
  cursor: pointer;
}

.username {
  font-size: 14px;
  color: #303133;
}

.share-main {
  padding: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

.share-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 16px;
}

.share-toolbar h2 {
  font-size: 18px;
  color: #303133;
  margin: 0;
}
</style>