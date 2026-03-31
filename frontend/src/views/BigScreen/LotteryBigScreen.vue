<template>
  <div class="lottery-screen">
    <section class="screen-header">
      <div>
        <span class="eyebrow">会议互动中心 / 抽签大屏</span>
        <h1>{{ currentRoundTitle }}</h1>
        <p>{{ sessionSummary }}</p>
      </div>

      <div class="header-metrics">
        <div class="metric-box">
          <span>会话状态</span>
          <strong>{{ sessionStatusLabel }}</strong>
        </div>
        <div class="metric-box">
          <span>参与池</span>
          <strong>{{ session.participants_count || 0 }} 人</strong>
        </div>
        <div class="metric-box">
          <span>已完成轮次</span>
          <strong>{{ finishedRoundsCount }}/{{ rounds.length }}</strong>
        </div>
      </div>
    </section>

    <section class="screen-body">
      <div class="main-panel">
        <div class="panel-toolbar">
          <div class="toolbar-status" :class="`status-${session.session_status}`">
            <span class="dot" />
            {{ sessionStatusLabel }}
          </div>
          <div class="toolbar-actions">
            <el-button
              v-if="!session.current_round && nextRound"
              type="primary"
              size="large"
              :loading="actionLoading"
              @click="prepareRound(nextRound)"
            >
              准备下一轮
            </el-button>
            <el-button
              v-if="session.current_round && ['collecting', 'ready', 'result', 'completed', 'idle'].includes(session.session_status)"
              type="primary"
              size="large"
              :loading="actionLoading"
              :disabled="session.participants_count === 0 || session.current_round.status === 'finished'"
              @click="startRoll"
            >
              开始滚动
            </el-button>
            <el-button
              v-if="session.session_status === 'rolling'"
              type="danger"
              size="large"
              :loading="actionLoading"
              @click="stopRoll"
            >
              停止开奖
            </el-button>
            <el-button plain size="large" :loading="actionLoading" @click="resetSession">
              重置会话
            </el-button>
          </div>
        </div>

        <div v-if="!rounds.length" class="empty-stage">
          <h2>还没有抽签轮次</h2>
          <p>请先在后台互动中心创建轮次，再进入大屏控制现场节奏。</p>
        </div>

        <template v-else>
          <div v-if="session.session_status === 'rolling'" class="rolling-stage">
            <div class="rolling-card">
              <span class="rolling-label">{{ currentRoundTitle }}</span>
              <strong>{{ rollingDisplay }}</strong>
              <p>主持人停止后立即生成本轮中奖结果</p>
            </div>
          </div>

          <div v-else-if="session.winners?.length" class="winner-stage">
            <article v-for="winner in session.winners" :key="winner.user_id || winner.id" class="winner-card">
              <span class="winner-badge">中签</span>
              <h2>{{ winner.name || winner.user_name }}</h2>
              <p>{{ winner.department || '会议参会人员' }}</p>
            </article>
          </div>

          <div v-else class="pool-stage">
            <div class="pool-copy">
              <h2>{{ session.current_round ? `当前轮次：${session.current_round.title}` : '等待准备轮次' }}</h2>
              <p>
                {{
                  session.current_round
                    ? `本轮抽取 ${session.current_round.count} 人，移动端成员可在会议详情页主动加入或退出抽签池。`
                    : '选择一轮后即可进入准备状态，现场成员加入后再开始滚动。'
                }}
              </p>
            </div>

            <div v-if="participants.length" class="participant-grid">
              <div
                v-for="participant in participants"
                :key="participant.user_id"
                class="participant-card"
                :class="{ winner: participant.is_winner }"
              >
                <strong>{{ participant.name }}</strong>
                <span>{{ participant.department || '参会人员' }}</span>
              </div>
            </div>
            <el-empty v-else description="等待成员加入抽签池" :image-size="120" />
          </div>
        </template>
      </div>

      <aside class="side-panel">
        <section class="side-card">
          <div class="side-head">
            <h3>轮次编排</h3>
            <span>{{ rounds.length }} 轮</span>
          </div>
          <div class="round-list">
            <button
              v-for="round in rounds"
              :key="round.id"
              type="button"
              class="round-item"
              :class="{
                active: session.current_round_id === round.id,
                finished: round.status === 'finished'
              }"
              @click="prepareRound(round)"
              :disabled="round.status === 'finished' || actionLoading"
            >
              <div>
                <strong>{{ round.title }}</strong>
                <span>抽取 {{ round.count }} 人</span>
              </div>
              <el-tag size="small" :type="round.status === 'finished' ? 'success' : round.status === 'ready' ? 'warning' : 'info'">
                {{ getRoundStatusLabel(round.status) }}
              </el-tag>
            </button>
          </div>
        </section>

        <section class="side-card">
          <div class="side-head">
            <h3>历史结果</h3>
            <span>{{ winnerHistory.length }} 条</span>
          </div>
          <div v-if="winnerHistory.length" class="history-list">
            <div v-for="history in winnerHistory" :key="history.id" class="history-item">
              <strong>{{ history.title }}</strong>
              <div class="history-winners">
                <span v-for="winner in history.winners" :key="winner.id">{{ winner.user_name }}</span>
              </div>
            </div>
          </div>
          <el-empty v-else description="尚未产生中奖结果" :image-size="80" />
        </section>
      </aside>
    </section>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { io } from 'socket.io-client'
