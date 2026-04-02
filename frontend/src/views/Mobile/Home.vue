<template>
  <div class="mobile-home">
    <section class="home-hero">
      <div>
        <span class="hero-kicker">我的会议</span>
        <h1>{{ username }}，今天要参加哪些会议？</h1>
        <p>会议详情里已经接入投票与抽签互动入口，现场操作会实时同步。</p>
      </div>
      <el-button class="logout-btn" plain @click="logout">
        <el-icon><SwitchButton /></el-icon>
        退出登录
      </el-button>
    </section>

    <section class="meeting-section">
      <div class="section-head">
        <div>
          <h2>会议列表</h2>
          <p>仅展示当前登录人可参与的会议。</p>
        </div>
        <el-button plain :loading="loadingMeetings" @click="fetchMeetings">刷新</el-button>
      </div>

      <div v-if="meetings.length" class="meeting-grid">
        <article
          v-for="meeting in meetings"
          :key="meeting.id"
          class="meeting-card"
          @click="openMeeting(meeting.id)"
        >
          <div
            class="meeting-cover"
            :style="{ backgroundImage: meeting.card_image_thumb_url ? `linear-gradient(180deg, rgba(15,23,42,.12), rgba(15,23,42,.78)), url(${meeting.card_image_thumb_url})` : undefined }"
          >
            <el-tag size="small" effect="dark" :type="meeting.is_today_meeting ? 'success' : 'info'">
              {{ meeting.is_today_meeting ? '今日会议' : '待参加' }}
            </el-tag>
            <span class="meeting-type">{{ meeting.meeting_type_name || '普通会议' }}</span>
          </div>

          <div class="meeting-body">
            <h3>{{ meeting.title }}</h3>
            <div class="meeting-meta">
              <span><el-icon><Clock /></el-icon>{{ formatDateTime(meeting.start_time) }}</span>
              <span><el-icon><Location /></el-icon>{{ meeting.location || '地点待定' }}</span>
            </div>
            <div class="meeting-footer">
              <span>{{ meeting.attachments?.length || 0 }} 份资料</span>
              <el-button text type="primary">
                查看详情
                <el-icon><ArrowRight /></el-icon>
              </el-button>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-else description="当前没有可参与的会议" :image-size="120" />
    </section>

    <el-drawer
      v-model="detailVisible"
      :size="drawerSize"
      append-to-body
      direction="rtl"
      destroy-on-close
      @closed="handleDetailClosed"
    >
      <template #header>
        <div class="drawer-header">
          <div>
            <h2>{{ selectedMeeting?.title || '会议详情' }}</h2>
            <p>{{ selectedMeeting?.meeting_type_name || '普通会议' }}</p>
          </div>
          <el-button plain :loading="detailLoading || overviewLoading" @click="refreshSelectedMeeting">
            刷新
          </el-button>
        </div>
      </template>

      <div v-loading="detailLoading || overviewLoading" class="drawer-content">
        <template v-if="selectedMeeting">
          <section
            class="meeting-summary"
            :style="{ backgroundImage: selectedMeeting.card_image_url ? `linear-gradient(180deg, rgba(15,23,42,.1), rgba(15,23,42,.8)), url(${selectedMeeting.card_image_url})` : undefined }"
          >
            <div class="summary-tags">
              <el-tag effect="dark" type="success">{{ selectedMeeting.status || 'scheduled' }}</el-tag>
              <el-tag effect="dark" type="info">{{ selectedMeeting.card_image_source || 'cover' }}</el-tag>
            </div>
            <h3>{{ selectedMeeting.title }}</h3>
            <p>{{ selectedMeeting.speaker || '主持信息待补充' }}</p>
          </section>

          <section class="info-grid">
            <div class="info-card">
              <span>开始时间</span>
              <strong>{{ formatDateTime(selectedMeeting.start_time) }}</strong>
            </div>
            <div class="info-card">
              <span>会议地点</span>
              <strong>{{ selectedMeeting.location || '待定' }}</strong>
            </div>
            <div class="info-card">
              <span>与会人员</span>
              <strong>{{ selectedMeeting.attendees?.length || 0 }} 人</strong>
            </div>
          </section>

          <section class="interaction-section">
            <div class="section-head">
              <div>
                <h3>互动入口</h3>
                <p>有活动投票或可加入抽签时，会在这里实时更新。</p>
              </div>
            </div>

            <div class="interaction-grid">
              <article class="interaction-card">
                <div class="interaction-top">
                  <div>
                    <span class="interaction-kicker">投票</span>
                    <h4>{{ displayVote?.title || '当前没有活动投票' }}</h4>
                  </div>
                  <el-tag :type="getVoteTagType(displayVote?.status)">
                    {{ getVoteStatusLabel(displayVote?.status) }}
                  </el-tag>
                </div>
                <p class="interaction-desc">
                  {{
                    displayVote
                      ? displayVote.description || '移动端可参与投票，结果会实时同步。'
                      : '主持人发布后，这里会出现投票入口。'
                  }}
                </p>
                <div class="interaction-meta" v-if="displayVote">
                  <span>{{ displayVote.is_multiple ? `多选 / 最多 ${displayVote.max_selections} 项` : '单选' }}</span>
                  <span>{{ buildVoteStatusHint(displayVote) }}</span>
                </div>
                <div class="interaction-actions">
                  <el-button
                    v-if="displayVote?.status === 'active' && !displayVote.user_voted"
                    type="primary"
                    @click="openVotePanel"
                  >
                    去投票
                  </el-button>
                  <el-button
                    v-else-if="displayVote?.status === 'active' && displayVote.user_voted"
                    type="success"
                    plain
                    disabled
                  >
                    已参与
                  </el-button>
                  <el-button
                    v-if="displayVote"
                    plain
                    @click="openVoteResultPanel(displayVote)"
                  >
                    {{ displayVote.status === 'closed' ? '查看结果' : '查看进度' }}
                  </el-button>
                </div>
              </article>

              <article class="interaction-card">
                <div class="interaction-top">
                  <div>
                    <span class="interaction-kicker">抽签</span>
                    <h4>{{ lotteryRoundTitle }}</h4>
                  </div>
                  <el-tag :type="getLotteryTagType(interactionOverview.lottery.session_status)">
                    {{ lotteryStatusLabel }}
                  </el-tag>
                </div>
                <p class="interaction-desc">{{ lotteryDescription }}</p>
                <div class="interaction-meta">
                  <span>参与池 {{ interactionOverview.lottery.participants_count || 0 }} 人</span>
                  <span v-if="interactionOverview.lottery.current_round">
                    本轮抽取 {{ interactionOverview.lottery.current_round.count }} 人
                  </span>
                </div>
                <div class="interaction-actions">
                  <el-button
                    v-if="canJoinLottery"
                    type="primary"
                    :loading="lotteryActionLoading"
                    @click="joinLottery"
                  >
                    加入抽签池
                  </el-button>
                  <el-button
                    v-if="canQuitLottery"
                    plain
                    :loading="lotteryActionLoading"
                    @click="quitLottery"
                  >
                    退出抽签池
                  </el-button>
                  <el-button
                    v-if="interactionOverview.lottery.winners?.length"
                    plain
                    @click="showLotteryWinners = !showLotteryWinners"
                  >
                    {{ showLotteryWinners ? '收起结果' : '查看结果' }}
                  </el-button>
                </div>
                <div v-if="showLotteryWinners && interactionOverview.lottery.winners?.length" class="winner-list">
                  <span
                    v-for="winner in interactionOverview.lottery.winners"
                    :key="winner.user_id || winner.id"
                    class="winner-chip"
                  >
                    {{ winner.name || winner.user_name }}
                  </span>
                </div>
              </article>
            </div>
          </section>

          <section class="agenda-section" v-if="selectedMeeting.agenda_items?.length">
            <div class="section-head">
              <div>
                <h3>会议议程</h3>
                <p>按会议详情配置展示。</p>
              </div>
            </div>
            <div class="agenda-list">
              <div v-for="(agenda, index) in selectedMeeting.agenda_items" :key="`${agenda.content}-${index}`" class="agenda-item">
                <span>{{ index + 1 }}</span>
                <p>{{ agenda.content }}</p>
              </div>
            </div>
          </section>

          <section class="agenda-section" v-if="selectedMeeting.attendees?.length">
            <div class="section-head">
              <div>
                <h3>参会人员</h3>
                <p>仅展示当前会议名单中的人员。</p>
              </div>
            </div>
            <div class="attendee-list">
              <span v-for="attendee in selectedMeeting.attendees" :key="`${attendee.type}-${attendee.user_id || attendee.name}`" class="attendee-chip">
                {{ attendee.name }}
              </span>
            </div>
          </section>
        </template>
      </div>
    </el-drawer>

    <el-drawer
      v-model="votePanelVisible"
      title="参与投票"
      append-to-body
      direction="btt"
      size="72%"
    >
      <template v-if="currentActiveVote">
        <div class="vote-panel">
          <div class="vote-panel-head">
            <h3>{{ currentActiveVote.title }}</h3>
            <p>{{ currentActiveVote.description || '请根据现场说明选择对应选项。' }}</p>
          </div>

          <el-radio-group v-if="!currentActiveVote.is_multiple" v-model="singleVoteOptionId" class="vote-option-list">
            <el-radio
              v-for="option in currentActiveVote.options"
              :key="option.id"
              :label="option.id"
              size="large"
              border
            >
              {{ option.content }}
            </el-radio>
          </el-radio-group>

          <el-checkbox-group v-else v-model="multiVoteOptionIds" class="vote-option-list">
            <el-checkbox
              v-for="option in currentActiveVote.options"
              :key="option.id"
              :label="option.id"
              size="large"
              border
            >
              {{ option.content }}
            </el-checkbox>
          </el-checkbox-group>

          <div class="vote-submit-bar">
            <span>
              {{
                currentActiveVote.is_multiple
                  ? `最多可选 ${currentActiveVote.max_selections} 项`
                  : '本次为单选投票'
              }}
            </span>
            <el-button type="primary" :loading="voteSubmitting" @click="submitVote">
              提交投票
            </el-button>
          </div>
        </div>
      </template>
    </el-drawer>

    <el-drawer
      v-model="voteResultVisible"
      title="投票结果"
      append-to-body
      direction="btt"
      size="72%"
    >
      <div v-loading="voteResultLoading" class="vote-result-panel">
        <template v-if="voteResult">
          <div class="vote-panel-head">
            <h3>{{ voteResult.title }}</h3>
            <p>共 {{ voteResult.total_voters }} 人参与</p>
          </div>

          <div v-for="item in voteResult.results" :key="item.option_id" class="vote-result-item">
            <div class="vote-result-line">
              <strong>{{ item.content }}</strong>
              <span>{{ item.count }} 票 / {{ item.percent }}%</span>
            </div>
            <el-progress :percentage="Number(item.percent)" :stroke-width="12" />
            <div v-if="item.voters?.length" class="attendee-list">
              <span v-for="name in item.voters" :key="name" class="attendee-chip">{{ name }}</span>
            </div>
          </div>
        </template>
      </div>
    </el-drawer>
  </div>
