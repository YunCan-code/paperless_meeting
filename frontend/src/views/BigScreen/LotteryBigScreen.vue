<template>
  <div class="lottery-bigscreen">
    <!-- Top Banner -->
    <div class="top-banner">
      <div class="banner-content">
        <div class="title-section">
          <h1 class="page-title">{{ state.current_title || '抽签活动' }}</h1>
          <div class="round-info" v-if="rounds.length > 0">
            第 {{ currentRoundIndex + 1 }}/{{ rounds.length }} 轮
            <span v-if="state.current_count > 0"> · 抽取 {{ state.current_count }} 人</span>
          </div>
        </div>
        <div class="status-section">
          <div class="status-badge" :class="state.status.toLowerCase()">
            <span class="status-dot"></span>
            {{ getStatusText(state.status) }}
          </div>
          <div class="participant-stats">
            <el-icon><User /></el-icon>
            <span>{{ state.participant_count }} 人参与</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Main Content -->
    <div class="main-content">
      <!-- IDLE / PREPARING: Participant Pool -->
      <div v-if="state.status === 'IDLE' || state.status === 'PREPARING'" class="pool-container">
        <!-- IDLE State Notice -->
        <div v-if="state.status === 'IDLE'" class="idle-notice">
          <el-icon class="notice-icon"><InfoFilled /></el-icon>
          <div class="notice-content">
            <h3 v-if="rounds.length === 0">暂无抽签轮次</h3>
            <h3 v-else-if="rounds.every(r => r.status === 'finished')">所有轮次已完成</h3>
            <h3 v-else>等待配置中...</h3>
            <p v-if="rounds.length === 0">请先在管理界面创建抽签轮次</p>
            <p v-else-if="rounds.every(r => r.status === 'finished')">所有抽签轮次已完成,可在历史记录中查看结果</p>
            <p v-else>正在加载抽签配置,请稍候...</p>
          </div>
        </div>
        
        <!-- Control Panel -->
        <div v-if="state.status === 'PREPARING'" class="control-panel">
          <el-button 
            type="primary" 
            size="large" 
            @click="startLottery"
            :disabled="state.participant_count === 0"
          >
            <el-icon><VideoPlay /></el-icon>
            开始抽签
          </el-button>
          <el-button 
            size="large" 
            @click="resetLottery"
          >
            <el-icon><RefreshLeft /></el-icon>
            重置
          </el-button>
          <el-button 
            type="warning"
            size="large" 
            @click="addTestParticipants"
          >
            <el-icon><UserFilled /></el-icon>
            添加测试参与者
          </el-button>
        </div>

        <!-- Participant Pool -->
        <div class="pool-section">
          <div class="pool-header" v-if="state.participants.length > 0">
            <h2>参与者池</h2>
            <div class="pool-count">{{ state.participants.length }} 人</div>
          </div>
          
          <div class="pool-grid" v-if="state.participants.length > 0">
            <transition-group name="list">
              <div 
                v-for="p in state.participants" 
                :key="p.id" 
                class="participant-card"
              >
                <div class="avatar-placeholder">
                  <img v-if="p.avatar" :src="p.avatar" :alt="p.name" />
                  <span v-else class="avatar-text">{{ p.name.charAt(0) }}</span>
                </div>
                <div class="participant-info">
                  <span class="name">{{ p.name }}</span>
                  <span class="dept" v-if="p.department">{{ p.department }}</span>
                </div>
              </div>
            </transition-group>
          </div>
          
          <div v-else class="empty-pool">
            <div class="scanning-animation">
              <el-icon class="scanning-icon"><Cpu /></el-icon>
            </div>
            <h3>等待参与者加入</h3>
            <p>请在移动端扫码或点击"抽签"加入本轮抽签</p>
            <div class="mobile-hint">
              <el-icon><Cellphone /></el-icon>
              <span>手机端路径: 会议详情 → 抽签</span>
            </div>
          </div>
        </div>
      </div>

      <!-- ROLLING: Rolling Animation -->
      <div v-else-if="state.status === 'ROLLING'" class="rolling-container">
        <div class="rolling-machine">
          <div class="rolling-window">
            <div class="rolling-name">{{ rollingName }}</div>
          </div>
          <div class="rolling-status">正在抽取 {{ state.current_count }} 位幸运儿...</div>
          
          <!-- Stop Button -->
          <div class="stop-button-section">
            <el-button 
              type="danger" 
              size="large"
              @click="stopLottery"
            >
              <el-icon><CircleClose /></el-icon>
              停止抽签
            </el-button>
          </div>
        </div>
      </div>

      <!-- RESULT: Winners Display -->
      <div v-else-if="state.status === 'RESULT'" class="result-container">
        <div class="result-header">
          <h2>🎉 {{ state.current_title }} 中奖名单 🎉</h2>
        </div>
        
        <div class="winners-grid">
          <div 
            v-for="(winner, index) in state.winners" 
            :key="winner.id || index" 
            class="winner-card"
            :style="{ animationDelay: index * 0.15 + 's' }"
          >
            <div class="winner-rank">{{ index + 1 }}</div>
            <div class="trophy-icon">🏆</ div>
            <div class="winner-name">{{ winner.name }}</div>
          </div>
        </div>
        
        <!-- Action Buttons -->
        <div class="result-actions">
          <el-button 
            v-if="hasNextRound" 
            type="primary" 
            size="large"
            @click="prepareNextRound"
          >
            <el-icon><DArrowRight /></el-icon>
            下一轮抽签
          </el-button>
          <el-button 
            v-else
            type="success"
            size="large"
            @click="viewAllResults"
          >
            <el-icon><Document /></el-icon>
            查看所有轮次结果
          </el-button>
          <el-button 
            size="large"
            @click="backToPool"
          >
            <el-icon><Back /></el-icon>
            返回参与者池
          </el-button>
        </div>
        
        <div class="confetti-canvas"></div>
      </div>

      <!-- ALL ROUNDS SUMMARY -->
      <div v-else-if="showSummary" class="summary-container">
        <div class="summary-header">
          <h1>🎊 本次抽签活动完整结果 🎊</h1>
          <p>共 {{ rounds.length }} 轮抽签</p>
        </div>

        <div class="rounds-summary">
          <div 
            v-for="(round, rIndex) in rounds" 
            :key="round.id"
            class="round-block"
            :style="{ animationDelay: rIndex * 0.1 + 's' }"
          >
            <div class="round-header">
              <span class="round-number">第 {{ rIndex + 1 }} 轮</span>
              <h3>{{ round.title }}</h3>
              <span class="round-count">抽取 {{ round.count }} 人</span>
            </div>
            
            <div class="round-winners" v-if="round.winners && round.winners.length > 0">
              <div 
                v-for="(winner, wIndex) in round.winners" 
                :key="winner.id || wIndex"
                class="summary-winner-card"
              >
                <div class="winner-badge">{{ wIndex + 1 }}</div>
                <div class="winner-info">
                  <div class="winner-name">{{ winner.name }}</div>
                  <div class="winner-dept" v-if="winner.department">{{ winner.department }}</div>
                </div>
              </div>
            </div>
            <div class="no-winners" v-else>
              <span>暂无中奖者</span>
            </div>
          </div>
        </div>

        <div class="summary-actions">
          <el-button 
            type="primary"
            size="large"
            @click="showSummary = false"
          >
            <el-icon><Back /></el-icon>
            返回当前轮结果
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted } from 'vue'
import { useRoute } from 'vue-router'
import { io } from 'socket.io-client'
import { 
  Cpu, User, VideoPlay, RefreshLeft, Cellphone, 
  DArrowRight, CircleCheck, Back, CircleClose, InfoFilled, UserFilled, Document 
} from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const meetingId = route.params.meetingId

