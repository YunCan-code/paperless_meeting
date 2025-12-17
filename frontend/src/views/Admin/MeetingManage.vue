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
    <el-dialog v-model="dialogVisible" title="发起新会议" width="500px" destroy-on-close>
      <el-form :model="form" label-width="80px" label-position="top">
        <el-form-item label="会议主题">
          <el-input v-model="form.title" placeholder="请输入会议主题" />
        </el-form-item>
        <el-form-item label="会议类型">
          <el-select v-model="form.meeting_type_id" placeholder="选择会议类型" style="width: 100%">
            <el-option v-for="item in meetingTypes" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="开始时间">
          <el-date-picker v-model="form.start_time" type="datetime" placeholder="选择会议时间" style="width: 100%" format="YYYY-MM-DD HH:mm" />
        </el-form-item>
        <el-form-item label="会议地点">
          <el-input v-model="form.location" placeholder="输入会议室或地点" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleAdd">确认发起</el-button>
      </template>
    </el-dialog>

    <!-- 上传附件对话框 (隐藏el-upload) -->
    <el-upload
      ref="uploadRef"
      :action="uploadAction"
      :show-file-list="false"
      :on-success="handleUploadSuccess"
      style="display: none;"
    />
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage } from 'element-plus'
import { Calendar, Timer as Clock, User, CircleCheck, Fold, Expand } from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'

const { isCollapse, toggleSidebar } = useSidebar()

import SessionCalendar from './components/SessionCalendar.vue'
import TodayMeetings from './components/TodayMeetings.vue'
import MeetingHistory from './components/MeetingHistory.vue'

const meetings = ref([])
const meetingTypes = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const uploadRef = ref(null)
const uploadAction = ref('')

const form = ref({ title: '', meeting_type_id: null, start_time: '', location: '' })

// 统计数据
const statsData = computed(() => {
  const total = meetings.value.length
  const upcoming = meetings.value.filter(m => m.status === 'scheduled').length
  const active = meetings.value.filter(m => m.status === 'active').length
  const finished = meetings.value.filter(m => m.status === 'finished').length

  return [
    { title: '总会议数', value: total, subtitle: `进行中 ${active} | 计划中 ${upcoming}`, icon: Calendar, textClass: 'text-blue', bgClass: 'bg-blue' },
    { title: '即将开始', value: upcoming, subtitle: '需要准备文件', icon: Clock, textClass: 'text-green', bgClass: 'bg-green' },
    { title: '参会人数', value: '24+', subtitle: '平均参会人数', icon: User, textClass: 'text-purple', bgClass: 'bg-purple' },
    { title: '完成率', value: total > 0 ? `${Math.round(finished / total * 100)}%` : '0%', subtitle: '已结束/总数', icon: CircleCheck, textClass: 'text-teal', bgClass: 'bg-teal' }
  ]
})

const fetchMeetings = async () => {
  loading.value = true
  try { meetings.value = await request.get('/meetings/') } finally { loading.value = false }
}

const fetchMeetingTypes = async () => {
  try { meetingTypes.value = await request.get('/meeting_types/') } catch (e) {}
}

const handleAdd = async () => {
  if (!form.value.title || !form.value.start_time || !form.value.meeting_type_id) return ElMessage.warning('请填写必要信息')
  try {
    await request.post('/meetings/', form.value)
    ElMessage.success('发起成功')
    dialogVisible.value = false
    form.value = { title: '', meeting_type_id: null, start_time: '', location: '' }
    fetchMeetings()
  } catch (e) {}
}

const handleUploadClick = (meeting) => {
  uploadAction.value = `/api/meetings/${meeting.id}/upload`
  // Trigger upload dialog
  uploadRef.value?.$el.querySelector('input[type=file]')?.click()
}

const handleUploadSuccess = () => {
  ElMessage.success('文件上传成功')
  fetchMeetings()
}

const viewDetails = (row) => {
  ElMessage.info(`查看详情: ${row.title}`)
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
</style>
