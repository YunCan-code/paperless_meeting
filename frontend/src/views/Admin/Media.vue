<template>
  <div class="media-page">
    <input
      ref="fileInputRef"
      type="file"
      accept="image/*,video/*"
      multiple
      class="hidden-file-input"
      @change="handleFileImport"
    />

    <div class="page-header">
      <div class="header-left">
        <el-button class="collapse-btn" link @click="toggleSidebar">
          <el-icon size="24" color="#64748b">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        <div class="title-group">
          <h1 class="page-title">媒体</h1>
          <p class="page-subtitle">集中管理会议图片、视频与展示素材，像文稿库一样清晰浏览</p>
        </div>
      </div>
    </div>

    <div class="toolbar-row">
      <div class="breadcrumb-bar">
        <button
          v-for="crumb in breadcrumbs"
          :key="crumb.key"
          class="breadcrumb-item"
          :class="{ active: crumb.active }"
          @click="goToPath(crumb.path)"
        >
          {{ crumb.title }}
        </button>
      </div>

      <div class="toolbar-center">
        <div class="type-filter">
          <button
            v-for="option in filterOptions"
            :key="option.value"
            class="filter-pill"
            :class="{ active: activeFilter === option.value }"
            @click="activeFilter = option.value"
          >
            {{ option.label }}
          </button>
        </div>
      </div>

      <div class="toolbar-right">
        <el-button class="view-button" @click="toggleViewMode">
          {{ viewMode === 'grid' ? '列表视图' : '卡片视图' }}
        </el-button>
      </div>
    </div>

    <div v-if="viewMode === 'grid'" class="library-grid">
      <el-popover
        trigger="click"
        :width="280"
        placement="bottom-start"
        :offset="6"
        :show-arrow="false"
        :visible="createMenuVisible"
        popper-class="create-popover"
        @update:visible="val => createMenuVisible = val"
      >
        <template #reference>
          <div class="library-card create-card">
            <div class="create-thumb">
              <el-icon><Plus /></el-icon>
            </div>
            <div class="card-title">新建...</div>
            <div class="card-meta">创建文件夹或导入本地图片、视频</div>
          </div>
        </template>
        <div class="create-menu">
          <div class="create-menu-item" @click="handleCreateCommand('folder')">
            <div class="create-menu-icon folder-icon">
              <el-icon :size="20"><FolderOpened /></el-icon>
            </div>
            <div class="create-menu-text">
              <div class="create-menu-title">新建文件夹</div>
              <div class="create-menu-desc">在当前目录创建空文件夹</div>
            </div>
          </div>
          <div class="create-menu-item" @click="handleCreateCommand('import')">
            <div class="create-menu-icon import-icon">
              <el-icon :size="20"><Upload /></el-icon>
            </div>
            <div class="create-menu-text">
              <div class="create-menu-title">导入媒体</div>
              <div class="create-menu-desc">从本地选择图片或视频文件</div>
            </div>
          </div>
        </div>
      </el-popover>

      <div
        v-for="item in displayedItems"
        :key="item.id"
        class="library-card"
        :class="`${item.kind}-card`"
        @click="handleItemClick(item)"
      >
        <div v-if="item.kind === 'folder'" class="folder-thumb">
          <div class="folder-flap"></div>
          <div class="folder-body">
            <div class="folder-badge">{{ getFolderCount(item) }} 项</div>
          </div>
        </div>

        <div v-else class="media-thumb" :class="item.kind">
          <img
            v-if="item.kind === 'image' && item.previewUrl"
            :src="item.previewUrl"
            :alt="item.title"
            class="thumb-image"
          />
          <div v-else class="thumb-overlay">
            <el-icon v-if="item.kind === 'image'" class="thumb-icon"><PictureFilled /></el-icon>
            <el-icon v-else class="thumb-icon"><VideoPlay /></el-icon>
          </div>
          <div class="thumb-badge">{{ kindLabel(item.kind) }}</div>
          <div class="thumb-footer">{{ item.kind === 'video' ? item.size : item.extension }}</div>
        </div>

        <div class="card-info">
          <div class="card-title-row">
            <div class="card-title clickable-name" :title="item.title" @click.stop="openDrawer(item)">{{ item.title }}</div>
            <el-icon v-if="item.kind === 'folder'" class="more-icon"><ArrowRight /></el-icon>
          </div>
          <div class="card-meta-row">
            <span class="meta-item">
              <el-icon><Calendar /></el-icon>
              {{ formatDate(item.updatedAt) }}
            </span>
            <span class="meta-item">
              {{ item.kind === 'folder' ? `${getFolderCount(item)} 个内容` : item.size }}
            </span>
          </div>
        </div>
      </div>

      <div v-if="displayedItems.length === 0" class="empty-state">
        <div class="empty-title">当前目录暂无内容</div>
        <div class="empty-desc">点击左侧“新建...”即可创建文件夹或导入本地媒体</div>
      </div>
    </div>

    <div v-else class="library-list">
      <el-popover
        trigger="click"
        :width="280"
        placement="bottom-start"
        :offset="6"
        :show-arrow="false"
        :visible="createMenuVisible"
        popper-class="create-popover"
        @update:visible="val => createMenuVisible = val"
      >
        <template #reference>
          <div class="list-row create-row">
            <div class="list-main">
              <div class="list-icon create-icon">
                <el-icon><Plus /></el-icon>
              </div>
              <div class="list-content">
                <div class="list-title">新建...</div>
                <div class="list-subtitle">创建文件夹或导入本地图片、视频</div>
              </div>
            </div>
          </div>
        </template>
        <div class="create-menu">
          <div class="create-menu-item" @click="handleCreateCommand('folder')">
            <div class="create-menu-icon folder-icon">
              <el-icon :size="20"><FolderOpened /></el-icon>
            </div>
            <div class="create-menu-text">
              <div class="create-menu-title">新建文件夹</div>
              <div class="create-menu-desc">在当前目录创建空文件夹</div>
            </div>
          </div>
          <div class="create-menu-item" @click="handleCreateCommand('import')">
            <div class="create-menu-icon import-icon">
              <el-icon :size="20"><Upload /></el-icon>
            </div>
            <div class="create-menu-text">
              <div class="create-menu-title">导入媒体</div>
              <div class="create-menu-desc">从本地选择图片或视频文件</div>
            </div>
          </div>
        </div>
      </el-popover>

      <div
        v-for="item in displayedItems"
        :key="item.id"
        class="list-row item-row"
        @click="handleItemClick(item)"
      >
        <div class="list-main">
          <div class="list-icon" :class="item.kind">
            <div v-if="item.kind === 'folder'" class="mini-folder"></div>
            <img
              v-else-if="item.kind === 'image' && item.previewUrl"
              :src="item.previewUrl"
              :alt="item.title"
              class="mini-image"
            />
            <el-icon v-else-if="item.kind === 'image'"><PictureFilled /></el-icon>
            <el-icon v-else><VideoPlay /></el-icon>
          </div>

          <div class="list-content">
            <div class="list-title clickable-name" @click.stop="openDrawer(item)">{{ item.title }}</div>
            <div class="list-subtitle">
              {{ item.kind === 'folder' ? `${getFolderCount(item)} 个内容` : `${kindLabel(item.kind)} · ${item.extension}` }}
            </div>
          </div>
        </div>

        <div class="list-side">
          <div class="list-date">{{ formatDate(item.updatedAt) }}</div>
          <div class="list-extra">{{ item.kind === 'folder' ? '文件夹' : item.size }}</div>
          <el-icon v-if="item.kind === 'folder'" class="list-arrow"><ArrowRight /></el-icon>
        </div>
      </div>

      <div v-if="displayedItems.length === 0" class="empty-list-state">
        当前目录暂无内容，点击上方“新建...”开始添加
      </div>
    </div>

    <!-- 属性抽屉 -->
    <el-drawer
      v-model="drawerVisible"
      direction="rtl"
      size="380px"
      :show-close="false"
      :append-to-body="true"
      class="prop-drawer"
    >
      <template #header>
        <div class="drawer-header">
          <span class="drawer-header-title">属性</span>
          <el-button class="drawer-close-btn" link @click="drawerVisible = false">
            <el-icon :size="18"><Close /></el-icon>
          </el-button>
        </div>
      </template>

      <div v-if="selectedItem" class="drawer-body">
        <!-- 名称区 -->
        <div class="prop-section">
          <div class="prop-label">名称</div>
          <div v-if="!isEditingName" class="prop-name-row">
            <span class="prop-name-text">{{ selectedItem.title }}</span>
            <el-button class="prop-edit-btn" link @click="startRename">
              <el-icon :size="16"><Edit /></el-icon>
            </el-button>
          </div>
          <div v-else class="prop-name-row editing">
            <el-input
              ref="renameInputRef"
              v-model="editName"
              size="default"
              @keyup.enter="saveRename"
              @blur="saveRename"
            />
          </div>
        </div>

        <!-- 信息区 -->
        <div class="prop-section">
          <div class="prop-label">信息</div>
          <div class="prop-info-grid">
            <div class="prop-info-item">
              <span class="prop-info-key">类型</span>
              <span class="prop-info-val">{{ kindLabel(selectedItem.kind) }}</span>
            </div>
            <div v-if="selectedItem.extension" class="prop-info-item">
              <span class="prop-info-key">格式</span>
              <span class="prop-info-val">{{ selectedItem.extension }}</span>
            </div>
            <div v-if="selectedItem.size" class="prop-info-item">
              <span class="prop-info-key">大小</span>
              <span class="prop-info-val">{{ selectedItem.size }}</span>
            </div>
            <div v-if="selectedItem.updatedAt" class="prop-info-item">
              <span class="prop-info-key">修改日期</span>
              <span class="prop-info-val">{{ formatDate(selectedItem.updatedAt) }}</span>
            </div>
            <div v-if="selectedItem.kind === 'folder'" class="prop-info-item">
              <span class="prop-info-key">包含项目</span>
              <span class="prop-info-val">{{ getFolderCount(selectedItem) }} 个</span>
            </div>
          </div>
        </div>

        <!-- 属性区 -->
        <div class="prop-section">
          <div class="prop-label">属性</div>
          <div class="prop-switch-row">
            <div class="prop-switch-info">
              <el-icon :size="16"><Cellphone /></el-icon>
              <span>安卓端可见</span>
            </div>
            <el-switch v-model="selectedItem.visibleOnAndroid" @change="handleVisibilityChange" />
          </div>
        </div>

        <!-- 操作区 -->
        <div class="prop-section prop-actions">
          <el-button class="prop-action-btn" @click="openMoveDialog">
            <el-icon><Rank /></el-icon>
            移动到...
          </el-button>
          <el-button class="prop-action-btn danger" @click="handleDelete">
            <el-icon><Delete /></el-icon>
            删除
          </el-button>
        </div>
      </div>
    </el-drawer>

    <!-- 移动目标选择 Dialog -->
    <el-dialog
      v-model="moveDialogVisible"
      title="移动到..."
      width="500px"
      append-to-body
      align-center
      class="move-dialog"
    >
      <div class="move-tree-wrap">
        <el-tree
          ref="moveTreeRef"
          :data="folderTreeData"
          :props="{ label: 'title', children: 'children' }"
          node-key="id"
          highlight-current
          default-expand-all
          @node-click="handleMoveTargetSelect"
        />
      </div>
      <template #footer>
        <el-button @click="moveDialogVisible = false">取消</el-button>
        <el-button type="primary" :disabled="!moveTargetSelected" @click="confirmMove">
          确定移动
        </el-button>
      </template>
    </el-dialog>

    <el-image-viewer
      v-if="imagePreviewVisible"
      :url-list="imagePreviewUrls"
      :initial-index="imagePreviewIndex"
      @close="imagePreviewVisible = false"
    />

    <el-dialog
      v-model="videoPreviewVisible"
      width="760px"
      append-to-body
      align-center
      class="video-preview-dialog"
      @closed="videoPreviewItem = null"
    >
      <template #header>
        <div class="video-preview-header">
          <span>{{ videoPreviewItem?.title || '瑙嗛棰勮' }}</span>
        </div>
      </template>
      <div class="video-preview-body">
        <video
          v-if="videoPreviewUrl"
          class="video-preview-player"
          :src="videoPreviewUrl"
          controls
          preload="metadata"
        />
        <div v-else class="video-preview-empty">瑙嗛鏃犳硶棰勮</div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from 'vue'
