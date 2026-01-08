<template>
  <div class="type-manage">
    <div class="page-header">
      <div class="header-left">
        <el-button 
          class="collapse-btn" 
          link 
          @click="toggleSidebar"
        >
          <el-icon size="24" color="#64748b">
            <Expand v-if="isCollapse" />
            <Fold v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        
        <div class="title-group">
          <h2 class="page-title">会议类型配置</h2>
          <p class="page-subtitle">定义系统中的会议分类标准</p>
        </div>
      </div>
      
      <div class="header-right">
        <el-button type="primary" size="large" @click="openDialog()" class="add-btn">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          新建类型
        </el-button>
      </div>
    </div>

    <div v-if="loading" class="loading-grid">
       <el-skeleton v-for="i in 4" :key="i" animated class="skeleton-card">
        <template #template>
          <el-skeleton-item variant="rect" style="height: 140px; border-radius: 12px;" />
        </template>
      </el-skeleton>
    </div>
    
    <div v-else-if="types.length > 0" class="type-grid">
      <div 
        v-for="(item, index) in types" 
        :key="item.id" 
        class="grid-card"
        :style="{ 
          '--theme-color': getColor(item.name),
          '--theme-bg': getColor(item.name) + '15' 
        }"
      >
        <div class="card-deco-line"></div>
        <div class="card-body">
          <div class="card-top">
            <div class="type-icon">
              <el-icon size="24">
                <component :is="getSmartIcon(item.name)" />
              </el-icon>
            </div>
            <div class="card-actions">
              <el-tooltip content="编辑" placement="top">
                <el-button circle size="small" @click="openDialog(item)">
                  <el-icon><Edit /></el-icon>
                </el-button>
              </el-tooltip>
              <el-tooltip content="删除" placement="top">
                <el-button circle size="small" type="danger" plain @click="handleDelete(item)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </el-tooltip>
            </div>
          </div>
          <h3 class="card-title">{{ item.name }}</h3>
          <p class="card-desc">{{ item.description || '该类型暂无详细描述说明...' }}</p>
          <div class="card-footer">
            <span class="date-tag">
              <el-icon><Clock /></el-icon>
              {{ formatDate(item.created_at) }}
            </span>
          </div>
        </div>
      </div>
    </div>
    
    <div v-else class="empty-state">
      <el-empty description="暂无会议类型数据" :image-size="160">
        <el-button type="primary" @click="openDialog()">创建第一个类型</el-button>
      </el-empty>
    </div>

    <el-dialog 
      v-model="dialogVisible" 
      :title="isEdit ? '编辑类型' : '添加类型'" 
      width="460px" 
      destroy-on-close 
      align-center
      class="custom-dialog"
    >
      <el-form :model="form" label-position="top" size="large">
        <el-form-item label="类型名称" required>
          <el-input v-model="form.name" placeholder="如：党委会、办公会" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="封面策略">
          <el-radio-group v-model="form.is_fixed_image">
            <el-radio :label="false" border>系统随机分配</el-radio>
            <el-radio :label="true" border>固定封面图片</el-radio>
          </el-radio-group>
        </el-form-item>

        <el-form-item label="封面设置" v-if="form.is_fixed_image">
          <el-upload
            class="cover-uploader"
            action="/api/meeting_types/upload_cover"
            :show-file-list="false"
            :on-success="handleCoverSuccess"
            :before-upload="beforeCoverUpload"
          >
            <img v-if="form.cover_image" :src="form.cover_image" class="cover-image" />
            <el-icon v-else class="cover-uploader-icon"><Plus /></el-icon>
            <template #tip>
              <div class="el-upload__tip">
                建议尺寸 800x400，支持 JPG/PNG，小于 2MB
              </div>
            </template>
          </el-upload>
        </el-form-item>

        <el-form-item label="描述说明">
          <el-input 
            v-model="form.description" 
            type="textarea" 
            :rows="3" 
            placeholder="可选，对该类型的简要说明..." 
            maxlength="100" 
            show-word-limit 
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSave" :loading="saving">
            {{ isEdit ? '保存修改' : '立即创建' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
// 引入 sidebar 逻辑
import { useSidebar } from '@/composables/useSidebar' 

import { 
  Plus, Clock, Edit, Delete, Fold, Expand, // 引入 Fold 和 Expand 图标
  Monitor, DataBoard, Memo, VideoCamera, Mic, 
  Trophy, Flag, Reading, Stamp, Briefcase, Calendar,
  DataAnalysis, Notification, UserFilled, Collection
} from '@element-plus/icons-vue'

// 获取侧边栏状态
const { isCollapse, toggleSidebar } = useSidebar()

// ... (以下所有原有的 script 逻辑保持完全不变) ...
const types = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({ name: '', description: '' })
const isEdit = computed(() => editingId.value !== null)

// HSL Color Generator: Generates a unique HSL color for any string
const getColor = (str) => {
  if (!str) return '#3b82f6'
  let hash = 0
  for (let i = 0; i < str.length; i++) {
    hash = str.charCodeAt(i) + ((hash << 5) - hash)
  }
  
  // H: 0-360 (Full spectrum)
  // S: 60-80% (Vibrant but not neon)
  // L: 45-60% (Readable on white, not too dark)
  const h = Math.abs(hash) % 360
  const s = 60 + (Math.abs(hash) % 20) 
  const l = 45 + (Math.abs(hash) % 15)
  
  return `hsl(${h}, ${s}%, ${l}%)`
}

const getSmartIcon = (name) => {
  if (!name) return 'DataBoard'
  const n = name.toLowerCase()
  if (n.includes('党') || n.includes('政') || n.includes('红')) return 'Flag'
  if (n.includes('视') || n.includes('频') || n.includes('云')) return 'VideoCamera'
  if (n.includes('培') || n.includes('训') || n.includes('课')) return 'Reading'
  if (n.includes('审') || n.includes('评') || n.includes('决')) return 'Stamp'
  if (n.includes('例') || n.includes('周') || n.includes('月')) return 'Calendar'
  if (n.includes('办') || n.includes('工') || n.includes('行')) return 'Briefcase'
  if (n.includes('表') || n.includes('彰') || n.includes('奖')) return 'Trophy'
  if (n.includes('通') || n.includes('知') || n.includes('宣')) return 'Notification'
  if (n.includes('人') || n.includes('员') || n.includes('面')) return 'UserFilled'
  if (n.includes('总') || n.includes('结') || n.includes('汇')) return 'DataAnalysis'
  return 'Collection'
}

const fetchTypes = async () => {
  loading.value = true
  try {
    types.value = await request.get('/meeting_types/')
  } catch (error) {
    ElMessage.error('获取数据失败')
  } finally {
    loading.value = false
  }
}

const openDialog = (item = null) => {
  if (item) {
    editingId.value = item.id
    form.value = { 
      name: item.name, 
      description: item.description || '',
      is_fixed_image: item.is_fixed_image || false,
      cover_image: item.cover_image || ''
    }
  } else {
    editingId.value = null
    form.value = { 
      name: '', 
      description: '',
      is_fixed_image: false,
      cover_image: ''
    }
  }
  dialogVisible.value = true
}

const handleCoverSuccess = (res) => {
  form.value.cover_image = res.url
  ElMessage.success('封面上传成功')
}

const beforeCoverUpload = (rawFile) => {
  if (rawFile.size / 1024 / 1024 > 2) {
    ElMessage.error('图片大小不能超过 2MB!')
    return false
  }
  return true
}

const handleSave = async () => {
  if (!form.value.name.trim()) return ElMessage.warning('类型名称不能为空')
  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/meeting_types/${editingId.value}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/meeting_types/', form.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    fetchTypes()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = (item) => {
  ElMessageBox.confirm(`确定要删除类型「${item.name}」吗？删除后不可恢复。`, '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    await request.delete(`/meeting_types/${item.id}`)
    ElMessage.success('已删除')
    fetchTypes()
  }).catch(() => {})
}

const formatDate = (iso) => {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(fetchTypes)
</script>

<style scoped>
.type-manage {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 头部样式调整 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  /* margin-bottom: 32px; REMOVED */
  padding: 0 4px;
}

/* 左侧标题区域样式增强 */
.header-left {
  display: flex;
  align-items: center; 
  gap: 12px;
}

/* 折叠按钮样式 */
.collapse-btn {
  padding: 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
  height: auto; 
}
.collapse-btn:hover {
  background-color: var(--bg-main); 
}

.header-divider {
  height: 24px;
  border-color: var(--border-color);
  margin: 0 4px;
}

.title-group {
  display: flex;
  flex-direction: column;
}

.page-title {
  font-size: 26px;
  font-weight: 700;
  color: var(--text-main);
  margin: 0 0 4px 0;
  letter-spacing: -0.5px;
  line-height: 1.2;
}
.page-subtitle {
  color: var(--text-secondary);
  margin: 0;
  font-size: 14px;
  line-height: 1.4;
}

/* ... 以下所有其他样式保持不变 ... */
.header-right {
  display: flex;
  align-items: center;
  gap: 24px;
}
.add-btn {
  box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.3);
}

.type-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); 
  gap: 24px;
  animation: fadeIn 0.4s ease-out;
}
.loading-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr)); 
  gap: 24px;
}

