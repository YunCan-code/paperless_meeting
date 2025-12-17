<template>
  <el-card class="session-calendar" shadow="never">
    <template #header>
      <div class="calendar-header">
        <h3 class="calendar-title">日历</h3>
        <el-button type="primary" size="small" @click="$emit('create')">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          新建会议
        </el-button>
      </div>
      <div class="calendar-controls">
        <span class="current-month">{{ currentYear }}年 {{ currentMonth + 1 }}月</span>
        <div class="nav-buttons">
          <el-button size="small" circle :icon="ArrowLeft" @click="navigateMonth(-1)" />
          <el-button size="small" circle :icon="ArrowRight" @click="navigateMonth(1)" />
        </div>
      </div>
    </template>

    <div class="calendar-grid">
      <!-- Week Headers -->
      <div v-for="day in weekDays" :key="day" class="week-day-header">{{ day }}</div>
      
      <!-- Empty Slots -->
      <div v-for="n in firstDayOfMonth" :key="'empty-'+n" class="day-cell empty"></div>
      
      <!-- Days -->
      <div 
        v-for="day in daysInMonth" 
        :key="day" 
        class="day-cell"
        :class="{
          'is-today': isToday(day),
          'is-selected': isSelected(day),
          'has-meeting': hasMeeting(day)
        }"
        @click="selectDate(day)"
      >
        <span class="day-number">{{ day }}</span>
         <div v-if="hasMeeting(day)" class="meeting-dot"></div>
      </div>
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { ArrowLeft, ArrowRight, Plus } from '@element-plus/icons-vue'

const props = defineProps({
  meetings: {
    type: Array,
    default: () => []
  }
})

defineEmits(['create'])

const weekDays = ['日', '一', '二', '三', '四', '五', '六']
const now = new Date()
const currentYear = ref(now.getFullYear())
const currentMonth = ref(now.getMonth())
const selectedDate = ref(now.getDate())

const daysInMonth = computed(() => new Date(currentYear.value, currentMonth.value + 1, 0).getDate())
const firstDayOfMonth = computed(() => new Date(currentYear.value, currentMonth.value, 1).getDay())

const navigateMonth = (step) => {
  let newMonth = currentMonth.value + step
  if (newMonth > 11) {
    currentMonth.value = 0
    currentYear.value++
  } else if (newMonth < 0) {
    currentMonth.value = 11
    currentYear.value--
  } else {
    currentMonth.value = newMonth
  }
}

const isToday = (day) => {
  return day === now.getDate() && currentMonth.value === now.getMonth() && currentYear.value === now.getFullYear()
}

const isSelected = (day) => {
  return day === selectedDate.value
}

const selectDate = (day) => {
  selectedDate.value = day
}

const hasMeeting = (day) => {
  // Check if any meeting exists on this day
  // Assuming meeting.start_time is ISO string
  if (!props.meetings) return false
  const targetDateStr = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  
  return props.meetings.some(m => {
    if (!m.start_time) return false
    const mDate = new Date(m.start_time)
    const mDateStr = `${mDate.getFullYear()}-${String(mDate.getMonth() + 1).padStart(2, '0')}-${String(mDate.getDate()).padStart(2, '0')}`
    return mDateStr === targetDateStr
  })
}
</script>

<style scoped>
.session-calendar {
  border-radius: 12px;
  border: 1px solid var(--color-slate-200);
}
.calendar-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
.calendar-title {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-slate-800);
}
.calendar-controls {
  display: flex;
  align-items: center;
  gap: 12px;
}
.current-month {
  font-size: 14px;
  font-weight: 500;
  color: var(--color-slate-600);
}
.calendar-grid {
  display: grid;
  grid-template-columns: repeat(7, 1fr);
  gap: 8px;
  padding: 8px 0;
}
.week-day-header {
  text-align: center;
  font-size: 12px;
  color: var(--color-slate-400);
  padding: 4px;
}
.day-cell {
  aspect-ratio: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  border-radius: 8px;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-slate-700);
  transition: all 0.2s;
  position: relative;
}
.day-cell:hover {
  background-color: var(--color-slate-100);
}
.day-cell.is-selected {
  background-color: var(--color-primary);
  color: white;
}
.day-cell.is-today:not(.is-selected) {
  background-color: #eff6ff;
  color: var(--color-primary);
  font-weight: 600;
}
.meeting-dot {
  position: absolute;
  bottom: 4px;
  width: 4px;
  height: 4px;
  border-radius: 50%;
  background-color: currentColor;
  opacity: 0.8;
}
.day-cell.is-selected .meeting-dot {
  background-color: white;
}
</style>