// Rounds list (fetched from API)
const rounds = ref([])
const currentRoundIndex = ref(0)

// State
const state = ref({
  status: 'IDLE',
  participants: [],
  current_title: '',
  current_count: 1,
  winners: [],
  participant_count: 0
})

const showSummary = ref(false) // Show all rounds summary

const rollingName = ref('???')
let rollingInterval = null
let socket = null

// Computed: is there a next round?
const hasNextRound = computed(() => {
  return currentRoundIndex.value < rounds.value.length - 1
})

// Flag to track if initial state has been received
let initialStateReceived = false

// Helpers
const getStatusText = (status) => {
  const map = {
    'IDLE': '等待配置',
    'PREPARING': '准备就绪',
    'ROLLING': '正在抽签',
    'RESULT': '结果公布'
  }
  return map[status] || status
}

// Socket Initialization
const initSocket = () => {
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket']
  })

  socket.on('connect', () => {
    console.log('Connected to socket')
    socket.emit('join_meeting', { meeting_id: meetingId })
    // Fetch initial state - fetchRoundsAndPrepareFirst will be called after state is received
    socket.emit('get_lottery_state', { meeting_id: meetingId })
  })

  socket.on('lottery_state_change', (data) => {
    console.log('State update:', data)
    handleStateChange(data)
  })

  socket.on('lottery_players_update', (data) => {
    console.log('Players update:', data)
    // Update participants list
    if (data.all_participants) {
      state.value.participants = data.all_participants
      state.value.participant_count = data.count || data.all_participants.length
    }
  })

  socket.on('lottery_state_sync', (data) => {
    console.log('State sync (initial load):', data)
    // Handle initial state from database
    // Map backend field names to frontend state structure
    state.value.status = data.status || 'IDLE'
    state.value.participants = data.all_participants || []
    state.value.participant_count = data.participants_count || 0
    state.value.current_title = data.config?.title || ''
    state.value.current_count = data.config?.count || 1
    state.value.winners = data.last_result || []
    
    // On first state receive, fetch rounds and prepare if needed
    if (!initialStateReceived) {
      initialStateReceived = true
      fetchRoundsAndPrepareFirst()
    }
  })

  socket.on('lottery_error', (data) => {
    ElMessage.error(data.message)
  })
}