import { ElMessage, ElMessageBox } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const meetingId = Number(route.params.meetingId)

const session = ref({
  session_status: 'idle',
  current_round_id: null,
  current_round: null,
  participants: [],
  participants_count: 0,
  winners: [],
  rounds: []
})
const actionLoading = ref(false)
const rollingDisplay = ref('准备开始')

let socket = null
let rollingTimer = null

const rounds = computed(() => Array.isArray(session.value.rounds) ? session.value.rounds : [])
const participants = computed(() => Array.isArray(session.value.participants) ? session.value.participants : [])
const finishedRoundsCount = computed(() => rounds.value.filter(item => item.status === 'finished').length)
const nextRound = computed(() => rounds.value.find(item => item.status !== 'finished') || null)
const winnerHistory = computed(() => rounds.value.filter(item => Array.isArray(item.winners) && item.winners.length > 0))

const sessionStatusLabel = computed(() => ({
  idle: '空闲',
  collecting: '收集中',
  ready: '准备就绪',
  rolling: '滚动中',
  result: '结果展示中',
  completed: '全部完成'
}[session.value.session_status] || '未知状态'))

const currentRoundTitle = computed(() => session.value.current_round?.title || nextRound.value?.title || '抽签活动')

const sessionSummary = computed(() => {
  if (!rounds.value.length) {
    return '先在后台创建轮次，再由现场大屏控制开始、停止与结果展示。'
  }
  if (session.value.session_status === 'rolling') {
    return `当前正在滚动 ${currentRoundTitle.value}，主持人可随时停止并开奖。`
  }
  if (session.value.winners?.length) {
    return `本轮已产生 ${session.value.winners.length} 位中奖者，可继续准备下一轮或保留结果展示。`
  }
  return session.value.current_round
    ? `当前轮次将抽取 ${session.value.current_round.count} 人，等待主持控制开始。`
    : '当前还没有准备好的轮次，可从右侧直接选择并准备。'
})

const clearRollingTimer = () => {
  if (rollingTimer) {
    clearInterval(rollingTimer)
    rollingTimer = null
  }
}

const startRollingAnimation = () => {
  clearRollingTimer()
  const source = participants.value.filter(item => !item.is_winner || session.value.current_round?.allow_repeat)
  if (!source.length) {
    rollingDisplay.value = '等待参与者'
    return
  }
  rollingDisplay.value = source[0].name
  rollingTimer = window.setInterval(() => {
    const pick = source[Math.floor(Math.random() * source.length)]
    rollingDisplay.value = pick?.name || '等待参与者'
  }, 120)
}

const applySnapshot = (payload) => {
  if (!payload || Number(payload.meeting_id) !== meetingId) return
  session.value = {
    session_status: payload.session_status || 'idle',
    current_round_id: payload.current_round_id || null,
    current_round: payload.current_round || null,
    participants: Array.isArray(payload.participants) ? payload.participants : [],
    participants_count: payload.participants_count || 0,
    winners: Array.isArray(payload.winners) ? payload.winners : [],
    rounds: Array.isArray(payload.rounds) ? payload.rounds : []
  }

  if (session.value.session_status === 'rolling') {
    startRollingAnimation()
  } else {
    clearRollingTimer()
  }
}

