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
        <el-card shadow="hover" class="stat-card" :class="{ 'clickable': stat.clickable }" @click="stat.onClick && stat.onClick()">
          <div class="stat-content">
            <div class="stat-icon" :class="stat.bgClass">
              <el-icon :class="stat.textClass" :size="24">
                <component :is="stat.icon" />
              </el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ stat.title }}</div>
              <div class="stat-value">
                {{ stat.value }}
                <span class="stat-trend" :class="stat.trend >= 0 ? 'up' : 'down'" v-if="stat.trend !== undefined">
                  <el-icon><component :is="stat.trend >= 0 ? 'Top' : 'Bottom'" /></el-icon>
                  {{ Math.abs(stat.trend) }}%
                </span>
              </div>
              <!-- <div class="stat-desc">{{ stat.subtitle }}</div> -->
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

    <!-- 文件列表抽屉 -->
    <el-drawer v-model="filesDrawerVisible" title="会议文件列表" size="500px">
      <div class="files-drawer-content">
        <div v-if="allFilesList.length === 0" class="empty-state" style="padding: 40px;">
          <el-icon :size="48" color="#e2e8f0"><FolderOpened /></el-icon>
          <p>暂无已上传的会议文件</p>
        </div>
        <div v-else class="files-list">
          <div v-for="file in allFilesList" :key="file.id" class="file-card">
            <div class="file-card-left">
              <div class="file-icon-box">
                <el-icon><Document /></el-icon>
              </div>
              <div class="file-info">
                <div class="file-name">{{ file.display_name }}</div>
                <div class="file-meeting">{{ file.meetingTitle }}</div>
                <div class="file-date">{{ new Date(file.meetingDate).toLocaleDateString() }} · {{ formatSize(file.file_size) }}</div>
              </div>
            </div>
            <el-button link type="primary" @click="downloadFile(file)">
              <el-icon><Download /></el-icon>
            </el-button>
          </div>
        </div>
      </div>
      <template #footer>
        <div style="color: #94a3b8; font-size: 13px;">共 {{ allFilesList.length }} 个文件</div>
      </template>
    </el-drawer>

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

            <div class="meta-card" v-if="currentDetail.speaker">
               <div class="meta-icon bg-orange-50 text-orange-500"><el-icon><User /></el-icon></div>
               <div class="meta-info">
                 <div class="meta-label">主讲人</div>
                 <div class="meta-value">{{ currentDetail.speaker }}</div>
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
          
          <!-- 议程展示 -->
          <div class="detail-agenda-section" v-if="currentDetail.agenda && currentDetail.agenda !== '[]'">
             <h4 class="section-title" style="margin: 20px 0 12px 0;">会议议程</h4>
             <div class="agenda-list">
                <div v-for="(item, idx) in parseAgenda(currentDetail.agenda)" :key="idx" class="agenda-item">
                   <span class="agenda-time">{{ item.time }}</span>
                   <span class="agenda-content">{{ item.content }}</span>
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
      width="1000px" 
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
            
            <el-form-item label="主讲人" prop="speaker">
              <el-select 
                 v-model="form.speaker" 
                 placeholder="请选择主讲人" 
                 filterable 
                 allow-create 
                 default-first-option
                 style="width: 100%"
              >
                 <el-option
                    v-for="item in speakerOptions"
                    :key="item.value"
                    :label="item.label"
                    :value="item.value"
                 />
              </el-select>
            </el-form-item>
    
            <div style="margin-bottom: 12px; font-weight: 600; color: #334155;">会议议程</div>
            <div v-for="(item, index) in form.agendaItems" :key="index" style="display: flex; gap: 8px; margin-bottom: 8px; align-items: flex-start;">
                <el-time-picker
                   v-model="item.timeObj"
                   format="HH:mm"
                   placeholder="时间"
                   style="width: 120px; flex-shrink: 0;"
                   :clearable="false"
                   @change="(val) => handleTimeChange(val, item)"
                />
                <el-input v-model="item.content" placeholder="议程内容" style="flex: 1;" />
                <el-button type="danger" link @click="removeAgendaItem(index)">
                   <el-icon><Delete /></el-icon>
                </el-button>
            </div>
            <el-button type="primary" link @click="addAgendaItem" style="margin-bottom: 18px;">
               <el-icon><Plus /></el-icon> 添加议程项
            </el-button>
            
            <el-form-item label="会议地点" prop="location">
               <el-input v-model="form.location" placeholder="请输入会议地点" />
            </el-form-item>
          </el-form>
        </div>
      
        <!-- 右侧：文件管理 -->
        <div class="dialog-right">
          <div class="section-header">
            <div>
              <h4 class="section-title">会议资料</h4>
              <div class="section-tip">仅支持上传 PDF 格式文件（安卓端仅支持 PDF 预览）</div>
            </div>
            <el-upload
              action=""
              :auto-upload="false"
              :show-file-list="false"
              :on-change="handleFileSelect"
              accept="application/pdf"
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
  CollectionTag, LocationInformation, FolderOpened, Download, DataAnalysis
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

