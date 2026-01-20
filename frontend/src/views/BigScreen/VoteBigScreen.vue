<template>
  <div class="vote-bigscreen">
    <!-- 顶部横幅区域 -->
    <div class="top-banner">
      <div class="banner-content">
        <div class="vote-title-section">
          <h1 class="vote-title">{{ voteData.title || '正在加载投票...' }}</h1>
          <div class="vote-subtitle" v-if="voteData.description">{{ voteData.description }}</div>
        </div>
        
        <div class="status-indicators" v-if="voteData.id">
          <!-- 状态标签 -->
          <div class="status-badge" :class="['status-' + voteData.status]">
            <span class="status-dot"></span>
            {{ getStatusLabel(voteData.status) }}
          </div>
          
          <!-- 参与人数 -->
          <div class="participants-badge">
            <el-icon><User /></el-icon>
            <span class="count">{{ totalVoters }}</span>
            <span class="label">人参与</span>
          </div>
        </div>
      </div>
    </div>

    <!-- 中心内容区 -->
    <div class="main-content">
      <!-- 视图切换按钮 (右上角) -->
      <div class="view-switcher-float" v-if="voteData.id">
        <el-button-group size="small">
          <el-button :type="viewMode === 'list' ? 'primary' : 'default'" @click="switchMode('list')">
            <el-icon><List /></el-icon> 列表
          </el-button>
          <el-button :type="viewMode === 'bar' ? 'primary' : 'default'" @click="switchMode('bar')">
            <el-icon><Histogram /></el-icon> 柱状
          </el-button>
          <el-button :type="viewMode === 'pie' ? 'primary' : 'default'" @click="switchMode('pie')">
            <el-icon><PieChart /></el-icon> 饼图
          </el-button>
        </el-button-group>
      </div>
      <!-- 倒计时/控制区 -->
      <div class="control-zone" v-if="voteData.id">
        <!-- 大号倒计时 -->
        <div v-if="voteData.status === 'active'" 
             class="countdown-display" 
             :class="{ 'countdown-urgent': remainingTime <= 10 && waitingTime === 0, 'countdown-waiting': waitingTime > 0 }">
          <div class="countdown-label">{{ waitingTime > 0 ? '距开始还有' : '剩余时间' }}</div>
          <div class="countdown-value">{{ waitingTime > 0 ? waitingTime + 's' : formatTimeDisplay(remainingTime) }}</div>
          <div class="countdown-progress">
            <div class="progress-bar" :style="{ width: (waitingTime > 0 ? (waitingTime/10*100) : (remainingTime / voteData.duration_seconds * 100)) + '%' }"></div>
          </div>
        </div>

        <!-- 启动按钮 -->
        <div v-if="voteData.status === 'draft'" class="launch-zone">
          <el-button type="primary" size="large" class="launch-btn" @click="handleStart" :loading="starting">
            <el-icon><VideoPlay /></el-icon>
            启动投票
          </el-button>
        </div>

        <!-- 已结束提示 -->
        <div v-if="voteData.status === 'closed'" class="finished-banner">
          <el-icon><CircleCheck /></el-icon>
          投票已结束
        </div>
      </div>

      <!-- 投票结果区域 -->
      <div class="results-area">
        <!-- 列表视图 -->
        <transition-group 
          v-if="viewMode === 'list'"
          name="result-list" 
          tag="div" 
          class="results-grid"
        >
          <div 
            v-for="(item, index) in sortedResults" 
            :key="item.option_id"
            class="result-item"
            :class="'rank-' + (index + 1)"
          >
            <!-- 排名徽章 -->
            <div class="rank-badge">
              <el-icon v-if="index === 0" class="rank-icon gold"><Trophy /></el-icon>
              <el-icon v-else-if="index === 1" class="rank-icon silver"><Medal /></el-icon>
              <el-icon v-else-if="index === 2" class="rank-icon bronze"><Medal /></el-icon>
              <span v-else class="rank-number">{{ index + 1 }}</span>
            </div>

            <!-- 选项内容 -->
            <div class="option-section">
              <div class="option-label">{{ item.content }}</div>
              <div class="stats-row">
                <span class="vote-count">{{ item.count }} 票</span>
                <span class="vote-percent">{{ item.percent }}%</span>
              </div>
            </div>

            <!-- 可视化条 -->
            <div class="bar-container">
              <div class="bar-bg">
                <div 
                  class="bar-fill" 
                  :style="{ 
                    width: item.percent + '%',
                    background: getGradient(index)
                  }"
                >
                  <div class="bar-shimmer"></div>
                </div>
              </div>
            </div>

            <!-- 投票人名单 (结束后显示) -->
            <div v-if="voteData.status === 'closed' && item.voters && item.voters.length > 0" 
                 class="voters-list">
              <div class="voters-label">
                <el-icon><User /></el-icon>
                投票人
              </div>
              <div class="voters-tags">
                <span v-for="name in item.voters" :key="name" class="voter-tag">
                  {{ name }}
                </span>
              </div>
            </div>
          </div>
        </transition-group>

        <!-- 图表视图 -->
        <div v-else class="chart-container" ref="chartRef"></div>

        <!-- 空状态 -->
        <div v-if="sortedResults.length === 0" class="empty-state">
          <el-icon><DataLine /></el-icon>
          <p>暂无投票数据</p>
        </div>
      </div>
    </div>

    <!-- 底部装饰 -->
    <div class="footer-decoration"></div>
  </div>
