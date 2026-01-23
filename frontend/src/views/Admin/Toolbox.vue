<template>
  <div class="toolbox-page">
    <div class="page-header">
      <div class="title-group">
        <h1 class="page-title">快捷功能</h1>
        <p class="page-subtitle">常用会议互动工具集合</p>
      </div>
    </div>

    <el-row :gutter="20">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="tool in tools" :key="tool.id">
        <el-card 
          class="tool-card" 
          shadow="hover" 
          @click="handleToolClick(tool)"
        >
          <div class="tool-icon" :style="{ background: tool.bgColor, color: tool.color }">
            <el-icon :size="32">
              <component :is="tool.icon" />
            </el-icon>
          </div>
          <div class="tool-info">
            <h3 class="tool-title">{{ tool.title }}</h3>
            <p class="tool-desc">{{ tool.desc }}</p>
          </div>
          <div class="tool-action">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- Meeting Selector Dialog -->
    <el-dialog
      v-model="meetingSelectVisible"
      title="选择会议"
      width="500px"
      append-to-body
      align-center
    >
      <div class="meeting-select-content">
        <p class="select-tip">{{ selectTip }}</p>
        <el-select 
          v-model="selectedMeetingId" 
          placeholder="搜索或选择会议" 
          filterable 
          style="width: 100%"
          size="large"
          :loading="loadingMeetings"
        >
          <el-option
            v-for="item in meetings"
            :key="item.id"
            :label="item.title"
            :value="item.id"
          >
            <span style="float: left">{{ item.title }}</span>
            <span style="float: right; color: #8492a6; font-size: 13px">{{ formatDate(item.start_time) }}</span>
          </el-option>
        </el-select>
      </div>
      <template #footer>
        <div class="dialog-footer">
          <el-button @click="meetingSelectVisible = false">取消</el-button>
          <el-button type="primary" @click="confirmMeetingSelect" :disabled="!selectedMeetingId">
            下一步
          </el-button>
        </div>
      </template>
    </el-dialog>

    <!-- Vote Configuration Component -->
    <VoteConfigDialog
      v-model="voteDialogVisible"
      :meeting-id="currentMeeting?.id"
      :meeting-title="currentMeeting?.title"
    />

    <!-- Lottery Manager Drawer -->
    <LotteryManagerDrawer
      v-model="lotteryDialogVisible"
      :meeting-id="currentMeeting?.id"
      :meeting-title="currentMeeting?.title"
    />

  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import { 
  DataAnalysis, Trophy, CircleCheck, 
  ArrowRight, MagicStick, Timer 
} from '@element-plus/icons-vue'
import request from '@/utils/request'
import VoteConfigDialog from '@/views/Admin/components/VoteConfigDialog.vue'
import LotteryManagerDrawer from '@/views/Admin/components/LotteryManagerDrawer.vue'
import { ElMessage } from 'element-plus'

// Tools Configuration
const tools = [
  {
    id: 'vote',
    title: '现场投票',
    desc: '发起实时投票，支持大屏展示结果',
    icon: 'DataAnalysis',
    bgColor: '#eff6ff', // blue-50
    color: '#3b82f6'    // blue-500
  },
  {
    id: 'lottery',
    title: '抽签',
    desc: '随机抽取参会人员',
    icon: 'Trophy',
    bgColor: '#fff7ed', // orange-50
    color: '#f97316'    // orange-500
  },
  {
    id: 'signin',
    title: '扫码签到',
    desc: '生成签到二维码 (开发中)',
    icon: 'CircleCheck',
    bgColor: '#f0fdf4', // green-50
    color: '#22c55e'    // green-500
  },
  {
    id: 'timer',
    title: '会议计时',
    desc: '全屏倒计时提醒 (开发中)',
    icon: 'Timer',
    bgColor: '#faf5ff', // purple-50
    color: '#a855f7'    // purple-500
  }
]

// Logic
const meetingSelectVisible = ref(false)
const voteDialogVisible = ref(false)
const lotteryDialogVisible = ref(false)
const meetings = ref([])
const loadingMeetings = ref(false)
const selectedMeetingId = ref(null)
const activeToolId = ref(null)

const selectTip = computed(() => {
  if (activeToolId.value === 'lottery') {
    return '请选择通过哪个会议发起抽签：'
  }
  return '请选择通过哪个会议发起投票：'
})

const currentMeeting = computed(() => {
  return meetings.value.find(m => m.id === selectedMeetingId.value)
})

const handleToolClick = (tool) => {
  if (tool.id === 'vote' || tool.id === 'lottery') {
    activeToolId.value = tool.id
    openMeetingSelect()
  } else {
    ElMessage.info('该功能正在开发中，敬请期待')
  }
}

const openMeetingSelect = async () => {
  selectedMeetingId.value = null
  meetingSelectVisible.value = true
  if (meetings.value.length === 0) {
    await fetchMeetings()
  }
}

const fetchMeetings = async () => {
  loadingMeetings.value = true
  try {
    // Standard params, maybe sort by start_time desc
    const res = await request.get('/meetings/', { params: { limit: 100 } }) 
    // Usually res is the list or res.items. Assuming wrapper handles it or it returns list.
    // Based on MeetingManage.vue: meetings.value = await request.get('/meetings/', ...)
    // which seemed to imply direct array or handled by interceptor.
    // MeetingManage uses params: { force_show_all: true }
    let list = Array.isArray(res) ? res : (res.items || [])
    // Sort by start_time desc
    list.sort((a, b) => new Date(b.start_time) - new Date(a.start_time))
    meetings.value = list
  } catch (e) {
    ElMessage.error('获取会议列表失败')
  } finally {
    loadingMeetings.value = false
  }
}

const confirmMeetingSelect = () => {
  if (!selectedMeetingId.value) return
  meetingSelectVisible.value = false
  
  if (activeToolId.value === 'vote') {
    voteDialogVisible.value = true
  } else if (activeToolId.value === 'lottery') {
    lotteryDialogVisible.value = true
  }
}

const formatDate = (str) => {
  if (!str) return ''
  const d = new Date(str)
  const year = d.getFullYear()
  const month = String(d.getMonth() + 1).padStart(2, '0')
  const day = String(d.getDate()).padStart(2, '0')
  return `${year}-${month}-${day}`
}

</script>

<style scoped>
.toolbox-page {
  padding: 8px; 
}

.page-header {
  margin-bottom: 32px;
}
.page-title {
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main);
  margin: 0 0 8px 0;
}
.page-subtitle {
  color: var(--text-secondary);
  font-size: 14px;
  margin: 0;
}

.tool-card {
  border: none;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  margin-bottom: 20px;
  height: 100%;
  position: relative;
  overflow: hidden;
  background-color: var(--card-bg);
}

.tool-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.tool-card :deep(.el-card__body) {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.tool-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-info {
  flex: 1;
}

.tool-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}

.tool-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.tool-action {
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s ease;
  color: var(--text-secondary);
}

.tool-card:hover .tool-action {
  opacity: 1;
  transform: translateX(0);
}

.select-tip {
  margin-bottom: 12px;
  color: var(--text-secondary);
}
</style>
