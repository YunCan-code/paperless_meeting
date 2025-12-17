<template>
  <div class="meeting-manage">
    <!-- 页面头部 -->
    <div class="page-header">
      <div class="header-left">
        <el-button 
          class="collapse-btn" 
          link 
          @click="toggleSidebar"
        >
          <el-icon size="24" color="#64748b">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        
        <div class="title-group">
          <h1 class="page-title">会议管理</h1>
          <p class="page-subtitle">管理会议日程与文件分发</p>
        </div>
      </div>
    </div>

    <!-- 顶部统计卡片 (Sessions Overview) -->
    <el-row :gutter="20" class="stats-row">
      <el-col :span="6" v-for="(stat, index) in statsData" :key="index">
        <el-card shadow="hover" class="stat-card">
          <div class="stat-content">
            <div class="stat-icon" :class="stat.bgClass">
              <el-icon :class="stat.textClass" :size="24">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ stat.title }}</div>
              <div class="stat-value">{{ stat.value }}</div>
              <div class="stat-desc">{{ stat.subtitle }}</div>
            </div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 主体区域: 日历 (2/3) + 今日会议 (1/3) -->
    <el-row :gutter="24" class="main-content-row">
      <el-col :span="16">
        <SessionCalendar :meetings="meetings" @create="dialogVisible = true" />
      </el-col>
      <el-col :span="8">
        <TodayMeetings :meetings="meetings" @create="dialogVisible = true" @view="viewDetails" />
      </el-col>
    </el-row>

    <!-- 会议历史列表 -->
    <MeetingHistory 
      :meetings="meetings" 
      :meeting-types="meetingTypes" 
      @view="viewDetails"
      @upload="handleUploadClick"
    />

    <!-- 创建会议对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      title="发起新会议" 
      width="900px" 
      destroy-on-close 
      align-center
      class="meeting-dialog"
    >
      <div class="dialog-layout">
        <!-- 左侧：会议信息 -->
        <div class="dialog-left">
          <h4 class="section-title">基本信息</h4>
          <el-form :model="form" label-position="top" size="large">
            <el-form-item label="会议主题" required>
              <el-input v-model="form.title" placeholder="请输入会议主题" />
            </el-form-item>
            <el-form-item label="会议类型" required>
              <el-select v-model="form.meeting_type_id" placeholder="选择会议类型" style="width: 100%">
                <el-option v-for="item in meetingTypes" :key="item.id" :label="item.name" :value="item.id" />
              </el-select>
            </el-form-item>
            <el-form-item label="开始时间" required>
              <el-date-picker 
                v-model="form.start_time" 
                type="datetime" 
                placeholder="选择会议时间" 
                style="width: 100%" 
                format="YYYY-MM-DD HH:mm"
              />
            </el-form-item>
            <el-form-item label="会议地点">
              <el-input v-model="form.location" placeholder="输入会议室或地点" />
            </el-form-item>
          </el-form>
        </div>
        
        <!-- 右侧：文件管理 -->
        <div class="dialog-right">
          <div class="section-header">
            <h4 class="section-title">会议资料</h4>
            <el-upload 
              action="" 
              :auto-upload="false" 
              :show-file-list="false" 
              :on-change="handleFileSelect"
            >
              <el-button type="primary" link size="small"><el-icon class="el-icon--left"><Plus/></el-icon>添加文件</el-button>
            </el-upload>
          </div>
          
          <div class="file-list-container">
            <div v-if="fileList.length === 0" class="empty-state">
              <el-icon :size="48" color="#e2e8f0"><UploadFilled /></el-icon>
              <p>暂无文件，点击上方按钮添加</p>
            </div>
            
            <div v-else class="file-list">
              <div v-for="(file, index) in fileList" :key="file.id" class="file-item">
                <div class="file-icon">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="file-content">
                  <el-input v-model="file.name" size="small" class="name-input" placeholder="文件名" />
                  <span class="file-meta">{{ formatSize(file.size) }}</span>
                </div>
                <div class="file-actions">
                  <el-tooltip content="上移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, -1)" :disabled="index === 0">
                      <el-icon><Top /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="下移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, 1)" :disabled="index === fileList.length - 1">
                      <el-icon><Bottom /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-button circle size="small" type="danger" plain @click="removeFile(index)">
                    <el-icon><Delete /></el-icon>
                  </el-button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <div class="footer-tip">
            <span v-if="fileList.length > 0">已添加 {{ fileList.length }} 个文件</span>
          </div>
          <div class="footer-btns">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleAdd" :loading="submitting">确认发起</el-button>
          </div>
        </div>
      </template>
    </el-dialog>


  </div>