import {
  ArrowRight,
  Calendar,
  Cellphone,
  Close,
  Delete,
  Edit,
  Expand,
  Fold,
  FolderOpened,
  PictureFilled,
  Plus,
  Rank,
  Upload,
  VideoPlay
} from '@element-plus/icons-vue'
import { ElImageViewer, ElMessage, ElMessageBox } from 'element-plus'
import { useSidebar } from '@/composables/useSidebar'
import request from '@/utils/request'

const { isCollapse, toggleSidebar } = useSidebar()

const fileInputRef = ref(null)
const activeFilter = ref('all')
const viewMode = ref('grid')
const loading = ref(false)
const createMenuVisible = ref(false)

const drawerVisible = ref(false)
const selectedItem = ref(null)
const isEditingName = ref(false)
const editName = ref('')
const renameInputRef = ref(null)

const moveDialogVisible = ref(false)
const moveTargetId = ref(null)
const moveTargetSelected = ref(false)
const moveTreeRef = ref(null)
const folderTreeData = ref([])

const filterOptions = [
  { label: '全部', value: 'all' },
  { label: '图片', value: 'image' },
  { label: '视频', value: 'video' },
  { label: '文件夹', value: 'folder' }
]

const currentFolderId = ref(null)
const items = ref([])
const breadcrumbPath = ref([])
const imagePreviewVisible = ref(false)
const imagePreviewIndex = ref(0)
const videoPreviewVisible = ref(false)
const videoPreviewItem = ref(null)

