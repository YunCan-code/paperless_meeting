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
      <div class="status-control">
        <el-tag v-if="meetingStatus === 'finished'" type="success" size="large" effect="dark">已结束</el-tag>
        <el-tag v-else-if="meetingStatus === 'active'" type="primary" size="large" effect="dark">进行中</el-tag>
        <el-button v-else type="primary" size="large" @click="startNextRound">
           开始抽签 (草稿)
        </el-button>
        <!-- Reset Button (Small) -->
        <el-popconfirm title="确定要重置由于抽签记录吗？所有中奖数据将被清空！" @confirm="resetLottery">
             <template #reference>
                <el-button type="info" link size="small" style="margin-left:8px">重置</el-button>
             </template>
        </el-popconfirm>
      </div>
    </div>

    <!-- Finished Overlay -->
    <div v-if="allFinished" class="finished-overlay">
        <div class="finished-content">
            <el-icon class="finished-icon"><Trophy /></el-icon>
            <h1>抽签活动圆满结束</h1>
            <p>感谢大家的参与！</p>
            <el-button type="primary" plain @click="resetLottery" class="reset-link">
               重新开始
            </el-button>
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
           <!-- Add Button -->
           <div class="add-participant-row">
              <el-button type="primary" plain size="small" style="width:100%" @click="handleAddParticipantClick">
                 + 手动添加参与者
              </el-button>
           </div>

          <div v-for="user in participants" :key="user.id" 
               class="participant-item" 
               :class="{ 'is-winner': isWinner(user.id) }">
            <div class="participant-info">
              <span class="participant-name">
                  {{ user.name }}
                  <el-icon v-if="isWinner(user.id)" class="winner-icon"><Trophy /></el-icon>
              </span>
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
        <!-- Reset Button in Panel Footer -->
        <div style="padding: 10px; border-top: 1px solid #eee; text-align: center;">
             <el-popconfirm title="确定要重置吗？" @confirm="resetLottery">
                <template #reference>
                   <el-button type="danger" link size="small">重置本场抽签</el-button>
                </template>
             </el-popconfirm>
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

          <div class="winners-grid" v-if="winners.length > 0">
            <div v-for="winner in winners" :key="winner.id" class="winner-card">
              <div class="winner-avatar">{{ winner.name.substring(0,1) }}</div>
              <div class="winner-info">
                <div class="winner-name">{{ winner.name }}</div>
                <div class="winner-dept">{{ winner.department || '参会嘉宾' }}</div>
              </div>
            </div>
          </div>
          
          <div v-else class="empty-result-hint">
             🤔 本轮未产生中奖者 (候选人不足或已全部中奖)
          </div>

          <div class="controls">
            <el-button v-if="hasNextRound" type="primary" size="large" @click="waitForNextRound">
              等待下一轮
            </el-button>
            <div v-else class="finished-text">
               🎉 所有轮次已完成，感谢参与！
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

    <!-- Manual Add Dialog -->
    <el-dialog v-model="addDialogVisible" title="手动添加参与者" width="400px" append-to-body>
        <el-form :model="addForm" label-width="80px">
            <el-form-item label="姓名">
                <el-input v-model="addForm.name" placeholder="请输入姓名" />
            </el-form-item>
            <el-form-item label="部门">
                <el-input v-model="addForm.department" placeholder="请输入部门（选填）" />
            </el-form-item>
        </el-form>
        <template #footer>
            <div class="dialog-footer">
                <el-button @click="addDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="confirmAddParticipant">确认添加</el-button>
            </div>
        </template>
    </el-dialog>
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
const dataLoaded = ref(false) // Prevent UI flicker before socket data

// 数据
const participants = ref([]) 
const winners = ref([])
const historyWinners = ref([]) 
const allFinished = ref(false) // New: All Finished Flag
const fullRoundList = ref([]) // To store full list for status logic
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

// 计算会议整体抽签状态
const meetingStatus = computed(() => {
    if (!dataLoaded.value) return 'loading'
    if (fullRoundList.value.some(r => r.status === 'active')) return 'active'
    if (fullRoundList.value.some(r => r.status === 'pending')) return 'pending'
    if (fullRoundList.value.length > 0 && fullRoundList.value.every(r => r.status === 'finished')) return 'finished'
    return 'pending'
})

