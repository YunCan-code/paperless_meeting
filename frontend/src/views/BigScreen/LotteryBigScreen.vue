<template>
  <div class="lottery-screen">
    <header class="overview-card">
      <div class="overview-head">
        <div>
          <span class="overview-eyebrow">会议互动中心 / 抽签大屏</span>
          <p class="overview-note">顶部只保留关键指标，现场控制集中在左侧抽签舞台。</p>
        </div>
        <span v-if="isMockMode" class="mock-chip">Mock 演示</span>
      </div>
      <div class="overview-metrics">
        <button type="button" class="metric-item metric-button" @click="openParticipantDialog">
          <span>参与人数</span>
          <strong>{{ session.participants_count || 0 }}</strong>
          <em>点击查看并管理</em>
        </button>
        <article class="metric-item"><span>总轮次数</span><strong>{{ rounds.length }}</strong></article>
        <article class="metric-item"><span>当前第几轮</span><strong>{{ currentRoundOrderLabel }}</strong></article>
        <article class="metric-item"><span>本轮抽取</span><strong>{{ currentRoundPickCountLabel }}</strong></article>
        <article class="metric-item"><span>剩余轮次</span><strong>{{ remainingRoundsCount }}</strong></article>
        <article class="metric-item"><span>重复抽签</span><strong>{{ repeatRuleLabel }}</strong></article>
      </div>
    </header>

    <main class="screen-grid">
      <section class="stage-card">
        <div class="section-head stage-head">
          <div class="section-main">
            <span class="section-kicker">抽签过程</span>
            <h2>{{ stageCardTitle }}</h2>
            <p class="section-description">{{ stageDescription }}</p>
          </div>
          <div class="stage-actions">
            <span class="state-chip" :class="`state-${session.session_status}`">{{ stageStateLabel }}</span>
            <el-button
              v-if="canStartNextRound"
              type="primary"
              size="large"
              :loading="actionLoading"
              :disabled="!canStartNextRound || session.participants_count === 0"
              @click="startRoll"
            >{{ startActionLabel }}</el-button>
            <el-button
              v-if="session.session_status === 'rolling'"
              type="danger"
              size="large"
              :loading="actionLoading"
              @click="stopRoll"
            >结束抽签</el-button>
          </div>
        </div>

        <div v-if="!rounds.length" class="stage-body stage-empty">
          <h3>还没有抽签轮次</h3>
          <p>请先在后台互动中心创建抽签轮次，然后再打开大屏开始抽签。</p>
        </div>

        <div v-else class="stage-body">
          <template v-if="session.session_status === 'rolling'">
            <div class="slot-stage">
              <div class="slot-machine">
                <div class="slot-mask slot-mask-top" />
                <div class="slot-mask slot-mask-bottom" />
                <div class="slot-highlight" />
                <div class="slot-columns">
                  <div v-for="(column, columnIndex) in slotColumns" :key="`slot-${columnIndex}`" class="slot-column">
                    <div class="slot-track" :style="slotTrackStyle(columnIndex)">
                      <span v-for="(name, nameIndex) in column" :key="`slot-${columnIndex}-${nameIndex}`" class="slot-name">
                        {{ name }}
                      </span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="stage-footnote">
                <span>{{ currentRoundOrderLabel }}</span>
                <strong>{{ currentRoundTitle }}</strong>
                <em>正在滚动姓名，点击“结束抽签”后锁定本轮中签人员。</em>
              </div>
            </div>
          </template>

          <template v-else-if="currentWinners.length">
            <div class="winner-stage">
              <div class="winner-stage-head">
                <span class="winner-badge">{{ currentRoundOrderLabel }}</span>
                <h3>{{ currentRoundTitle }}</h3>
                <p>{{ currentRoundResultSummary }}</p>
              </div>
              <div class="winner-grid">
                <article v-for="winner in currentWinners" :key="winner.id || winner.user_id || winner.user_name" class="winner-card">
                  <strong>{{ winner.user_name || winner.name }}</strong>
                </article>
              </div>
            </div>
          </template>

          <template v-else>
            <div class="ready-stage">
              <span class="ready-badge">{{ currentRoundOrderLabel }}</span>
              <h3>{{ currentRoundTitle }}</h3>
              <p>{{ readyDescription }}</p>
              <div class="participant-preview-head">
                <span class="preview-label">参与池预览</span>
                <button type="button" class="preview-link" @click="openParticipantDialog">
                  {{ participantPreviewHint }}
                </button>
              </div>
              <div class="participant-preview">
                <span
                  v-for="participant in participantPreview"
                  :key="participant.user_id || participant.id || participant.name"
                  class="participant-chip"
                >{{ participant.name }}</span>
                <span v-if="participantPreviewRemainderCount > 0" class="participant-chip participant-chip-summary">
                  等 {{ participantPreviewRemainderCount }} 人
                </span>
              </div>
            </div>
          </template>
        </div>
      </section>

      <aside class="result-card">
        <div class="section-head result-head">
          <div class="section-main">
            <span class="section-kicker">抽签结果</span>
            <h2>每轮中签名单</h2>
            <p class="section-description">按轮次持续保留已抽取结果，方便现场直接回看。</p>
          </div>
          <span class="result-count">{{ resultRounds.length }} 轮</span>
        </div>

        <div v-if="resultRounds.length" class="result-list">
          <article
            v-for="round in resultRounds"
            :key="round.id"
            class="result-round"
            :class="{ 'is-current-result': Number(round.id) === Number(session.current_round_id) && currentWinners.length > 0 }"
          >
            <div class="result-round-head">
              <div>
                <span class="round-order">{{ formatLotteryRoundOrder(round.sort_order) }}</span>
                <strong>{{ round.title }}</strong>
              </div>
              <span class="round-count">抽取 {{ round.count }} 人</span>
            </div>
            <div class="winner-chip-list">
              <span
                v-for="winner in round.winners"
                :key="winner.id || `${round.id}-${winner.user_id}`"
                class="winner-chip"
              >{{ winner.user_name || winner.name }}</span>
            </div>
          </article>
        </div>

        <div v-else class="result-empty">
          <h3>尚未产生抽签结果</h3>
          <p>抽签完成后，这里会按轮次显示每轮中签人员。</p>
        </div>
      </aside>
    </main>

    <el-dialog
      v-model="participantDialogVisible"
      class="participant-dialog"
      width="min(1080px, 92vw)"
      align-center
      append-to-body
    >
      <template #header>
        <div class="participant-dialog-header">
          <div>
            <h3>参与人员管理</h3>
            <p>{{ participantDialogSubtitle }}</p>
          </div>
          <span class="participant-dialog-status" :class="{ 'is-locked': isParticipantManagementLocked }">
            {{ isParticipantManagementLocked ? '抽签进行中，暂不可调整' : '当前可调整参与池' }}
          </span>
        </div>
      </template>

      <div class="participant-toolbar">
        <el-input
          v-model="participantSearch"
          clearable
          placeholder="搜索姓名"
          class="participant-search"
        />
        <span class="participant-toolbar-meta">
          已加入 {{ joinedParticipants.length }} 人，可加入 {{ availableAttendees.length }} 人
        </span>
      </div>

      <div v-if="meetingDetailLoading" class="participant-loading">
        正在加载参会人员名单...
      </div>

      <template v-else>
        <div v-if="isParticipantManagementLocked" class="participant-lock-tip">
          抽签滚动过程中仅可查看名单，开始后请等待本轮结束再调整参与池。
        </div>

        <div class="participant-manage-grid">
          <section class="participant-manage-section">
            <div class="participant-section-head">
              <div>
                <h4>已加入抽签池</h4>
                <p>这里展示当前真正参与抽签的完整人员名单。</p>
              </div>
              <span>{{ filteredJoinedParticipants.length }} 人</span>
            </div>

            <div v-if="filteredJoinedParticipants.length" class="participant-manage-list">
              <article
                v-for="participant in filteredJoinedParticipants"
                :key="participant.user_id || participant.id || participant.name"
                class="participant-row"
              >
                <div class="participant-row-main">
                  <strong>{{ participant.name }}</strong>
                  <span>{{ participant.department || '已加入抽签池' }}</span>
                </div>
                <el-button
                  size="small"
                  plain
                  type="danger"
                  :disabled="isParticipantManagementLocked || isParticipantPending(participant.user_id)"
                  :loading="isParticipantPending(participant.user_id)"
                  @click="removeParticipantFromPool(participant)"
                >
                  移出
                </el-button>
              </article>
            </div>
            <el-empty v-else description="当前没有符合条件的已加入人员" :image-size="72" />
          </section>

          <section class="participant-manage-section">
            <div class="participant-section-head">
              <div>
                <h4>可加入抽签池</h4>
                <p>仅支持会议参会人员中的系统用户加入抽签池。</p>
              </div>
              <span>{{ filteredAvailableAttendees.length }} 人</span>
            </div>

            <div v-if="filteredAvailableAttendees.length" class="participant-manage-list">
              <article
                v-for="attendee in filteredAvailableAttendees"
                :key="attendee.user_id || attendee.name"
                class="participant-row"
              >
                <div class="participant-row-main">
                  <strong>{{ attendee.name }}</strong>
                  <span>{{ attendee.meeting_role || '参会人员' }}</span>
                </div>
                <el-button
                  size="small"
                  type="primary"
                  plain
                  :disabled="isParticipantManagementLocked || isParticipantPending(attendee.user_id)"
                  :loading="isParticipantPending(attendee.user_id)"
                  @click="addParticipantToPool(attendee)"
                >
                  加入
                </el-button>
              </article>
            </div>
            <el-empty v-else description="当前没有可加入的会议参会人员" :image-size="72" />
          </section>
        </div>

        <section v-if="filteredManualAttendees.length" class="participant-manual-section">
          <div class="participant-section-head">
            <div>
              <h4>手填参会人员</h4>
              <p>这部分只展示，不支持直接加入抽签池。</p>
            </div>
            <span>{{ filteredManualAttendees.length }} 人</span>
          </div>
          <div class="participant-manual-list">
            <span
              v-for="attendee in filteredManualAttendees"
              :key="`manual-${attendee.name}-${attendee.meeting_role}`"
              class="manual-attendee-chip"
            >
              {{ attendee.name }}
            </span>
          </div>
        </section>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { io } from 'socket.io-client'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'
