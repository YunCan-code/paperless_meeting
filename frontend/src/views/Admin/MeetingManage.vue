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
      <el-col :xs="12" :sm="12" :md="6" :span="6" v-for="(stat, index) in statsData" :key="index">
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
      <el-col :xs="24" :sm="24" :md="16" :span="16">
        <SessionCalendar 
           :meetings="meetings" 
           @create="openCreate" 
           @select-date="(val) => currentSelectedDate = val"
        />
      </el-col>
      <el-col :xs="24" :sm="24" :md="8" :span="8">
        <TodayMeetings 
           :meetings="meetings" 
           :meeting-types="meetingTypes" 
           :date="currentSelectedDate"
           @create="openCreate" 
           @view="viewDetails" 
        />
      </el-col>
    </el-row>

    <!-- 会议历史列表 -->
    <MeetingHistory 
      :meetings="meetings" 
      :meeting-types="meetingTypes" 
      @view="viewDetails"
      @upload="handleUploadClick"
    />

    <!-- 会议详情对话框 -->
    <el-dialog 
      v-model="detailDialogVisible" 
      title="会议详情" 
      width="800px" 
      destroy-on-close 
      align-center
      class="meeting-dialog detail-mode"
    >
      <div class="dialog-layout" v-if="currentDetail">
        <div class="dialog-left">
          <!-- 标题区域 -->
          <div class="detail-header-section">
             <h3 class="detail-main-title">{{ currentDetail.title }}</h3>
          </div>

          <!-- 信息网格 -->
          <div class="detail-meta-list">
            <div class="meta-card">
              <div class="meta-icon bg-blue-50 text-blue-500"><el-icon><CollectionTag /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议类型</div>
                <div class="meta-value highlight">{{ getTypeName(currentDetail.meeting_type_id) }}</div>
              </div>
            </div>

            <div class="meta-card">
              <div class="meta-icon bg-green-50 text-green-500"><el-icon><Clock /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">开始时间</div>
                <div class="meta-value">{{ new Date(currentDetail.start_time).toLocaleString(undefined, {dateStyle: 'medium', timeStyle: 'short'}) }}</div>
              </div>
            </div>

            <div class="meta-card">
              <div class="meta-icon bg-purple-50 text-purple-500"><el-icon><LocationInformation /></el-icon></div>
              <div class="meta-info">
                <div class="meta-label">会议地点</div>
                <div class="meta-value">{{ currentDetail.location || '线上会议 / 未指定' }}</div>
              </div>
            </div>
          </div>
        </div>
        
        <div class="dialog-right">
          <div class="section-header">
            <h4 class="section-title">会议资料 ({{ currentDetail.attachments?.length || 0 }})</h4>
          </div>
          <div class="file-list-container">
             <div v-if="!currentDetail.attachments || currentDetail.attachments.length === 0" class="empty-state">
              <el-icon size="40" color="#e2e8f0"><FolderOpened /></el-icon>
              <p style="font-size: 13px;">暂无相关资料</p>
            </div>
            <div v-else class="file-list">
              <div v-for="file in currentDetail.attachments" :key="file.id" class="file-item read-only">
                <el-icon class="file-icon"><Document /></el-icon>
                <div class="file-content">
                  <span class="file-name">{{ file.display_name }}</span>
                  <span class="file-meta">{{ formatSize(file.file_size) }}</span>
                </div>
                <!-- 预留下载按钮 -->
                <el-button link type="primary" size="small" @click="downloadFile(file)"><el-icon><Download /></el-icon></el-button>
              </div>
            </div>
          </div>
        </div>
      </div>
      
      <template #footer>
        <div class="dialog-footer detail-footer">
           <div class="footer-left"></div> <!-- Spacer -->
           <div class="footer-actions">
              <el-button @click="handleDeleteMeeting" type="danger" plain>删除会议</el-button>
              <el-button @click="openEdit" type="primary">编辑会议</el-button>
           </div>
        </div>
      </template>
    </el-dialog>

    <!-- 创建/编辑会议对话框 -->
    <el-dialog 
      v-model="dialogVisible" 
      :title="isEditMode ? '编辑会议' : '发起新会议'" 
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
              multiple
            >
              <el-button type="primary" link size="small"><el-icon class="el-icon--left"><Plus/></el-icon>添加文件</el-button>
            </el-upload>
          </div>
          
          <div class="file-list-container">
            <div v-if="attachmentList.length === 0" class="empty-state">
              <el-icon :size="48" color="#e2e8f0"><UploadFilled /></el-icon>
              <p>暂无文件，点击上方按钮添加</p>
            </div>
            
            <div v-else class="file-list">
              <div v-for="(file, index) in attachmentList" :key="file.id" class="file-item">
                <div class="file-icon">
                  <el-icon><Document /></el-icon>
                </div>
                <div class="file-content">
                  <div style="display: flex; align-items: center; gap: 8px;">
                     <el-input v-model="file.name" size="small" class="name-input" placeholder="文件名" />
                     <el-tag v-if="file.type === 'new'" size="small" type="danger" effect="plain" round>NEW</el-tag>
                  </div>
                  <span class="file-meta">{{ formatSize(file.size) }}</span>
                </div>
                <div class="file-actions">
                  <el-tooltip content="上移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, -1)" :disabled="index === 0">
                      <el-icon><Top /></el-icon>
                    </el-button>
                  </el-tooltip>
                  <el-tooltip content="下移" placement="top" :show-after="500">
                    <el-button circle size="small" @click="moveFile(index, 1)" :disabled="index === attachmentList.length - 1">
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
            <span v-if="attachmentList.length > 0">共 {{ attachmentList.length }} 个文件</span>
          </div>
          <div class="footer-btns">
            <el-button @click="dialogVisible = false">取消</el-button>
            <el-button type="primary" @click="handleSubmit" :loading="submitting">
              {{ isEditMode ? '保存修改' : '确认发起' }}
            </el-button>
          </div>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed, reactive } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { 
  Calendar, Timer as Clock, User, CircleCheck, Fold, Expand, 
  Document, UploadFilled, Top, Bottom, Delete, Edit, Plus,
  CollectionTag, LocationInformation, FolderOpened, Download
} from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import SessionCalendar from './components/SessionCalendar.vue'
import TodayMeetings from './components/TodayMeetings.vue'
import MeetingHistory from './components/MeetingHistory.vue'