</template>

<script setup>
import { computed, onMounted, onUnmounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { io } from 'socket.io-client'
import {
  ArrowRight,
  Clock,
  Location,
  SwitchButton
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import request from '@/utils/request'

const router = useRouter()

const userId = Number(localStorage.getItem('user_id') || 0)
const username = ref(localStorage.getItem('user_name') || '参会人')

const loadingMeetings = ref(false)
const detailLoading = ref(false)
const overviewLoading = ref(false)
const lotteryActionLoading = ref(false)
const voteSubmitting = ref(false)
const voteResultLoading = ref(false)

const meetings = ref([])
const detailVisible = ref(false)
const votePanelVisible = ref(false)
const voteResultVisible = ref(false)
const showLotteryWinners = ref(false)
const selectedMeetingId = ref(null)
const selectedMeeting = ref(null)
const voteResult = ref(null)
const singleVoteOptionId = ref(null)
const multiVoteOptionIds = ref([])

const interactionOverview = reactive({
  vote: { active: null, items: [], draft_count: 0, closed_count: 0 },
  lottery: {
    session_status: 'idle',
    current_round: null,
    participants: [],
    participants_count: 0,
    winners: [],
    joined: false,
    rounds: [],
    all_rounds_finished: false
  }
})

let socket = null

const drawerSize = window.innerWidth < 900 ? '100%' : '62%'

const latestClosedVote = computed(() => interactionOverview.vote.items.find(item => item.status === 'closed') || null)
const displayVote = computed(() => interactionOverview.vote.active || latestClosedVote.value || null)
const currentActiveVote = computed(() => interactionOverview.vote.active?.status === 'active' ? interactionOverview.vote.active : null)

const lotteryStatusLabel = computed(() => ({
  idle: '空闲',
  collecting: '可加入',
  ready: '待开始',
  rolling: '滚动中',
  result: '结果展示中',
  completed: '全部完成'
}[interactionOverview.lottery.session_status] || '暂无'))

const lotteryRoundTitle = computed(() => interactionOverview.lottery.current_round?.title || '当前没有准备中的轮次')

const lotteryDescription = computed(() => {
  const lottery = interactionOverview.lottery
  if (lottery.session_status === 'rolling') return '抽签正在滚动中，当前无法加入或退出。'
  if (lottery.winners?.length) return `本轮已产生 ${lottery.winners.length} 位中签人员。`
  if (lottery.current_round) return `本轮为「${lottery.current_round.title}」，可根据状态加入或退出抽签池。`
  return '主持人准备轮次后，这里会开放加入入口。'
})

const canJoinLottery = computed(() => {
  const lottery = interactionOverview.lottery
  return Boolean(
    selectedMeetingId.value &&
    lottery.current_round &&
    !lottery.joined &&
    !lottery.all_rounds_finished &&
    ['collecting', 'ready'].includes(lottery.session_status)
  )
})

const canQuitLottery = computed(() => {
  const lottery = interactionOverview.lottery
  return Boolean(
    selectedMeetingId.value &&
    lottery.joined &&
    ['collecting', 'ready', 'idle', 'result'].includes(lottery.session_status)
  )
})

const resetInteractionOverview = () => {
  interactionOverview.vote = { active: null, items: [], draft_count: 0, closed_count: 0 }
  interactionOverview.lottery = {
    session_status: 'idle',
    current_round: null,
    participants: [],
    participants_count: 0,
    winners: [],
    joined: false,
    rounds: [],
    all_rounds_finished: false
  }
}

const fetchMeetings = async () => {
  if (!userId) {
    router.push('/mobile/login')
    return
  }

  loadingMeetings.value = true
  try {
    const data = await request.get('/meetings/', {
      params: {
        user_id: userId,
        sort: 'asc',
        limit: 100
      }
    })
    meetings.value = Array.isArray(data) ? data : []
  } finally {
    loadingMeetings.value = false
  }
}

const fetchMeetingDetail = async (meetingId) => {
  detailLoading.value = true
  try {
    selectedMeeting.value = await request.get(`/meetings/${meetingId}`, {
      params: { user_id: userId }
    })
  } finally {
    detailLoading.value = false
  }
}

const fetchInteractionOverview = async (meetingId) => {
  overviewLoading.value = true
  try {
    const overview = await request.get(`/interactions/meeting/${meetingId}/overview`, {
      params: { user_id: userId }
    })
    interactionOverview.vote = overview.vote || interactionOverview.vote
    interactionOverview.lottery = overview.lottery || interactionOverview.lottery
  } finally {
    overviewLoading.value = false
  }
}

const refreshSelectedMeeting = async () => {
  if (!selectedMeetingId.value) return
  await Promise.all([
    fetchMeetingDetail(selectedMeetingId.value),
    fetchInteractionOverview(selectedMeetingId.value)
  ])
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
  const refreshOverview = () => fetchInteractionOverview(meetingId)
  socket.on('vote_state_change', refreshOverview)
  socket.on('vote_results_change', refreshOverview)
  socket.on('lottery_session_change', refreshOverview)
  socket.on('meeting_changed', async () => {
    await fetchMeetings()
    if (selectedMeetingId.value === meetingId) {
      await fetchMeetingDetail(meetingId)
    }
  })
}

const disconnectSocket = () => {
  if (!socket) return
  socket.disconnect()
  socket = null
}

const openMeeting = async (meetingId) => {
  selectedMeetingId.value = meetingId
  detailVisible.value = true
  showLotteryWinners.value = false
  await refreshSelectedMeeting()
  connectSocket(meetingId)
}

const handleDetailClosed = () => {
  selectedMeetingId.value = null
  selectedMeeting.value = null
  showLotteryWinners.value = false
  votePanelVisible.value = false
  voteResultVisible.value = false
  voteResult.value = null
  singleVoteOptionId.value = null
  multiVoteOptionIds.value = []
  resetInteractionOverview()
  disconnectSocket()
}

const openVotePanel = () => {
  if (!currentActiveVote.value) return
  singleVoteOptionId.value = null
  multiVoteOptionIds.value = []
  votePanelVisible.value = true
}

const submitVote = async () => {
  if (!currentActiveVote.value) return

  const optionIds = currentActiveVote.value.is_multiple
    ? [...multiVoteOptionIds.value]
    : (singleVoteOptionId.value ? [singleVoteOptionId.value] : [])

  if (!optionIds.length) {
    ElMessage.warning('请至少选择一个选项')
    return
  }
  if (currentActiveVote.value.is_multiple && optionIds.length > currentActiveVote.value.max_selections) {
    ElMessage.warning(`最多只能选择 ${currentActiveVote.value.max_selections} 项`)
    return
  }

  voteSubmitting.value = true
  try {
    await request.post(`/vote/${currentActiveVote.value.id}/submit`, {
      user_id: userId,
      option_ids: optionIds
    })
    ElMessage.success('投票已提交')
    votePanelVisible.value = false
    await fetchInteractionOverview(selectedMeetingId.value)
  } finally {
    voteSubmitting.value = false
  }
}

const openVoteResultPanel = async (vote) => {
  if (!vote?.id) return
  voteResultVisible.value = true
  voteResultLoading.value = true
  try {
    voteResult.value = await request.get(`/vote/${vote.id}/result`)
  } finally {
    voteResultLoading.value = false
  }
}

const joinLottery = async () => {
  if (!selectedMeetingId.value) return
  lotteryActionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${selectedMeetingId.value}/participants/join`, {
      user_id: userId
    })
    interactionOverview.lottery = payload
    ElMessage.success('已加入抽签池')
  } finally {
    lotteryActionLoading.value = false
  }
}

const quitLottery = async () => {
  if (!selectedMeetingId.value) return
  lotteryActionLoading.value = true
  try {
    const payload = await request.post(`/lottery/${selectedMeetingId.value}/participants/quit`, {
      user_id: userId
    })
    interactionOverview.lottery = payload
    ElMessage.success('已退出抽签池')
  } finally {
    lotteryActionLoading.value = false
  }
}

const logout = () => {
  localStorage.removeItem('user_id')
  localStorage.removeItem('user_name')
  localStorage.removeItem('token')
  disconnectSocket()
  router.push('/mobile/login')
}

const formatDateTime = (value) => {
  if (!value) return '未设置'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未设置'
  return `${date.getMonth() + 1}月${date.getDate()}日 ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

const getVoteStatusLabel = (status) => ({
  draft: '草稿',
  countdown: '倒计时',
  active: '进行中',
  closed: '已结束'
}[status] || '暂无')

const getVoteTagType = (status) => ({
  draft: 'info',
  countdown: 'warning',
  active: 'success',
  closed: ''
}[status] || 'info')

const getLotteryTagType = (status) => ({
  idle: 'info',
  collecting: 'warning',
  ready: 'warning',
  rolling: 'success',
  result: '',
  completed: ''
}[status] || 'info')

const buildVoteStatusHint = (vote) => {
  if (!vote) return '等待发布'
  if (vote.status === 'countdown') return `开始倒计时 ${vote.countdown_remaining_seconds || 0} 秒`
  if (vote.status === 'active') return `${vote.total_voters || 0} 人已参与`
  return `共 ${vote.total_voters || 0} 人参与`
}

onMounted(fetchMeetings)

onUnmounted(disconnectSocket)
</script>

<style scoped>
.mobile-home {
  min-height: 100vh;
  padding: 18px;
  background:
    radial-gradient(circle at top right, rgba(14, 165, 233, 0.16), transparent 22%),
    linear-gradient(180deg, #f8fafc 0%, #e2e8f0 100%);
  color: #0f172a;
}

.home-hero,
.meeting-card,
.meeting-summary,
.info-card,
.interaction-card,
.agenda-section {
  border: 1px solid rgba(148, 163, 184, 0.16);
  box-shadow: 0 20px 60px rgba(15, 23, 42, 0.08);
}

.home-hero {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 22px;
  padding: 22px;
  border-radius: 28px;
  background: linear-gradient(135deg, #0f172a, #1d4ed8);
  color: #f8fafc;
}

.hero-kicker {
  display: inline-block;
  margin-bottom: 10px;
  color: rgba(191, 219, 254, 0.88);
  font-size: 12px;
  letter-spacing: 0.14em;
  text-transform: uppercase;
}

.home-hero h1 {
  margin: 0;
  font-size: 28px;
  line-height: 1.25;
}

.home-hero p {
  margin: 10px 0 0;
  max-width: 620px;
  color: rgba(226, 232, 240, 0.9);
  line-height: 1.7;
}

.logout-btn {
  border-color: rgba(226, 232, 240, 0.28);
  color: #f8fafc;
  background: rgba(255, 255, 255, 0.08);
}

.meeting-section,
.agenda-section {
  margin-bottom: 20px;
}

.section-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
}

.section-head h2,
.section-head h3 {
  margin: 0;
  font-size: 22px;
}

.section-head p {
  margin: 6px 0 0;
  color: #64748b;
}

.meeting-grid {
  display: grid;
  gap: 16px;
}

.meeting-card {
  overflow: hidden;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
  cursor: pointer;
  transition: transform 0.2s ease, box-shadow 0.2s ease;
}

.meeting-card:hover {
  transform: translateY(-2px);
  box-shadow: 0 24px 70px rgba(15, 23, 42, 0.14);
}

.meeting-cover {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  min-height: 132px;
  padding: 16px;
  background: linear-gradient(135deg, #1d4ed8, #0f172a);
  background-size: cover;
  background-position: center;
}

.meeting-type {
  display: inline-flex;
  align-items: center;
  padding: 6px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.14);
  color: #f8fafc;
  font-size: 13px;
}

.meeting-body {
  padding: 18px;
}

.meeting-body h3 {
  margin: 0;
  font-size: 22px;
}

.meeting-meta {
  display: grid;
  gap: 8px;
  margin-top: 14px;
  color: #475569;
}

.meeting-meta span,
.interaction-meta span {
  display: flex;
  align-items: center;
  gap: 8px;
}

.meeting-footer {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  margin-top: 18px;
  color: #64748b;
}

.drawer-header h2 {
  margin: 0;
  font-size: 24px;
}

.drawer-header p {
  margin: 6px 0 0;
  color: #64748b;
}

.drawer-content {
  display: grid;
  gap: 18px;
}

.meeting-summary {
  padding: 22px;
  border-radius: 26px;
  background: linear-gradient(160deg, #0f172a, #1e40af);
  background-size: cover;
  background-position: center;
  color: #f8fafc;
}

.summary-tags {
  display: flex;
  gap: 10px;
}

.meeting-summary h3 {
  margin: 40px 0 8px;
  font-size: 28px;
}

.meeting-summary p {
  margin: 0;
  color: rgba(226, 232, 240, 0.9);
}

.info-grid,
.interaction-grid {
  display: grid;
  gap: 14px;
}

.info-grid {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.info-card,
.interaction-card,
.agenda-section {
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 255, 255, 0.88);
}

.info-card span {
  display: block;
  color: #64748b;
  margin-bottom: 10px;
}

.info-card strong {
  font-size: 18px;
}

.interaction-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
}

.interaction-kicker {
  color: #2563eb;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
}

.interaction-top h4 {
  margin: 8px 0 0;
  font-size: 22px;
}

.interaction-desc {
  margin: 14px 0 0;
  color: #475569;
  line-height: 1.7;
}

.interaction-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-top: 14px;
  color: #64748b;
}

.interaction-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.winner-list,
.agenda-list,
.attendee-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 16px;
}

.winner-chip,
.attendee-chip {
  padding: 7px 12px;
  border-radius: 999px;
  background: rgba(37, 99, 235, 0.1);
  color: #1d4ed8;
  font-size: 14px;
}

.agenda-item {
  display: grid;
  grid-template-columns: 36px 1fr;
  gap: 12px;
  align-items: flex-start;
}

.agenda-item span {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 36px;
  height: 36px;
  border-radius: 12px;
  background: #dbeafe;
  color: #1d4ed8;
  font-weight: 700;
}

.agenda-item p {
  margin: 6px 0 0;
  color: #334155;
  line-height: 1.7;
}

.vote-panel,
.vote-result-panel {
  display: grid;
  gap: 18px;
}

.vote-panel-head h3 {
  margin: 0;
  font-size: 24px;
}

.vote-panel-head p {
  margin: 8px 0 0;
  color: #64748b;
  line-height: 1.7;
}

.vote-option-list {
  display: grid;
  gap: 12px;
}

.vote-submit-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.vote-submit-bar span {
  color: #64748b;
}

.vote-result-item {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 18px;
  background: #f8fafc;
}

.vote-result-line {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

@media (min-width: 960px) {
  .meeting-grid,
  .interaction-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .home-hero,
  .section-head,
  .vote-submit-bar,
  .vote-result-line {
    flex-direction: column;
    align-items: flex-start;
  }

  .info-grid {
    grid-template-columns: 1fr;
  }

  .meeting-summary h3 {
    margin-top: 24px;
    font-size: 24px;
  }
}
</style>
