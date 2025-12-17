<template>
  <el-card class="today-sessions" shadow="never">
    <template #header>
      <div class="card-header">
        <h3>今日会议</h3>
        <el-button type="primary" size="small" @click="$emit('create')">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          新建会议
        </el-button>
      </div>
    </template>

    <div class="session-list" v-if="todayMeetings.length > 0">
      <div 
        v-for="meeting in todayMeetings" 
        :key="meeting.id" 
        class="session-item"
      >
        <div class="session-info">
          <div class="session-header">
            <span class="session-title">{{ meeting.title }}</span>
            <el-tag :type="getStatusType(meeting.status)" size="small" effect="light">
              {{ getStatusText(meeting.status) }}
            </el-tag>
          </div>
          <div class="session-meta">
            <span><el-icon><Clock /></el-icon> {{ formatTime(meeting.start_time) }}</span>
            <span><el-icon><Location /></el-icon> {{ meeting.location || '线上' }}</span>
          </div>
        </div>
        <div class="session-actions">
          <el-button size="small" link type="primary" @click="$emit('view', meeting)">详情</el-button>
        </div>
      </div>
    </div>
    <el-empty v-else description="今日暂无会议" :image-size="80" />
  </el-card>
</template>

<script setup>
import { computed } from 'vue'
import { Plus, Clock, Location } from '@element-plus/icons-vue'

const props = defineProps({
  meetings: {
    type: Array,
    default: () => []
  }
})

defineEmits(['create', 'view'])

const todayMeetings = computed(() => {
  const today = new Date()
  const todayStr = `${today.getFullYear()}-${String(today.getMonth() + 1).padStart(2, '0')}-${String(today.getDate()).padStart(2, '0')}`
  
  return props.meetings.filter(m => {
    if (!m.start_time) return false
    const mDate = new Date(m.start_time)
    const mDateStr = `${mDate.getFullYear()}-${String(mDate.getMonth() + 1).padStart(2, '0')}-${String(mDate.getDate()).padStart(2, '0')}`
    return mDateStr === todayStr
  })
})

const formatTime = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

const getStatusType = (status) => {
  const map = { scheduled: 'info', active: 'success', finished: 'warning' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { scheduled: '计划中', active: '进行中', finished: '已结束' }
  return map[status] || status
}
</script>

<style scoped>
.today-sessions {
  border-radius: 12px;
  border: 1px solid var(--color-slate-200);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.card-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-slate-800);
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}
.session-item {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 12px;
  border: 1px solid var(--color-slate-200);
  border-radius: 8px;
  transition: background 0.2s;
}
.session-item:hover {
  background-color: var(--color-slate-50);
}

.session-info {
  flex: 1;
}
.session-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 4px;
}
.session-title {
  font-weight: 500;
  font-size: 14px;
  color: var(--color-slate-800);
}
.session-meta {
  display: flex;
  gap: 16px;
  font-size: 12px;
  color: var(--color-slate-500);
}
.session-meta span {
  display: flex;
  align-items: center;
  gap: 4px;
}
</style>