</template>

<script setup>
import * as echarts from 'echarts'
import { ref, onMounted, onUnmounted, computed, nextTick, watch } from 'vue'
import { useRoute } from 'vue-router'
import { User, Timer, VideoPlay, CircleCheck, DataLine, PieChart, Histogram, List, Trophy, Medal } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { io } from 'socket.io-client'
import { ElMessage } from 'element-plus'

const route = useRoute()
const voteId = route.params.id

const voteData = ref({})
const results = ref([])
const totalVoters = ref(0)
const socket = ref(null)
const remainingTime = ref(0)
const waitingTime = ref(0) // 准备倒计时
const starting = ref(false)
let timerInterval = null
let waitInterval = null

// View Mode: 'list' | 'pie' | 'bar'
const viewMode = ref('list')
const chartRef = ref(null)
let myChart = null

// ... (computed, helpers)
const sortedResults = computed(() => {
  if (!Array.isArray(results.value)) return []
  return [...results.value].sort((a, b) => b.count - a.count)
})

const showFinishedBanner = ref(false)

// 状态标签转换
const getStatusLabel = (status) => {
  const labels = {
    'draft': '未开始',
    'active': '进行中',
    'closed': '已结束'
  }
  return labels[status] || status
}

// 时间格式化显示
const formatTimeDisplay = (seconds) => {
  if (seconds >= 60) {
    const mins = Math.floor(seconds / 60)
    const secs = seconds % 60
    return `${mins}:${secs.toString().padStart(2, '0')}`
  }
  return `${seconds}s`
}

// 渐变色数组
const gradients = [
  'linear-gradient(90deg, #667eea, #764ba2)',
  'linear-gradient(90deg, #f093fb, #f5576c)',
  'linear-gradient(90deg, #4facfe, #00f2fe)',
  'linear-gradient(90deg, #43e97b, #38f9d7)',
  'linear-gradient(90deg, #fa709a, #fee140)',
  'linear-gradient(90deg, #a8edea, #fed6e3)'
]

const getGradient = (index) => {
  return gradients[index % gradients.length]
}

// 视图模式切换
const switchMode = (mode) => {
  viewMode.value = mode
}

// 窗口大小调整处理
const handleResize = () => {
  if (myChart) {
    myChart.resize()
  }
}