// Files Drawer
const filesDrawerVisible = ref(false)
const allFilesList = computed(() => {
    const files = []
    meetings.value.forEach(m => {
        if (m.attachments && m.attachments.length > 0) {
            m.attachments.forEach(a => {
                files.push({
                    ...a,
                    meetingTitle: m.title,
                    meetingDate: m.start_time
                })
            })
        }
    })
    // Sort by date descending
    files.sort((a, b) => new Date(b.meetingDate) - new Date(a.meetingDate))
    return files
})

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

// Stats State
const stats = ref({
  yearly_count: 0,
  yearly_growth: 0,
  monthly_count: 0,
  monthly_growth: 0,
  weekly_count: 0,
  weekly_growth: 0,
  total_storage_bytes: 0,
  storage_growth: 0
})

const fetchStats = async () => {
  try {
    const res = await request.get('/meetings/stats')
    if (res) {
      stats.value = res
    }
  } catch(e) { 
    console.error('Failed to fetch stats', e)
  }
}

onMounted(async () => {
  await fetchMeetingTypes()
  await fetchMeetings()
  fetchStats()
})



const statsData = computed(() => {
  if (!stats.value) return []
  
  const formatBytesSimple = (bytes) => {
      const k = 1024
      const sizes = ['B', 'KB', 'MB', 'GB']
      if (bytes === 0) return '0 B'
      const i = Math.floor(Math.log(bytes) / Math.log(k))
      return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
  }
  const storageStr = formatBytesSimple(stats.value.total_storage_bytes || 0)

  return [
    { 
      title: '本年会议数', 
      value: stats.value.yearly_count, 
      trend: stats.value.yearly_growth || 0,
      icon: 'DataAnalysis', 
      bgClass: 'bg-purple-50', 
      textClass: 'text-purple-500' 
    },
    { 
      title: '本月会议数', 
      value: stats.value.monthly_count, 
      trend: stats.value.monthly_growth || 0,
      icon: 'Calendar', 
      bgClass: 'bg-blue-50', 
      textClass: 'text-blue-500' 
    },
    { 
      title: '本周会议数', 
      value: stats.value.weekly_count, 
      trend: stats.value.weekly_growth || 0,
      icon: 'CollectionTag', 
      bgClass: 'bg-orange-50', 
      textClass: 'text-orange-500' 
    },
    { 
      title: '文件存储', 
      value: storageStr, 
      trend: stats.value.storage_growth || 0,
      icon: 'FolderOpened', 
      bgClass: 'bg-green-50', 
      textClass: 'text-green-500',
      clickable: true,
      onClick: () => { filesDrawerVisible.value = true }
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
  try { meetings.value = await request.get('/meetings/', { params: { force_show_all: true } }) } finally { loading.value = false }
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



// Speakers
const speakerOptions = ref([])
const fetchSpeakers = async () => {
   try {
      const res = await request.get('/users/', { params: { page: 1, page_size: 100 } })
      const users = res.items || []
      speakerOptions.value = users.map(u => ({
          label: u.name,
          value: u.name
      }))
      speakerOptions.value = users.map(u => ({
          label: u.name,
          value: u.name
      }))
   } catch(e) {}
}

const parseAgenda = (jsonStr) => {
    try {
        return JSON.parse(jsonStr || '[]')
    } catch(e) { return [] }
}

// Agenda
const addAgendaItem = () => {
    form.value.agendaItems.push({ timeObj: new Date().setHours(9,0,0,0), timeStr: '09:00', content: '' })
}
const removeAgendaItem = (index) => {
    form.value.agendaItems.splice(index, 1)
}
const handleTimeChange = (val, item) => {
    if(!val) return
    const hours = val.getHours().toString().padStart(2, '0')
    const minutes = val.getMinutes().toString().padStart(2, '0')
    item.timeStr = `${hours}:${minutes}`
}

// Actions
const openCreate = () => {
  if(speakerOptions.value.length === 0) fetchSpeakers()
  isEditMode.value = false
  editingId.value = null
  const defaultLoc = localStorage.getItem('defaultMeetingLocation') || ''
  
  const defaultAgenda = []

  form.value = { title: '', meeting_type_id: null, start_time: null, location: defaultLoc, speaker: '', agendaItems: defaultAgenda }
  attachmentList.value = []
  dialogVisible.value = true
}

const openEdit = () => {
  if (!currentDetail.value) return
  const m = currentDetail.value
  
  let agendaItems = []
  try {
      const parsed = JSON.parse(m.agenda || '[]')
      agendaItems = parsed.map(p => {
          const [h, min] = (p.time || '09:00').split(':')
          const d = new Date()
          d.setHours(parseInt(h), parseInt(min), 0)
          return { timeObj: d, timeStr: p.time, content: p.content } 
      })
  } catch(e) {}
  
  // if(agendaItems.length === 0) agendaItems.push({ timeObj: new Date().setHours(9,0,0), timeStr: '09:00', content: '' })
  
  if(speakerOptions.value.length === 0) fetchSpeakers()

  form.value = { 
      title: m.title, 
      meeting_type_id: m.meeting_type_id, 
      start_time: m.start_time, 
      location: m.location,
      speaker: m.speaker,
      agendaItems: agendaItems
  }
  editingId.value = m.id
  isEditMode.value = true
  
  const sorted = [...(m.attachments || [])].sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
  attachmentList.value = sorted.map(a => ({
     id: Date.now() + Math.random(),
     existingId: a.id,
     name: a.display_name,
     size: a.file_size,
     type: 'existing'
  }))
  
  detailDialogVisible.value = false
  dialogVisible.value = true
}

const handleSubmit = async () => {
  if (!form.value.title || !form.value.start_time || !form.value.meeting_type_id) return ElMessage.warning('请填写完整')
  
  // Serialize Agenda
  const agendaJson = JSON.stringify((form.value.agendaItems || []).map(i => ({
      time: i.timeStr || '09:00',
      content: i.content
  })))

  const payload = {
    ...form.value,
    agenda: agendaJson,
  }
  delete payload.agendaItems

  try {
    submitting.value = true
    if (isEditMode.value) {
       await request.put(`/meetings/${editingId.value}`, payload)
       ElMessage.success('更新成功')
    } else {
       const res = await request.post('/meetings/', payload)
       editingId.value = res.id
    }
    dialogVisible.value = false
    fetchMeetings()
    
    // Process new files...
    // Process new files...
    const newFiles = attachmentList.value.filter(f => f.type === 'new')
    for (const f of newFiles) {
        try {
            const formData = new FormData()
            formData.append('file', f.raw)
            await request.post(`/meetings/${editingId.value}/upload`, formData)
        } catch(e) {
            console.error('Upload failed for file', f.name)
        }
    }
    if(newFiles.length > 0) ElMessage.success('附件上传完成')
  } catch (e) {
      ElMessage.error('保存失败')
  } finally {
      submitting.value = false
  }
}
const downloadFile = (file) => {
  if (!file || !file.filename) return
  if (!file || !file.filename) return
  const url = import.meta.env.PROD 
      ? `https://coso.top/static/${file.filename}` 
      : `/static/${file.filename}` // Local development uses proxy or relative path
  // Ideally, use relative path if Nginx is configured correctly.
  // But since user's VPS setup involves complex Nginx layers, let's use the explicit public domain to be safe, 
  // or better, use relative path `/static/...` which works if hosted correcty.
  // Providing the simplest fix: relative URL.
  // const url = `/static/${file.filename}`
  // WAIT, Nginx serves /static/ -> backend/static/. 
  // So accessing https://coso.top/static/file.pdf should work.
  // So relative path `/static/${file.filename}` is correct.
  const downloadUrl = `/static/${file.filename}`
  window.open(downloadUrl, '_blank')
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
.stat-value { 
    font-size: 24px; font-weight: 700; color: var(--text-main); margin: 4px 0; 
    display: flex; align-items: flex-end; gap: 8px; /* Added flex for trend alignment */
}
.stat-trend { font-size: 13px; font-weight: 600; display: flex; align-items: center; margin-bottom: 3px; }
.stat-trend.up { color: #10b981; } .stat-trend.down { color: #ef4444; }
.stat-desc { font-size: 12px; color: var(--text-secondary); }

/* Dialog Styles */
.meeting-dialog :deep(.el-dialog__body) { padding: 0; }
.dialog-layout { display: flex; height: 500px; }
.dialog-left { flex: 1; padding: 24px; border-right: 1px solid var(--border-color); overflow-y: auto; }
.dialog-right { width: 400px; background-color: var(--bg-main); display: flex; flex-direction: column; }
.section-header { padding: 16px 20px; border-bottom: 1px solid var(--border-color); display: flex; justify-content: space-between; align-items: center; background: var(--card-bg); }
.section-title { margin: 0; font-size: 15px; font-weight: 600; color: var(--text-main); }
.section-tip { font-size: 12px; color: #f59e0b; margin-top: 4px; }
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

/* Agenda Styles */
.agenda-list { display: flex; flex-direction: column; gap: 8px; }
.agenda-item { display: flex; align-items: flex-start; gap: 12px; font-size: 14px; }
.agenda-time { font-family: monospace; font-weight: 600; color: var(--color-primary); background: var(--bg-main); padding: 2px 6px; border-radius: 4px; }
.agenda-content { color: var(--text-main); line-height: 1.5; }

/* Clickable Stat Card */
.stat-card.clickable { cursor: pointer; }

/* Files Drawer */
.files-drawer-content { height: 100%; overflow-y: auto; }
.files-list { display: flex; flex-direction: column; gap: 12px; }
.file-card {
    display: flex; justify-content: space-between; align-items: center;
    padding: 16px; background: var(--card-bg);
    border: 1px solid var(--border-color); border-radius: 12px;
    transition: all 0.2s;
}
.file-card:hover { border-color: var(--color-slate-400); box-shadow: 0 2px 8px rgba(0,0,0,0.06); }
.file-card-left { display: flex; align-items: center; gap: 14px; flex: 1; min-width: 0; }
.file-icon-box {
    width: 44px; height: 44px; border-radius: 10px;
    background: #f0fdf4; color: #22c55e;
    display: flex; align-items: center; justify-content: center;
    font-size: 20px; flex-shrink: 0;
}
.file-info { flex: 1; min-width: 0; }
.file-name { font-weight: 600; color: var(--text-main); font-size: 14px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-meeting { font-size: 13px; color: var(--text-secondary); margin-top: 2px; white-space: nowrap; overflow: hidden; text-overflow: ellipsis; }
.file-date { font-size: 12px; color: #94a3b8; margin-top: 4px; }

/* Dark Mode Overrides for Meeting Detail */
html.dark .meta-card {
    background-color: #2d3748;
    border-color: #4a5568;
}
html.dark .meta-card:hover {
    background-color: #374151;
}
html.dark .detail-main-title {
    color: #f1f5f9;
}
html.dark .meta-label {
    color: #a0aec0;
}
html.dark .meta-value {
    color: #e2e8f0;
}
</style>