import {
  formatLotteryRoundOrder,
  getLotteryDisplayRound,
  getLotterySessionStatusLabel
} from '@/utils/lotteryUi'

const route = useRoute()
const meetingId = Number(route.params.meetingId)
const activeMeetingId = Number.isFinite(meetingId) ? meetingId : 900001
const fallbackMeetingId = activeMeetingId

const createEmptySession = () => ({
  meeting_id: fallbackMeetingId,
  session_status: 'idle',
  current_round_id: null,
  current_round: null,
  next_round_id: null,
  next_round: null,
  participants: [],
  participants_count: 0,
  winners: [],
  rounds: []
})

const createEmptyMeetingDetail = () => ({
  id: activeMeetingId,
  title: '',
  attendees: []
})

const session = ref(createEmptySession())
const actionLoading = ref(false)
const participantDialogVisible = ref(false)
const participantSearch = ref('')
const meetingDetailLoading = ref(false)
const meetingDetail = ref(createEmptyMeetingDetail())
const participantPendingMap = ref({})
let socket = null

const isMockMode = computed(() => {
  const mockQuery = route.query.mock
  if (Array.isArray(mockQuery)) return mockQuery.includes('1')
  return mockQuery === '1'
})

const normalizeWinner = (winner = {}) => ({
  ...winner,
  id: winner.id ?? winner.user_id ?? winner.name,
  user_id: winner.user_id ?? winner.id ?? null,
  name: winner.name || winner.user_name || '未命名',
  user_name: winner.user_name || winner.name || '未命名',
  department: winner.department || ''
})