const fetchResults = async () => {
  try {
    const res = await request.get(`/vote/${voteId}/result`)
    const detail = await request.get(`/vote/${voteId}`)
    
    voteData.value = detail
    totalVoters.value = res.total_voters
    results.value = res.results

    if (detail.status === 'active') {
        if (detail.wait_seconds && detail.wait_seconds > 0) {
            // 进入准备倒计时
            waitingTime.value = detail.wait_seconds
            remainingTime.value = detail.duration_seconds
            startWaitTimer()
        } else {
            // 直接进入投票倒计时
            waitingTime.value = 0
            if (detail.remaining_seconds) {
                remainingTime.value = detail.remaining_seconds
                startTimer()
            }
        }
    }
  } catch (e) {
    console.error('Fetch error:', e)
    voteData.value = { title: '加载失败: ' + (e.message || '未知错误') }
    results.value = [] // 确保即使出错也保持为空数组
    ElMessage.error('无法加载: ' + e.message)
  }
}

const startWaitTimer = () => {
    if (waitInterval) clearInterval(waitInterval)
    waitInterval = setInterval(() => {
        if (waitingTime.value > 0) {
            waitingTime.value--
        } else {
            clearInterval(waitInterval)
            // 倒计时结束，正式开始
            waitingTime.value = 0
            startTimer()
            ElMessage.success('投票正式开始')
        }
    }, 1000)
}

const startTimer = () => {
  if (timerInterval) clearInterval(timerInterval)
  timerInterval = setInterval(() => {
    if (remainingTime.value > 0) {
      remainingTime.value--
    } else {
      clearInterval(timerInterval)
      // Timer finished: Trigger actual close
      if (voteData.value.status === 'active') {
          // Visual update immediate
          voteData.value.status = 'closed'
          ElMessage.info('倒计时结束，投票停止')
          
          // Show finished banner and hide after 5s
          showFinishedBanner.value = true
          setTimeout(() => { showFinishedBanner.value = false }, 5000)

          // API Call to finalize state
          request.post(`/vote/${voteId}/close`).catch(e => console.error('Auto close failed', e))
      }
    }
  }, 1000)
}

const handleStart = async () => {
  starting.value = true
  try {
    await request.post(`/vote/${voteId}/start`)
    // ElMessage.success('投票已启动') // Removed duplicate toast
    
    // Optimistic update
    voteData.value.status = 'active'
    if (voteData.value.duration_seconds) {
        remainingTime.value = voteData.value.duration_seconds
        waitingTime.value = 10 // Optimistic wait time
        startWaitTimer()
    }
  } catch(e) {
    console.error('Start error:', e)
    ElMessage.error('启动失败: ' + (e.message || '未知错误'))
  } finally {
    starting.value = false
  }
}

const initSocket = () => {
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket.value = io(url, {
    path: '/socket.io',
    transports: ['websocket']
  })

  socket.value.on('connect', () => {
    console.log('Socket connected')
    // Join meeting room if we have meeting_id
    if (voteData.value.meeting_id) {
      socket.value.emit('join_meeting', { meeting_id: voteData.value.meeting_id })
      console.log('Joined meeting room:', voteData.value.meeting_id)
    }
  })

  socket.value.on('vote_start', (data) => {
    console.log('Received vote_start:', data)
    if (data.id == voteId) {
      voteData.value.status = 'active'
      voteData.value.duration_seconds = data.duration_seconds
      remainingTime.value = data.duration_seconds
      
      // Check for wait_seconds or calculate from started_at
      if (data.wait_seconds) {
          waitingTime.value = data.wait_seconds
          startWaitTimer()
          ElMessage.warning(`投票将在 ${data.wait_seconds} 秒后开始`)
      } else {
          startTimer()
          ElMessage.success('投票已开始')
      }
    }
  })

  socket.value.on('vote_update', (data) => {
    console.log('Received vote_update:', data)
    if (data.vote_id == voteId) {
      results.value = data.results
    }
  })

  socket.value.on('vote_end', (data) => {
    console.log('Received vote_end:', data)
    if (data.vote_id == voteId) {
      voteData.value.status = 'closed'
      results.value = data.results
      totalVoters.value = data.total_voters
      if (timerInterval) clearInterval(timerInterval)
      if (waitInterval) clearInterval(waitInterval)
      remainingTime.value = 0
      waitingTime.value = 0
      
      // Show finished banner and hide after 5s
      showFinishedBanner.value = true
      setTimeout(() => { showFinishedBanner.value = false }, 5000)
    }
  })
}