const { isCollapse, toggleSidebar } = useSidebar()

const meetings = ref([])
const meetingTypes = ref([])
const loading = ref(false)

// Dialogs
const dialogVisible = ref(false)
const detailDialogVisible = ref(false)
const currentDetail = ref(null)

// Forms & States
const submitting = ref(false)
const isEditMode = ref(false)
const editingId = ref(null)

const form = ref({ 
  title: '', 
  meeting_type_id: null, 
  start_time: null, 
  location: '' 
})

const currentSelectedDate = ref(new Date())

// Stats
const statsData = computed(() => {
  const total = meetings.value.length || 0
  
  // Card 1: 本周会议总数 (Weekly Meeting Count)
  const now = new Date()
  const dayOfWeek = now.getDay() || 7 // 1-7
  const startOfWeek = new Date(now)
  startOfWeek.setDate(now.getDate() - dayOfWeek + 1)
  startOfWeek.setHours(0,0,0,0)
  
  const weeklyCount = meetings.value.filter(m => {
      if(!m.start_time) return false
      const d = new Date(m.start_time)
      return d >= startOfWeek
  }).length
  
  const future = meetings.value.filter(m => m.start_time && new Date(m.start_time) > now).length

  // Card 3: 参会人次 (Mocked, unchanged from previous thought process/user intent? 
  // Wait, user said "Third card unchanged". 
  // Original 3rd card was "File Storage" or "Meeting Rooms"? 
  // Let's look at previous file content. 
  // Original 3rd card was "File Storage".
  // Original 4th card was "Meeting Rooms".
  
  // Calculate file storage
  let totalBytes = 0
  meetings.value.forEach(m => {
     if(m.attachments) {
        m.attachments.forEach(a => totalBytes += (a.file_size || 0))
     }
  })
  const formatBytesSimple = (bytes) => {
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }
  const storageStr = totalBytes > 0 ? formatBytesSimple(totalBytes) : '0 MB'

  const onlineUsers = 12

  return [
    { 
      title: '本周会议总数', 
      value: weeklyCount, 
      subtitle: '本周新增', 
      icon: 'CollectionTag', 
      bgClass: 'bg-blue-50', 
      textClass: 'text-blue-500' 
    },
    { 
      title: '即将开始', 
      value: future, 
      subtitle: '近期日程', 
      icon: 'Timer', 
      bgClass: 'bg-orange-50', 
      textClass: 'text-orange-500' 
    },
    { 
      title: '文件存储', 
      value: storageStr, 
      subtitle: '已用空间', 
      icon: 'FolderOpened', 
      bgClass: 'bg-green-50', 
      textClass: 'text-green-500' 
    },
    { 
      title: '用户在线数', 
      value: onlineUsers, 
      subtitle: '实时在线', 
      icon: 'User', 
      bgClass: 'bg-purple-50', 
      textClass: 'text-purple-500' 
    }
  ]
})


