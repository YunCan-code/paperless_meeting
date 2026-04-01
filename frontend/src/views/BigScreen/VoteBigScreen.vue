<template>
  <div class="vote-screen">
    <header v-if="vote.id" class="toolbar-shell">
      <div class="toolbar-main">
        <h1 class="toolbar-title">{{ vote.title }}</h1>
        <p v-if="vote.description" class="toolbar-description">{{ vote.description }}</p>
      </div>

      <div class="toolbar-status">
        <div class="status-chip" :class="`status-${vote.status}`">
          <span>{{ statusLabel }}</span>
          <strong>{{ timerText }}</strong>
        </div>

        <div class="toolbar-tags">
          <span class="meta-tag">{{ vote.is_multiple ? `多选 · 最多 ${vote.max_selections} 项` : '单选' }}</span>
          <span class="meta-tag">{{ vote.is_anonymous ? '匿名投票' : '实名投票' }}</span>
          <span class="meta-tag">已参与 {{ totalVoters }} 人</span>
        </div>
      </div>

      <div class="toolbar-actions">
        <el-button
          v-if="vote.status === 'draft'"
          type="primary"
          size="large"
          :loading="actionLoading"
          @click="startVote"
        >
          开始投票
        </el-button>
        <el-button
          v-if="['countdown', 'active'].includes(vote.status)"
          type="danger"
          size="large"
          :loading="actionLoading"
          @click="closeVote"
        >
          结束投票
        </el-button>
        <el-button plain size="large" :loading="loading" @click="fetchVoteBundle">
          刷新
        </el-button>
      </div>
    </header>

    <main v-if="vote.id" class="stage-shell">
      <section class="options-container">
        <div class="options-scroll">
          <template v-if="vote.status !== 'closed'">
            <article
              v-for="(item, index) in optionRows"
              :key="item.option_id"
              class="option-item"
            >
              <div class="option-content">
                <div class="option-line">
                  <div class="option-badge">{{ optionCode(index) }}</div>
                  <h2 class="option-title">{{ item.content }}</h2>
                </div>

                <div v-if="vote.status === 'active'" class="option-body">
                  <div class="option-progress">
                    <div class="option-progress-bar" :style="{ width: `${item.percent}%` }" />
                  </div>

                  <div class="option-meta">
                    <strong>{{ item.percent }}%</strong>
                    <span>{{ item.count }} 票</span>
                  </div>
                </div>
              </div>
            </article>
          </template>

          <template v-else>
            <article
              v-for="(item, index) in resultRows"
              :key="item.option_id"
              class="option-item result-item"
              :class="{ 'is-leading': index === 0 }"
            >
              <div class="option-content">
                <div class="option-line">
                  <div class="option-badge result-badge">{{ index + 1 }}</div>
                  <h2 class="option-title">{{ item.content }}</h2>
                  <span v-if="index === 0" class="leader-chip">领先</span>
                </div>

                <div class="option-body result-body">
                  <div class="option-progress">
                    <div class="option-progress-bar" :style="{ width: `${item.percent}%` }" />
                  </div>

                  <div class="option-meta result-meta">
                    <strong>{{ item.percent }}%</strong>
                    <span>{{ item.count }} 票</span>
                  </div>

                  <div v-if="!vote.is_anonymous && item.voters?.length" class="voter-list">
                    <span
                      v-for="name in visibleVoters(item)"
                      :key="`${item.option_id}-${name}`"
                      class="voter-chip"
                    >
                      {{ name }}
                    </span>
                    <button
                      v-if="shouldShowVoterToggle(item)"
                      type="button"
                      class="voter-toggle"
                      @click="toggleVoters(item.option_id)"
                    >
                      {{ isVotersExpanded(item.option_id) ? '收起' : '查看全部' }}
                    </button>
                  </div>
                </div>
              </div>
            </article>
          </template>
        </div>
      </section>
    </main>

    <el-empty v-else description="投票不存在或已被删除" :image-size="120" />
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { io } from 'socket.io-client'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const route = useRoute()
const voteId = Number(route.params.id)
const mockVoteId = Number.isFinite(voteId) ? voteId : 900001

const vote = ref({})
const results = ref([])
const totalVoters = ref(0)
const loading = ref(false)
const actionLoading = ref(false)
const countdownValue = ref(0)
const expandedVoterMap = ref({})

let socket = null
let timer = null

