<template>
  <div class="interaction-page">
    <div class="page-header">
      <div class="header-left">
        <el-button class="collapse-btn" link @click="toggleSidebar">
          <el-icon size="24" color="#64748b">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        <div class="title-group">
          <h1 class="page-title">会议互动中心</h1>
          <p class="page-subtitle">统一管理投票、抽签、大屏控制与移动端互动入口</p>
        </div>
      </div>
      <div class="header-actions">
        <el-select
          v-model="selectedMeetingId"
          placeholder="请选择会议"
          filterable
          class="meeting-select"
          :loading="loadingMeetings"
        >
          <el-option
            v-for="meeting in meetings"
            :key="meeting.id"
            :label="meeting.title"
            :value="meeting.id"
          />
        </el-select>
        <el-button plain @click="fetchOverview" :loading="loadingOverview">刷新状态</el-button>
      </div>
    </div>

    <el-empty v-if="!currentMeeting && !loadingMeetings" description="请先选择会议" :image-size="80" />

    <template v-else-if="currentMeeting">
      <div class="summary-grid">
        <el-card class="summary-card" shadow="hover">
          <div class="summary-label">当前会议</div>
          <div class="summary-text">{{ currentMeeting.title }}</div>
          <div class="summary-desc">{{ formatDateTime(currentMeeting.start_time) }}</div>
        </el-card>
        <el-card class="summary-card" shadow="hover">
          <div class="summary-label">活动投票</div>
          <div class="summary-value">{{ activeVoteLabel }}</div>
          <div class="summary-desc">{{ voteSummaryLabel }}</div>
        </el-card>
        <el-card class="summary-card" shadow="hover">
          <div class="summary-label">抽签会话</div>
          <div class="summary-value">{{ lotteryStatusLabel }}</div>
          <div class="summary-desc">参与池 {{ interactionOverview.lottery.participants_count || 0 }} 人</div>
        </el-card>
        <el-card class="summary-card" shadow="hover">
          <div class="summary-label">历史记录</div>
          <div class="summary-value">{{ historyCount }}</div>
          <div class="summary-desc">投票 {{ interactionOverview.vote.items.length }} 条，抽签 {{ interactionOverview.lottery.rounds.length }} 轮</div>
        </el-card>
      </div>

      <el-tabs v-model="activeTab" class="tabs-shell">
        <el-tab-pane label="投票" name="vote">
          <div class="content-grid">
            <el-card class="main-card" shadow="hover">
              <template #header>
                <div class="section-header">
                  <div>
                    <h3>{{ voteForm.id ? '编辑投票草稿' : '新建投票草稿' }}</h3>
                    <p>草稿仅后台可见，启动后才会开放到大屏与移动端。</p>
                  </div>
                  <div class="section-actions">
                    <el-button plain @click="resetVoteForm">清空</el-button>
                    <el-button type="primary" :loading="savingVote" @click="saveVoteDraft">保存草稿</el-button>
                  </div>
                </div>
              </template>

              <el-form label-position="top">
                <el-form-item label="投票标题" required>
                  <el-input v-model="voteForm.title" placeholder="请输入投票标题" />
                </el-form-item>
                <el-form-item label="补充说明">
                  <el-input v-model="voteForm.description" type="textarea" :rows="3" placeholder="说明投票背景和注意事项" />
                </el-form-item>
                <div class="inline-grid">
                  <el-form-item label="投票类型">
                    <el-switch v-model="voteForm.is_multiple" inline-prompt active-text="多选" inactive-text="单选" />
                  </el-form-item>
                  <el-form-item label="匿名设置">
                    <el-switch v-model="voteForm.is_anonymous" inline-prompt active-text="匿名" inactive-text="实名" />
                  </el-form-item>
                  <el-form-item label="投票时长（秒）">
                    <el-input-number v-model="voteForm.duration_seconds" :min="10" :max="1800" />
                  </el-form-item>
                  <el-form-item label="开始倒计时（秒）">
                    <el-input-number v-model="voteForm.countdown_seconds" :min="0" :max="60" />
                  </el-form-item>
                </div>
                <el-form-item v-if="voteForm.is_multiple" label="最多可选">
                  <el-input-number v-model="voteForm.max_selections" :min="2" :max="Math.max(voteForm.options.length, 2)" />
                </el-form-item>
                <el-form-item label="投票选项">
                  <div class="option-list">
                    <div v-for="(option, index) in voteForm.options" :key="index" class="option-row">
                      <span class="option-index">{{ String.fromCharCode(65 + index) }}</span>
                      <el-input v-model="voteForm.options[index]" placeholder="请输入选项内容" />
                      <el-button v-if="voteForm.options.length > 2" text type="danger" @click="removeVoteOption(index)">删除</el-button>
                    </div>
                    <el-button plain @click="addVoteOption">新增选项</el-button>
                  </div>
                </el-form-item>
              </el-form>
            </el-card>

            <el-card class="side-card" shadow="hover">
              <template #header>
                <div class="section-header">
                  <div>
                    <h3>投票列表</h3>
                    <p>在这里查看草稿、当前投票与结果入口。</p>
                  </div>
                </div>
              </template>

              <el-empty v-if="interactionOverview.vote.items.length === 0" description="当前会议还没有投票" :image-size="72" />
              <div v-else class="item-list">
                <div v-for="vote in interactionOverview.vote.items" :key="vote.id" class="item-card">
                  <div class="item-head">
                    <div class="item-main">
                      <div class="item-title">{{ vote.title }}</div>
                      <div class="item-meta">
                        <el-tag size="small" :type="getVoteTagType(vote.status)">{{ getVoteStatusLabel(vote.status) }}</el-tag>
                        <span>{{ vote.is_multiple ? '多选' : '单选' }}</span>
                        <span>{{ vote.duration_seconds }} 秒</span>
                      </div>
                      <div class="item-description">{{ vote.description || '暂无说明' }}</div>
                      </div>
                      <div class="item-actions">
                        <div class="item-actions-top">
                        <el-button v-if="vote.status === 'draft'" link class="action-link action-edit" @click="fillVoteForm(vote)">
                          <el-icon><Edit /></el-icon>
                          <span>编辑</span>
                        </el-button>
                        <el-button v-if="['countdown', 'active'].includes(vote.status)" link class="action-link action-close" @click="closeVote(vote)">
                          <el-icon><CircleClose /></el-icon>
                          <span>结束</span>
                        </el-button>
                        <el-button v-if="vote.status === 'draft'" link class="action-link action-delete" @click="deleteVote(vote)">
                          <el-icon><Delete /></el-icon>
                          <span>删除</span>
                        </el-button>
                      </div>
                      <div class="item-actions-bottom">
                        <el-button link class="action-link action-screen" @click="openVoteBigScreen(vote)">
                          <el-icon><Monitor /></el-icon>
                          <span>打开大屏</span>
                        </el-button>
                        <el-button link class="action-link action-result" @click="viewVoteResult(vote)">
                          <el-icon><DataAnalysis /></el-icon>
                          <span>查看结果</span>
                        </el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>

        <el-tab-pane label="抽签" name="lottery">
          <div class="content-grid">
            <el-card class="main-card" shadow="hover">
              <template #header>
                <div class="section-header">
                  <div>
                    <h3>{{ lotteryRoundForm.id ? '编辑抽签轮次' : '新建抽签轮次' }}</h3>
                    <p>移动端成员主动加入抽签池，后台负责轮次编排与现场节奏控制。</p>
                  </div>
                  <div class="section-actions">
                    <el-button plain @click="resetLotteryRoundForm">清空</el-button>
                    <el-button type="primary" :loading="savingLotteryRound" @click="saveLotteryRound">保存轮次</el-button>
                  </div>
                </div>
              </template>

              <el-form label-position="top">
                <el-form-item label="轮次名称">
                  <el-input v-model="lotteryRoundForm.title" placeholder="如：第一轮 / 第二轮 / 主席台抽签" />
                </el-form-item>
                <div class="inline-grid">
                  <el-form-item label="抽取人数">
                    <el-input-number v-model="lotteryRoundForm.count" :min="1" :max="100" />
                  </el-form-item>
                  <el-form-item label="允许重复中签">
                    <el-switch v-model="lotteryRoundForm.allow_repeat" inline-prompt active-text="允许" inactive-text="不允许" />
                  </el-form-item>
                </div>
              </el-form>
            </el-card>

            <el-card class="side-card" shadow="hover">
              <template #header>
                <div class="section-header">
                  <div>
                    <h3>轮次与参与池</h3>
                    <p>通过上移 / 下移调整抽签顺序，现场抽签控制统一在大屏进行。</p>
                  </div>
                  <div class="section-actions">
                    <el-button plain @click="openLotteryBigScreen">打开大屏</el-button>
                  </div>
                </div>
              </template>

              <div v-if="interactionOverview.lottery.rounds.length > 0" class="lottery-round-guide">
                <div class="guide-title">操作说明</div>
                <div class="guide-text">1. 轮次按“第一轮、第二轮、第三轮”顺序依次抽取，可用“上移 / 下移”调整未抽取轮次的位置。</div>
                <div class="guide-text">2. 后台仅负责编排轮次与查看参与池，开始抽签、停止抽签和重置会话统一在大屏操作。</div>
              </div>

              <el-empty v-if="interactionOverview.lottery.rounds.length === 0" description="当前会议还没有抽签轮次" :image-size="72" />
              <div v-else class="item-list">
                <div v-for="round in interactionOverview.lottery.rounds" :key="round.id" class="item-card lottery-round-card">
                  <div class="item-head">
                    <div class="item-main">
                      <div class="lottery-round-title-row">
                        <span class="round-order-badge">{{ formatLotteryRoundOrder(round.sort_order) }}</span>
                        <div class="item-title">{{ round.title }}</div>
                        <span class="lottery-round-state" :class="getLotteryRoundDisplay(round).className">{{ getLotteryRoundDisplay(round).label }}</span>
                      </div>
                      <div class="item-meta">
                        <span>抽取 {{ round.count }} 人</span>
                        <span v-if="round.allow_repeat">允许重复中签</span>
                        <span>{{ getLotteryRoundStatusLabel(round.status) }}</span>
                      </div>
                      <div class="lottery-round-hint">
                        {{ getLotteryRoundHint(round) }}
                      </div>
                    </div>
                    <div class="lottery-round-actions">
                      <el-button text :disabled="!canEditLotteryRound(round)" @click="fillLotteryRoundForm(round)">编辑</el-button>
                      <el-button text :disabled="!canMoveLotteryRound(round, 'up')" @click="moveLotteryRound(round, 'up')">上移</el-button>
                      <el-button text :disabled="!canMoveLotteryRound(round, 'down')" @click="moveLotteryRound(round, 'down')">下移</el-button>
                      <el-button text type="danger" :disabled="!canDeleteLotteryRound(round)" @click="deleteLotteryRound(round)">删除</el-button>
                    </div>
                  </div>
                  <div v-if="round.winners?.length" class="chip-list">
                    <span v-for="winner in round.winners" :key="winner.id" class="winner-chip">{{ winner.user_name }}</span>
                  </div>
                </div>
              </div>

              <div class="participant-block">
                <div class="item-title">当前参与池</div>
                <el-empty v-if="interactionOverview.lottery.participants_count === 0" description="当前还没有成员加入" :image-size="60" />
                <div v-else class="chip-list">
                  <span v-for="participant in interactionOverview.lottery.participants" :key="participant.user_id" class="participant-chip">
                    {{ participant.name }}
                  </span>
                </div>
              </div>
            </el-card>
          </div>
        </el-tab-pane>
      </el-tabs>
    </template>

    <el-dialog v-model="voteResultVisible" title="投票结果" width="560px" align-center>
      <div v-if="voteResult">
        <div class="result-head">
          <h3>{{ voteResult.title }}</h3>
          <span>共 {{ voteResult.total_voters }} 人参与</span>
        </div>
        <div v-for="result in voteResult.results" :key="result.option_id" class="result-row">
          <div class="result-row-top">
            <span>{{ result.content }}</span>
            <span>{{ result.count }} 票 / {{ result.percent }}%</span>
          </div>
          <el-progress :percentage="result.percent" :stroke-width="10" />
          <div v-if="result.voters?.length" class="voter-list">{{ result.voters.join('、') }}</div>
        </div>
      </div>
    </el-dialog>
  </div>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { io } from 'socket.io-client'