const imagePreviewItems = computed(() => items.value.filter((item) => item.kind === 'image' && item.previewUrl))
const imagePreviewUrls = computed(() => imagePreviewItems.value.map((item) => item.previewUrl))
const videoPreviewUrl = computed(() => videoPreviewItem.value?.previewUrl || '')

function mapItem(raw) {
  return {
    id: raw.id,
    kind: raw.kind,
    title: raw.title,
    parentId: raw.parent_id,
    extension: raw.extension,
    fileSize: raw.file_size,
    visibleOnAndroid: raw.visible_on_android,
    updatedAt: raw.updated_at,
    createdAt: raw.created_at,
    size: raw.size || '',
    previewUrl: raw.previewUrl || '',
    childrenCount: raw.children_count ?? 0
  }
}

async function fetchItems() {
  loading.value = true
  try {
    const params = {}
    if (currentFolderId.value !== null) params.parent_id = currentFolderId.value
    if (activeFilter.value !== 'all') params.kind = activeFilter.value
    const data = await request.get('/media/items', { params })
    items.value = data.map(mapItem)
  } catch {
    items.value = []
  } finally {
    loading.value = false
  }
}

async function fetchBreadcrumbs() {
  if (currentFolderId.value === null) {
    breadcrumbPath.value = []
    return
  }
  try {
    breadcrumbPath.value = await request.get(`/media/ancestors/${currentFolderId.value}`)
  } catch {
    breadcrumbPath.value = []
  }
}