const fetchSession = async () => {
  try {
    const payload = await request.get(`/lottery/${meetingId}/session`)
    applySnapshot(payload)
    if (!socket) {
      connectSocket()
    }
  } catch (error) {
    session.value = {
      session_status: 'idle',
      current_round_id: null,
      current_round: null,
      participants: [],
      participants_count: 0,
      winners: [],
      rounds: []
    }
  }
}

const connectSocket = () => {
  if (socket) return
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket', 'polling'],
    reconnection: true
  })

  socket.on('connect', () => {
    socket.emit('join_meeting', { meeting_id: meetingId })
  })
  socket.on('lottery_session_change', applySnapshot)
}

const disconnectSocket = () => {
  if (!socket) return
  socket.disconnect()
  socket = null
}

const prepareRound = async (round) => {
  if (!round || round.status === 'finished') return
  actionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${meetingId}/prepare`, { lottery_id: round.id })
    applySnapshot(payload)
    ElMessage.success(`已准备轮次「${round.title}」`)
  } finally {
    actionLoading.value = false
  }
}

const startRoll = async () => {
  actionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${meetingId}/roll`)
    applySnapshot(payload)
    ElMessage.success('抽签已开始滚动')
  } finally {
    actionLoading.value = false
  }
}

const stopRoll = async () => {
  actionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${meetingId}/stop`)
    applySnapshot(payload)
    ElMessage.success('本轮开奖结果已生成')
  } finally {
    actionLoading.value = false
  }
}

const resetSession = async () => {
  try {
    await ElMessageBox.confirm('确定重置当前会议的抽签会话吗？将清空参与池与轮次中奖结果。', '重置确认', {
      type: 'warning',
      confirmButtonText: '重置',
      cancelButtonText: '取消'
    })
  } catch {
    return
  }

  actionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${meetingId}/reset`)
    applySnapshot(payload)
    ElMessage.success('抽签会话已重置')
  } finally {
    actionLoading.value = false
  }
}

const getRoundStatusLabel = (status) => ({
  draft: '草稿',
  ready: '已准备',
  finished: '已完成'
}[status] || status)

onMounted(fetchSession)

onUnmounted(() => {
  clearRollingTimer()
  disconnectSocket()
})
</script>

<style scoped>
.lottery-screen {
  min-height: 100vh;
  padding: 32px;
  background:
    radial-gradient(circle at top left, rgba(14, 165, 233, 0.16), transparent 22%),
    radial-gradient(circle at bottom right, rgba(245, 158, 11, 0.14), transparent 22%),
    linear-gradient(145deg, #0f172a 0%, #111827 46%, #1e293b 100%);
  color: #e5eefb;
}

.screen-header,
.main-panel,
.side-card {
  border: 1px solid rgba(148, 163, 184, 0.16);
  background: rgba(15, 23, 42, 0.84);
  backdrop-filter: blur(18px);
  box-shadow: 0 22px 60px rgba(15, 23, 42, 0.34);
}

.screen-header {
  display: grid;
  grid-template-columns: 1.2fr 1fr;
  gap: 24px;
  max-width: 1440px;
  margin: 0 auto 24px;
  padding: 32px;
  border-radius: 28px;
}

.eyebrow {
  display: inline-block;
  margin-bottom: 12px;
  color: #7dd3fc;
  font-size: 13px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.screen-header h1 {
  margin: 0;
  font-size: 42px;
}

.screen-header p {
  margin: 14px 0 0;
  color: #94a3b8;
  font-size: 18px;
  line-height: 1.7;
}

.header-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
}

.metric-box {
  padding: 20px;
  border-radius: 20px;
  background: rgba(30, 41, 59, 0.78);
}

.metric-box span {
  display: block;
  margin-bottom: 10px;
  color: #94a3b8;
}

.metric-box strong {
  font-size: 24px;
}

.screen-body {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) 380px;
  gap: 24px;
  max-width: 1440px;
  margin: 0 auto;
}

.main-panel {
  border-radius: 28px;
  padding: 28px;
}

.panel-toolbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 28px;
}

.toolbar-status {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 10px 16px;
  border-radius: 999px;
  font-weight: 600;
}

.dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  background: currentColor;
}

.status-idle {
  color: #cbd5e1;
  background: rgba(148, 163, 184, 0.14);
}

