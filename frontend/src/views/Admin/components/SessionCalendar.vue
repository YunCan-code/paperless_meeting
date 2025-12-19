<template>
  <el-card class="session-calendar" shadow="never">
    <template #header>
      <div class="calendar-header-new">
        <!-- Left: Quick Jump -->
        <div class="header-left">
          <el-button size="small" @click="goToday">今天</el-button>
        </div>

        <!-- Center: Navigation -->
        <div class="header-center date-navigator">
          <el-button circle text :icon="ArrowLeft" @click="navigateMonth(-1)" />
          <span class="current-month">{{ currentYear }}年 {{ currentMonth + 1 }}月</span>
          <el-button circle text :icon="ArrowRight" @click="navigateMonth(1)" />
        </div>

        <!-- Right: Action -->
        <div class="header-right">
          <el-button type="primary" @click="$emit('create')">
            <el-icon class="el-icon--left"><Plus /></el-icon>
            新建会议
          </el-button>
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
        :class="[
          {
            'is-today': isToday(day),
            'is-selected': isSelected(day),
            'has-meeting': hasMeeting(day)
          },
          getHeatClass(day)
        ]"
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

const emit = defineEmits(['create', 'select-date'])

const weekDays = ['日', '一', '二', '三', '四', '五', '六']
const now = new Date()
const currentYear = ref(now.getFullYear())
const currentMonth = ref(now.getMonth())
const selectedDate = ref(now.getDate())

const daysInMonth = computed(() => new Date(currentYear.value, currentMonth.value + 1, 0).getDate())
const firstDayOfMonth = computed(() => new Date(currentYear.value, currentMonth.value, 1).getDay())

const goToday = () => {
  currentYear.value = now.getFullYear()
  currentMonth.value = now.getMonth()
  selectedDate.value = now.getDate()
  // Emit selection
  selectDate(now.getDate())
}

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
  const dateObj = new Date(currentYear.value, currentMonth.value, day)
  emit('select-date', dateObj)
}

const getMeetingCount = (day) => {
  if (!props.meetings) return 0
  const targetDateStr = `${currentYear.value}-${String(currentMonth.value + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`
  let count = 0
  props.meetings.forEach(m => {
    if (!m.start_time) return
    const mDate = new Date(m.start_time)
    const mDateStr = `${mDate.getFullYear()}-${String(mDate.getMonth() + 1).padStart(2, '0')}-${String(mDate.getDate()).padStart(2, '0')}`
    if (mDateStr === targetDateStr) count++
  })
  return count
}

const getHeatClass = (day) => {
  const count = getMeetingCount(day)
  if (count === 0) return ''
  if (count <= 1) return 'heat-level-1'
  if (count <= 3) return 'heat-level-2'
  return 'heat-level-3'
}

const hasMeeting = (day) => getMeetingCount(day) > 0
</script>

<style scoped>
.session-calendar {
  border-radius: 12px;
  border: 1px solid var(--color-slate-200);
}
.calendar-header-new {
  display: flex;
  justify-content: space-between;
  align-items: center;
  position: relative;
  padding: 4px 0;
}

.header-left, .header-right {
  flex: 0 0 auto;
  z-index: 10;
}

.header-center {
  position: absolute;
  left: 50%;
  transform: translateX(-50%);
  display: flex;
  align-items: center;
  gap: 16px;
}

.date-navigator {
  display: flex;
  align-items: center;
}

.current-month {
  font-size: 24px;
  font-weight: 600;
  color: var(--color-slate-800);
  min-width: 140px;
  text-align: center;
  font-variant-numeric: tabular-nums;
  cursor: default;
}

.today-btn {
  margin-left: 4px;
  font-weight: 500;
  color: var(--color-slate-600);
}
.today-btn:hover {
  color: var(--color-primary);
  background-color: var(--color-primary-light-9);
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
/* Hover effect only for cells with NO heat/color */
.day-cell:not(.heat-level-1):not(.heat-level-2):not(.heat-level-3):not(.is-selected):hover {
  background-color: var(--color-slate-100);
}
/* Optional: subtle opacity change for heat cells on hover instead of color change, 
   but user asked for "no change", so we stick to that or just cursor pointer (which is default) */
.day-cell.heat-level-1:hover,
.day-cell.heat-level-2:hover,
.day-cell.heat-level-3:hover {
  filter: brightness(0.95); /* Subtle reaction without changing hue */
}
/* Selected State */
.day-cell.is-selected {
  /* Remove blue border and transparent bg override */
  font-weight: 800;
  box-shadow: inset 0 0 0 2px var(--color-primary); /* Use inset shadow instead of border to avoid layout shift, maintain BG */
  /* Do NOT set background-color here, let heat class control it */
}

/* Today styling */
.day-cell.is-today {
  color: #169e8c; /* Teal text for today if no heat */
  font-weight: 800;
}
.day-cell.is-today::after {
   content: '';
   position: absolute;
   bottom: 4px;
   width: 4px; 
   height: 4px;
   border-radius: 50%;
   background-color: #169e8c; /* Teal dot */
}

/* Heatmap Colors (Orange Scale) */
.heat-level-1 { background-color: #ffedd5; color: #c2410c; }
.heat-level-2 { background-color: #fed7aa; color: #c2410c; }
.heat-level-3 { background-color: #fdba74; color: #9a3412; }

/* Today with Heat: Use Teal Background */
.day-cell.is-today.heat-level-1,
.day-cell.is-today.heat-level-2,
.day-cell.is-today.heat-level-3 {
    background-color: #169e8c; /* Teal BG override */
    color: white; /* White text on Teal */
}
/* When Today has heat (Teal BG), the "Today" dot should be white */
.day-cell.is-today.heat-level-1::after,
.day-cell.is-today.heat-level-2::after,
.day-cell.is-today.heat-level-3::after {
   background-color: white; 
}
/* When Today has heat (Teal BG), the "Meeting" dot should also be white for visibility */
.day-cell.is-today.heat-level-1 .meeting-dot,
.day-cell.is-today.heat-level-2 .meeting-dot,
.day-cell.is-today.heat-level-3 .meeting-dot {
   background-color: white;
}
</style>