const normalizeRound = (round = {}) => ({
  ...round,
  id: round.id,
  title: round.title || '未命名轮次',
  count: Number(round.count) || 0,
  allow_repeat: Boolean(round.allow_repeat),
  sort_order: Number(round.sort_order) || 0,
  status: round.status || 'draft',
  winners: Array.isArray(round.winners) ? round.winners.map(normalizeWinner) : []
})

const normalizeParticipant = (participant = {}) => ({
  ...participant,
  id: participant.id ?? participant.user_id ?? participant.name,
  user_id: participant.user_id ?? participant.id ?? null,
  name: participant.name || participant.user_name || '未命名',
  department: participant.department || ''
})

const normalizeMeetingAttendee = (attendee = {}, index = 0) => ({
  type: attendee.type || (attendee.user_id ? 'user' : 'manual'),
  user_id: attendee.user_id ?? null,
  name: attendee.name || attendee.user_name || `参会人员 ${index + 1}`,
  meeting_role: attendee.meeting_role || '参会人员'
})

const findRoundById = (rounds, roundId) => rounds.find(item => Number(item.id) === Number(roundId)) || null
const findNextRound = (rounds, currentRound) => rounds.find(item => item.status !== 'finished' && Number(item.id) !== Number(currentRound?.status !== 'finished' ? currentRound.id : null)) || null

const buildSessionSnapshot = (payload = {}) => {
  const normalizedRounds = (Array.isArray(payload.rounds) ? payload.rounds : []).map(normalizeRound).sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0))
  const normalizedParticipants = Array.isArray(payload.participants) ? payload.participants.map(normalizeParticipant) : []
  let currentRound = payload.current_round ? normalizeRound(payload.current_round) : null
  if (!currentRound && payload.current_round_id) currentRound = findRoundById(normalizedRounds, payload.current_round_id)
  let nextRound = payload.next_round ? normalizeRound(payload.next_round) : null
  if (!nextRound && payload.next_round_id) nextRound = findRoundById(normalizedRounds, payload.next_round_id)
  if (!nextRound) nextRound = findNextRound(normalizedRounds, currentRound)
  return {
    meeting_id: Number(payload.meeting_id) || fallbackMeetingId,
    session_status: payload.session_status || 'idle',
    current_round_id: currentRound?.id ?? payload.current_round_id ?? null,
    current_round: currentRound,
    next_round_id: nextRound?.id ?? payload.next_round_id ?? null,
    next_round: nextRound,
    participants: normalizedParticipants,
    participants_count: Number(payload.participants_count ?? normalizedParticipants.length) || 0,
    winners: Array.isArray(payload.winners) ? payload.winners.map(normalizeWinner) : [],
    rounds: normalizedRounds
  }
}

const cloneWinner = (winner) => ({ ...winner })
const cloneRound = (round) => ({ ...round, winners: Array.isArray(round.winners) ? round.winners.map(cloneWinner) : [] })
const cloneParticipant = (participant) => ({ ...participant })
const cloneSession = (snapshot) => ({
  meeting_id: snapshot.meeting_id,
  session_status: snapshot.session_status,
  current_round_id: snapshot.current_round_id,
  current_round: snapshot.current_round ? cloneRound(snapshot.current_round) : null,
  next_round_id: snapshot.next_round_id,
  next_round: snapshot.next_round ? cloneRound(snapshot.next_round) : null,
  participants: Array.isArray(snapshot.participants) ? snapshot.participants.map(cloneParticipant) : [],
  participants_count: snapshot.participants_count,
  winners: Array.isArray(snapshot.winners) ? snapshot.winners.map(cloneWinner) : [],
  rounds: Array.isArray(snapshot.rounds) ? snapshot.rounds.map(cloneRound) : []
})

const MOCK_NAMES = ['张敏', '陈伟', '李宁', '王璇', '赵楠', '周琳', '高磊', '蒋涛', '韩旭', '孙悦', '何楠', '冯佳', '刘航', '谢彤', '彭凯', '朱琳']
const buildMockParticipants = () => MOCK_NAMES.map((name, index) => ({ id: 8000 + index, user_id: 8000 + index, name, department: `第${(index % 4) + 1}组` }))
const buildMockMeetingDetail = () => ({
  id: fallbackMeetingId,
  title: 'Mock 抽签演示会议',
  attendees: [
    ...MOCK_NAMES.map((name, index) => ({
      type: 'user',
      user_id: 8000 + index,
      name,
      meeting_role: ['正式代表', '列席人员', '工作人员'][index % 3]
    })),
    { type: 'user', user_id: 8101, name: '林川', meeting_role: '观察员' },
    { type: 'user', user_id: 8102, name: '姜岚', meeting_role: '观察员' },
    { type: 'user', user_id: 8103, name: '唐越', meeting_role: '后备人员' },
    { type: 'user', user_id: 8104, name: '顾宁', meeting_role: '后备人员' },
    { type: 'manual', user_id: null, name: '临时嘉宾甲', meeting_role: '手填参会人' },
    { type: 'manual', user_id: null, name: '临时嘉宾乙', meeting_role: '手填参会人' }
  ]
})
const buildMockRounds = () => ([
  { id: 9101, title: '主席台抽签', count: 1, allow_repeat: false, sort_order: 1, status: 'draft', winners: [] },
  { id: 9102, title: '列席代表抽签', count: 2, allow_repeat: false, sort_order: 2, status: 'draft', winners: [] },
  { id: 9103, title: '提案发言顺序抽签', count: 3, allow_repeat: true, sort_order: 3, status: 'draft', winners: [] }
])