</template>

<script setup>
import { ref, onMounted, computed, reactive } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
// 引入更多图标
import { 
  Calendar, Timer as Clock, User, CircleCheck, Fold, Expand, 
  Document, UploadFilled, Top, Bottom, Delete, Edit, Plus
} from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'

import SessionCalendar from './components/SessionCalendar.vue'
import TodayMeetings from './components/TodayMeetings.vue'
import MeetingHistory from './components/MeetingHistory.vue'

const { isCollapse, toggleSidebar } = useSidebar()

const meetings = ref([])
const meetingTypes = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const submitting = ref(false)

// 表单数据
const form = ref({ 
  title: '', 
  meeting_type_id: null, 
  start_time: null, // 改为 null 初始化
  location: '' 
})

// 文件列表 (本地暂存)
const fileList = ref([])

// 统计数据 (更新后的文案)
const statsData = computed(() => {
  const total = meetings.value.length || 0
  
  // 计算本周会议 (简单模拟: 假设 scheduled 都在本周)
  const scheduled = meetings.value.filter(m => m.status === 'scheduled').length
  const active = meetings.value.filter(m => m.status === 'active').length
  
  // 计算今日会议
  const todayStr = new Date().toDateString()
  const todayCount = meetings.value.filter(m => {
    if (!m.start_time) return false
    return new Date(m.start_time).toDateString() === todayStr
  }).length

  return [
    { title: '本周会议', value: scheduled + active + 2, subtitle: `进行中 ${active} | 待开始 ${scheduled}`, icon: Calendar, textClass: 'text-blue', bgClass: 'bg-blue' },
    { title: '今日会议', value: todayCount, subtitle: '今日待办事项', icon: Clock, textClass: 'text-green', bgClass: 'bg-green' },
    { title: '文件分发', value: '128', subtitle: '本月累计 1.2GB', icon: Document, textClass: 'text-purple', bgClass: 'bg-purple' },
    { title: '参会人次', value: '382', subtitle: '节省纸张 ~2,000张', icon: User, textClass: 'text-teal', bgClass: 'bg-teal' }
  ]
})

const fetchMeetings = async () => {
  loading.value = true
  try { meetings.value = await request.get('/meetings/') } finally { loading.value = false }
}

const fetchMeetingTypes = async () => {
  try { meetingTypes.value = await request.get('/meeting_types/') } catch (e) {}
}

// ------ 文件处理逻辑 ------
const handleFileSelect = (uploadFile) => {
  const raw = uploadFile.raw
  // 检查是否已存在
  if (fileList.value.some(f => f.name === raw.name)) {
    ElMessage.warning('文件已存在')
    return
  }
  
  fileList.value.push({
    id: Date.now() + Math.random(), // 临时 ID
    raw: raw,
    name: raw.name, // 显示名称 (可重命名)
    size: raw.size,
    type: raw.type
  })
}

const removeFile = (index) => {
  fileList.value.splice(index, 1)
}

const moveFile = (index, direction) => {
  if (direction === -1 && index === 0) return
  if (direction === 1 && index === fileList.value.length - 1) return
  
  const temp = fileList.value[index]
  fileList.value[index] = fileList.value[index + direction]
  fileList.value[index + direction] = temp
}

const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

// ------ 提交逻辑 ------
const handleAdd = async () => {
  // 校验
  if (!form.value.title || !form.value.start_time || !form.value.meeting_type_id) {
    return ElMessage.warning('请填写完整的会议信息')
  }
  
  submitting.value = true
  try {
    // 1. 创建会议
    const meetingRes = await request.post('/meetings/', form.value)
    const meetingId = meetingRes.id
    
    // 2. 上传文件 (串行或并行均可，这里用串行确保顺序)
    for (let i = 0; i < fileList.value.length; i++) {
        const item = fileList.value[i]
        const formData = new FormData()
        formData.append('file', item.raw)
        
        // 上传
        const attachRes = await request.post(`/meetings/${meetingId}/upload`, formData)
        
        // 更新元数据 (display_name 和 sort_order)
        // 只有当显示名改变或需要排序时才调用
        if (item.name !== item.raw.name || i !== 0) {
            await request.put(`/meetings/attachments/${attachRes.id}`, {
                display_name: item.name,
                sort_order: i
            })
        }
    }

    ElMessage.success('会议发起成功')
    dialogVisible.value = false
    
    // 重置状态
    form.value = { title: '', meeting_type_id: null, start_time: null, location: '' }
    fileList.value = []
    
    fetchMeetings()
  } catch (e) {
    console.error(e)
    ElMessage.error('创建失败，请重试')
  } finally {
    submitting.value = false
  }
}

