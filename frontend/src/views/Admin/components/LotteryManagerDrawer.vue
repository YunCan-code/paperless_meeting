<template>
  <el-drawer
    :model-value="modelValue"
    @update:model-value="$emit('update:modelValue', $event)"
    title="抽签管理"
    direction="rtl"
    size="480px"
    :before-close="handleClose"
  >
    <div class="lottery-drawer">
      <!-- 会议信息 -->
      <div class="meeting-info" v-if="meetingTitle">
        <el-icon><Calendar /></el-icon>
        <span>{{ meetingTitle }}</span>
      </div>

      <!-- 轮次配置 -->
      <div class="section">
        <h4>新建抽签轮次</h4>
        <el-form :model="roundForm" label-width="80px" size="large">
          <el-form-item label="轮次名称">
            <el-input v-model="roundForm.title" placeholder="如：一等奖" />
          </el-form-item>
          <el-form-item label="中奖人数">
            <el-input-number v-model="roundForm.count" :min="1" :max="100" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="createRound" :loading="creating">
              创建轮次
            </el-button>
            <el-button @click="openBigScreen" :disabled="!meetingId">
              <el-icon><Monitor /></el-icon>
              打开大屏
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <!-- 历史轮次 -->
      <div class="section">
        <h4>历史轮次</h4>
        <div v-if="loading" class="loading-state">
          <el-icon class="is-loading"><Loading /></el-icon>
          加载中...
        </div>
        <div v-else-if="history.rounds.length === 0" class="empty-state">
          暂无抽签记录
        </div>
        <div v-else class="history-list">
          <div 
            v-for="round in history.rounds" 
            :key="round.id" 
            class="history-item"
            :class="{ finished: round.status === 'finished' }"
          >
            <div class="round-header">
              <span class="round-title">{{ round.title }}</span>
              <el-tag :type="round.status === 'finished' ? 'success' : 'warning'" size="small">
                {{ round.status === 'finished' ? '已完成' : '待抽取' }}
              </el-tag>
            </div>
            <div class="winners" v-if="round.winners.length > 0">
              <span v-for="w in round.winners" :key="w.id" class="winner-tag">
                🎉 {{ w.user_name }}
              </span>
            </div>
            <div class="round-actions">
              <el-button 
                v-if="round.status !== 'finished'"
                size="small" 
                type="primary" 
                @click="prepareRound(round)"
              >
                开始抽签
              </el-button>
              <el-button 
                size="small" 
                type="danger" 
                plain
                @click="deleteRound(round.id)"
              >
                删除
              </el-button>
            </div>
          </div>
        </div>
      </div>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, watch, computed } from 'vue'
import { useRouter } from 'vue-router'
import { Calendar, Monitor, Loading } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { io } from 'socket.io-client'

const props = defineProps({
  modelValue: Boolean,
  meetingId: Number,
  meetingTitle: String
})

const emit = defineEmits(['update:modelValue'])
const router = useRouter()

// 表单
const roundForm = ref({
  title: '',
  count: 1
})

// 状态
const creating = ref(false)
const loading = ref(false)
const history = ref({ rounds: [] })

// Socket.IO
let socket = null

// 获取历史
const fetchHistory = async () => {
  if (!props.meetingId) return
  loading.value = true
  try {
    const res = await request.get(`/lottery/${props.meetingId}/history`)
    history.value = res
  } catch (e) {
    console.error('Fetch history error:', e)
  } finally {
    loading.value = false
  }
}

// 创建轮次
const createRound = async () => {
  if (!roundForm.value.title) {
    ElMessage.warning('请输入轮次名称')
    return
  }
  creating.value = true
  try {
    await request.post(`/lottery/${props.meetingId}/round`, {
      title: roundForm.value.title,
      count: roundForm.value.count
    })
    ElMessage.success('轮次创建成功')
    roundForm.value.title = ''
    roundForm.value.count = 1
    await fetchHistory()
  } catch (e) {
    ElMessage.error('创建失败: ' + (e.message || '未知错误'))
  } finally {
    creating.value = false
  }
}

// 准备抽签 (发送 prepare 事件)
const prepareRound = (round) => {
  if (!socket || !socket.connected) {
    initSocket()
  }
  socket.emit('lottery_action', {
    action: 'prepare',
    meeting_id: props.meetingId,
    lottery_id: round.id,
    title: round.title,
    count: round.count
  })
  ElMessage.success('已准备抽签，请在大屏操作')
  openBigScreen()
}

// 删除轮次
const deleteRound = async (id) => {
  try {
    await ElMessageBox.confirm('确定删除此轮次？', '确认删除')
    await request.delete(`/lottery/round/${id}`)
    ElMessage.success('删除成功')
    await fetchHistory()
  } catch (e) {
    if (e !== 'cancel') {
      ElMessage.error('删除失败')
    }
  }
}

// 打开大屏
const openBigScreen = () => {
  const url = router.resolve({ name: 'LotteryBigScreen', params: { meetingId: props.meetingId } })
  window.open(url.href, '_blank')
}

// 初始化 Socket
const initSocket = () => {
  if (socket) return
  const url = import.meta.env.VITE_API_URL || window.location.origin
  socket = io(url, {
    path: '/socket.io',
    transports: ['websocket']
  })
  socket.on('connect', () => {
    socket.emit('join_meeting', { meeting_id: props.meetingId })
  })
}

// 关闭处理
const handleClose = (done) => {
  if (socket) {
    socket.disconnect()
    socket = null
  }
  done()
}

// 监听 meetingId 变化
watch(() => props.meetingId, (newId) => {
  if (newId) {
    fetchHistory()
    initSocket()
  }
}, { immediate: true })

watch(() => props.modelValue, (visible) => {
  if (visible && props.meetingId) {
    fetchHistory()
    initSocket()
  }
})
</script>

<style scoped>
.lottery-drawer {
  padding: 0 8px;
}

.meeting-info {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 12px 16px;
  background: #f0f9ff;
  border-radius: 8px;
  color: #0369a1;
  font-weight: 500;
  margin-bottom: 20px;
}

.section {
  margin-bottom: 24px;
}

.section h4 {
  margin: 0 0 12px 0;
  color: #374151;
  font-size: 15px;
}

.loading-state,
.empty-state {
  text-align: center;
  color: #9ca3af;
  padding: 32px 0;
}

.loading-state .el-icon {
  margin-right: 8px;
}

.history-list {
  display: flex;
  flex-direction: column;
  gap: 12px;
}

.history-item {
  padding: 16px;
  background: #f9fafb;
  border-radius: 12px;
  border: 1px solid #e5e7eb;
}

.history-item.finished {
  background: #f0fdf4;
  border-color: #86efac;
}

.round-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 8px;
}

.round-title {
  font-weight: 600;
  color: #1f2937;
}

.winners {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  margin-bottom: 12px;
}

.winner-tag {
  padding: 4px 12px;
  background: linear-gradient(135deg, #fef3c7, #fde68a);
  border-radius: 16px;
  font-size: 13px;
  color: #92400e;
}

.round-actions {
  display: flex;
  gap: 8px;
}
</style>