const createInitialMockSession = () => {
  const participants = buildMockParticipants()
  const rounds = buildMockRounds()
  return buildSessionSnapshot({
    meeting_id: fallbackMeetingId,
    session_status: 'idle',
    current_round_id: null,
    current_round: null,
    next_round_id: rounds[0]?.id ?? null,
    next_round: rounds[0] ?? null,
    participants,
    participants_count: participants.length,
    winners: [],
    rounds
  })
}

const mockSessionState = ref(createInitialMockSession())
const mockMeetingDetail = ref(buildMockMeetingDetail())
const hydrateMockSession = (draft) => {
  const rounds = Array.isArray(draft.rounds) ? draft.rounds.map(cloneRound) : []
  const currentRound = findRoundById(rounds, draft.current_round_id)
  const nextRound = findNextRound(rounds, currentRound)
  return buildSessionSnapshot({
    meeting_id: draft.meeting_id,
    session_status: draft.session_status,
    current_round_id: currentRound?.id ?? null,
    current_round: currentRound,
    next_round_id: nextRound?.id ?? null,
    next_round: nextRound,
    participants: draft.participants,
    participants_count: draft.participants?.length ?? 0,
    winners: draft.winners,
    rounds
  })
}

const updateMockSession = (mutator) => {
  const draft = cloneSession(mockSessionState.value)
  mutator(draft)
  const hydrated = hydrateMockSession(draft)
  mockSessionState.value = hydrated
  session.value = hydrated
}

const randomPick = (list, count) => {
  const source = [...list]
  const picked = []
  const safeCount = Math.max(0, Math.min(count, source.length))
  for (let index = 0; index < safeCount; index += 1) {
    const randomIndex = Math.floor(Math.random() * source.length)
    picked.push(source.splice(randomIndex, 1)[0])
  }
  return picked
}

const getWinnerIdentity = (winner) => String(winner.user_id ?? winner.id ?? winner.name)
const getMockCandidatePool = (draft, round) => {
  if (round.allow_repeat) return draft.participants
  const finishedWinnerSet = new Set(draft.rounds.filter(item => item.status === 'finished').flatMap(item => item.winners).map(getWinnerIdentity))
  return draft.participants.filter(item => !finishedWinnerSet.has(getWinnerIdentity(item)))
}

const rounds = computed(() => Array.isArray(session.value.rounds) ? session.value.rounds : [])
const participants = computed(() => Array.isArray(session.value.participants) ? session.value.participants : [])
const currentDisplayRound = computed(() => getLotteryDisplayRound(session.value))
const currentRoundTitle = computed(() => currentDisplayRound.value?.title || '等待开始抽签')
const currentRoundOrderLabel = computed(() => formatLotteryRoundOrder(currentDisplayRound.value?.sort_order || 0))
const currentRoundPickCountLabel = computed(() => currentDisplayRound.value?.count ? `${currentDisplayRound.value.count} 人` : '--')
const remainingRoundsCount = computed(() => rounds.value.filter(item => item.status !== 'finished').length)
const repeatRuleLabel = computed(() => !currentDisplayRound.value ? '--' : currentDisplayRound.value.allow_repeat ? '允许重复' : '不允许重复')
const currentWinners = computed(() => Array.isArray(session.value.winners) && session.value.winners.length ? session.value.winners : session.value.current_round?.winners?.length ? session.value.current_round.winners : [])
const participantPreview = computed(() => participants.value.slice(0, 8))
const participantPreviewRemainderCount = computed(() => Math.max(0, participants.value.length - participantPreview.value.length))
const canStartNextRound = computed(() => {
  if (session.value.session_status === 'rolling') return false
  if (session.value.current_round && session.value.current_round.status !== 'finished') return true
  return Boolean(session.value.next_round)
})
const startActionLabel = computed(() => session.value.winners?.length && session.value.next_round ? '开始下一轮' : '开始抽签')
const stageCardTitle = computed(() => `${currentRoundOrderLabel.value} · ${currentRoundTitle.value}`)
const stageStateLabel = computed(() => getLotterySessionStatusLabel(session.value.session_status))
const stageDescription = computed(() => {
  if (!rounds.value.length) return '创建轮次后，这里会直接展示抽签动画。'
  if (session.value.session_status === 'rolling') return '姓名正在持续滚动，现场可在右上角随时结束本轮抽签。'
  if (currentWinners.value.length) return '本轮结果已锁定，右侧列表会继续保留历轮中签人员。'
  return `当前参与池 ${session.value.participants_count || 0} 人，本轮计划抽取 ${currentDisplayRound.value?.count || 0} 人。`
})
const readyDescription = computed(() => !rounds.value.length ? '请先创建抽签轮次。' : '点击右上角“开始抽签”后，会以滚动动画演示当前轮次的抽取过程。')
const currentRoundResultSummary = computed(() => `本轮共抽出 ${currentWinners.value.length} 人，结果已同步保留到右侧名单。`)
const resultRounds = computed(() => rounds.value.filter(item => Array.isArray(item.winners) && item.winners.length > 0).sort((a, b) => (a.sort_order || 0) - (b.sort_order || 0)))
const participantDialogSubtitle = computed(() => {
  const meetingTitle = meetingDetail.value.title || '当前会议'
  return `${meetingTitle} · 已加入 ${joinedParticipants.value.length} 人 · 当前状态：${stageStateLabel.value}`
})
const isParticipantManagementLocked = computed(() => session.value.session_status === 'rolling')
const joinedParticipantIdSet = computed(() => new Set(joinedParticipants.value.map(item => Number(item.user_id)).filter(value => Number.isFinite(value))))
const joinedParticipants = computed(() => participants.value.map(normalizeParticipant))
const availableAttendees = computed(() => (meetingDetail.value.attendees || [])
  .map(normalizeMeetingAttendee)
  .filter(item => item.type === 'user' && item.user_id != null && !joinedParticipantIdSet.value.has(Number(item.user_id))))
