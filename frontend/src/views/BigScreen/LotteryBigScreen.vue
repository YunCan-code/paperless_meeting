<template>
  <div class="lottery-screen">
    <!-- 背景层 -->
    <div class="background-layer"></div>

    <!-- 顶部状态栏 -->
    <div class="header">
      <div class="meeting-title">
        <el-icon><Trophy /></el-icon>
        <span>抽签大屏</span>
      </div>
      <div class="round-progress" v-if="totalRounds > 0">
        <span class="progress-label">轮次进度:</span>
        <span class="progress-value">第 {{ currentRoundIndex }} 轮 / 共 {{ totalRounds }} 轮</span>
      </div>
      <div class="connection-status">
        <span class="status-dot" :class="{ connected: socketConnected }"></span>
        {{ socketConnected ? '已连接' : '连接断开' }}
      </div>
    </div>

    <!-- 三栏主体 -->
    <div class="main-layout">
      
      <!-- 左侧栏: 参与人员列表 -->
      <div class="side-panel left-panel">
        <div class="panel-header">
          <h3>参与人员</h3>
          <span class="count-badge">{{ participants.length }} 人</span>
        </div>
        <div class="participant-list">
          <div v-for="user in participants" :key="user.id" class="participant-item">
            <div class="participant-info">
              <span class="participant-name">{{ user.name }}</span>
              <span class="participant-dept" v-if="user.department">({{ user.department }})</span>
            </div>
            <el-button 
              type="danger" 
              size="small" 
              link 
              class="remove-btn"
              @click="removeParticipant(user)"
            >
              移除
            </el-button>
          </div>
          <div v-if="participants.length === 0" class="empty-hint">
            等待参与者加入...
          </div>
        </div>
      </div>

      <!-- 中央区域: 主抽签区 -->
      <div class="center-area">
        
        <!-- 阶段1: 等待/准备 -->
        <div v-if="phase === 'JOINING'" class="phase-container joining">
          <h1 class="prize-title">{{ title }}</h1>
          <div class="count-display">
            <span class="label">当前参与抽签人数</span>
            <span class="number">{{ participants.length }}</span>
          </div>
          
          <div class="controls">
            <el-button 
              type="primary" 
              size="large" 
              @click="startRolling" 
              :disabled="participants.length === 0"
            >
              开始第 {{ currentRoundIndex }} 轮抽签 - 抽取 {{ targetCount }} 人
            </el-button>
          </div>
        </div>

        <!-- 阶段2: 滚动中 -->
        <div v-if="phase === 'ROLLING'" class="phase-container rolling">
          <h2 class="sub-title">正在为 {{ title }} 抽签...</h2>
          
          <div class="rolling-box">
            <div class="rolling-name">{{ rollingName }}</div>
          </div>

          <div class="controls">
            <el-button type="danger" size="large" @click="stopRolling">
              停！
            </el-button>
          </div>
        </div>

        <!-- 阶段3: 结果展示 -->
        <div v-if="phase === 'RESULT'" class="phase-container result">
          <h1 class="congrats-title">🎉 恭喜 🎉</h1>
          <h2 class="prize-subtitle">{{ title }} 中签名单</h2>

          <div class="winners-grid">
            <div v-for="winner in winners" :key="winner.id" class="winner-card">
              <div class="winner-avatar">{{ winner.name.substring(0,1) }}</div>
              <div class="winner-info">
                <div class="winner-name">{{ winner.name }}</div>
                <div class="winner-dept">{{ winner.department || '参会嘉宾' }}</div>
              </div>
            </div>
          </div>

          <div class="controls">
            <el-button v-if="hasNextRound" type="primary" size="large" @click="waitForNextRound">
              等待下一轮
            </el-button>
            <el-button v-else @click="closePage">
              抽签结束
            </el-button>
          </div>
        </div>

      </div>

      <!-- 右侧栏: 抽签结果 -->
      <div class="side-panel right-panel">
        <div class="panel-header">
          <h3>抽签结果</h3>
          <el-button size="small" text @click="toggleSortOrder" v-if="historyWinners.length > 1">
            {{ sortAsc ? '正序' : '逆序' }}
          </el-button>
        </div>
        <div class="history-list">
          <div v-for="(round, idx) in sortedHistoryWinners" :key="idx" class="history-round">
            <div class="round-header">
              <span class="round-index">第 {{ sortAsc ? idx + 1 : historyWinners.length - idx }} 轮</span>
              <span class="round-title">{{ round.title }}</span>
            </div>
            <div class="round-winners">
              <div v-for="w in round.winners" :key="w.id" class="history-winner">
                <span class="winner-name">{{ w.name }}</span>
                <span class="winner-dept" v-if="w.department">{{ w.department }}</span>
              </div>
            </div>
          </div>
          <div v-if="historyWinners.length === 0" class="empty-hint">
            暂无抽签记录
          </div>
        </div>
      </div>

    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { Trophy } from '@element-plus/icons-vue'