// 无需独立上传按钮处理，统一在 handleAdd 处理
const handleUploadClick = (meeting) => { /* 历史列表的上传逻辑暂保留或移除 */ }
const handleUploadSuccess = () => { fetchMeetings() }
const viewDetails = (row) => { ElMessage.info(`查看详情: ${row.title}`) }

onMounted(async () => {
  await fetchMeetingTypes()
  await fetchMeetings()
})
</script>

<style scoped>
.meeting-manage {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

/* 头部样式调整 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
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
  background-color: #f1f5f9;
}

.header-divider {
  height: 24px;
  border-color: #cbd5e1;
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
  color: #1e293b;
  line-height: 1.2;
}
.page-subtitle {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.4;
}

/* Stats Row */
/* .stats-row 使用默认 row 样式 */
.stat-card {
  border: none;
  background: white;
  border-radius: 12px;
  box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06);
  transition: all 0.2s;
}
.stat-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06);
}
.stat-content {
  display: flex;
  align-items: flex-start;
}
.stat-icon {
  padding: 12px;
  border-radius: 12px;
  margin-right: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
}
.stat-info { flex: 1; }
.stat-label { font-size: 14px; color: var(--color-slate-500); font-weight: 500; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--color-slate-900); margin: 4px 0; }
.stat-desc { font-size: 12px; color: var(--color-slate-400); }

.bg-blue { background-color: #eff6ff; }
.text-blue { color: #3b82f6; }
.bg-green { background-color: #f0fdf4; }
.text-green { color: #22c55e; }
.bg-purple { background-color: #faf5ff; }
.text-purple { color: #a855f7; }
.bg-teal { background-color: #f0fdfa; }
.text-teal { color: #14b8a6; }

/* Dialog Styles */
.meeting-dialog :deep(.el-dialog__body) {
  padding: 0;
}
.dialog-layout {
  display: flex;
  height: 500px;
}
.dialog-left {
  flex: 1;
  padding: 24px;
  border-right: 1px solid #e2e8f0;
  overflow-y: auto;
}
.dialog-right {
  width: 400px;
  background-color: #f8fafc;
  display: flex;
  flex-direction: column;
}
.section-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: white;
}
.section-title {
  margin: 0;
  font-size: 15px;
  font-weight: 600;
  color: #1e293b;
}
.file-list-container {
  flex: 1;
  padding: 16px;
  overflow-y: auto;
}
.empty-state {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
  gap: 12px;
}
.file-item {
  background: white;
  border: 1px solid #e2e8f0;
  border-radius: 8px;
  padding: 12px;
  margin-bottom: 12px;
  display: flex;
  align-items: center;
  gap: 12px;
  transition: all 0.2s;
}
.file-item:hover {
  border-color: #cbd5e1;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05);
}
.file-icon {
  color: #64748b;
  font-size: 20px;
  flex-shrink: 0;
}
.file-content {
  flex: 1;
  min-width: 0;
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.name-input :deep(.el-input__wrapper) {
  box-shadow: none;
  padding: 0;
  background: transparent;
}
.name-input :deep(.el-input__inner) {
  font-weight: 500;
  color: #1e293b;
  height: 24px;
  line-height: 24px;
}
.file-meta {
  font-size: 12px;
  color: #94a3b8;
}
.file-actions {
  display: flex;
  gap: 4px;
  opacity: 0;
  transition: opacity 0.2s;
}
.file-item:hover .file-actions {
  opacity: 1;
}
.dialog-footer {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-top: 8px;
}
.footer-tip {
  font-size: 13px;
  color: #64748b;
}
</style>