const manualAttendees = computed(() => (meetingDetail.value.attendees || [])
  .map(normalizeMeetingAttendee)
  .filter(item => item.type !== 'user' || item.user_id == null))
const normalizedParticipantSearch = computed(() => participantSearch.value.trim().toLowerCase())
const participantMatchesKeyword = (participant) => {
  const keyword = normalizedParticipantSearch.value
  if (!keyword) return true
  return [participant.name, participant.department, participant.meeting_role].filter(Boolean).join(' ').toLowerCase().includes(keyword)
}
const filteredJoinedParticipants = computed(() => joinedParticipants.value.filter(participantMatchesKeyword))
const filteredAvailableAttendees = computed(() => availableAttendees.value.filter(participantMatchesKeyword))
const filteredManualAttendees = computed(() => manualAttendees.value.filter(participantMatchesKeyword))
const participantPreviewHint = computed(() => participants.value.length ? `已显示 ${participantPreview.value.length} 人预览，点击查看完整名单` : '点击查看并管理参与池')

const slotBaseNames = computed(() => {
  const names = participants.value.map(item => item.name).filter(Boolean)
  return names.length ? names : ['等待参与者', '正在准备', '请稍候']
})
const buildSlotColumn = (names, shift) => {
  const rotated = names.map((_, index) => names[(index + shift) % names.length])
  const loop = Array.from({ length: 10 }, () => rotated).flat()
  return [...loop, ...loop]
}
const slotColumns = computed(() => {
  const names = slotBaseNames.value
  return [0, 1, 2].map(shift => buildSlotColumn(names, shift % names.length))
})
const slotTrackStyle = (columnIndex) => ({ '--duration': `${1.45 + columnIndex * 0.22}s`, '--delay': `${-0.18 * columnIndex}s` })

const applySnapshot = (payload) => {
  if (!payload) return
  if (payload.meeting_id && Number(payload.meeting_id) !== activeMeetingId) return
  session.value = buildSessionSnapshot(payload)
}

const fetchMeetingDetail = async () => {
  if (isMockMode.value) {
    meetingDetail.value = {
      ...mockMeetingDetail.value,
      attendees: mockMeetingDetail.value.attendees.map(normalizeMeetingAttendee)
    }
    return
  }
  meetingDetailLoading.value = true
  try {
    const payload = await request.get(`/meetings/${activeMeetingId}`)
    meetingDetail.value = {
      id: payload?.id ?? activeMeetingId,
      title: payload?.title || '当前会议',
      attendees: Array.isArray(payload?.attendees) ? payload.attendees.map(normalizeMeetingAttendee) : []
    }
  } catch (error) {
    meetingDetail.value = createEmptyMeetingDetail()
    ElMessage.error(error?.response?.data?.detail || '参会人员名单加载失败')
  } finally {
    meetingDetailLoading.value = false
  }
}

const fetchSession = async () => {
  if (isMockMode.value) {
    session.value = mockSessionState.value
    meetingDetail.value = {
      ...mockMeetingDetail.value,
      attendees: mockMeetingDetail.value.attendees.map(normalizeMeetingAttendee)
    }
    return
  }
  try {
    const payload = await request.get(`/lottery/${activeMeetingId}/session`)
    applySnapshot(payload)
    if (!socket) connectSocket()
  } catch (error) {
    session.value = createEmptySession()
  }
}

const connectSocket = () => {
  if (socket || isMockMode.value) return
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, { path: '/socket.io', transports: ['websocket', 'polling'], reconnection: true })
  socket.on('connect', () => {
    socket.emit('join_meeting', { meeting_id: activeMeetingId })
  })
  socket.on('lottery_session_change', applySnapshot)
}

const disconnectSocket = () => {
  if (!socket) return
  socket.disconnect()
  socket = null
}

const startMockRoll = () => {
  if (!canStartNextRound.value) return
  updateMockSession((draft) => {
    const currentRound = draft.current_round_id ? findRoundById(draft.rounds, draft.current_round_id) : null
    const targetRound = currentRound && currentRound.status !== 'finished' ? currentRound : draft.rounds.find(item => item.status !== 'finished')
    if (!targetRound) return
    targetRound.status = 'ready'
    draft.current_round_id = targetRound.id
    draft.session_status = 'rolling'
    draft.winners = []
  })
}

const stopMockRoll = () => {
  updateMockSession((draft) => {
    const currentRound = findRoundById(draft.rounds, draft.current_round_id)
    if (!currentRound) return
    const candidatePool = getMockCandidatePool(draft, currentRound)
    const winners = randomPick(candidatePool, currentRound.count || 1).map(item => ({
      id: `${currentRound.id}-${item.user_id}`,
      user_id: item.user_id || item.id,
      user_name: item.name,
      name: item.name,
      department: item.department || ''
    }))
    currentRound.status = 'finished'
    currentRound.winners = winners
    draft.winners = winners
    draft.session_status = draft.rounds.some(item => item.status !== 'finished') ? 'result' : 'completed'
  })
}

const openParticipantDialog = async () => {
  participantDialogVisible.value = true
  participantSearch.value = ''
  await fetchMeetingDetail()
}

const isParticipantPending = (userId) => Boolean(participantPendingMap.value[String(userId)])

const setParticipantPending = (userId, pending) => {
  const key = String(userId)
  participantPendingMap.value = pending
    ? { ...participantPendingMap.value, [key]: true }
    : Object.fromEntries(Object.entries(participantPendingMap.value).filter(([entryKey]) => entryKey !== key))
}

