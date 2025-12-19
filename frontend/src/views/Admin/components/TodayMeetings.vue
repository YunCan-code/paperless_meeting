<template>
  <el-card class="today-sessions" shadow="hover">
    <template #header>
      <div class="card-header">
        <div class="header-title">
          <h3>{{ titleText }}</h3>
          <span class="header-subtitle">{{ titleDate }}</span>
        </div>
      </div>
    </template>

    <div class="session-list" v-if="filterMeetings.length > 0">
      <div 
        v-for="meeting in filterMeetings" 
        :key="meeting.id" 
        class="session-item"
        @click="$emit('view', meeting)"
      >
        <div class="session-left-bar"></div>
        <div class="session-content">
           <div class="session-top">
              <span class="session-time">{{ formatTime(meeting.start_time) }}</span>
              <el-tag 
                size="small" 
                class="type-tag"
                :style="{ 
                   color: getTypeColor(meeting.meeting_type_id),
                   backgroundColor: getTypeColor(meeting.meeting_type_id) + '1a' 
                }"
              >
                {{ getTypeName(meeting.meeting_type_id) }}
              </el-tag>
           </div>
           <div class="session-title">{{ meeting.title }}</div>
           <div class="session-footer">
             <!-- Location shown only if specific location exists, otherwise hidden as requested -->
             <span v-if="meeting.location && meeting.location !== '线上'" class="location-text">
               <el-icon><Location /></el-icon> {{ meeting.location }}
             </span>
           </div>
        </div>
        <div class="session-arrow">
          <el-icon><ArrowRight /></el-icon>
        </div>
      </div>
    </div>
    <el-empty v-else description="今日暂无会议" :image-size="60" />
  </el-card>
</template>

<script setup>
import { computed, inject } from 'vue'
import { Clock, Location, ArrowRight } from '@element-plus/icons-vue'

const props = defineProps({
  meetings: {
    type: Array,
    default: () => []
  },
  // Inject or pass meeting types if needed for name resolution, 
  // currently we might need to rely on parent or just show ID if not passed. 
  // Let's assume we can get types or parent passes it. 
  // Actually the original code didn't show type name, it just showed status. 
  // User requested "Meeting Type Info". We typically need meetingTypes prop.
  // I'll check if parent passes it, if not I'll use a placeholder or try to get it.
  date: {
      type: Date,
      default: () => new Date()
  },
  meetingTypes: {
     type: Array,
     default: () => []
  }
})

defineEmits(['create', 'view'])

const today = new Date()
const isSameDay = (d1, d2) => {
    return d1.getFullYear() === d2.getFullYear() && 
           d1.getMonth() === d2.getMonth() && 
           d1.getDate() === d2.getDate()
}

const titleDate = computed(() => {
    const d = props.date || new Date()
    return `${d.getFullYear()}年${d.getMonth() + 1}月${d.getDate()}日`
})

const titleText = computed(() => {
    if (isSameDay(props.date || new Date(), today)) return '今日会议'
    return '当日会议'
})

const filterMeetings = computed(() => {
  const targetDate = props.date || new Date()
  const targetStr = targetDate.toDateString()
  if (!props.meetings) return []
  return props.meetings.filter(m => {
    if (!m.start_time) return false
    return new Date(m.start_time).toDateString() === targetStr
  }).sort((a, b) => new Date(a.start_time) - new Date(b.start_time))
})

const formatTime = (iso) => {
  if (!iso) return ''
  const d = new Date(iso)
  return `${String(d.getHours()).padStart(2, '0')}:${String(d.getMinutes()).padStart(2, '0')}`
}

// Helpers
const colors = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#06b6d4', '#ec4899', '#84cc16', '#6366f1', '#14b8a6']

const getTypeName = (id) => {
  if (!props.meetingTypes || props.meetingTypes.length === 0) return '会议' 
  const found = props.meetingTypes.find(t => t.id === id)
  return found ? found.name : '会议'
}

const getTypeColor = (id) => {
   if (!props.meetingTypes) return '#3b82f6'
   const index = props.meetingTypes.findIndex(t => t.id === id)
   if (index === -1) return '#3b82f6'
   return colors[index % colors.length]
}
</script>

<style scoped>
.today-sessions {
  border-radius: 16px;
  border: none;
  background: var(--card-bg); /* Simplified from gradient to solid card background for consistency */
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding-bottom: 4px;
}
.header-title h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: var(--text-main);
}
.header-subtitle {
  font-size: 12px;
  color: var(--text-secondary);
  margin-top: 2px;
  display: block;
}

.session-list {
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.session-item {
  position: relative;
  display: flex;
  align-items: center;
  padding: 16px;
  background: var(--bg-main); /* Slightly distinct from card bg */
  border-radius: 12px;
  border: 1px solid var(--border-color);
  cursor: pointer;
  transition: all 0.3s ease;
  overflow: hidden;
}

.session-item:hover {
  transform: translateY(-2px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.05);
  border-color: var(--color-slate-400);
}

.session-left-bar {
  position: absolute;
  left: 0;
  top: 0;
  bottom: 0;
  width: 4px;
  background: var(--color-primary);
  opacity: 0.8;
}

.session-content {
  flex: 1;
  padding-left: 12px;
}

.session-top {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
}

.session-time {
  font-size: 20px;
  font-weight: 800;
  color: var(--color-primary);
  line-height: 1;
  font-family: 'Segoe UI', Roboto, Helvetica, Arial, sans-serif;
}

.type-tag {
  border: none;
  /* background/color are inline styles handled by JS, but we should check if they need opacity adjustment for dark mode. 
     The JS uses simple opacity '1a' which works on dark too. */
  font-weight: 600;
}

.session-title {
  font-size: 15px;
  font-weight: 600;
  color: var(--text-main);
  margin-bottom: 4px;
  line-height: 1.4;
}

.session-footer {
  display: flex;
  align-items: center;
}

.location-text {
  font-size: 12px;
  color: var(--text-secondary);
  display: flex;
  align-items: center;
  gap: 4px;
}

.session-arrow {
  color: var(--text-secondary);
  transition: transform 0.2s;
}

.session-item:hover .session-arrow {
  transform: translateX(4px);
  color: var(--text-main);
}
</style>