// State Logic
const handleStateChange = (newState) => {
  const oldStatus = state.value.status
  state.value = newState

  // On first state receive, fetch rounds and prepare if needed
  if (!initialStateReceived) {
    initialStateReceived = true
    fetchRoundsAndPrepareFirst()
  }

  // Status transitions
  if (newState.status === 'ROLLING' && oldStatus !== 'ROLLING') {
    startRolling()
  } else if (newState.status !== 'ROLLING' && oldStatus === 'ROLLING') {
    stopRolling()
  }
}

// Rolling Logic
const startRolling = () => {
  if (rollingInterval) return
  const names = state.value.participants.map(p => p.name)
  if (names.length === 0) return

  rollingInterval = setInterval(() => {
    const randomName = names[Math.floor(Math.random() * names.length)]
    rollingName.value = randomName
  }, 50) // Fast switching
}

const stopRolling = () => {
  if (rollingInterval) {
    clearInterval(rollingInterval)
    rollingInterval = null
  }
}

// Fetch rounds from API and prepare first unfinished
const fetchRoundsAndPrepareFirst = async () => {
  try {
    const res = await request.get(`/lottery/${meetingId}/history`)
    rounds.value = res.rounds || []
    
    // Find first unfinished round
    const firstUnfinished = rounds.value.findIndex(r => r.status !== 'finished')
    if (firstUnfinished >= 0) {
      currentRoundIndex.value = firstUnfinished
      const round = rounds.value[firstUnfinished]
      
      // Only send prepare if state is IDLE (to avoid resetting when refreshing)
      // If already PREPARING/ROLLING/RESULT, just sync the round index
      if (state.value.status === 'IDLE') {
        console.log('Auto-preparing round:', round.title)
        socket.emit('lottery_action', {
          action: 'prepare',
          meeting_id: parseInt(meetingId),
          lottery_id: round.id,
          title: round.title,
          count: round.count
        })
      } else {
        console.log('State is not IDLE, skipping auto-prepare. Current status:', state.value.status)
      }
    } else if (rounds.value.length > 0) {
      // All finished, show last round result
      currentRoundIndex.value = rounds.value.length - 1
    }
  } catch (e) {
    console.error('Failed to fetch rounds:', e)
  }
}

// Prepare next round
const prepareNextRound = () => {
  const nextIndex = currentRoundIndex.value + 1
  if (nextIndex < rounds.value.length) {
    currentRoundIndex.value = nextIndex
    const round = rounds.value[nextIndex]
    console.log('Preparing next round:', round.title)
    socket.emit('lottery_action', {
      action: 'prepare',
      meeting_id: parseInt(meetingId),
      lottery_id: round.id,
      title: round.title,
      count: round.count
    })
  }
}