import { ElMessage, ElMessageBox } from 'element-plus'
import { CircleClose, DataAnalysis, Delete, Edit, Monitor } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useSidebar } from '@/composables/useSidebar'

const router = useRouter()
const { isCollapse, toggleSidebar } = useSidebar()

const meetings = ref([])
const selectedMeetingId = ref(null)
const loadingMeetings = ref(false)
const loadingOverview = ref(false)
const savingVote = ref(false)
const savingLotteryRound = ref(false)
const activeTab = ref('vote')
const voteResultVisible = ref(false)
const voteResult = ref(null)

const interactionOverview = reactive({
  vote: { active: null, items: [], draft_count: 0, closed_count: 0 },
  lottery: { session_status: 'idle', current_round: null, next_round: null, participants: [], participants_count: 0, winners: [], rounds: [] }
})

const voteForm = reactive({
  id: null,
  title: '',
  description: '',
  is_multiple: false,
  is_anonymous: false,
  max_selections: 1,
  duration_seconds: 60,
  countdown_seconds: 10,
  options: ['', '']
})

const lotteryRoundForm = reactive({
  id: null,
  title: '',
  count: 1,
  allow_repeat: false
})

let socket = null

const currentMeeting = computed(() => meetings.value.find(item => item.id === selectedMeetingId.value) || null)
const historyCount = computed(() => interactionOverview.vote.items.length + interactionOverview.lottery.rounds.length)
const activeVoteLabel = computed(() => interactionOverview.vote.active ? getVoteStatusLabel(interactionOverview.vote.active.status) : '无')
const voteSummaryLabel = computed(() => interactionOverview.vote.active ? `${interactionOverview.vote.active.title} · ${interactionOverview.vote.active.total_voters || 0} 人已参与` : '当前没有活动投票')
const lotteryStatusLabel = computed(() => getLotterySessionStatusLabel(interactionOverview.lottery.session_status))
const isLotteryRolling = computed(() => interactionOverview.lottery.session_status === 'rolling')
const lotteryRoundNumberMap = ['零', '一', '二', '三', '四', '五', '六', '七', '八', '九']
const formatLotteryRoundOrder = (sortOrder) => {
  const value = Number(sortOrder) || 0
  if (value <= 0) return '未排轮次'
  if (value < 10) return `第${lotteryRoundNumberMap[value]}轮`
  if (value === 10) return '第十轮'
  if (value < 20) return `第十${lotteryRoundNumberMap[value - 10]}轮`
  const tens = Math.floor(value / 10)
  const units = value % 10
  return `第${lotteryRoundNumberMap[tens]}十${units ? lotteryRoundNumberMap[units] : ''}轮`
}