// 启动下一轮 (用于右上角按钮)
const startNextRound = () => {
    const next = fullRoundList.value.find(r => r.status === 'pending')
    if (next && socket) {
        socket.emit('lottery_action', {
            action: 'prepare',
            meeting_id: meetingId,
            lottery_id: next.round_id
        })
    }
}

// 重置抽签
const resetLottery = () => {
    if (socket) {
        socket.emit('lottery_action', {
            action: 'reset',
            meeting_id: meetingId
        })
    }
}

// Helper: Check Winner
const isWinner = (uid) => {
    // Check against historyWinners
    for (const round of historyWinners.value) {
        if (round.winners && round.winners.some(w => String(w.id) === String(uid))) {
            return true
        }
    }
    return false
}

// --- Socket Logic ---
const initSocket = () => {
  const url = import.meta.env.VITE_API_URL || ''
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket'],
    reconnection: true
  })

  socket.on('connect', () => {
    socketConnected.value = true
    socket.emit('join_meeting', { meeting_id: meetingId })
    // 主动同步
    socket.emit('get_lottery_state', { meeting_id: meetingId })
    socket.emit('lottery_action', { action: 'get_history', meeting_id: meetingId })
  })

  socket.on('disconnect', () => {
    socketConnected.value = false
  })

  // 1. 统一监听状态变更 (核心修复: 解决延迟和不同步)
  socket.on('lottery_state_change', (data) => {
    console.log('State changed:', data.status)
    
    // 同步基础信息
    if (data.config) {
        title.value = data.config.title || '抽签'
        targetCount.value = data.config.count || 1
    }
    
    // 状态机处理
    if (data.status === 'PREPARING') {
        phase.value = 'JOINING'
        stopAnimation()
        winners.value = []
    } 
    else if (data.status === 'ROLLING') {
        phase.value = 'ROLLING'
        if (!rollingTimer) {
            startAnimation()
        }
    } 
    else if (data.status === 'RESULT') {
        phase.value = 'RESULT'
        stopAnimation()
        if (data.last_result && data.last_result.winners) {
            winners.value = data.last_result.winners
            // 收到结果后，刷新一下右侧历史记录
            socket.emit('lottery_action', { action: 'get_history', meeting_id: meetingId })
        }
    }
    
    // Check All Finished
    if (data.all_finished !== undefined) {
        allFinished.value = data.all_finished
    }
  })

  // 2. 监听参与者列表更新 (修复: 确保大屏能看到人)
  socket.on('lottery_players_update', (data) => {
    // data: { count, all_participants, removed_user_id }
    if (data.all_participants) {
      participants.value = data.all_participants
    }
  })

  // 3. 监听初始状态同步 (用于刷新页面恢复)
  socket.on('lottery_state_sync', (data) => {
      // 模拟触发 state change
      if(data.status) {
          // Manually handle sync logic
          if (data.config) {
             title.value = data.config.title || '抽签'
             targetCount.value = data.config.count || 1
          }
          if (data.status === 'IDLE' || data.status === 'PREPARING') phase.value = 'JOINING'
          else if (data.status === 'ROLLING') { phase.value = 'ROLLING'; startAnimation(); }
          else if (data.status === 'RESULT') {
              phase.value = 'RESULT'
              stopAnimation()
              if (data.last_result) winners.value = data.last_result.winners || []
          }
      }
      if(data.all_participants) {
          participants.value = data.all_participants
      }
  })
  
  // 4. 监听历史
  socket.on('lottery_history', (data) => {
      fullRoundList.value = (data.rounds || []).sort((a,b) => a.round_id - b.round_id)
      const finished = fullRoundList.value.filter(r => r.status === 'finished')
      historyWinners.value = finished.map(r => ({
          title: r.title, winners: r.winners || []
      }))
      dataLoaded.value = true
      totalRounds.value = fullRoundList.value.length
      const activeOrPending = fullRoundList.value.findIndex(r => r.status === 'active' || r.status === 'pending')
      if (activeOrPending !== -1) currentRoundIndex.value = activeOrPending + 1
      else currentRoundIndex.value = totalRounds.value
  })

  // 5. 监听列表更新
  socket.on('lottery_list_update', () => {
      socket.emit('lottery_action', { action: 'get_history', meeting_id: meetingId })
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
    // Auto-advance logic
    startNextRound()
}

const closePage = () => {
  // Removed window.close()
}

// Manual Add
const addDialogVisible = ref(false)
const addForm = ref({ name: '', department: '' })

const handleAddParticipantClick = () => {
    addForm.value = { name: '', department: '' }
    addDialogVisible.value = true
}

const confirmAddParticipant = () => {
    if(!addForm.value.name) return
    if(socket) {
        socket.emit('lottery_action', {
            action: 'admin_add_participant',
            meeting_id: meetingId,
            user: {
                name: addForm.value.name,
                department: addForm.value.department
            }
        })
    }
    addDialogVisible.value = false
}

// 移除参与者
const removeParticipant = (user) => {
  participants.value = participants.value.filter(p => p.id !== user.id)
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
/* ========== 深色科技主题 ========== */
.lottery-screen {
  width: 100vw;
  height: 100vh;
  position: relative;
  background: linear-gradient(135deg, #0f0c29 0%, #302b63 50%, #24243e 100%);
  color: #ffffff;
  overflow: hidden;
  font-family: 'Inter', -apple-system, BlinkMacSystemFont, sans-serif;
}

/* 动态粒子背景 */
.background-layer {
  position: absolute;
  top: 0;
  left: 0;
  width: 100%;
  height: 100%;
  background-image: 
    radial-gradient(2px 2px at 20px 30px, rgba(255,255,255,0.15), transparent),
    radial-gradient(2px 2px at 40px 70px, rgba(255,255,255,0.1), transparent),
    radial-gradient(2px 2px at 50px 160px, rgba(255,255,255,0.15), transparent),
    radial-gradient(2px 2px at 90px 40px, rgba(255,255,255,0.1), transparent),
    radial-gradient(2px 2px at 130px 80px, rgba(255,255,255,0.15), transparent),
    radial-gradient(2px 2px at 160px 120px, rgba(255,255,255,0.1), transparent);
  background-size: 200px 200px;
  animation: particleMove 20s linear infinite;
  z-index: 0;
}

@keyframes particleMove {
  0% { background-position: 0 0; }
  100% { background-position: 200px 200px; }
}

/* 顶部状态栏 - 半透明毒砂玻璃效果 */
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
  background: rgba(15, 12, 41, 0.8);
  backdrop-filter: blur(10px);
  border-bottom: 1px solid rgba(255,255,255,0.1);
  z-index: 100;
}

.meeting-title {
  font-size: 20px;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 10px;
  color: #ffd700;
}

.round-progress {
  font-size: 16px;
  display: flex;
  align-items: center;
  gap: 8px;
}
.progress-label { color: rgba(255,255,255,0.6); }
.progress-value { color: #00d9ff; font-weight: bold; text-shadow: 0 0 10px rgba(0,217,255,0.5); }

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
  position: relative;
  z-index: 1;
}

.side-panel {
  width: 280px;
  background: rgba(15, 12, 41, 0.7);
  backdrop-filter: blur(10px);
  display: flex;
  flex-direction: column;
  border: 1px solid rgba(255,255,255,0.1);
}

.left-panel { border-right: 1px solid rgba(255,255,255,0.1); border-left: none; }
.right-panel { border-left: 1px solid rgba(255,255,255,0.1); border-right: none; }

.panel-header {
  padding: 16px 20px;
  border-bottom: 1px solid rgba(255,255,255,0.1);
  display: flex;
  justify-content: space-between;
  align-items: center;
  background: rgba(255,255,255,0.05);
}
.panel-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
}
.count-badge {
  background: linear-gradient(135deg, #00d9ff 0%, #0099ff 100%);
  color: #ffffff;
  padding: 2px 10px;
  border-radius: 12px;
  font-size: 12px;
  box-shadow: 0 0 10px rgba(0,217,255,0.3);
}

.participant-list, .history-list {
  flex: 1;
  overflow-y: auto;
  padding: 12px;
}

/* 参与者卡片 - 深色主题 */
.participant-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 8px 12px;
  background: rgba(255,255,255,0.08);
  border-radius: 8px;
  margin-bottom: 6px;
  font-size: 14px;
  border: 1px solid rgba(255,255,255,0.1);
  transition: all 0.3s ease;
}
.participant-item:hover {
  background: rgba(255,255,255,0.15);
  transform: translateX(3px);
}
.participant-info {
  flex: 1;
}
.participant-name { color: rgba(255,255,255,0.9); font-weight: 500; }
.participant-dept { color: rgba(255,255,255,0.5); font-size: 12px; }
.remove-btn { opacity: 0; transition: opacity 0.2s; }
.participant-item:hover .remove-btn { opacity: 1; }

/* 中奖者高亮 */
.participant-item.is-winner {
  background: linear-gradient(135deg, rgba(255,215,0,0.2) 0%, rgba(255,165,0,0.2) 100%);
  border-color: #ffd700;
  box-shadow: 0 0 15px rgba(255,215,0,0.3);
}
.participant-item.is-winner .participant-name {
  color: #ffd700;
  font-weight: bold;
  display: flex;
  align-items: center;
  gap: 4px;
}
.winner-icon { color: #ffd700; animation: pulse 1.5s ease infinite; }

@keyframes pulse {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}

.empty-hint {
  text-align: center;
  color: rgba(255,255,255,0.4);
  padding: 20px;
  font-size: 14px;
}

/* 抽签结果 - 深色主题 */
.history-round {
  margin-bottom: 12px;
  padding: 14px;
  background: rgba(255,255,255,0.08);
  border-radius: 10px;
  border: 1px solid rgba(255,255,255,0.1);
  backdrop-filter: blur(5px);
}
.round-header {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 10px;
  padding-bottom: 8px;
  border-bottom: 1px dashed rgba(255,255,255,0.2);
}
.round-index {
  background: linear-gradient(135deg, #00d9ff 0%, #0099ff 100%);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 11px;
  font-weight: 600;
}
.round-title {
  font-size: 14px;
  font-weight: 600;
  color: rgba(255,255,255,0.9);
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
  background: rgba(255,215,0,0.1);
  border: 1px solid rgba(255,215,0,0.3);
  padding: 8px 12px;
  border-radius: 8px;
  font-size: 13px;
}
.history-winner .winner-name {
  color: #ffd700;
  font-weight: 500;
}
.history-winner .winner-dept {
  color: rgba(255,255,255,0.5);
  font-size: 12px;
}

/* 中央区域 - 全屏深色 */
.center-area {
  flex: 1;
  display: flex;
  justify-content: center;
  align-items: center;
  background: transparent;
}

.phase-container {
  text-align: center;
  width: 100%;
  max-width: 800px;
  padding: 40px;
}

/* 奖项标题 - 霍光效果 */
.prize-title {
  font-size: 56px;
  font-weight: 800;
  margin-bottom: 30px;
  background: linear-gradient(135deg, #00d9ff 0%, #0099ff 50%, #00d9ff 100%);
  background-size: 200% 200%;
  -webkit-background-clip: text;
  background-clip: text;
  color: transparent;
  animation: shimmer 3s ease infinite;
  text-shadow: 0 0 30px rgba(0,217,255,0.3);
}

@keyframes shimmer {
  0%, 100% { background-position: 0% 50%; }
  50% { background-position: 100% 50%; }
}

.count-display {
  font-size: 20px;
  margin-bottom: 40px;
  color: rgba(255,255,255,0.6);
}

.count-display .number {
  color: #00d9ff;
  font-size: 48px;
  font-weight: bold;
  margin-left: 10px;
  text-shadow: 0 0 20px rgba(0,217,255,0.5);
}

.sub-title {
  font-size: 28px;
  color: rgba(255,255,255,0.8);
  margin-bottom: 20px;
}

/* 滚动框 - 霍光边框 */
.rolling-box {
  margin: 40px auto;
  width: 450px;
  height: 180px;
  background: rgba(15, 12, 41, 0.9);
  border: 3px solid #00d9ff;
  border-radius: 20px;
  display: flex;
  justify-content: center;
  align-items: center;
  box-shadow: 0 0 40px rgba(0,217,255,0.4), inset 0 0 30px rgba(0,217,255,0.1);
  animation: borderGlow 2s ease-in-out infinite;
}

@keyframes borderGlow {
  0%, 100% { box-shadow: 0 0 40px rgba(0,217,255,0.4), inset 0 0 30px rgba(0,217,255,0.1); }
  50% { box-shadow: 0 0 60px rgba(0,217,255,0.6), inset 0 0 40px rgba(0,217,255,0.2); }
}

.rolling-name {
  font-size: 48px;
  font-weight: bold;
  color: #ffd700;
  text-shadow: 0 0 20px rgba(255,215,0,0.5);
  animation: textFlash 0.1s ease infinite;
}

@keyframes textFlash {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.8; }
}

/* 恭喜标题 */
.congrats-title {
  font-size: 48px;
  color: #ffd700;
  margin-bottom: 16px;
  text-shadow: 0 0 30px rgba(255,215,0,0.5);
  animation: celebratePulse 1s ease infinite;
}

@keyframes celebratePulse {
  0%, 100% { transform: scale(1); }
  50% { transform: scale(1.05); }
}

.prize-subtitle {
  font-size: 26px;
  color: rgba(255,255,255,0.8);
  margin-bottom: 40px;
}

.winners-grid {
  display: flex;
  justify-content: center;
  flex-wrap: wrap;
  gap: 24px;
  margin-bottom: 40px;
}

/* 中奖卡片 - 金色边框 */
.winner-card {
  background: linear-gradient(135deg, rgba(255,215,0,0.15) 0%, rgba(255,165,0,0.1) 100%);
  color: #ffffff;
  width: 220px;
  padding: 28px;
  border-radius: 20px;
  display: flex;
  flex-direction: column;
  align-items: center;
  box-shadow: 0 0 30px rgba(255,215,0,0.3);
  border: 2px solid #ffd700;
  animation: winnerPopIn 0.6s cubic-bezier(0.175, 0.885, 0.32, 1.275) forwards;
  backdrop-filter: blur(10px);
}

@keyframes winnerPopIn {
  0% { transform: scale(0) rotate(-10deg); opacity: 0; }
  60% { transform: scale(1.1) rotate(3deg); }
  100% { transform: scale(1) rotate(0); opacity: 1; }
}

.winner-avatar {
  width: 72px;
  height: 72px;
  background: linear-gradient(135deg, #ffd700 0%, #ff8c00 100%);
  color: #0f172a;
  border-radius: 50%;
  font-size: 32px;
  display: flex;
  justify-content: center;
  align-items: center;
  margin-bottom: 16px;
  font-weight: bold;
  box-shadow: 0 0 20px rgba(255,215,0,0.5);
}

.winner-info { text-align: center; }
.winner-name { font-size: 36px; font-weight: 800; margin-bottom: 8px; color: #ffd700; line-height: 1.2; }
.winner-dept { font-size: 18px; color: rgba(255,255,255,0.6); font-weight: 500; }

.empty-result-hint {
    font-size: 28px;
    color: rgba(255,255,255,0.4);
    margin: 40px 0;
    font-weight: bold;
}

.finished-text {
    font-size: 24px;
    font-weight: bold;
    color: #ffd700;
    margin-top: 20px;
    text-shadow: 0 0 15px rgba(255,215,0,0.5);
}

.controls .el-button {
  font-size: 18px;
  padding: 16px 32px;
  border-radius: 24px;
}

@keyframes popIn {
  from { transform: scale(0.8); opacity: 0; }
  to { transform: scale(1); opacity: 1; }
}

/* 结束遮罩层 - 深色主题 */
.finished-overlay {
    position: fixed;
    top: 60px;
    left: 0;
    width: 100%;
    height: calc(100% - 60px);
    background: linear-gradient(135deg, rgba(15,12,41,0.98) 0%, rgba(48,43,99,0.98) 100%);
    z-index: 200;
    display: flex;
    justify-content: center;
    align-items: center;
    flex-direction: column;
}
.finished-content {
    text-align: center;
    animation: popIn 0.8s ease;
}
.finished-icon {
    font-size: 100px;
    color: #ffd700;
    margin-bottom: 30px;
    animation: celebratePulse 1.5s ease infinite;
}
.finished-content h1 {
    font-size: 52px;
    color: #ffd700;
    margin-bottom: 16px;
    text-shadow: 0 0 30px rgba(255,215,0,0.5);
}
.finished-content p {
    font-size: 24px;
    color: rgba(255,255,255,0.6);
    margin-bottom: 40px;
}
</style>