onMounted(async () => {
  console.log('BigScreen mounted, voteId:', voteId)
  voteData.value = { title: `正在连接服务器...` }
  
  if (!voteId) {
    voteData.value = { title: '错误: 未提供投票ID' }
    return
  }

  await fetchResults()
  initSocket()
  
  // Join room after socket is initialized and we have meeting_id
  if (socket.value && voteData.value.meeting_id) {
    socket.value.emit('join_meeting', { meeting_id: voteData.value.meeting_id })
    console.log('Joined meeting room on mount:', voteData.value.meeting_id)
  }
  
  document.documentElement.classList.add('dark')
})

onUnmounted(() => {
  if (socket.value) socket.value.disconnect()
  if (timerInterval) clearInterval(timerInterval)
  if (myChart) myChart.dispose()
  window.removeEventListener('resize', handleResize)
  document.documentElement.classList.remove('dark')
})
</script>

<style scoped>
/* CSS Updates for Controls and Charts */
.right-controls {
  display: flex;
  align-items: center;
  gap: 24px;
}

.view-switcher .el-button {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #cbd5e1;
}

.view-switcher .el-button--primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}

.chart-container {
  width: 100%;
  height: 100%;
  min-height: 500px;
}

/* ===== 全局容器 ===== */
.vote-bigscreen {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #0a0e27 0%, #1a1f3a 50%, #0f1419 100%);
  color: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* 背景装饰网格 */
.vote-bigscreen::before {
  content: '';
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background-image: 
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  pointer-events: none;
  opacity: 0.3;
}

/* ===== 顶部横幅 ===== */
.top-banner {
  background: linear-gradient(180deg, rgba(26, 31, 58, 0.95) 0%, rgba(26, 31, 58, 0.7) 100%);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 12px 48px; /* Slimmer */
  z-index: 10;
  flex-shrink: 0;
}

.banner-content {
  max-width: 1400px;
  margin: 0 auto;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 32px;
}

.vote-title-section {
  flex: 1;
}

.vote-title {
  font-size: 32px; /* Smaller */
  font-weight: 700;
  margin: 0;
  background: linear-gradient(135deg, #ffffff 0%, #a0aec0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 1px;
  line-height: 1.2;
}

.vote-subtitle {
  font-size: 14px;
  color: #94a3b8;
  font-weight: 400;
  margin-top: 4px;
}

.status-indicators {
  display: flex;
  gap: 16px;
  align-items: center;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  border-radius: 24px;
  font-size: 16px;
  font-weight: 600;
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #94a3b8;
  animation: pulse-dot 2s infinite;
}

.status-draft .status-dot { background: #64748b; }
.status-active .status-dot { background: #10b981; }
.status-closed .status-dot { background: #ef4444; }

@keyframes pulse-dot {
  0%, 100% { opacity: 1; transform: scale(1); }
  50% { opacity: 0.6; transform: scale(1.2); }
}

.participants-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 10px 20px;
  background: linear-gradient(135deg, rgba(99, 102, 241, 0.2), rgba(168, 85, 247, 0.2));
  border: 1px solid rgba(139, 92, 246, 0.3);
  border-radius: 24px;
  font-size: 16px;
}

.participants-badge .count {
  font-size: 24px;
  font-weight: 700;
  color: #a78bfa;
}

.participants-badge .label {
  color: #cbd5e1;
}

/* ===== 中心内容区 ===== */
.main-content {
  flex: 1;
  display: flex;
  flex-direction: column;
  padding: 16px 48px 48px;
  max-width: 1400px;
  width: 100%;
  margin: 0 auto;
  position: relative;
  z-index: 5;
}

/* Floating View Switcher */
.view-switcher-float {
  position: absolute;
  top: 16px;
  right: 48px;
  z-index: 20;
}

.view-switcher-float .el-button {
  background: rgba(255, 255, 255, 0.1);
  border: 1px solid rgba(255, 255, 255, 0.2);
  color: #cbd5e1;
}

.view-switcher-float .el-button--primary {
  background: #6366f1;
  border-color: #6366f1;
  color: #fff;
}

/* ===== 控制区 ===== */
.control-zone {
  margin-bottom: 32px;
  display: flex;
  justify-content: center;
}

/* 倒计时显示 */
.countdown-display {
  background: linear-gradient(135deg, rgba(59, 130, 246, 0.15), rgba(147, 51, 234, 0.15));
  border: 2px solid rgba(99, 102, 241, 0.4);
  border-radius: 16px;
  padding: 12px 32px; /* Reduced padding */
  text-align: center;
  min-width: 240px;
  transition: all 0.3s ease;
}
...
.countdown-value {
  font-size: 42px; /* Reduced from 56px */
  font-weight: 700;
  font-family: 'Courier New', monospace;
  color: #ffffff;
  line-height: 1;
  margin-bottom: 8px;
}

.countdown-urgent .countdown-value {
  color: #fca5a5;
}

.countdown-waiting .countdown-value {
  color: #fbbf24; /* Amber for waiting */
}

.countdown-waiting .progress-bar {
  background: linear-gradient(90deg, #f59e0b, #d97706);
}

.countdown-progress {
  width: 100%;
  height: 6px;
  background: rgba(255, 255, 255, 0.1);
  border-radius: 3px;
  overflow: hidden;
}

.progress-bar {
  height: 100%;
  background: linear-gradient(90deg, #6366f1, #a855f7);
  transition: width 1s linear;
  border-radius: 3px;
}

.countdown-urgent .progress-bar {
  background: linear-gradient(90deg, #ef4444, #dc2626);
}

/* 启动按钮 */
.launch-zone {
  text-align: center;
}

.launch-btn {
  font-size: 20px;
  padding: 20px 48px;
  border-radius: 50px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border: none;
  box-shadow: 0 10px 30px rgba(102, 126, 234, 0.4);
  transition: all 0.3s ease;
}

.launch-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 15px 40px rgba(102, 126, 234, 0.6);
}

/* 已结束横幅 */
@keyframes heartbeat {
  0% { transform: scale(1); }
  15% { transform: scale(1.1); }
  30% { transform: scale(1); }
  45% { transform: scale(1.1); }
  60% { transform: scale(1); }
  100% { transform: scale(1); }
}

.countdown-display.heartbeat {
  animation: heartbeat 1s infinite alternate;
  border-color: #ef4444;
  background: rgba(239, 68, 68, 0.1);
}

.finished-banner {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 16px 32px;
  background: linear-gradient(135deg, rgba(16, 185, 129, 0.2), rgba(5, 150, 105, 0.2));
  border: 2px solid rgba(16, 185, 129, 0.5);
  border-radius: 16px;
  font-size: 20px;
  font-weight: 600;
  color: #6ee7b7;
  /* Ensure it's centered if needed or full width */
  margin-top: 20px;
}

.fade-enter-active,
.fade-leave-active {
  transition: opacity 0.5s ease;
}

.fade-enter-from,
.fade-leave-to {
  opacity: 0;
}

.finished-banner .el-icon {
  font-size: 28px;
}

/* ===== 结果区域 ===== */
.results-area {
  flex: 1;
  overflow-y: auto;
  padding-right: 8px;
  /* 隐藏滚动条但保留功能 */
  scrollbar-width: none; 
}
.results-area::-webkit-scrollbar { 
  display: none; 
}

.results-grid {
  display: grid;
  grid-template-columns: repeat(2, 1fr); /* 双列布局 */
  gap: 20px;
  align-items: start;
}

/* 当选项只有1-4个时，切换回单列大卡片模式，居中显示 */
.results-grid:has(.result-item:nth-last-child(-n+4):first-child) {
  grid-template-columns: 1fr;
  max-width: 900px;
  margin: 0 auto;
}

/* 结果项 - 紧凑设计 */
.result-item {
  background: linear-gradient(135deg, rgba(30, 41, 59, 0.4), rgba(15, 23, 42, 0.4));
  backdrop-filter: blur(10px);
  border: 1px solid rgba(255, 255, 255, 0.08);
  border-radius: 12px;
  padding: 16px 20px;
  position: relative;
  overflow: hidden;
  transition: all 0.5s cubic-bezier(0.4, 0, 0.2, 1);
  display: flex;
  flex-direction: column;
}

/* 排名徽章 - 缩小 */
.rank-badge {
  position: absolute;
  top: 12px;
  left: 12px;
  width: 36px;
  height: 36px;
  border-radius: 8px;
  font-size: 16px;
  background: rgba(255, 255, 255, 0.1); /* Default background */
  display: flex;
  align-items: center;
  justify-content: center;
  border: 1px solid rgba(255, 255, 255, 0.2);
}

.rank-number { font-size: 16px; font-weight: 700; color: #94a3b8; }

.rank-icon { font-size: 20px; }
.rank-icon.gold { color: #fff; }
.rank-icon.silver { color: #fff; }
.rank-icon.bronze { color: #fff; }

/* Top 3 Badge Styles Override */
.rank-1 .rank-badge {
  background: linear-gradient(135deg, #fbbf24 0%, #d97706 100%);
  border-color: #fbbf24;
  box-shadow: 0 2px 10px rgba(251, 191, 36, 0.4);
}
.rank-2 .rank-badge {
  background: linear-gradient(135deg, #e5e7eb 0%, #9ca3af 100%);
  border-color: #d1d5db;
  box-shadow: 0 2px 10px rgba(156, 163, 175, 0.4);
}
.rank-3 .rank-badge {
  background: linear-gradient(135deg, #fcd34d 0%, #b45309 100%); /* Bronze tone tweak */
  border-color: #f59e0b;
  box-shadow: 0 2px 10px rgba(245, 158, 11, 0.4);
}

/* 选项区 - 紧凑调整 */
.option-section {
  margin-left: 50px; /* 留出徽章空间 */
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
}

.option-label {
  font-size: 20px;
  font-weight: 600;
  color: #f1f5f9;
  line-height: 1.2;
}

.stats-row {
  display: flex;
  align-items: baseline;
  gap: 12px;
  flex-shrink: 0;
}

.vote-count { font-size: 14px; }
.vote-percent { font-size: 20px; }

/* 条形图容器 */
.bar-container {
  margin-left: 0; /* 全宽 */
  margin-bottom: 12px;
  width: 100%;
}

.bar-bg {
  height: 20px; /* 变细 */
  border-radius: 10px;
}
.bar-fill { border-radius: 10px; }

/* 投票人名单 - 紧凑 */
.voters-list {
  margin-left: 0;
  margin-top: 8px;
  padding-top: 8px;
}

.voters-label {
  font-size: 12px;
  margin-bottom: 4px;
}

.voter-tag {
  padding: 2px 8px;
  font-size: 12px;
  border-radius: 4px;
}


/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: #64748b;
}

.empty-state .el-icon {
  font-size: 64px;
  margin-bottom: 16px;
  opacity: 0.5;
}

.empty-state p {
  font-size: 18px;
  margin: 0;
}

/* 列表过渡动画 */
.result-list-move,
.result-list-enter-active,
.result-list-leave-active {
  transition: all 0.6s cubic-bezier(0.4, 0, 0.2, 1);
}

.result-list-enter-from {
  opacity: 0;
  transform: translateX(50px) scale(0.9);
}

.result-list-leave-to {
  opacity: 0;
  transform: translateX(-50px) scale(0.9);
}

.result-list-leave-active {
  position: absolute;
  width: 100%;
}

/* 底部装饰 */
.footer-decoration {
  position: absolute;
  bottom: 0;
  left: 0;
  right: 0;
  height: 4px;
  background: linear-gradient(90deg, 
    transparent 0%,
    rgba(99, 102, 241, 0.5) 25%,
    rgba(168, 85, 247, 0.5) 50%,
    rgba(236, 72, 153, 0.5) 75%,
    transparent 100%
  );
  opacity: 0.6;
}
</style>