const fetchMeetings = async () => {
  loadingMeetings.value = true
  try {
    const data = await request.get('/meetings/', { params: { limit: 200, force_show_all: true } })
    meetings.value = Array.isArray(data) ? data : []
    if (!selectedMeetingId.value && meetings.value.length > 0) {
      selectedMeetingId.value = meetings.value[0].id
    }
  } catch (error) {
    ElMessage.error('加载会议列表失败')
  } finally {
    loadingMeetings.value = false
  }
}

const fetchOverview = async () => {
  if (!selectedMeetingId.value) return
  loadingOverview.value = true
  try {
    const overview = await request.get(`/interactions/meeting/${selectedMeetingId.value}/overview`)
    interactionOverview.vote = overview.vote || interactionOverview.vote
    interactionOverview.lottery = overview.lottery || interactionOverview.lottery
  } catch (error) {
    ElMessage.error('加载互动中心失败')
  } finally {
    loadingOverview.value = false
  }
}

const connectSocket = () => {
  if (!selectedMeetingId.value || socket) return
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, { path: '/socket.io', transports: ['websocket', 'polling'], reconnection: true })
  socket.on('connect', () => {
    socket.emit('join_meeting', { meeting_id: selectedMeetingId.value })
  })
  const refresh = () => fetchOverview()
  socket.on('vote_state_change', refresh)
  socket.on('vote_results_change', refresh)
  socket.on('lottery_session_change', refresh)
}