const getTypeName = (id) => meetingTypes.value.find(t => t.id === id)?.name || id
const formatSize = (bytes) => {
  if (bytes === 0) return '0 B'
  const k = 1024
  const sizes = ['B', 'KB', 'MB', 'GB']
  const i = Math.floor(Math.log(bytes) / Math.log(k))
  return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

// Fetch
const fetchMeetings = async () => {
  loading.value = true
  try { meetings.value = await request.get('/meetings/') } finally { loading.value = false }
}
const fetchMeetingTypes = async () => {
  try { meetingTypes.value = await request.get('/meeting_types/') } catch (e) {}
}
const handleUploadClick = (meeting) => {}
const handleUploadSuccess = () => { fetchMeetings() }

// Details
const viewDetails = async (row) => {
  try {
    const res = await request.get(`/meetings/${row.id}`)
    currentDetail.value = res
    // Sort attachments by sort_order
    if (currentDetail.value.attachments) {
       currentDetail.value.attachments.sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
    }
    detailDialogVisible.value = true
  } catch (e) { ElMessage.error('获取详情失败') }
}

// Unified File List
 // Structure: { id, name, size, type: 'existing'|'new', raw?: File, existingId?: int }
const attachmentList = ref([])

// File Actions
const handleFileSelect = (uploadFile) => {
  const raw = uploadFile.raw
  // Check dupes by name maybe? Allow same name? User usually prefers unique names but let's allow duplicates to be safe or warn.
  // Warning only if exact same file added? 
  // User just wants "new files below existing".
  attachmentList.value.push({ 
      id: Date.now() + Math.random(), 
      name: raw.name, 
      size: raw.size, 
      type: 'new', 
      raw: raw 
  })
}

const removeFile = async (index) => {
  const item = attachmentList.value[index]
  if (item.type === 'existing') {
     try {
        await ElMessageBox.confirm('确定删除该附件吗？', '提示')
        await request.delete(`/meetings/attachments/${item.existingId}`)
        attachmentList.value.splice(index, 1)
        ElMessage.success('附件已删除')
     } catch (e) {}
  } else {
     attachmentList.value.splice(index, 1)
  }
}

const moveFile = (index, direction) => {
  if (direction === -1 && index === 0) return
  if (direction === 1 && index === attachmentList.value.length - 1) return
  const temp = attachmentList.value[index]
  attachmentList.value[index] = attachmentList.value[index + direction]
  attachmentList.value[index + direction] = temp
}

// Actions
const openCreate = () => {
  isEditMode.value = false
  editingId.value = null
  const defaultLoc = localStorage.getItem('defaultMeetingLocation') || ''
  form.value = { title: '', meeting_type_id: null, start_time: null, location: defaultLoc }
  attachmentList.value = []
  dialogVisible.value = true
}
const downloadFile = (file) => {
  if (!file || !file.filename) return
  const url = `http://127.0.0.1:8000/static/${file.filename}`
  window.open(url, '_blank')
}
const openEdit = () => {
  if (!currentDetail.value) return
  const m = currentDetail.value
  form.value = { title: m.title, meeting_type_id: m.meeting_type_id, start_time: m.start_time, location: m.location }
  editingId.value = m.id
  isEditMode.value = true
  
  // Populate attachmentList from existing. Sort by sort_order
  const sorted = [...(m.attachments || [])].sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
  attachmentList.value = sorted.map(a => ({
     id: Date.now() + Math.random(), // temp id for list key
     existingId: a.id,
     name: a.display_name,
     size: a.file_size,
     type: 'existing'
  }))
  
  detailDialogVisible.value = false
  dialogVisible.value = true
}

const handleDeleteMeeting = async () => {
  if (!currentDetail.value) return
  try {
    await ElMessageBox.confirm('确定要删除该会议吗？', '警告', { type: 'warning' })
    await request.delete(`/meetings/${currentDetail.value.id}`)
    ElMessage.success('已删除')
    detailDialogVisible.value = false
    fetchMeetings()
  } catch (e) {}
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.start_time || !form.value.meeting_type_id) return ElMessage.warning('请填写完整')
  submitting.value = true
  try {
    let meetingId = editingId.value
    if (isEditMode.value) {
      await request.put(`/meetings/${meetingId}`, form.value)
    } else {
      const res = await request.post('/meetings/', form.value)
      meetingId = res.id
    }
    
    // Process attachments
    // 1. Upload new files
    // 2. Update all files with new sort_order and name
    
    // We do sequential processing to keep logic simple
    for (let i = 0; i < attachmentList.value.length; i++) {
        const item = attachmentList.value[i]
        
        if (item.type === 'new') {
            const formData = new FormData()
            formData.append('file', item.raw)
            const attachRes = await request.post(`/meetings/${meetingId}/upload`, formData)
            // Update name and sort_order
            await request.put(`/meetings/attachments/${attachRes.id}`, { 
                display_name: item.name, 
                sort_order: i 
            })
        } else if (item.type === 'existing') {
            // Always update sort_order and name
            await request.put(`/meetings/attachments/${item.existingId}`, { 
                display_name: item.name, 
                sort_order: i 
            })
        }
    }
    
    ElMessage.success(isEditMode.value ? '更新成功' : '发起成功')
    dialogVisible.value = false
    fetchMeetings()
  } catch (e) { 
      console.error(e)
      ElMessage.error('操作失败') 
  } finally { submitting.value = false }
}

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