const isMockMode = computed(() => {
  const mockQuery = route.query.mock
  if (Array.isArray(mockQuery)) {
    return mockQuery.includes('1')
  }
  return mockQuery === '1'
})

// Temporary mock preview data for visual iteration. Remove when the design is finalized.
const MOCK_TOTAL_VOTERS = 32
const mockResultsSeed = [
  { option_id: 1, count: 12, percent: 37.5, voters: ['张敏', '陈伟', '李宁', '赵楠', '王璇', '刘航'] },
  { option_id: 2, count: 8, percent: 25, voters: ['周琳', '孙悦', '高磊', '何楠'] },
  { option_id: 3, count: 5, percent: 15.6, voters: ['郑晨', '蒋涛', '韩旭'] },
  { option_id: 4, count: 3, percent: 9.4, voters: ['冯佳', '方锐'] },
  { option_id: 5, count: 2, percent: 6.3, voters: ['谢彤'] },
  { option_id: 6, count: 1, percent: 3.1, voters: ['彭凯'] },
  { option_id: 7, count: 1, percent: 3.1, voters: ['朱琳'] },
  { option_id: 8, count: 0, percent: 0, voters: [] }
]

const buildMockVoteSnapshot = (status = 'draft') => ({
  id: mockVoteId,
  meeting_id: null,
  title: '2026 年度重点事项优先级投票',
  description: '请选择你认为本季度最需要优先推进的工作方向，用于现场大屏视觉预览。',
  status,
  is_multiple: false,
  max_selections: 1,
  is_anonymous: false,
  total_voters: MOCK_TOTAL_VOTERS,
  options: [
    { id: 1, content: '优化会前材料发放与会中资料跟屏链路' },
    { id: 2, content: '重构会议互动中心的投票与抽签主流程' },
    { id: 3, content: '完善参会人员签到后的设备联动提醒' },
    { id: 4, content: '统一会议封面中心与会议详情来源展示说明' },
    { id: 5, content: '提高安卓端登录海报与封面展示稳定性' },
    { id: 6, content: '补齐移动端互动入口状态提示' },
    { id: 7, content: '压缩后台页面冗余表单与低频入口' },
    { id: 8, content: '现场控制页视觉规范整理与二次抽样验证' }
  ],
  results: mockResultsSeed,
  countdown_remaining_seconds: status === 'countdown' ? 5 : 0,
  remaining_seconds: status === 'active' ? 90 : 0
})

const mockVoteState = ref(buildMockVoteSnapshot('draft'))
const DEFAULT_VISIBLE_VOTERS = 6

const resultRows = computed(() => {
  if (!Array.isArray(results.value)) return []
  const optionMap = new Map(
    (Array.isArray(vote.value.options) ? vote.value.options : []).map(option => [option.id, option.content])
  )

  return [...results.value]
    .map(item => ({
      ...item,
      content: item.content || optionMap.get(item.option_id) || ''
    }))
    .sort((a, b) => b.count - a.count)
})

const optionRows = computed(() => {
  const baseOptions = Array.isArray(vote.value.options) ? vote.value.options : []
  const resultMap = new Map(resultRows.value.map(item => [item.option_id, item]))
  return baseOptions.map(option => {
    const matched = resultMap.get(option.id)
    return {
      option_id: option.id,
      content: option.content,
      count: matched?.count ?? 0,
      percent: matched?.percent ?? 0
    }
  })
})

const statusLabel = computed(() => ({
  draft: '草稿待开始',
  countdown: '开始倒计时',
  active: '投票进行中',
  closed: '最终结果'
}[vote.value.status] || '未知状态'))

const timerText = computed(() => {
  if (vote.value.status === 'countdown') {
    return `${Math.max(0, countdownValue.value)}s`
  }
  if (vote.value.status === 'active') {
    const seconds = Math.max(0, countdownValue.value)
    const minutes = Math.floor(seconds / 60)
    const rest = String(seconds % 60).padStart(2, '0')
    return `${minutes}:${rest}`
  }
  if (vote.value.status === 'closed') {
    return '已结束'
  }
  return '待启动'
})

const clearTimer = () => {
  if (timer) {
    clearInterval(timer)
    timer = null
  }
}

const setMockVoteState = (status) => {
  mockVoteState.value = buildMockVoteSnapshot(status)
}