const disconnectSocket = () => {
  if (!socket) return
  socket.disconnect()
  socket = null
}

const resetVoteForm = () => {
  voteForm.id = null
  voteForm.title = ''
  voteForm.description = ''
  voteForm.is_multiple = false
  voteForm.is_anonymous = false
  voteForm.max_selections = 1
  voteForm.duration_seconds = 60
  voteForm.countdown_seconds = 10
  voteForm.options = ['', '']
}

const addVoteOption = () => voteForm.options.push('')
const removeVoteOption = (index) => {
  if (voteForm.options.length > 2) {
    voteForm.options.splice(index, 1)
  }
}

const fillVoteForm = (vote) => {
  voteForm.id = vote.id
  voteForm.title = vote.title || ''
  voteForm.description = vote.description || ''
  voteForm.is_multiple = !!vote.is_multiple
  voteForm.is_anonymous = !!vote.is_anonymous
  voteForm.max_selections = vote.max_selections || 1
  voteForm.duration_seconds = vote.duration_seconds || 60
  voteForm.countdown_seconds = vote.countdown_seconds || 10
  voteForm.options = vote.options?.length ? vote.options.map(item => item.content) : ['', '']
}

const saveVoteDraft = async () => {
  if (!selectedMeetingId.value) return
  if (!voteForm.title.trim()) return ElMessage.warning('请输入投票标题')
  const options = voteForm.options.map(item => item.trim()).filter(Boolean)
  if (options.length < 2) return ElMessage.warning('至少需要两个有效选项')
  savingVote.value = true
  try {
    const payload = {
      meeting_id: selectedMeetingId.value,
      title: voteForm.title,
      description: voteForm.description,
      is_multiple: voteForm.is_multiple,
      is_anonymous: voteForm.is_anonymous,
      max_selections: voteForm.is_multiple ? Math.min(voteForm.max_selections, options.length) : 1,
      duration_seconds: voteForm.duration_seconds,
      countdown_seconds: voteForm.countdown_seconds,
      options
    }
    if (voteForm.id) {
      await request.put(`/vote/${voteForm.id}`, payload)
      ElMessage.success('投票草稿已更新')
    } else {
      await request.post('/vote/', payload)
      ElMessage.success('投票草稿已创建')
    }
    resetVoteForm()
    await fetchOverview()
  } catch (error) {
  } finally {
    savingVote.value = false
  }
}