.status-collecting,
.status-ready {
  color: #fde68a;
  background: rgba(245, 158, 11, 0.16);
}

.status-rolling {
  color: #86efac;
  background: rgba(34, 197, 94, 0.18);
}

.status-result,
.status-completed {
  color: #93c5fd;
  background: rgba(59, 130, 246, 0.16);
}

.toolbar-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.empty-stage,
.pool-stage,
.rolling-stage,
.winner-stage {
  min-height: 520px;
}

.empty-stage,
.pool-stage {
  display: flex;
  flex-direction: column;
  justify-content: center;
}

.empty-stage h2,
.pool-copy h2 {
  margin: 0 0 12px;
  font-size: 32px;
}

.empty-stage p,
.pool-copy p {
  margin: 0;
  color: #94a3b8;
  font-size: 17px;
  line-height: 1.7;
}

.participant-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
  margin-top: 26px;
}

.participant-card {
  padding: 18px;
  border-radius: 20px;
  background: rgba(30, 41, 59, 0.8);
  border: 1px solid rgba(148, 163, 184, 0.12);
}

.participant-card strong,
.winner-card h2 {
  display: block;
  font-size: 22px;
}

.participant-card span,
.winner-card p {
  display: block;
  margin-top: 8px;
  color: #94a3b8;
}

.participant-card.winner {
  border-color: rgba(59, 130, 246, 0.4);
  background: rgba(37, 99, 235, 0.14);
}

.rolling-stage {
  display: flex;
  align-items: center;
  justify-content: center;
}

.rolling-card {
  width: min(100%, 780px);
  padding: 48px 36px;
  border-radius: 32px;
  text-align: center;
  background: linear-gradient(135deg, rgba(37, 99, 235, 0.18), rgba(14, 165, 233, 0.16));
  border: 1px solid rgba(125, 211, 252, 0.18);
}

.rolling-label {
  color: #bae6fd;
  font-size: 18px;
}

.rolling-card strong {
  display: block;
  margin-top: 18px;
  font-size: 82px;
  line-height: 1;
  letter-spacing: 0.08em;
}

.rolling-card p {
  margin: 20px 0 0;
  color: #cbd5e1;
  font-size: 18px;
}

.winner-stage {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 18px;
}

.winner-card {
  padding: 30px 26px;
  border-radius: 24px;
  background: linear-gradient(150deg, rgba(30, 64, 175, 0.22), rgba(14, 165, 233, 0.18));
  border: 1px solid rgba(125, 211, 252, 0.18);
}

.winner-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.12);
  color: #e0f2fe;
  font-size: 14px;
}

.side-panel {
  display: grid;
  gap: 18px;
  align-content: start;
}

.side-card {
  border-radius: 24px;
  padding: 22px;
}

.side-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 16px;
}

.side-head h3 {
  margin: 0;
  font-size: 22px;
}

.side-head span {
  color: #94a3b8;
}

.round-list,
.history-list {
  display: grid;
  gap: 12px;
}

.round-item,
.history-item {
  width: 100%;
  padding: 14px 16px;
  border-radius: 18px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  background: rgba(30, 41, 59, 0.72);
  color: inherit;
}

.round-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  cursor: pointer;
}

.round-item strong,
.history-item strong {
  display: block;
  font-size: 17px;
  text-align: left;
}

.round-item span {
  display: block;
  margin-top: 6px;
  color: #94a3b8;
}

.round-item.active {
  border-color: rgba(125, 211, 252, 0.34);
  background: rgba(14, 165, 233, 0.14);
}

.round-item.finished {
  opacity: 0.72;
  cursor: not-allowed;
}

.history-winners {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-top: 10px;
}

.history-winners span {
  padding: 4px 10px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.14);
  color: #dbeafe;
  font-size: 13px;
}

@media (max-width: 1120px) {
  .lottery-screen {
    padding: 18px;
  }

  .screen-header,
  .screen-body {
    grid-template-columns: 1fr;
  }

  .header-metrics {
    grid-template-columns: 1fr;
  }

  .screen-header,
  .main-panel,
  .side-card {
    border-radius: 22px;
  }

  .screen-header h1 {
    font-size: 30px;
  }

  .rolling-card strong {
    font-size: 48px;
  }
}
</style>