const syncMockCountdown = () => {
  if (!isMockMode.value) return

  if (mockVoteState.value.status === 'countdown') {
    mockVoteState.value = {
      ...mockVoteState.value,
      countdown_remaining_seconds: Math.max(0, countdownValue.value)
    }
  } else if (mockVoteState.value.status === 'active') {
    mockVoteState.value = {
      ...mockVoteState.value,
      remaining_seconds: Math.max(0, countdownValue.value)
    }
  }
}

const advanceMockVoteState = () => {
  if (!isMockMode.value) return

  if (mockVoteState.value.status === 'countdown') {
    setMockVoteState('active')
  } else if (mockVoteState.value.status === 'active') {
    setMockVoteState('closed')
  }
}

const sanitizeTimerSeed = (status, rawValue, configuredSeconds) => {
  const safeRaw = Number(rawValue || 0)
  const safeConfigured = Number(configuredSeconds || 0)
  if (!Number.isFinite(safeRaw) || safeRaw <= 0) return 0

  const abnormalUpperBound = status === 'countdown'
    ? Math.max(safeConfigured + 120, 3600)
    : Math.max(safeConfigured + 120, 3600)

  if (safeRaw > abnormalUpperBound) {
    return Math.max(0, safeConfigured)
  }
  return safeRaw
}

const startLocalTimer = () => {
  clearTimer()

  if (vote.value.status === 'countdown') {
    countdownValue.value = sanitizeTimerSeed(
      'countdown',
      vote.value.countdown_remaining_seconds,
      vote.value.countdown_seconds
    )
  } else if (vote.value.status === 'active') {
    countdownValue.value = sanitizeTimerSeed(
      'active',
      vote.value.remaining_seconds,
      vote.value.duration_seconds
    )
  } else {
    countdownValue.value = 0
    return
  }

  timer = window.setInterval(() => {
    if (countdownValue.value <= 0) {
      clearTimer()
      if (isMockMode.value) {
        advanceMockVoteState()
      }
      fetchVoteBundle()
      return
    }
    countdownValue.value -= 1
    syncMockCountdown()
  }, 1000)
}

const applyVoteSnapshot = (snapshot) => {
  if (!snapshot || Number(snapshot.id) !== voteId) return
  vote.value = snapshot
  totalVoters.value = snapshot.total_voters || totalVoters.value || 0
  if (Array.isArray(snapshot.results)) {
    results.value = snapshot.results
  }
  startLocalTimer()

  if (!isMockMode.value && !socket && snapshot.meeting_id) {
    connectSocket(snapshot.meeting_id)
  }
}

const applyVoteResult = (payload) => {
  if (!payload || Number(payload.vote_id) !== voteId) return
  results.value = Array.isArray(payload.results) ? payload.results : []
  totalVoters.value = payload.total_voters || totalVoters.value || 0
}

const fetchVoteBundle = async () => {
  loading.value = true
  try {
    if (isMockMode.value) {
      applyVoteSnapshot(mockVoteState.value)
      applyVoteResult({
        vote_id: mockVoteState.value.id,
        total_voters: mockVoteState.value.total_voters,
        results: mockVoteState.value.results
      })
      return
    }

    const [voteDetail, voteResult] = await Promise.all([
      request.get(`/vote/${voteId}`),
      request.get(`/vote/${voteId}/result`)
    ])
    applyVoteSnapshot(voteDetail)
    applyVoteResult(voteResult)
  } catch (error) {
    vote.value = {}
    results.value = []
    totalVoters.value = 0
  } finally {
    loading.value = false
  }
}

const connectSocket = (meetingId) => {
  if (socket || !meetingId) return
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket', 'polling'],
    reconnection: true
  })

  socket.on('connect', () => {
    socket.emit('join_meeting', { meeting_id: meetingId })
  })
  socket.on('vote_state_change', applyVoteSnapshot)
  socket.on('vote_results_change', applyVoteResult)
}

const disconnectSocket = () => {
  if (!socket) return
  socket.disconnect()
  socket = null
}

const startVote = async () => {
  actionLoading.value = true
  try {
    if (isMockMode.value) {
      setMockVoteState('countdown')
      ElMessage.success('Mock 投票已进入倒计时')
      await fetchVoteBundle()
      return
    }

    await request.post(`/vote/${voteId}/start`)
    ElMessage.success('投票已进入倒计时')
    await fetchVoteBundle()
  } finally {
    actionLoading.value = false
  }
}