const addParticipantToPool = async (attendee) => {
  const userId = Number(attendee?.user_id)
  if (!Number.isFinite(userId)) return
  setParticipantPending(userId, true)
  try {
    if (isMockMode.value) {
      updateMockSession((draft) => {
        if (draft.participants.some(item => Number(item.user_id) === userId)) return
        draft.participants.push(normalizeParticipant({
          id: userId,
          user_id: userId,
          name: attendee.name,
          department: attendee.meeting_role || ''
        }))
        draft.participants_count = draft.participants.length
      })
      ElMessage.success(`已将 ${attendee.name} 加入抽签池`)
      return
    }
    const payload = await request.post(`/lottery/${activeMeetingId}/participants/join`, { user_id: userId })
    applySnapshot(payload)
    ElMessage.success(`已将 ${attendee.name} 加入抽签池`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || '加入抽签池失败')
  } finally {
    setParticipantPending(userId, false)
  }
}

const removeParticipantFromPool = async (participant) => {
  const userId = Number(participant?.user_id)
  if (!Number.isFinite(userId)) return
  setParticipantPending(userId, true)
  try {
    if (isMockMode.value) {
      updateMockSession((draft) => {
        draft.participants = draft.participants.filter(item => Number(item.user_id) !== userId)
        draft.participants_count = draft.participants.length
      })
      ElMessage.success(`已将 ${participant.name} 移出抽签池`)
      return
    }
    const payload = await request.post(`/lottery/${activeMeetingId}/participants/quit`, { user_id: userId })
    applySnapshot(payload)
    ElMessage.success(`已将 ${participant.name} 移出抽签池`)
  } catch (error) {
    ElMessage.error(error?.response?.data?.detail || '移出抽签池失败')
  } finally {
    setParticipantPending(userId, false)
  }
}

const startRoll = async () => {
  actionLoading.value = true
  try {
    if (isMockMode.value) {
      startMockRoll()
      ElMessage.success('Mock 抽签已开始')
      return
    }
    const payload = await request.post(`/lottery/${activeMeetingId}/roll`)
    applySnapshot(payload)
    ElMessage.success('抽签已开始滚动')
  } finally {
    actionLoading.value = false
  }
}

const stopRoll = async () => {
  actionLoading.value = true
  try {
    if (isMockMode.value) {
      stopMockRoll()
      ElMessage.success('Mock 抽签结果已生成')
      return
    }
    const payload = await request.post(`/lottery/${activeMeetingId}/stop`)
    applySnapshot(payload)
    ElMessage.success('本轮抽签结果已生成')
  } finally {
    actionLoading.value = false
  }
}

onMounted(fetchSession)
onUnmounted(disconnectSocket)
</script>