/* 详情样式优化 */
.detail-header-section { margin-bottom: 24px; }
.detail-main-title { margin: 0 0 8px 0; font-size: 22px; font-weight: 700; color: #0f172a; line-height: 1.3; }
.detail-meta-list { display: flex; flex-direction: column; gap: 12px; }
.meta-card { display: flex; align-items: center; padding: 12px 16px; background-color: #ffffff; border: 1px solid #f1f5f9; border-radius: 12px; transition: all 0.2s; }
.meta-card:hover { border-color: #e2e8f0; background-color: #f8fafc; }
.meta-icon { width: 40px; height: 40px; border-radius: 10px; display: flex; align-items: center; justify-content: center; font-size: 20px; margin-right: 16px; flex-shrink: 0; }
.meta-info { flex: 1; }
.meta-label { font-size: 13px; color: #94a3b8; margin-bottom: 2px; }
.meta-value { font-size: 15px; color: #334155; font-weight: 600; }
.detail-footer { display: flex; justify-content: flex-end; gap: 12px; width: 100%; }

/* Colors Utility */
.bg-blue-50 { background-color: #eff6ff; } .text-blue-500 { color: #3b82f6; }
.bg-green-50 { background-color: #f0fdf4; } .text-green-500 { color: #22c55e; }
.bg-purple-50 { background-color: #faf5ff; } .text-purple-500 { color: #a855f7; }
.bg-orange-50 { background-color: #fff7ed; } .text-orange-500 { color: #f97316; }

/* Read Only File List */
.read-only { cursor: default; border-style: dashed; }

/* 头部样式调整 */
.page-header { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 4px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main); }
.header-divider { height: 24px; border-color: var(--border-color); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-main); line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 14px; line-height: 1.4; }

/* Stats Row */
.stat-card { border: none; background: var(--card-bg); border-radius: 12px; box-shadow: 0 1px 3px 0 rgba(0, 0, 0, 0.1), 0 1px 2px 0 rgba(0, 0, 0, 0.06); transition: all 0.2s; }
.stat-card:hover { transform: translateY(-2px); box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.1), 0 2px 4px -1px rgba(0, 0, 0, 0.06); }
.stat-content { display: flex; align-items: flex-start; }
.stat-icon { padding: 12px; border-radius: 12px; margin-right: 16px; display: flex; align-items: center; justify-content: center; }
.stat-info { flex: 1; }
.stat-label { font-size: 14px; color: var(--text-secondary); font-weight: 500; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-main); margin: 4px 0; }
.stat-desc { font-size: 12px; color: var(--text-secondary); }

/* Dialog Styles */
.meeting-dialog :deep(.el-dialog__body) { padding: 0; }
.dialog-layout { display: flex; height: 500px; }
.dialog-left { flex: 1; padding: 24px; border-right: 1px solid var(--border-color); overflow-y: auto; }
.dialog-right { width: 400px; background-color: var(--bg-main); display: flex; flex-direction: column; }
.section-header { padding: 16px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; background: var(--card-bg); }
.section-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-main); }
.file-list-container { flex: 1; padding: 16px; overflow-y: auto; }
.empty-state { height: 100%; display: flex; flex-direction: column; align-items: center; justify-content: center; color: var(--text-secondary); gap: 12px; }
.file-item { background: var(--card-bg); border: 1px solid var(--border-color); border-radius: 8px; padding: 12px; margin-bottom: 12px; display: flex; align-items: center; gap: 12px; transition: all 0.2s; }
.file-item:hover { border-color: var(--color-slate-400); box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05); }
.file-icon { color: var(--text-secondary); font-size: 20px; flex-shrink: 0; }
.file-content { flex: 1; min-width: 0; display: flex; flex-direction: column; gap: 4px; }
.name-input :deep(.el-input__wrapper) { box-shadow: none; padding: 0; background: transparent; }
.name-input :deep(.el-input__inner) { font-weight: 500; color: var(--text-main); height: 24px; line-height: 24px; }
.file-meta { font-size: 12px; color: var(--text-secondary); }
.file-actions { display: flex; gap: 4px; opacity: 0; transition: opacity 0.2s; }
.file-item:hover .file-actions { opacity: 1; }
.dialog-footer { display: flex; justify-content: space-between; align-items: center; padding-top: 8px; }
.footer-tip { font-size: 13px; color: var(--text-secondary); }
</style>