const breadcrumbs = computed(() => {
  const result = [{ key: 'root', title: '媒体库', path: null, active: currentFolderId.value === null }]
  breadcrumbPath.value.forEach((crumb, idx) => {
    result.push({
      key: crumb.id,
      title: crumb.title,
      path: crumb.id,
      active: idx === breadcrumbPath.value.length - 1
    })
  })
  return result
})

const displayedItems = computed(() => items.value)

function goToPath(folderId) {
  currentFolderId.value = folderId ?? null
  fetchItems()
  fetchBreadcrumbs()
}

function toggleViewMode() {
  viewMode.value = viewMode.value === 'grid' ? 'list' : 'grid'
}

function openImagePreview(item) {
  const idx = imagePreviewItems.value.findIndex((img) => img.id === item.id)
  if (idx === -1) return
  imagePreviewIndex.value = idx
  imagePreviewVisible.value = true
}

function openVideoPreview(item) {
  if (!item.previewUrl) {
    ElMessage.warning('瑙嗛鏃犳硶棰勮')
    return
  }
  videoPreviewItem.value = item
  videoPreviewVisible.value = true
}

function handleItemClick(item) {
  if (item.kind === 'folder') {
    currentFolderId.value = item.id
    fetchItems()
    fetchBreadcrumbs()
    return
  }
  if (item.kind === 'image') {
    openImagePreview(item)
    return
  }
  if (item.kind === 'video') {
    openVideoPreview(item)
  }
}

function openDrawer(item) {
  selectedItem.value = item
  isEditingName.value = false
  drawerVisible.value = true
}

function startRename() {
  editName.value = selectedItem.value.title
  isEditingName.value = true
  nextTick(() => {
    renameInputRef.value?.focus()
  })
}

async function saveRename() {
  if (!isEditingName.value) return
  const trimmed = editName.value.trim()
  if (!trimmed || trimmed === selectedItem.value.title) {
    isEditingName.value = false
    return
  }
  try {
    const updated = await request.patch(`/media/items/${selectedItem.value.id}`, { title: trimmed })
    const mapped = mapItem(updated)
    selectedItem.value.title = mapped.title
    selectedItem.value.updatedAt = mapped.updatedAt
    const idx = items.value.findIndex((i) => i.id === mapped.id)
    if (idx !== -1) items.value[idx] = { ...items.value[idx], ...mapped }
    ElMessage.success('已重命名')
  } catch {
    // request.js 已处理错误提示
  }
  isEditingName.value = false
}

async function handleVisibilityChange(val) {
  if (!selectedItem.value) return
  try {
    await request.patch(`/media/items/${selectedItem.value.id}`, { visible_on_android: val })
    const idx = items.value.findIndex((i) => i.id === selectedItem.value.id)
    if (idx !== -1) items.value[idx].visibleOnAndroid = val
  } catch {
    selectedItem.value.visibleOnAndroid = !val
  }
}

async function openMoveDialog() {
  moveTargetId.value = null
  moveTargetSelected.value = false
  try {
    const excludeId = selectedItem.value?.id
    const params = excludeId ? { exclude_id: excludeId } : {}
    folderTreeData.value = await request.get('/media/tree', { params })
  } catch {
    folderTreeData.value = []
  }
  moveDialogVisible.value = true
}

function handleMoveTargetSelect(data) {
  moveTargetId.value = data.id === 0 ? null : data.id
  moveTargetSelected.value = true
}

async function confirmMove() {
  if (!moveTargetSelected.value || !selectedItem.value) return
  try {
    await request.patch(`/media/items/${selectedItem.value.id}/move`, { parent_id: moveTargetId.value })
    moveDialogVisible.value = false
    drawerVisible.value = false
    ElMessage.success('已移动')
    fetchItems()
  } catch {
    // request.js 已处理错误提示
  }
}