import io from 'socket.io-client'

const route = useRoute()
const meetingId = route.params.meetingId

// 基础数据
const title = ref(route.query.title || '抽签')
const targetCount = ref(parseInt(route.query.count) || 1)

// 轮次信息
const currentRoundIndex = ref(1)
const totalRounds = ref(0)

// 状态: JOINING, ROLLING, RESULT
const phase = ref('JOINING')

// 数据
const participants = ref([]) // {id, name, department, ...}
const winners = ref([])
const historyWinners = ref([]) // [{title, winners: [{id, name}]}]
const socketConnected = ref(false)
let socket = null

// 滚动动画
const rollingName = ref('???')
let rollingTimer = null

// 是否还有下一轮
const hasNextRound = computed(() => {
  return currentRoundIndex.value < totalRounds.value
})

// 排序控制
const sortAsc = ref(true)
const sortedHistoryWinners = computed(() => {
  if (sortAsc.value) {
    return historyWinners.value
  } else {
    return [...historyWinners.value].reverse()
  }
})
const toggleSortOrder = () => {
  sortAsc.value = !sortAsc.value
}

// --- Socket Logic ---
const initSocket = () => {
  const url = import.meta.env.VITE_API_URL || 'http://localhost:8001'
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket'],
    reconnection: true
  })

  socket.on('connect', () => {
    socketConnected.value = true
    socket.emit('join_meeting', { meeting_id: meetingId })
  })

  socket.on('disconnect', () => {
    socketConnected.value = false
  })
  
  // 监听准备/配置更新
  socket.on('lottery_prepare', (data) => {
    // data: { title, count, allow_repeat, pool_size, round_index, total_rounds, history_winners }
    phase.value = 'JOINING'
    title.value = data.title
    targetCount.value = data.count
    currentRoundIndex.value = data.round_index || 1
    totalRounds.value = data.total_rounds || 1
    winners.value = []
    
    // 更新历史中奖名单
    if (data.history_winners) {
      historyWinners.value = data.history_winners
    }
  })

  // 监听加入
  socket.on('lottery_players_update', (data) => {
    // data: { count, latest_user }
    if (data.latest_user) {
      if (!participants.value.find(u => u.id === data.latest_user.id)) {
        participants.value.push(data.latest_user)
      }
    }
    // 如果有全量参与者列表
    if (data.all_participants) {
      participants.value = data.all_participants
    }
    // 处理移除的用户
    if (data.removed_user_id) {
      participants.value = participants.value.filter(p => p.id !== data.removed_user_id)
    }
  })

  // 监听开始
  socket.on('lottery_start', () => {
    phase.value = 'ROLLING'
    startAnimation()
  })

  // 监听停止
  socket.on('lottery_stop', (data) => {
    winners.value = data.winners || []
    phase.value = 'RESULT'
    stopAnimation()
    
    // 将本轮结果加入历史
    if (winners.value.length > 0) {
      historyWinners.value.push({
        title: title.value,
        winners: [...winners.value]
      })
    }
  })
}

const startRolling = () => {
  socket.emit('lottery_action', {
    action: 'start',
    meeting_id: meetingId
  })
}

const stopRolling = () => {
  socket.emit('lottery_action', {
    action: 'stop',
    meeting_id: meetingId,
    count: targetCount.value
  })
}

const waitForNextRound = () => {
  // 返回JOINING状态等待管理员启动下一轮
  phase.value = 'JOINING'
  winners.value = []
}

const closePage = () => {
  window.close()
}

// 移除参与者
const removeParticipant = (user) => {
  // 从本地列表移除
  participants.value = participants.value.filter(p => p.id !== user.id)
  // 通知后端
  if (socket) {
    socket.emit('lottery_action', {
      action: 'remove_participant',
      meeting_id: meetingId,
      user_id: user.id
    })
  }
}

// 动画逻辑
const startAnimation = () => {
  if (participants.value.length === 0) return
  clearInterval(rollingTimer)
  rollingTimer = setInterval(() => {
    const randomIndex = Math.floor(Math.random() * participants.value.length)
    const user = participants.value[randomIndex]
    rollingName.value = user.department ? `${user.name} (${user.department})` : user.name
  }, 50)
}

const stopAnimation = () => {
  clearInterval(rollingTimer)
}

onMounted(() => {
  initSocket()
})

onUnmounted(() => {
  if (socket) socket.disconnect()
  clearInterval(rollingTimer)
})
</script>