<style scoped>
.lottery-screen { min-height: 100vh; padding: 18px 20px 22px; background: radial-gradient(circle at top left, rgba(14, 165, 233, 0.14), transparent 26%), radial-gradient(circle at bottom right, rgba(245, 158, 11, 0.12), transparent 24%), linear-gradient(145deg, #08111f 0%, #111827 44%, #1e293b 100%); color: #e5eefb; }
.overview-card, .stage-card, .result-card { border: 1px solid rgba(148, 163, 184, 0.14); background: rgba(10, 18, 33, 0.82); backdrop-filter: blur(16px); box-shadow: 0 18px 48px rgba(2, 6, 23, 0.3); }
.overview-card, .screen-grid { max-width: 1560px; margin-left: auto; margin-right: auto; }
.overview-card { padding: 18px 22px 20px; border-radius: 26px; }
.overview-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.overview-eyebrow { display: inline-flex; align-items: center; padding: 6px 10px; border-radius: 999px; background: rgba(14, 165, 233, 0.14); color: #7dd3fc; font-size: 12px; letter-spacing: 0.08em; }
.overview-note { margin: 8px 0 0; color: #94a3b8; font-size: 13px; }
.mock-chip { display: inline-flex; align-items: center; min-height: 32px; padding: 0 12px; border-radius: 999px; background: rgba(245, 158, 11, 0.16); color: #fde68a; font-size: 13px; font-weight: 700; white-space: nowrap; }
.overview-metrics { display: grid; grid-template-columns: repeat(6, minmax(0, 1fr)); gap: 12px; }
.metric-item { padding: 14px 16px; border-radius: 18px; background: rgba(15, 23, 42, 0.72); border: 1px solid rgba(148, 163, 184, 0.12); }
.metric-item span { display: block; color: #94a3b8; font-size: 13px; }
.metric-item strong { display: block; margin-top: 10px; color: #f8fafc; font-size: clamp(22px, 2vw, 30px); line-height: 1.15; }
.metric-item em { display: block; margin-top: 10px; color: #7dd3fc; font-size: 12px; font-style: normal; }
.metric-button { width: 100%; color: inherit; text-align: left; cursor: pointer; transition: transform 0.2s ease, border-color 0.2s ease, box-shadow 0.2s ease; }
.metric-button:hover { transform: translateY(-1px); border-color: rgba(125, 211, 252, 0.26); box-shadow: 0 12px 24px rgba(2, 6, 23, 0.18); }
.metric-button:active { transform: translateY(0); }
.screen-grid { display: grid; grid-template-columns: minmax(0, 1.72fr) minmax(300px, 0.88fr); gap: 18px; margin-top: 18px; }
.stage-card, .result-card { display: flex; flex-direction: column; min-height: calc(100vh - 182px); padding: 22px; border-radius: 28px; }
.section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 14px; }
.section-main { min-width: 0; }
.section-kicker { display: inline-block; color: #7dd3fc; font-size: 12px; letter-spacing: 0.08em; }
.section-head h2 { margin: 8px 0 0; font-size: clamp(28px, 2.2vw, 36px); line-height: 1.18; }
.section-description { margin: 10px 0 0; color: #94a3b8; font-size: 14px; line-height: 1.65; }
.stage-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; flex-shrink: 0; }
.state-chip { display: inline-flex; align-items: center; min-height: 36px; padding: 0 14px; border-radius: 999px; font-size: 13px; font-weight: 700; white-space: nowrap; }
.state-idle, .state-collecting, .state-ready { color: #fde68a; background: rgba(245, 158, 11, 0.14); }
.state-rolling { color: #86efac; background: rgba(34, 197, 94, 0.16); }
.state-result, .state-completed { color: #bfdbfe; background: rgba(59, 130, 246, 0.16); }
.stage-body, .result-list, .result-empty { flex: 1; min-height: 0; }
.stage-body { display: flex; flex-direction: column; justify-content: center; margin-top: 16px; padding: 10px 2px 0; }
.stage-empty, .ready-stage, .winner-stage { text-align: center; }
.stage-empty, .ready-stage { display: flex; flex-direction: column; align-items: center; justify-content: center; gap: 14px; }
.stage-empty h3, .ready-stage h3, .winner-stage h3, .result-empty h3 { margin: 0; font-size: clamp(30px, 2.3vw, 42px); }
.stage-empty p, .ready-stage p, .winner-stage p, .result-empty p { margin: 0; max-width: 620px; color: #94a3b8; font-size: 16px; line-height: 1.7; }
.ready-badge, .winner-badge, .round-order { display: inline-flex; align-items: center; justify-content: center; min-height: 34px; padding: 0 14px; border-radius: 999px; background: rgba(96, 165, 250, 0.14); color: #dbeafe; font-size: 12px; font-weight: 700; }
.participant-preview-head { display: flex; align-items: center; justify-content: center; gap: 12px; flex-wrap: wrap; margin-top: 8px; }
.preview-label { color: #7dd3fc; font-size: 13px; letter-spacing: 0.04em; }
.preview-link { padding: 0; border: 0; background: none; color: #c4e9ff; font-size: 13px; cursor: pointer; text-decoration: underline; text-underline-offset: 4px; }
.preview-link:hover { color: #f8fafc; }
.participant-preview { display: flex; flex-wrap: wrap; justify-content: center; gap: 10px; max-width: 760px; margin-top: 6px; }
.participant-chip { display: inline-flex; align-items: center; min-height: 38px; padding: 0 14px; border-radius: 999px; background: rgba(15, 23, 42, 0.56); border: 1px solid rgba(148, 163, 184, 0.14); color: #dbeafe; font-size: 13px; }
.participant-chip-summary { background: rgba(14, 165, 233, 0.14); border-color: rgba(125, 211, 252, 0.22); color: #7dd3fc; }
.slot-stage { display: flex; flex: 1; flex-direction: column; justify-content: center; gap: 18px; }
.slot-machine { position: relative; min-height: 620px; padding: 22px; overflow: hidden; border-radius: 32px; background: radial-gradient(circle at center, rgba(56, 189, 248, 0.18), transparent 46%), linear-gradient(180deg, rgba(15, 23, 42, 0.95) 0%, rgba(8, 17, 31, 0.88) 100%); }
.slot-machine::before { content: ''; position: absolute; inset: 16px; border-radius: 26px; border: 1px solid rgba(125, 211, 252, 0.12); pointer-events: none; }
.slot-mask { position: absolute; left: 0; right: 0; height: 110px; z-index: 2; pointer-events: none; }
.slot-mask-top { top: 0; background: linear-gradient(180deg, rgba(8, 17, 31, 0.96), rgba(8, 17, 31, 0)); }
.slot-mask-bottom { bottom: 0; background: linear-gradient(0deg, rgba(8, 17, 31, 0.96), rgba(8, 17, 31, 0)); }
.slot-highlight { position: absolute; left: 28px; right: 28px; top: 50%; height: 92px; transform: translateY(-50%); border-radius: 22px; border: 1px solid rgba(125, 211, 252, 0.22); background: rgba(37, 99, 235, 0.1); box-shadow: inset 0 0 0 1px rgba(255, 255, 255, 0.03); z-index: 1; }
.slot-columns { position: relative; z-index: 1; display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 18px; height: 100%; min-height: 576px; }
.slot-column { position: relative; height: 100%; overflow: hidden; }
.slot-track { display: flex; flex-direction: column; align-items: stretch; animation: slot-scroll var(--duration) linear infinite; animation-delay: var(--delay); will-change: transform; }
.slot-name { display: flex; align-items: center; justify-content: center; height: 92px; color: #f8fafc; font-size: clamp(32px, 3vw, 48px); font-weight: 700; letter-spacing: 0.06em; text-shadow: 0 10px 36px rgba(14, 165, 233, 0.22); }
.stage-footnote { display: flex; align-items: center; justify-content: center; gap: 12px; flex-wrap: wrap; color: #cbd5e1; text-align: center; }
.stage-footnote span { display: inline-flex; align-items: center; min-height: 30px; padding: 0 12px; border-radius: 999px; background: rgba(30, 41, 59, 0.7); color: #dbeafe; font-size: 12px; }
.stage-footnote strong { font-size: 18px; }
.stage-footnote em { color: #94a3b8; font-size: 13px; font-style: normal; }
.winner-stage { display: flex; flex: 1; flex-direction: column; align-items: center; justify-content: center; }
.winner-stage-head { max-width: 700px; }
.winner-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(220px, 1fr)); gap: 16px; width: min(100%, 860px); margin-top: 26px; }
.winner-card { padding: 22px 20px; border-radius: 22px; background: linear-gradient(180deg, rgba(37, 99, 235, 0.14), rgba(14, 165, 233, 0.08)); border: 1px solid rgba(96, 165, 250, 0.18); text-align: left; }
.winner-card strong { display: block; color: #f8fafc; font-size: 24px; }
.result-head { margin-bottom: 16px; }
.result-count { display: inline-flex; align-items: center; min-height: 32px; padding: 0 12px; border-radius: 999px; background: rgba(30, 41, 59, 0.72); color: #cbd5e1; font-size: 13px; white-space: nowrap; }
.result-list { display: flex; flex-direction: column; gap: 12px; overflow-y: auto; padding-right: 4px; }
.result-round { padding: 14px 16px; border-radius: 18px; background: rgba(15, 23, 42, 0.54); border: 1px solid rgba(148, 163, 184, 0.12); }
.result-round.is-current-result { border-color: rgba(96, 165, 250, 0.32); background: linear-gradient(180deg, rgba(37, 99, 235, 0.16), rgba(15, 23, 42, 0.56)); }
.result-round-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; }
.result-round-head strong { display: block; margin-top: 8px; color: #f8fafc; font-size: 18px; }
.round-count { color: #94a3b8; font-size: 13px; white-space: nowrap; }
.winner-chip-list { display: flex; flex-wrap: wrap; gap: 10px; margin-top: 14px; }
.winner-chip { display: inline-flex; align-items: center; min-height: 34px; padding: 0 14px; border-radius: 999px; background: rgba(37, 99, 235, 0.14); border: 1px solid rgba(96, 165, 250, 0.14); color: #dbeafe; font-size: 13px; }
.result-empty { display: flex; flex-direction: column; align-items: center; justify-content: center; text-align: center; gap: 12px; }
:deep(.participant-dialog) { border-radius: 28px; overflow: hidden; }
:deep(.participant-dialog .el-dialog) { border-radius: 28px; background: #0f172a; border: 1px solid rgba(148, 163, 184, 0.18); box-shadow: 0 28px 72px rgba(2, 6, 23, 0.48); }
:deep(.participant-dialog .el-dialog__header) { margin: 0; padding: 24px 28px 12px; }
:deep(.participant-dialog .el-dialog__body) { padding: 0 28px 28px; }
.participant-dialog-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; }
.participant-dialog-header h3 { margin: 0; color: #f8fafc; font-size: 26px; }
.participant-dialog-header p { margin: 8px 0 0; color: #94a3b8; font-size: 14px; }
.participant-dialog-status { display: inline-flex; align-items: center; min-height: 34px; padding: 0 14px; border-radius: 999px; background: rgba(34, 197, 94, 0.16); color: #86efac; font-size: 13px; font-weight: 700; white-space: nowrap; }
.participant-dialog-status.is-locked { background: rgba(245, 158, 11, 0.16); color: #fde68a; }
.participant-toolbar { display: flex; align-items: center; justify-content: space-between; gap: 14px; margin-bottom: 16px; }
.participant-search { max-width: 360px; }
.participant-toolbar-meta { color: #94a3b8; font-size: 13px; white-space: nowrap; }
.participant-loading, .participant-lock-tip { margin-bottom: 16px; padding: 14px 16px; border-radius: 16px; font-size: 14px; }
.participant-loading { background: rgba(30, 41, 59, 0.72); color: #cbd5e1; }
.participant-lock-tip { background: rgba(245, 158, 11, 0.14); color: #fde68a; border: 1px solid rgba(245, 158, 11, 0.18); }
.participant-manage-grid { display: grid; grid-template-columns: minmax(0, 1fr) minmax(0, 1fr); gap: 18px; }
.participant-manage-section, .participant-manual-section { border-radius: 20px; padding: 18px; background: rgba(15, 23, 42, 0.72); border: 1px solid rgba(148, 163, 184, 0.12); }
.participant-section-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 12px; margin-bottom: 14px; }
.participant-section-head h4 { margin: 0; color: #f8fafc; font-size: 18px; }
.participant-section-head p { margin: 6px 0 0; color: #94a3b8; font-size: 13px; line-height: 1.6; }
.participant-section-head span { color: #7dd3fc; font-size: 13px; white-space: nowrap; }
.participant-manage-list { display: flex; flex-direction: column; gap: 10px; max-height: 420px; overflow-y: auto; padding-right: 4px; }
.participant-row { display: flex; align-items: center; justify-content: space-between; gap: 12px; padding: 14px 16px; border-radius: 16px; background: rgba(8, 17, 31, 0.74); border: 1px solid rgba(148, 163, 184, 0.1); }
.participant-row-main { min-width: 0; }
.participant-row-main strong { display: block; color: #f8fafc; font-size: 16px; }
.participant-row-main span { display: block; margin-top: 6px; color: #94a3b8; font-size: 13px; }
.participant-manual-section { margin-top: 18px; }
.participant-manual-list { display: flex; flex-wrap: wrap; gap: 10px; }
.manual-attendee-chip { display: inline-flex; align-items: center; min-height: 34px; padding: 0 14px; border-radius: 999px; background: rgba(30, 41, 59, 0.72); border: 1px solid rgba(148, 163, 184, 0.12); color: #dbeafe; font-size: 13px; }
@keyframes slot-scroll { from { transform: translateY(0); } to { transform: translateY(-50%); } }
@media (max-width: 1440px) { .overview-metrics { grid-template-columns: repeat(3, minmax(0, 1fr)); } .screen-grid { grid-template-columns: minmax(0, 1.52fr) minmax(280px, 0.92fr); } }
@media (max-width: 1200px) { .screen-grid { grid-template-columns: 1fr; } .stage-card, .result-card { min-height: auto; } .slot-machine { min-height: 520px; } .participant-manage-grid { grid-template-columns: 1fr; } }
@media (max-width: 900px) { .lottery-screen { padding: 12px; } .overview-head, .section-head, .participant-dialog-header, .participant-toolbar { flex-direction: column; align-items: stretch; } .overview-metrics { grid-template-columns: repeat(2, minmax(0, 1fr)); } .stage-actions { justify-content: flex-start; } .slot-columns { gap: 10px; } .slot-name { font-size: clamp(24px, 5vw, 34px); } .winner-grid { grid-template-columns: 1fr; } .participant-search { max-width: none; } .participant-toolbar-meta { white-space: normal; } }
</style>