// Start lottery (admin control)
const startLottery = () => {
  const round = rounds.value[currentRoundIndex.value]
  if (!round) {
    ElMessage.warning('没有可用的轮次')
    return
  }
  socket.emit('lottery_action', {
    action: 'roll',
    meeting_id: parseInt(meetingId),
    lottery_id: round.id
  })
}

// Reset lottery (clear all participants)
const resetLottery = async () => {
  try {
    await ElMessageBox.confirm(
      '确定要重置本轮抽签吗？这将清空所有参与者。',
      '确认重置',
      {
        confirmButtonText: '确定',
        cancelButtonText: '取消',
        type: 'warning'
      }
    )
    socket.emit('lottery_action', {
      action: 'reset',
      meeting_id: parseInt(meetingId)
    })
    ElMessage.success('已重置')
  } catch (e) {
    // User cancelled
  }
}

// Back to pool (return from result to preparing)
const backToPool = () => {
  const round = rounds.value[currentRoundIndex.value]
  if (!round) return
  
  socket.emit('lottery_action', {
    action: 'prepare',
    meeting_id: parseInt(meetingId),
    lottery_id: round.id,
    title: round.title,
    count: round.count
  })
}

// Stop lottery (trigger result)
const stopLottery = () => {
  socket.emit('lottery_action', {
    action: 'stop',
    meeting_id: parseInt(meetingId)
  })
}

// Add test participants for testing
const addTestParticipants = () => {
  const testUsers = [
    { id: 9001, name: '张三', department: '技术部', avatar: '' },
    { id: 9002, name: '李四', department: '市场部', avatar: '' },
    { id: 9003, name: '王五', department: '财务部', avatar: '' },
    { id: 9004, name: '赵六', department: '人事部', avatar: '' },
    { id: 9005, name: '钱七', department: '运营部', avatar: '' },
    { id: 9006, name: '孙八', department: '研发部', avatar: '' },
    { id: 9007, name: '周九', department: '设计部', avatar: '' },
    { id: 9008, name: '吴十', department: '销售部', avatar: '' }
  ]
  
  let count = 0
  testUsers.forEach((user, index) => {
    setTimeout(() => {
      socket.emit('lottery_action', {
        action: 'join',
        meeting_id: parseInt(meetingId),
        user_id: user.id,
        user_name: user.name,
        department: user.department,
        avatar: user.avatar
      })
      count++
      if (count === testUsers.length) {
        ElMessage.success(`已添加 ${count} 个测试参与者`)
      }
    }, index * 200) // Stagger the joins
  })
}

// View all results summary
const viewAllResults = async () => {
  try {
    // Fetch latest round data with winners
    const res = await request.get(`/lottery/${meetingId}/history`)
    rounds.value = res.rounds || []
    showSummary.value = true
  } catch (e) {
    console.error('Failed to fetch results:', e)
    ElMessage.error('获取结果失败')
  }
}

onMounted(() => {
  document.documentElement.classList.add('dark')
  initSocket()
})

onUnmounted(() => {
  document.documentElement.classList.remove('dark')
  if (socket) socket.disconnect()
  stopRolling()
})
</script>

<style scoped>
/* Base Layout matching VoteBigScreen */
.lottery-bigscreen {
  width: 100vw;
  height: 100vh;
  background: linear-gradient(135deg, #0a0e27 0%, #1a1f3a 50%, #0f1419 100%);
  color: #ffffff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
  position: relative;
}

/* Background Grid */
.lottery-bigscreen::before {
  content: '';
  position: absolute;
  top: 0; left: 0; right: 0; bottom: 0;
  background-image: 
    linear-gradient(rgba(255,255,255,0.03) 1px, transparent 1px),
    linear-gradient(90deg, rgba(255,255,255,0.03) 1px, transparent 1px);
  background-size: 50px 50px;
  pointer-events: none;
  opacity: 0.3;
}

/* Banner */
.top-banner {
  background: linear-gradient(180deg, rgba(26, 31, 58, 0.95) 0%, rgba(26, 31, 58, 0.7) 100%);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255, 255, 255, 0.1);
  padding: 16px 48px;
  z-index: 10;
}