const closeVote = async (vote) => {
  try {
    await request.post(`/vote/${vote.id}/close`)
    ElMessage.success('投票已结束')
    await fetchOverview()
  } catch (error) {}
}

const deleteVote = async (vote) => {
  try {
    await ElMessageBox.confirm(`确定删除投票「${vote.title}」吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await request.delete(`/vote/${vote.id}`)
    if (voteForm.id === vote.id) resetVoteForm()
    ElMessage.success('投票已删除')
    await fetchOverview()
  } catch (error) {}
}

const viewVoteResult = async (vote) => {
  try {
    voteResult.value = await request.get(`/vote/${vote.id}/result`)
    voteResultVisible.value = true
  } catch (error) {}
}

const openVoteBigScreen = (vote) => {
  const href = router.resolve({ name: 'vote-bigscreen', params: { id: vote.id } }).href
  window.open(href, '_blank')
}

const resetLotteryRoundForm = () => {
  lotteryRoundForm.id = null
  lotteryRoundForm.title = ''
  lotteryRoundForm.count = 1
  lotteryRoundForm.allow_repeat = false
}

const fillLotteryRoundForm = (round) => {
  lotteryRoundForm.id = round.id
  lotteryRoundForm.title = round.title || ''
  lotteryRoundForm.count = round.count || 1
  lotteryRoundForm.allow_repeat = !!round.allow_repeat
}

const saveLotteryRound = async () => {
  if (!selectedMeetingId.value) return
  if (!lotteryRoundForm.title.trim()) return ElMessage.warning('请输入轮次名称')
  savingLotteryRound.value = true
  try {
    const payload = { title: lotteryRoundForm.title, count: lotteryRoundForm.count, allow_repeat: lotteryRoundForm.allow_repeat }
    if (lotteryRoundForm.id) {
      await request.put(`/lottery/round/${lotteryRoundForm.id}`, payload)
      ElMessage.success('抽签轮次已更新')
    } else {
      await request.post(`/lottery/${selectedMeetingId.value}/round`, payload)
      ElMessage.success('抽签轮次已创建')
    }
    resetLotteryRoundForm()
    await fetchOverview()
  } catch (error) {
  } finally {
    savingLotteryRound.value = false
  }
}

const moveLotteryRound = async (round, direction) => {
  try {
    await request.post(`/lottery/round/${round.id}/move`, { direction })
    ElMessage.success(direction === 'up' ? '轮次已上移' : '轮次已下移')
    await fetchOverview()
  } catch (error) {}
}

const deleteLotteryRound = async (round) => {
  try {
    await ElMessageBox.confirm(`确定删除轮次「${round.title}」吗？`, '删除确认', { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' })
    await request.delete(`/lottery/round/${round.id}`)
    if (lotteryRoundForm.id === round.id) resetLotteryRoundForm()
    ElMessage.success('轮次已删除')
    await fetchOverview()
  } catch (error) {}
}

const openLotteryBigScreen = () => {
  const href = router.resolve({ name: 'LotteryBigScreen', params: { meetingId: selectedMeetingId.value } }).href
  window.open(href, '_blank')
}

const getVoteStatusLabel = (status) => ({ draft: '草稿', countdown: '倒计时', active: '进行中', closed: '已结束' }[status] || status)
const getVoteTagType = (status) => ({ draft: 'info', countdown: 'warning', active: 'success', closed: '' }[status] || 'info')
const getLotteryRoundIndex = (roundId) => interactionOverview.lottery.rounds.findIndex(item => item.id === roundId)
const canEditLotteryRound = (round) => !isLotteryRolling.value && round.status !== 'finished' && interactionOverview.lottery.current_round?.id !== round.id
const canDeleteLotteryRound = (round) => !isLotteryRolling.value && round.status !== 'finished' && interactionOverview.lottery.current_round?.id !== round.id
const canMoveLotteryRound = (round, direction) => {
  if (isLotteryRolling.value || round.status === 'finished' || interactionOverview.lottery.current_round?.id === round.id) {
    return false
  }
  const index = getLotteryRoundIndex(round.id)
  if (index < 0) return false
  const targetIndex = direction === 'up' ? index - 1 : index + 1
  if (targetIndex < 0 || targetIndex >= interactionOverview.lottery.rounds.length) {
    return false
  }
  const targetRound = interactionOverview.lottery.rounds[targetIndex]
  if (!targetRound || targetRound.status === 'finished' || interactionOverview.lottery.current_round?.id === targetRound.id) {
    return false
  }
  return true
}
const getLotteryRoundDisplay = (round) => {
  if (interactionOverview.lottery.current_round?.id === round.id && ['result', 'completed'].includes(interactionOverview.lottery.session_status)) {
    return { label: '已抽取', className: 'state-finished' }
  }
  if (interactionOverview.lottery.next_round?.id === round.id) {
    return { label: '下一轮', className: 'state-next' }
  }
  if (round.status === 'finished') {
    return { label: '已抽取', className: 'state-finished' }
  }
  return { label: '待抽取', className: 'state-pending' }
}
const getLotteryRoundHint = (round) => {
  if (interactionOverview.lottery.current_round?.id === round.id && interactionOverview.lottery.session_status === 'rolling') return '当前正在抽取这一轮，主持人停止后会生成本轮结果。'
  if (interactionOverview.lottery.current_round?.id === round.id && ['result', 'completed'].includes(interactionOverview.lottery.session_status)) return '这一轮已经抽取完成，结果会暂时保留在左侧展示。'
  if (interactionOverview.lottery.next_round?.id === round.id) return '这一轮就是下一轮，左侧点击“开始下一轮”后会自动进入。'
  if (round.status === 'finished') return '这一轮已经抽取完成，顺序固定，不再参与后续调整。'
  return '这一轮尚未抽取，可通过上移 / 下移调整它在顺序中的位置。'
}
const getLotteryRoundStatusLabel = (status) => ({ draft: '已创建', ready: '待开始', finished: '已完成' }[status] || status)
const getLotterySessionStatusLabel = (status) => ({ idle: '空闲', collecting: '收集中', ready: '准备就绪', rolling: '滚动中', result: '结果展示中', completed: '全部完成' }[status] || status)
const formatDateTime = (value) => {
  if (!value) return '未设置时间'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未设置时间'
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
}

watch(selectedMeetingId, async (value, oldValue) => {
  if (value && value !== oldValue) {
    disconnectSocket()
    resetVoteForm()
    resetLotteryRoundForm()
    await fetchOverview()
    connectSocket()
  }
})

onMounted(async () => {
  await fetchMeetings()
  if (selectedMeetingId.value) {
    await fetchOverview()
    connectSocket()
  }
})

onBeforeUnmount(() => {
  disconnectSocket()
})
</script>

<style scoped>
.interaction-page { display: flex; flex-direction: column; gap: 20px; }
.page-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 16px; padding: 0 4px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main, #f8fafc); }
.header-divider { height: 24px; border-color: var(--border-color, #e2e8f0); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 700; color: var(--text-main); line-height: 1.2; }
.page-subtitle { margin: 6px 0 0; color: var(--text-secondary); font-size: 14px; line-height: 1.5; }
.header-actions { display: flex; gap: 12px; align-items: center; }
.meeting-select { width: 320px; }
.summary-grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 16px; }
.summary-card { border-radius: 16px; }
.summary-label { color: var(--text-secondary); font-size: 13px; }
.summary-value { margin-top: 10px; font-size: 28px; font-weight: 700; color: var(--text-main); }
.summary-text { margin-top: 10px; font-size: 18px; font-weight: 700; color: var(--text-main); line-height: 1.6; }
.summary-desc { margin-top: 8px; font-size: 12px; line-height: 1.6; color: #94a3b8; }
.tabs-shell :deep(.el-tabs__header) { margin-bottom: 18px; }
.content-grid { display: grid; grid-template-columns: minmax(0, 1.08fr) minmax(0, 0.92fr); gap: 20px; }
.main-card, .side-card { border-radius: 18px; }
.section-header { display: flex; justify-content: space-between; align-items: flex-start; gap: 12px; }
.section-header h3 { margin: 0; font-size: 18px; color: var(--text-main); }
.section-header p { margin: 4px 0 0; font-size: 13px; line-height: 1.6; color: var(--text-secondary); }
.section-actions { display: flex; flex-wrap: wrap; gap: 8px; }
.inline-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 14px; }
.option-list, .item-list { display: flex; flex-direction: column; gap: 12px; width: 100%; }
.option-row { display: flex; align-items: center; gap: 10px; }
.option-index { width: 28px; height: 28px; border-radius: 8px; background: #e2e8f0; color: #475569; display: inline-flex; align-items: center; justify-content: center; font-size: 12px; font-weight: 700; flex-shrink: 0; }
.item-card { padding: 16px; border-radius: 16px; border: 1px solid var(--border-color); background: var(--card-bg); }
.item-head { display: flex; justify-content: space-between; align-items: flex-start; gap: 14px; }
.item-main { flex: 1; min-width: 0; }
.item-title { font-size: 16px; font-weight: 700; color: var(--text-main); }
.item-meta { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 8px; font-size: 12px; color: var(--text-secondary); }
.item-meta.block { display: grid; gap: 6px; }
.item-description { margin-top: 12px; font-size: 13px; line-height: 1.7; color: var(--text-secondary); }
.item-actions { display: flex; flex-direction: column; align-items: flex-end; gap: 10px; flex-shrink: 0; min-width: 208px; }
.item-actions-top, .item-actions-bottom { display: flex; flex-wrap: wrap; justify-content: flex-end; gap: 8px; }
.lottery-round-guide {
  margin-bottom: 12px;
  padding: 14px 16px;
  border-radius: 14px;
  background: rgba(148, 163, 184, 0.08);
  border: 1px dashed rgba(148, 163, 184, 0.22);
}
.guide-title {
  font-size: 13px;
  font-weight: 700;
  color: var(--text-main);
}
.guide-text {
  margin-top: 6px;
  font-size: 12px;
  line-height: 1.7;
  color: var(--text-secondary);
}
.lottery-round-card .item-head {
  align-items: flex-start;
}
.lottery-round-title-row {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}
.round-order-badge {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  min-width: 76px;
  height: 32px;
  padding: 0 12px;
  border-radius: 999px;
  background: rgba(59, 130, 246, 0.16);
  color: #93c5fd;
  font-size: 12px;
  font-weight: 700;
  white-space: nowrap;
}
.lottery-round-state {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border-radius: 999px;
  font-size: 12px;
  font-weight: 700;
}
.lottery-round-state.state-next {
  background: rgba(245, 158, 11, 0.14);
  color: #fcd34d;
}
.lottery-round-state.state-finished {
  background: rgba(148, 163, 184, 0.14);
  color: #cbd5e1;
}
.lottery-round-state.state-pending {
  background: rgba(148, 163, 184, 0.1);
  color: #94a3b8;
}
.lottery-round-hint {
  margin-top: 10px;
  font-size: 12px;
  line-height: 1.7;
  color: #94a3b8;
}
.lottery-round-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 4px;
  flex-shrink: 0;
  margin-left: 12px;
}
.lottery-round-actions :deep(.el-button) {
  margin-left: 0;
  padding: 4px 8px;
}
.lottery-round-actions :deep(.el-button.is-disabled) {
  opacity: 0.45;
}
.action-link {
  height: 34px;
  margin: 0;
  padding: 0 10px;
  border-radius: 10px;
  color: var(--text-secondary);
  font-weight: 600;
  text-decoration: none;
  transition: background-color 0.2s ease, color 0.2s ease, transform 0.2s ease;
}
.action-link :deep(.el-icon) {
  margin-right: 6px;
  font-size: 14px;
}
.action-link:hover {
  transform: translateY(-1px);
}
.action-edit,
.action-result {
  color: #cbd5e1;
}
.action-edit:hover,
.action-result:hover {
  background: rgba(148, 163, 184, 0.1);
  color: #f8fafc;
}
.action-screen {
  color: #60a5fa;
}
.action-screen:hover {
  background: rgba(59, 130, 246, 0.12);
  color: #bfdbfe;
}
.action-delete,
.action-close {
  color: #f87171;
}
.action-delete:hover,
.action-close:hover {
  background: rgba(239, 68, 68, 0.1);
  color: #fca5a5;
}
.participant-block { margin-top: 20px; padding-top: 20px; border-top: 1px dashed var(--border-color); }
.chip-list { display: flex; flex-wrap: wrap; gap: 8px; margin-top: 12px; }
.winner-chip, .participant-chip { padding: 6px 12px; border-radius: 999px; font-size: 12px; font-weight: 600; }
.winner-chip { background: #fef3c7; color: #92400e; }
.participant-chip { background: #eff6ff; color: #1d4ed8; }
.result-head { display: flex; justify-content: space-between; align-items: center; gap: 12px; margin-bottom: 18px; }
.result-head h3 { margin: 0; color: var(--text-main); }
.result-row + .result-row { margin-top: 16px; }
.result-row-top { display: flex; justify-content: space-between; gap: 10px; font-size: 13px; color: var(--text-main); margin-bottom: 8px; }
.voter-list { margin-top: 6px; font-size: 12px; line-height: 1.6; color: var(--text-secondary); }
@media (max-width: 1200px) { .summary-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } .content-grid { grid-template-columns: 1fr; } }
@media (max-width: 820px) { .page-header, .section-header, .item-head { flex-direction: column; align-items: stretch; } .header-actions { width: 100%; flex-direction: column; align-items: stretch; } .meeting-select { width: 100%; } .inline-grid { grid-template-columns: 1fr; } .item-actions { min-width: 0; align-items: stretch; } .item-actions-top, .item-actions-bottom, .lottery-round-actions { justify-content: flex-start; } .lottery-round-actions { margin-left: 0; } }
@media (max-width: 640px) { .summary-grid { grid-template-columns: 1fr; } .option-row { flex-wrap: wrap; } }
</style>