const closeVote = async () => {
  actionLoading.value = true
  try {
    if (isMockMode.value) {
      setMockVoteState('closed')
      ElMessage.success('Mock 投票已结束')
      await fetchVoteBundle()
      return
    }

    await request.post(`/vote/${voteId}/close`)
    ElMessage.success('投票已结束')
    await fetchVoteBundle()
  } finally {
    actionLoading.value = false
  }
}

const optionCode = (index) => String.fromCharCode(65 + index)

const isVotersExpanded = (optionId) => Boolean(expandedVoterMap.value[optionId])

const toggleVoters = (optionId) => {
  expandedVoterMap.value = {
    ...expandedVoterMap.value,
    [optionId]: !expandedVoterMap.value[optionId]
  }
}

const shouldShowVoterToggle = (item) => Array.isArray(item?.voters) && item.voters.length > DEFAULT_VISIBLE_VOTERS

const visibleVoters = (item) => {
  if (!Array.isArray(item?.voters)) return []
  if (isVotersExpanded(item.option_id)) return item.voters
  return item.voters.slice(0, DEFAULT_VISIBLE_VOTERS)
}

onMounted(fetchVoteBundle)

onUnmounted(() => {
  clearTimer()
  disconnectSocket()
})
</script>

<style scoped>
.vote-screen {
  min-height: 100vh;
  padding: 16px 20px 22px;
  background:
    radial-gradient(circle at top right, rgba(59, 130, 246, 0.16), transparent 26%),
    linear-gradient(180deg, #08111f 0%, #0f172a 100%);
  color: #e2e8f0;
  overflow: hidden;
}

.toolbar-shell,
.stage-shell {
  max-width: 1560px;
  margin: 0 auto;
}

.toolbar-shell {
  display: flex;
  align-items: center;
  gap: 14px;
  min-height: 76px;
  padding: 10px 14px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 20px;
  background: rgba(10, 18, 33, 0.78);
  backdrop-filter: blur(14px);
}

.toolbar-main {
  flex: 1;
  min-width: 0;
}

.toolbar-title {
  margin: 0;
  font-size: 26px;
  line-height: 1.2;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toolbar-description {
  margin: 4px 0 0;
  color: #94a3b8;
  font-size: 13px;
  line-height: 1.4;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.toolbar-status {
  display: flex;
  align-items: center;
  gap: 8px;
  min-width: 0;
  margin-left: auto;
}

.status-chip {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 999px;
  font-size: 13px;
  white-space: nowrap;
}

.status-chip strong {
  font-size: 17px;
  line-height: 1;
}

.status-draft {
  color: #cbd5e1;
  background: rgba(148, 163, 184, 0.14);
}

.status-countdown {
  color: #fde68a;
  background: rgba(234, 179, 8, 0.16);
}

.status-active {
  color: #86efac;
  background: rgba(34, 197, 94, 0.16);
}

.status-closed {
  color: #bfdbfe;
  background: rgba(59, 130, 246, 0.16);
}

.toolbar-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-tag {
  padding: 6px 10px;
  border-radius: 999px;
  border: 1px solid rgba(148, 163, 184, 0.12);
  background: rgba(15, 23, 42, 0.82);
  color: #cbd5e1;
  font-size: 12px;
  white-space: nowrap;
}

.toolbar-actions {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-shrink: 0;
}

.stage-shell {
  height: calc(100vh - 114px);
  margin-top: 12px;
}

.options-container {
  height: 100%;
  padding: 22px 28px;
  border: 1px solid rgba(148, 163, 184, 0.14);
  border-radius: 28px;
  background:
    linear-gradient(180deg, rgba(17, 24, 39, 0.92), rgba(15, 23, 42, 0.94));
  box-shadow: 0 18px 42px rgba(2, 6, 23, 0.28);
}

.options-scroll {
  height: 100%;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: 0;
  padding: 8px 4px 6px 0;
}

.option-item {
  width: 100%;
  display: flex;
  justify-content: center;
  padding: 0;
}

.result-item {
  gap: 12px;
}

.result-item.is-leading .option-title {
  color: #f8fafc;
}

.result-item.is-leading .option-content {
  position: relative;
}

.result-item.is-leading .option-content::before {
  content: '';
  position: absolute;
  left: -20px;
  top: 16px;
  bottom: 18px;
  width: 3px;
  border-radius: 999px;
  background: linear-gradient(180deg, rgba(250, 204, 21, 0.88), rgba(56, 189, 248, 0.72));
}

.result-item.is-leading .result-badge {
  background: linear-gradient(135deg, #f59e0b, #38bdf8);
  box-shadow: 0 0 0 1px rgba(250, 204, 21, 0.14), 0 10px 28px rgba(14, 165, 233, 0.16);
}

.result-item.is-leading .result-meta strong {
  color: #fef3c7;
}

.option-content {
  width: min(860px, calc(100% - 96px));
  display: grid;
  gap: 10px;
  padding: 16px 0 18px;
}

.option-item:not(:last-child) .option-content {
  border-bottom: 1px solid rgba(148, 163, 184, 0.16);
}

.option-line {
  display: flex;
  align-items: center;
  justify-content: flex-start;
  gap: 14px;
  width: 100%;
  min-width: 0;
}

.option-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 40px;
  height: 40px;
  padding: 0 10px;
  border-radius: 12px;
  background: linear-gradient(135deg, #2563eb, #38bdf8);
  color: #eff6ff;
  font-size: 18px;
  font-weight: 700;
  flex-shrink: 0;
}

.result-badge {
  background: linear-gradient(135deg, #0ea5e9, #2563eb);
}

.option-body {
  display: grid;
  gap: 9px;
  width: 100%;
  padding-left: 54px;
  box-sizing: border-box;
}

.result-body {
  gap: 12px;
}

.option-title {
  margin: 0;
  flex: 1;
  min-width: 0;
  font-size: 28px;
  line-height: 1.32;
  text-align: left;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.leader-chip {
  display: inline-flex;
  align-items: center;
  padding: 5px 10px;
  border-radius: 999px;
  background: rgba(250, 204, 21, 0.12);
  border: 1px solid rgba(250, 204, 21, 0.18);
  color: #fde68a;
  font-size: 12px;
  font-weight: 600;
  flex-shrink: 0;
}

.option-meta {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  color: #cbd5e1;
  font-size: 14px;
}

.option-meta strong {
  font-size: 22px;
  line-height: 1;
  color: #f8fafc;
}

.result-meta {
  gap: 12px;
}

.result-meta strong {
  font-size: 32px;
  line-height: 1;
  color: #f8fafc;
}

.option-progress {
  width: 100%;
  height: 6px;
  overflow: hidden;
  border-radius: 999px;
  background: rgba(148, 163, 184, 0.14);
}

.option-progress-bar {
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #38bdf8, #2563eb);
  transition: width 0.3s ease;
}

.voter-list {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-start;
  gap: 8px;
  max-height: 84px;
  overflow-y: auto;
  width: 100%;
}

.voter-chip {
  padding: 6px 10px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.14);
  color: #dbeafe;
  font-size: 13px;
}

.voter-toggle {
  padding: 6px 10px;
  border: 1px solid rgba(96, 165, 250, 0.24);
  border-radius: 999px;
  background: rgba(15, 23, 42, 0.72);
  color: #93c5fd;
  font-size: 13px;
  cursor: pointer;
  transition: background 0.2s ease, border-color 0.2s ease, color 0.2s ease;
}

.voter-toggle:hover {
  background: rgba(30, 41, 59, 0.92);
  border-color: rgba(96, 165, 250, 0.4);
  color: #dbeafe;
}

@media (max-width: 1400px) {
  .toolbar-shell {
    flex-wrap: wrap;
    align-items: flex-start;
  }

  .toolbar-status,
  .toolbar-actions {
    margin-left: 0;
  }
}

@media (max-width: 900px) {
  .vote-screen {
    padding: 12px;
  }

  .toolbar-title {
    font-size: 22px;
  }

  .toolbar-status,
  .toolbar-tags,
  .toolbar-actions {
    flex-wrap: wrap;
  }

  .stage-shell {
    height: auto;
    min-height: calc(100vh - 120px);
  }

  .options-container {
    padding: 14px;
  }

  .option-content {
    width: min(100%, calc(100% - 12px));
    gap: 8px;
    padding: 14px 0 16px;
  }

  .result-item.is-leading .option-content::before {
    left: -10px;
    top: 14px;
    bottom: 16px;
  }

  .option-line {
    gap: 10px;
  }

  .option-title {
    font-size: 22px;
  }

  .option-body {
    padding-left: 50px;
  }

  .result-meta strong {
    font-size: 26px;
  }
}
</style>