.page-title {
  font-size: 32px;
  font-weight: 700;
  background: linear-gradient(135deg, #ffffff 0%, #a0aec0 100%);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  margin: 0;
}

.subtitle {
  display: flex;
  gap: 16px;
  margin-top: 8px;
}

.status-badge {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 6px 16px;
  border-radius: 20px;
  background: rgba(255,255,255,0.1);
  font-size: 14px;
}

.status-dot {
  width: 8px; height: 8px; border-radius: 50%;
  background: #aaa;
}

.status-badge.preparing .status-dot { background: #10b981; animation: pulse 2s infinite; }
.status-badge.rolling .status-dot { background: #f59e0b; }
.status-badge.result .status-dot { background: #8b5cf6; }

@keyframes pulse {
  0% { opacity: 1; }
  50% { opacity: 0.5; }
  100% { opacity: 1; }
}

/* Main Content */
.main-content {
  flex: 1;
  display: flex;
  align-items: center;
  justify-content: center;
  padding: 40px;
  position: relative;
  z-index: 5;
}

/* Pool View */
.pool-container {
  width: 100%;
  max-width: 1200px;
  height: 100%;
}

.pool-grid {
  display: flex;
  flex-wrap: wrap;
  gap: 16px;
  justify-content: center;
  align-content: flex-start;
}

.participant-card {
  background: rgba(255,255,255,0.05);
  border: 1px solid rgba(255,255,255,0.1);
  padding: 8px 16px;
  border-radius: 30px;
  display: flex;
  align-items: center;
  gap: 10px;
  animation: popIn 0.3s cubic-bezier(0.34, 1.56, 0.64, 1);
}

.avatar-placeholder {
  width: 32px; height: 32px;
  background: linear-gradient(135deg, #6366f1, #8b5cf6);
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-weight: bold;
  font-size: 14px;
}

/* Empty State */
.empty-pool {
  height: 100%;
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  color: #64748b;
}

.scanning-icon {
  font-size: 64px;
  margin-bottom: 24px;
  animation: float 3s ease-in-out infinite;
}

.empty-pool .text { font-size: 24px; margin-bottom: 8px; }
.empty-pool .sub-text { font-size: 16px; opacity: 0.7; }

/* Rolling View */
.rolling-machine {
  text-align: center;
}

.rolling-window {
  font-size: 80px;
  font-weight: 800;
  color: #fbbf24;
  text-shadow: 0 0 30px rgba(251, 191, 36, 0.5);
  margin-bottom: 32px;
  min-height: 120px;
}

.rolling-status {
  font-size: 24px;
  color: #94a3b8;
}

/* Result View */
.winners-grid {
  display: flex;
  gap: 32px;
  justify-content: center;
  flex-wrap: wrap;
}

.winner-card {
  background: linear-gradient(135deg, rgba(251, 191, 36, 0.2), rgba(217, 119, 6, 0.2));
  border: 2px solid #fbbf24;
  border-radius: 20px;
  padding: 32px;
  text-align: center;
  min-width: 240px;
  animation: slideUp 0.6s cubic-bezier(0.34, 1.56, 0.64, 1) backwards;
  box-shadow: 0 0 50px rgba(251, 191, 36, 0.3);
}

.trophy-icon { font-size: 64px; margin-bottom: 16px; }
.winner-name { font-size: 36px; font-weight: bold; margin-bottom: 8px; color: #fff; }
.winner-label { color: #fbbf24; font-size: 18px; text-transform: uppercase; letter-spacing: 2px; }

/* Animations */
@keyframes popIn {
  from { opacity: 0; transform: scale(0.5); }
  to { opacity: 1; transform: scale(1); }
}

@keyframes float {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-10px); }
}

@keyframes slideUp {
  from { opacity: 0; transform: translateY(50px); }
  to { opacity: 1; transform: translateY(0); }
}

/* Transitions */
.list-enter-active, .list-leave-active { transition: all 0.5s ease; }
.list-enter-from, .list-leave-to { opacity: 0; transform: translateY(20px); }

/* Next Round Section */
.next-round-section {
  margin-top: 48px;
  text-align: center;
}

.next-round-section .el-button {
  padding: 16px 48px;
  font-size: 20px;
  font-weight: 600;
  background: linear-gradient(135deg, #10b981, #059669);
  border: none;
  animation: pulse 2s infinite;
}

.all-done-section {
  margin-top: 48px;
  text-align: center;
}

.all-done-text {
  font-size: 32px;
  color: #fbbf24;
  font-weight: 600;
  text-shadow: 0 0 20px rgba(251, 191, 36, 0.5);
}

.action-footer {
  margin-top: 32px;
  text-align: center;
  animation: slideUp 0.5s ease;
}

.start-btn {
  padding: 20px 60px;
  font-size: 24px;
  font-weight: 700;
  background: linear-gradient(135deg, #f59e0b, #d97706);
  border: none;
  box-shadow: 0 4px 20px rgba(245, 158, 11, 0.4);
  transition: all 0.3s ease;
}

.start-btn:hover {
  transform: translateY(-2px);
  box-shadow: 0 6px 25px rgba(245, 158, 11, 0.6);
}

.start-btn:active {
  transform: translateY(1px);
}

/* Enhanced UI Styles */
.banner-content { display: flex; justify-content: space-between; align-items: center; max-width: 1400px; margin: 0 auto; }
.title-section { flex: 1; }
.page-title { background-clip: text; }
.round-info { margin-top: 8px; font-size: 16px; color: rgba(255, 255, 255, 0.7); font-weight: 500; }
.status-section { display: flex; gap: 20px; align-items: center; }
.participant-stats { display: flex; align-items: center; gap: 8px; padding: 8px 20px; border-radius: 24px; background: rgba(59, 130, 246, 0.15); color: #93C5FD; font-size: 15px; font-weight: 500; }
.status-badge.idle .status-dot { background: #6B7280; }
.control-panel { display: flex; gap: 16px; justify-content: center; margin-bottom: 32px; padding: 24px; background: rgba(255, 255, 255, 0.03); border-radius: 16px; border: 1px solid rgba(255, 255, 255, 0.05); }
.control-panel .el-button { padding: 14px 32px; font-size: 16px; font-weight: 600; }
.pool-section { flex: 1; display: flex; flex-direction: column; overflow: hidden; }
.pool-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding-bottom: 16px; border-bottom: 2px solid rgba(59, 130, 246, 0.2); }
.pool-header h2 { font-size: 24px; font-weight: 600; color: #93C5FD; margin: 0; }
.pool-count { font-size: 18px; font-weight: 600; color: rgba(255, 255, 255, 0.8); padding: 8px 20px; background: rgba(59, 130, 246, 0.15); border-radius: 20px; }
.participant-info { display: flex; flex-direction: column; gap: 2px; }
.participant-info .name { font-size: 15px; font-weight: 600; color: #ffffff; }
.participant-info .dept { font-size: 12px; color: rgba(255, 255, 255, 0.6); }
.avatar-placeholder img { width: 100%; height: 100%; border-radius: 50%; object-fit: cover; }
.avatar-text { color: #ffffff; font-weight: 600; }
.scanning-animation { margin-bottom: 24px; animation: float 3s ease-in-out infinite; }
.empty-pool h3 { font-size: 28px; font-weight: 600; color: rgba(255, 255, 255, 0.9); margin: 0 0 12px 0; }
.empty-pool p { font-size: 16px; color: rgba(255, 255, 255, 0.6); margin: 0 0 20px 0; }
.mobile-hint { display: flex; align-items: center; gap: 8px; padding: 12px 24px; background: rgba(59, 130, 246, 0.1); border-radius: 24px; color: #93C5FD; font-size: 14px; }
.result-header { text-align: center; margin-bottom: 40px; }
.result-header h2 { font-size: 36px; font-weight: 700; background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 100%); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; margin: 0; animation: slideUp 0.6s ease; }
.winner-rank { position: absolute; top: -8px; left: -8px; width: 32px; height: 32px; background: linear-gradient(135deg, #3B82F6, #1D4ED8); border-radius: 50%; display: flex; align-items: center; justify-content: center; font-size: 16px; font-weight: 700; color: #ffffff; box-shadow: 0 4px 12px rgba(59, 130, 246, 0.4); }
.winner-card { position: relative; }
.result-actions { display: flex; gap: 16px; justify-content: center; margin-top: 48px; flex-wrap: wrap; }
.result-actions .el-button { padding: 14px 32px; font-size: 16px; font-weight: 600; }

/* Stop Button */
.stop-button-section { margin-top: 40px; text-align: center; }
.stop-button-section .el-button { padding: 16px 48px; font-size: 20px; font-weight: 700; animation: pulse 1.5s infinite; }

/* IDLE Notice */
.idle-notice { display: flex; align-items: flex-start; gap: 20px; padding: 60px 40px; background: rgba(59, 130, 246, 0.1); border-radius: 16px; border: 1px solid rgba(59, 130, 246, 0.2); max-width: 800px; margin: 100px auto; }
.notice-icon { font-size: 48px; color: #60A5FA; flex-shrink: 0; }
.notice-content h3 { font-size: 24px; font-weight: 600; color: #93C5FD; margin: 0 0 12px 0; }
.notice-content p { font-size: 16px; color: rgba(255, 255, 255, 0.7); margin: 0; line-height: 1.6; }

/* Summary View */
.summary-container { padding: 40px 60px; max-width: 1400px; margin: 0 auto; animation: fadeIn 0.6s ease; }
.summary-header { text-align: center; margin-bottom: 60px; }
.summary-header h1 { font-size: 48px; font-weight: 700; background: linear-gradient(135deg, #fbbf24 0%, #f59e0b 50%, #f59e0b 100%); -webkit-background-clip: text; background-clip: text; -webkit-text-fill-color: transparent; margin: 0 0 16px 0; }
.summary-header p { font-size: 20px; color: rgba(255, 255, 255, 0.7); margin: 0; }
.rounds-summary { display: flex; flex-direction: column; gap: 32px; margin-bottom: 60px; }
.round-block { background: linear-gradient(135deg, rgba(59, 130, 246, 0.1) 0%, rgba(37, 99, 235, 0.05) 100%); border-radius: 20px; border: 2px solid rgba(59, 130, 246, 0.3); padding: 32px; animation: slideUp 0.6s ease both; }
.round-header { display: flex; align-items: center; gap: 20px; margin-bottom: 24px; padding-bottom: 20px; border-bottom: 2px solid rgba(59, 130, 246, 0.2); }
.round-number { font-size: 18px; font-weight: 600; color: #60A5FA; padding: 8px 16px; background: rgba(59, 130, 246, 0.2); border-radius: 12px; }
.round-header h3 { flex: 1; font-size: 28px; font-weight: 700; color: #93C5FD; margin: 0; }
.round-count { font-size: 16px; color: rgba(255, 255, 255, 0.7); }
.round-winners { display: grid; grid-template-columns: repeat(auto-fill, minmax(200px, 1fr)); gap: 16px; }
.summary-winner-card { display: flex; align-items: center; gap: 12px; padding: 16px 20px; background: rgba(255, 255, 255, 0.05); border-radius: 12px; border: 1px solid rgba(255, 255, 255, 0.1); transition: all 0.3s ease; }
.summary-winner-card:hover { background: rgba(255, 255, 255, 0.08); border-color: rgba(59, 130, 246, 0.4); transform: translateY(-2px); }
.winner-badge { width: 32px; height: 32px; border-radius: 50%; background: linear-gradient(135deg, #fbbf24, #f59e0b); display: flex; align-items: center; justify-content: center; font-size: 14px; font-weight: 700; color: #000; flex-shrink: 0; }
.winner-info { flex: 1; min-width: 0; }
.winner-name { font-size: 16px; font-weight: 600; color: #ffffff; margin-bottom: 4px; }
.winner-dept { font-size: 13px; color: rgba(255, 255, 255, 0.6); }
.no-winners { text-align: center; padding: 40px; color: rgba(255, 255, 255, 0.4); font-size: 16px; }
.summary-actions { text-align: center; }
.summary-actions .el-button { padding: 16px 48px; font-size: 18px; font-weight: 600; }
</style>