<style scoped>
.lottery-screen {
  width: 100vw;
  height: 100vh;
  position: relative;
  background: linear-gradient(135deg, #f8fafc 0%, #e2e8f0 100%);
  color: #0f172a;
  overflow: hidden;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

.header {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  height: 60px;
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 0 30px;
  background: #ffffff;
  border-bottom: 1px solid #e2e8f0;
  z-index: 100;
}

.meeting-title {
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #0f172a;
}

.round-progress {
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-label { color: #64748b; }
.progress-value { color: #3b82f6; font-weight: bold; }

.connection-status {
  font-size: 14px;
  color: #64748b;
  display: flex;
  align-items: center;
  gap: 6px;
}

.status-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background-color: #ef4444;
}
.status-dot.connected {
  background-color: #22c55e;
}

/* 三栏布局 */
.main-layout {
  display: flex;
  height: calc(100vh - 60px);
  margin-top: 60px;
}

.side-panel {
  width: 280px;
  background: #ffffff;
  display: flex;
  flex-direction: column;
  border: 1px solid #e2e8f0;
}

.left-panel { border-right: 1px solid #e2e8f0; border-left: none; }
.right-panel { border-left: 1px solid #e2e8f0; border-right: none; }

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid #e2e8f0;
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: #f8fafc;
}
.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: #334155;
}
.count-badge {
  background: #3b82f6;
  color: #ffffff;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
}

.participant-list, .history-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

.participant-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: #f8fafc;
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 14px;
  border: 1px solid #e2e8f0;
  transition: background 0.2s;
}
.participant-item:hover {
  background: #f1f5f9;
}
.participant-info {
  flex: 1;
}
.participant-name { color: #0f172a; font-weight: 500; }
.participant-dept { color: #64748b; font-size: 12px; }
.remove-btn { opacity: 0; transition: opacity 0.2s; }
.participant-item:hover .remove-btn { opacity: 1; }

.empty-hint {
  text-align: center;
  color: #94a3b8;
  padding: 20px;
  font-size: 14px;
}

/* 抽签结果 */
.history-round {
  margin-bottom: 12px;
  padding: 14px;
  background: #ffffff;
  border-radius: 10px;
  border: 1px solid #e2e8f0;
  box-shadow: 0 1px 3px rgba(0,0,0,0.04);
}
.round-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed #e2e8f0;
}
.round-index {
  background: #3b82f6;
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.round-title {
  font-size: 14px;
  font-weight: 600;
  color: #334155;
}
.round-winners {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.history-winner {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #f8fafc;
  border: 1px solid #e2e8f0;
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.history-winner .winner-name {
  color: #0f172a;
  font-weight: 500;
}
.history-winner .winner-dept {
  color: #64748b;
  font-size: 12px;
}

/* 中央区域 */
.center-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: #f8fafc;
}

.phase-container {
  text-align: center;
  width: 100%;
  max-width: 800px;
  padding: 40px;
}

.prize-title {
  font-size: 52px;
  font-weight: 800;
  margin-bottom: 30px;
  background: linear-gradient(to right, #3b82f6, #2563eb);
  -webkit-background-clip: text;
  color: transparent;
}

.count-display {
  font-size: 20px;
  margin-bottom: 40px;
  color: #64748b;
}

.count-display .number {
  color: #3b82f6;
  font-size: 40px;
  font-weight: bold;
  margin-left: 10px;
}

.sub-title {
  font-size: 28px;
  color: #334155;
  margin-bottom: 20px;
}

.rolling-box {
  margin: 40px auto;
  width: 400px;
  height: 160px;
  background: #ffffff;
  border: 3px solid #3b82f6;
  border-radius: 16px;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 4px 24px rgba(59, 130, 246, 0.2);
}

.rolling-name {
  font-size: 42px;
  font-weight: bold;
  color: #0f172a;
}

.congrats-title {
  font-size: 42px;
  color: #22c55e;
  margin-bottom: 16px;
}

.prize-subtitle {
  font-size: 26px;
  color: #334155;
  margin-bottom: 40px;
}

.winners-grid {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 40px;
}

.winner-card {
  background: #ffffff;
  color: #0f172a;
  width: 200px;
  padding: 24px;
  border-radius: 16px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 4px 16px rgba(0,0,0,0.08);
  border: 1px solid #e2e8f0;
  animation: popIn 0.5s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
}

.winner-avatar {
  width: 64px;
  height: 64px;
  background: linear-gradient(135deg, #3b82f6 0%, #2563eb 100%);
  color: white;
  border-radius: 50%;
  font-size: 28px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
  font-weight: bold;
}

.winner-info { text-align: center; }
.winner-name { font-size: 22px; font-weight: bold; margin-bottom: 4px; color: #0f172a; }
.winner-dept { font-size: 14px; color: #64748b; }

.controls .el-button {
  font-size: 18px;
  padding: 16px 32px;
  border-radius: 24px;
}

@keyframes popIn {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}
</style>