async function handleDelete() {
  if (!selectedItem.value) return
  const label = selectedItem.value.kind === 'folder' ? '文件夹' : '文件'
  try {
    await ElMessageBox.confirm(
      `确定删除${label}「${selectedItem.value.title}」？${selectedItem.value.kind === 'folder' ? '其中所有内容将一并删除。' : ''}`,
      '确认删除',
      { confirmButtonText: '删除', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }

  try {
    await request.delete(`/media/items/${selectedItem.value.id}`)
    drawerVisible.value = false
    selectedItem.value = null
    ElMessage.success(`${label}已删除`)
    fetchItems()
  } catch {
    // request.js 已处理错误提示
  }
}

async function handleCreateCommand(command) {
  createMenuVisible.value = false
  if (command === 'folder') {
    await createFolder()
    return
  }
  if (command === 'import') {
    fileInputRef.value?.click()
  }
}

async function createFolder() {
  try {
    const { value } = await ElMessageBox.prompt('请输入文件夹名称', '新建文件夹', {
      confirmButtonText: '确定',
      cancelButtonText: '取消',
      inputPlaceholder: '例如：签到图片、宣传视频'
    })

    const folderName = value?.trim()
    if (!folderName) {
      ElMessage.warning('请输入文件夹名称')
      return
    }

    const formData = new FormData()
    formData.append('title', folderName)
    if (currentFolderId.value !== null) formData.append('parent_id', currentFolderId.value)

    await request.post('/media/folders', formData)
    ElMessage.success('文件夹已创建')
    fetchItems()
  } catch {
    // 用户取消或接口错误
  }
}

async function handleFileImport(event) {
  const selectedFiles = Array.from(event.target.files || [])
  if (!selectedFiles.length) return

  const supportedFiles = selectedFiles.filter((file) => file.type.startsWith('image/') || file.type.startsWith('video/'))

  if (!supportedFiles.length) {
    ElMessage.warning('仅支持导入图片或视频文件')
    event.target.value = ''
    return
  }

  const formData = new FormData()
  supportedFiles.forEach((file) => formData.append('files', file))
  if (currentFolderId.value !== null) formData.append('parent_id', currentFolderId.value)

  try {
    const result = await request.post('/media/upload', formData, { timeout: 0 })
    ElMessage.success(`已导入 ${result.length} 个文件`)
    fetchItems()
  } catch {
    // request.js 已处理错误提示
  }
  event.target.value = ''
}

function formatDate(value) {
  if (!value) return ''
  const date = new Date(value)
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

function getFolderCount(item) {
  return item.childrenCount ?? 0
}

function kindLabel(kind) {
  if (kind === 'image') return '图片'
  if (kind === 'video') return '视频'
  return '文件夹'
}

watch(activeFilter, () => {
  fetchItems()
})

onMounted(() => {
  fetchItems()
})
</script>

<style scoped>
.media-page {
  padding: 0;
}

.hidden-file-input {
  display: none;
}

.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  padding: 0 4px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  padding: 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
  height: auto;
}

.collapse-btn:hover {
  background-color: var(--bg-main, #f8fafc);
}

.header-divider {
  height: 24px;
  border-color: var(--border-color, #e2e8f0);
  margin: 0 4px;
}

.title-group {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  line-height: 1.2;
}

.page-subtitle {
  margin: 4px 0 0;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
  line-height: 1.4;
}

.toolbar-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto minmax(0, 1fr);
  align-items: center;
  gap: 16px;
  margin-bottom: 28px;
}

.breadcrumb-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  flex-wrap: wrap;
}

.breadcrumb-item {
  border: none;
  background: transparent;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  padding: 0;
  cursor: pointer;
}

.breadcrumb-item.active {
  color: var(--text-main, #0f172a);
  font-weight: 600;
}

.breadcrumb-item:not(:last-child)::after {
  content: '/';
  margin-left: 8px;
  color: var(--text-placeholder, #94a3b8);
}

.toolbar-center {
  justify-self: center;
}

.type-filter {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: var(--card-bg, #ffffff);
  border: 1px solid var(--border-color, #e2e8f0);
  border-radius: 999px;
  box-shadow: 0 8px 24px rgba(15, 23, 42, 0.04);
}

.filter-pill {
  border: none;
  background: transparent;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  line-height: 1;
  padding: 10px 16px;
  border-radius: 999px;
  cursor: pointer;
  transition: all 0.2s ease;
}

.filter-pill.active {
  background: var(--bg-main, #f8fafc);
  color: var(--text-main, #0f172a);
  font-weight: 600;
}

.toolbar-right {
  justify-self: end;
}

.view-button {
  border-radius: 999px;
  padding: 10px 18px;
}

.library-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 28px 22px;
}

.library-card {
  cursor: pointer;
  transition: transform 0.24s ease, box-shadow 0.24s ease;
}

.library-card:hover {
  transform: translateY(-4px);
}

.create-thumb,
.folder-thumb,
.media-thumb {
  border-radius: 22px;
  position: relative;
}

.folder-thumb {
  height: 150px;
}

.create-thumb,
.media-thumb {
  height: 132px;
  margin-top: 18px;
}

.create-card {
  color: var(--text-secondary, #64748b);
}

.create-thumb {
  border: 1.5px dashed #93c5fd;
  background: linear-gradient(180deg, rgba(255,255,255,0.78), rgba(239,246,255,0.7));
  display: flex;
  align-items: center;
  justify-content: center;
  color: #3b82f6;
  font-size: 30px;
}

.folder-flap {
  position: absolute;
  left: 18px;
  top: 6px;
  width: 72px;
  height: 24px;
  border-radius: 16px 16px 0 0;
  background: linear-gradient(180deg, #9ed0fb, #8fc4f5);
}

.folder-body {
  position: absolute;
  inset: 18px 0 0 0;
  border-radius: 18px;
  background: linear-gradient(180deg, #a9d6fb 0%, #93c9f7 100%);
  box-shadow: inset 0 1px 0 rgba(255,255,255,0.55);
}

.folder-badge {
  position: absolute;
  right: 14px;
  top: 14px;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  color: #1d4ed8;
  background: rgba(255,255,255,0.72);
}

.media-thumb {
  overflow: hidden;
  border: 1px solid var(--border-color, #e2e8f0);
  background: var(--card-bg, #ffffff);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.05);
}

.media-thumb.image {
  background: linear-gradient(180deg, #f8fafc, #e2e8f0);
}

.media-thumb.video {
  background: linear-gradient(180deg, #dbeafe, #bfdbfe 60%, #93c5fd);
}

.thumb-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-badge {
  position: absolute;
  top: 14px;
  left: 14px;
  z-index: 2;
  padding: 6px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 600;
  color: #ffffff;
  background: rgba(15, 23, 42, 0.56);
  backdrop-filter: blur(8px);
}

.thumb-overlay {
  position: absolute;
  inset: 0;
  display: flex;
  align-items: center;
  justify-content: center;
}

.thumb-icon {
  font-size: 38px;
  color: rgba(255, 255, 255, 0.92);
  filter: drop-shadow(0 6px 14px rgba(15, 23, 42, 0.22));
}

.thumb-footer {
  position: absolute;
  left: 0;
  right: 0;
  bottom: 0;
  padding: 14px 16px;
  color: #ffffff;
  font-size: 13px;
  background: linear-gradient(180deg, transparent, rgba(15, 23, 42, 0.7));
}

.card-info {
  padding: 14px 4px 0;
}

.card-title-row {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.card-title {
  flex: 1;
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  line-height: 1.45;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.more-icon {
  color: var(--text-secondary, #94a3b8);
  margin-top: 2px;
}

.card-meta,
.card-meta-row {
  color: var(--text-secondary, #64748b);
  font-size: 12px;
}

.card-meta-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  margin-top: 12px;
}

.meta-item {
  display: inline-flex;
  align-items: center;
  gap: 5px;
}

.empty-state {
  grid-column: 1 / -1;
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  border: 1px dashed var(--border-color, #e2e8f0);
  border-radius: 24px;
  color: var(--text-secondary, #64748b);
  background: var(--card-bg, #ffffff);
}

.empty-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  margin-bottom: 6px;
}

.library-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.list-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  padding: 14px 18px;
  border-radius: 20px;
  background: var(--card-bg, #ffffff);
  border: 1px solid var(--border-color, #e2e8f0);
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.item-row {
  cursor: pointer;
}

.item-row:hover,
.create-row:hover {
  transform: translateY(-2px);
  box-shadow: 0 12px 28px rgba(15, 23, 42, 0.06);
}

.list-main {
  display: flex;
  align-items: center;
  gap: 14px;
  min-width: 0;
  flex: 1;
}

.list-icon {
  width: 56px;
  height: 56px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
  color: #ffffff;
  background: linear-gradient(180deg, #cbd5e1, #94a3b8);
  overflow: hidden;
}

.list-icon.folder {
  background: linear-gradient(180deg, #a9d6fb 0%, #93c9f7 100%);
}

.list-icon.image {
  background: linear-gradient(180deg, #f8fafc, #e2e8f0);
  color: #64748b;
}

.list-icon.video {
  background: linear-gradient(180deg, #dbeafe, #93c5fd);
}

.create-icon {
  background: linear-gradient(180deg, rgba(255,255,255,0.78), rgba(239,246,255,0.7));
  border: 1.5px dashed #93c5fd;
  color: #3b82f6;
}

.mini-folder {
  width: 28px;
  height: 22px;
  border-radius: 8px;
  background: #ffffff;
  position: relative;
}

.mini-folder::before {
  content: '';
  position: absolute;
  left: 2px;
  top: -5px;
  width: 12px;
  height: 8px;
  border-radius: 6px 6px 0 0;
  background: #ffffff;
}

.mini-image {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.list-content {
  min-width: 0;
}

.list-title {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  line-height: 1.4;
}

.list-subtitle {
  margin-top: 4px;
  font-size: 13px;
  color: var(--text-secondary, #64748b);
}

.list-side {
  display: flex;
  align-items: center;
  gap: 24px;
  color: var(--text-secondary, #64748b);
  font-size: 13px;
  flex-shrink: 0;
}

.list-date,
.list-extra {
  min-width: 88px;
  text-align: right;
}

.list-arrow {
  color: var(--text-placeholder, #94a3b8);
}

.empty-list-state {
  padding: 28px;
  text-align: center;
  color: var(--text-secondary, #64748b);
  border: 1px dashed var(--border-color, #e2e8f0);
  border-radius: 20px;
}

@media (max-width: 992px) {
  .toolbar-row {
    grid-template-columns: 1fr;
  }

  .toolbar-center,
  .toolbar-right {
    justify-self: start;
  }
}

@media (max-width: 768px) {
  .type-filter {
    width: 100%;
    justify-content: space-between;
    overflow-x: auto;
  }

  .library-grid {
    grid-template-columns: repeat(auto-fill, minmax(160px, 1fr));
    gap: 22px 16px;
  }

  .create-thumb,
  .folder-thumb,
  .media-thumb {
    border-radius: 18px;
  }

  .folder-thumb {
    height: 128px;
  }

  .create-thumb,
  .media-thumb {
    height: 110px;
    margin-top: 18px;
  }

  .list-row {
    flex-direction: column;
    align-items: flex-start;
  }

  .list-side {
    width: 100%;
    justify-content: space-between;
  }

  .list-date,
  .list-extra {
    text-align: left;
    min-width: 0;
  }
}

:global(html.dark) .type-filter,
:global(html.dark) .media-thumb,
:global(html.dark) .list-row {
  box-shadow: 0 10px 28px rgba(2, 6, 23, 0.28);
}

:global(html.dark) .create-thumb,
:global(html.dark) .create-icon {
  border-color: rgba(96, 165, 250, 0.5);
  background: linear-gradient(180deg, rgba(30,41,59,0.92), rgba(15,23,42,0.92));
}

:global(html.dark) .folder-flap {
  background: linear-gradient(180deg, #4f8fd1, #427fbf);
}

:global(html.dark) .folder-body,
:global(html.dark) .list-icon.folder {
  background: linear-gradient(180deg, #5b9ad9 0%, #4b88c8 100%);
}

/* 可点击文件名 */
.clickable-name {
  cursor: pointer;
  border-radius: 6px;
  transition: background-color 0.15s ease, color 0.15s ease;
  padding: 2px 6px;
  margin: -2px -6px;
}

.clickable-name:hover {
  background-color: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
}

/* 抽屉面板 */
.drawer-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  width: 100%;
}

.drawer-header-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
}

.drawer-close-btn {
  color: var(--text-secondary, #64748b);
}

.drawer-body {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.prop-section {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.prop-label {
  font-size: 12px;
  font-weight: 600;
  color: var(--text-secondary, #64748b);
  text-transform: uppercase;
  letter-spacing: 0.5px;
}

.prop-name-row {
  display: flex;
  align-items: center;
  gap: 8px;
}

.prop-name-text {
  font-size: 17px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  word-break: break-all;
  line-height: 1.5;
}

.prop-edit-btn {
  color: var(--text-secondary, #94a3b8);
  flex-shrink: 0;
}

.prop-edit-btn:hover {
  color: #3b82f6;
}

.prop-name-row.editing {
  flex: 1;
}

.prop-info-grid {
  display: flex;
  flex-direction: column;
  gap: 10px;
  padding: 14px 16px;
  background: var(--bg-main, #f8fafc);
  border-radius: 14px;
  border: 1px solid var(--border-color, #e2e8f0);
}

.prop-info-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  font-size: 13px;
}

.prop-info-key {
  color: var(--text-secondary, #64748b);
}

.prop-info-val {
  color: var(--text-main, #0f172a);
  font-weight: 500;
}

.prop-switch-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  background: var(--bg-main, #f8fafc);
  border-radius: 14px;
  border: 1px solid var(--border-color, #e2e8f0);
}

.prop-switch-info {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--text-main, #0f172a);
  font-size: 14px;
}

.prop-actions {
  gap: 10px;
  margin-top: 8px;
}

.prop-actions .el-button + .el-button {
  margin-left: 0;
}

.prop-action-btn {
  width: 100%;
  justify-content: center;
  border-radius: 12px;
  padding: 12px 16px;
  font-size: 14px;
  gap: 6px;
}

.prop-action-btn.danger {
  color: var(--el-color-danger, #ef4444);
  border-color: var(--el-color-danger, #ef4444);
  background: transparent;
}

.prop-action-btn.danger:hover {
  background: rgba(239, 68, 68, 0.06);
}

/* 移动对话框 */
.move-tree-wrap {
  min-height: 200px;
  max-height: 420px;
  overflow-y: auto;
  padding: 8px 4px;
}

.move-tree-wrap :deep(.el-tree-node__content) {
  height: 40px;
  padding: 4px 8px;
  border-radius: 8px;
  font-size: 14px;
}

.move-tree-wrap :deep(.el-tree-node__content:hover) {
  background-color: var(--bg-main, #f1f5f9);
}

.move-tree-wrap :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: rgba(59, 130, 246, 0.08);
  color: #3b82f6;
  font-weight: 500;
}

.move-tree-wrap :deep(.el-tree-node__expand-icon) {
  font-size: 14px;
}

/* 暗色模式 */
:global(html.dark) .clickable-name:hover {
  background-color: rgba(96, 165, 250, 0.12);
  color: #60a5fa;
}

:global(html.dark) .drawer-header-title {
  color: var(--text-main, #e2e8f0);
}

:global(html.dark) .prop-name-text {
  color: var(--text-main, #e2e8f0);
}

:global(html.dark) .prop-info-grid,
:global(html.dark) .prop-switch-row {
  background: var(--bg-main, #1e293b);
  border-color: var(--border-color, #334155);
}

:global(html.dark) .prop-info-val,
:global(html.dark) .prop-switch-info {
  color: var(--text-main, #e2e8f0);
}

:global(html.dark) .move-tree-wrap :deep(.el-tree-node__content:hover) {
  background-color: rgba(255, 255, 255, 0.06);
}

:global(html.dark) .move-tree-wrap :deep(.el-tree-node.is-current > .el-tree-node__content) {
  background-color: rgba(96, 165, 250, 0.12);
  color: #60a5fa;
}

:global(html.dark) .prop-action-btn.danger {
  color: #f87171;
  border-color: #f87171;
}

:global(html.dark) .prop-action-btn.danger:hover {
  background: rgba(248, 113, 113, 0.1);
}

/* 新建弹出菜单 */
.create-menu {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.create-menu-item {
  display: flex;
  align-items: center;
  gap: 14px;
  padding: 14px 16px;
  border-radius: 12px;
  cursor: pointer;
  transition: background-color 0.18s ease;
}

.create-menu-item:hover {
  background: var(--bg-main, #f1f5f9);
}

.create-menu-icon {
  width: 42px;
  height: 42px;
  border-radius: 12px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.create-menu-icon.folder-icon {
  background: linear-gradient(135deg, #dbeafe, #bfdbfe);
  color: #2563eb;
}

.create-menu-icon.import-icon {
  background: linear-gradient(135deg, #d1fae5, #a7f3d0);
  color: #059669;
}

.create-menu-text {
  min-width: 0;
}

.create-menu-title {
  font-size: 14px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  line-height: 1.4;
}

.create-menu-desc {
  font-size: 12px;
  color: var(--text-secondary, #64748b);
  margin-top: 2px;
  line-height: 1.4;
}

:global(html.dark) .create-menu-item:hover {
  background: rgba(255, 255, 255, 0.06);
}

:global(html.dark) .create-menu-icon.folder-icon {
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.2), rgba(59, 130, 246, 0.15));
  color: #60a5fa;
}

:global(html.dark) .create-menu-icon.import-icon {
  background: linear-gradient(135deg, rgba(5, 150, 105, 0.2), rgba(16, 185, 129, 0.15));
  color: #34d399;
}

:global(html.dark) .create-menu-title {
  color: var(--text-main, #e2e8f0);
}

:global(.create-popover.el-popover) {
  padding: 6px !important;
  border-radius: 16px !important;
  box-shadow: 0 12px 40px rgba(15, 23, 42, 0.12), 0 0 0 1px rgba(15, 23, 42, 0.05) !important;
}

:global(html.dark .create-popover.el-popover) {
  background: var(--el-bg-color-overlay, #1e293b) !important;
  border-color: var(--border-color, #334155) !important;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.4), 0 0 0 1px rgba(255, 255, 255, 0.06) !important;
}

.video-preview-body {
  display: flex;
  align-items: center;
  justify-content: center;
  min-height: 320px;
}

.video-preview-player {
  width: 100%;
  max-height: 70vh;
  border-radius: 16px;
  background: #000000;
}

.video-preview-empty {
  color: var(--text-secondary, #64748b);
}

.video-preview-header {
  font-size: 16px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
}

:deep(.video-preview-dialog .el-dialog__body) {
  padding: 16px 24px 24px;
}
</style>