@keyframes fadeIn {
  from { opacity: 0; transform: translateY(10px); }
  to { opacity: 1; transform: translateY(0); }
}

.grid-card {
  background: var(--card-bg);
  border-radius: 16px;
  border: 1px solid var(--border-color);
  overflow: hidden;
  transition: all 0.3s cubic-bezier(0.4, 0, 0.2, 1);
  position: relative;
  display: flex;
  flex-direction: column;
  height: 200px;
}

.grid-card:hover {
  transform: translateY(-5px);
  box-shadow: 0 12px 24px -8px rgba(0, 0, 0, 0.08); /* Dark mode override handled in style.css */
  border-color: var(--theme-color);
}

.card-deco-line {
  height: 4px;
  background: var(--theme-color);
  width: 100%;
}

.card-body {
  padding: 20px;
  flex: 1;
  display: flex;
  flex-direction: column;
}

.card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  margin-bottom: 12px;
}

.type-icon {
  width: 44px;
  height: 44px;
  border-radius: 12px;
  background-color: var(--theme-bg); 
  /* --theme-bg includes alpha, works well on dark */
  color: var(--theme-color);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s ease;
}

.grid-card:hover .type-icon {
  transform: scale(1.1);
}

.card-actions {
  opacity: 0;
  transform: translateX(10px);
  transition: all 0.2s;
  display: flex;
  gap: 6px;
}
.grid-card:hover .card-actions {
  opacity: 1;
  transform: translateX(0);
}

.card-title {
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
  margin: 0 0 6px 0;
}

.card-desc {
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
  margin: 0;
  flex: 1; 
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}

.card-footer {
  margin-top: 16px;
  padding-top: 12px;
  border-top: 1px dashed var(--border-color);
  display: flex;
  align-items: center;
}

.date-tag {
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.empty-state {
  padding: 60px 0;
  background: var(--card-bg);
  border-radius: 16px;
  border: 1px dashed var(--border-color);
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.cover-uploader .el-upload {
  border: 1px dashed var(--border-color);
  border-radius: 8px;
  cursor: pointer;
  position: relative;
  overflow: hidden;
  transition: var(--el-transition-duration-fast);
}

.cover-uploader .el-upload:hover {
  border-color: var(--el-color-primary);
}

.cover-uploader-icon {
  font-size: 28px;
  color: #8c939d;
  width: 100%;
  height: 120px;
  text-align: center;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--bg-main);
}

.cover-image {
  width: 100%;
  height: 120px;
  display: block;
  object-fit: cover;
}
</style>