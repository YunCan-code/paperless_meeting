<template>
  <el-drawer
    v-model="visible"
    :title="`抽签管理 - ${meetingTitle}`"
    size="700px"
    destroy-on-close
    @open="handleOpen"
    @close="handleClose"
  >
    <div class="lottery-drawer-content">
      <div class="actions-bar">
        <el-button type="primary" :icon="Setting" @click="openManageDialog">
          管理抽签轮次
        </el-button>
        <div class="actions-right">
          <el-button color="#626aef" :icon="Monitor" @click="openBigScreen">
            进入抽签大屏
          </el-button>
          <el-button :icon="Refresh" circle @click="fetchLotteries" :loading="loading" />
        </div>
      </div>

       <div class="lottery-list" v-loading="loading">
         <el-alert v-if="totalParticipants > 0" :title="`当前会议共 ${totalParticipants} 人参与抽签池`" type="info" :closable="false" style="margin-bottom: 12px" />
         <el-empty v-if="lotteryList.length === 0" description="暂无抽签记录" />
         
         <el-card v-for="(item, index) in lotteryList" :key="index" class="lottery-card" shadow="hover">
            <div class="lottery-header">
              <div class="lottery-title-row">
                 <span class="lottery-title">{{ item.title }}</span>
                 <!-- Status Tags -->
                 <el-tag v-if="item.status === 'pending'" type="warning">待开始</el-tag>
                 <el-tag v-else-if="item.status === 'active'" type="primary">进行中</el-tag>
                 <el-tag v-else type="success">已完成</el-tag>
              </div>
              <div class="lottery-meta">
                 <span v-if="item.status === 'finished'" class="time">{{ formatDateTime(item.timestamp) }}</span>
                 <span v-else>计划抽取: {{ item.count }} 人</span>
              </div>
            </div>
            
            <!-- Result Preview (Only for finished) -->
            <div v-if="item.status === 'finished'" class="winners-preview">
                <span v-for="w in (item.winners || [])" :key="w.id" class="winner-tag">{{ w.name }}</span>
            </div>
            
            <div class="lottery-actions">
               <span v-if="item.status === 'pending'" class="status-hint">待开始</span>
               <span v-else-if="item.status === 'active'" class="status-hint active">进行中</span>
               <span v-else class="status-hint finished">本轮已结束</span>
            </div>
         </el-card>
       </div>

       <!-- Manage/Edit Dialog -->
       <el-dialog
         v-model="manageDialogVisible"
         title="管理抽签轮次"
         width="750px"
         append-to-body
         align-center
       >
         <div class="batch-form">
            <el-alert title="注意：只能修改未开始的轮次；已结束的轮次无法修改。" type="warning" :closable="false" style="margin-bottom:12px;" />
             <div class="batch-header">
                <span>轮次标题</span>
                <span>抽取人数</span>
                <span>允许重复</span>
                <span>操作</span>
             </div>
             
             <div v-for="(round, idx) in form.rounds" :key="idx" class="batch-row">
                 <el-input v-model="round.title" placeholder="轮次名称" style="width: 240px" :disabled="round.status === 'finished'" />
                 <el-input-number v-model="round.count" :min="1" :max="100" style="width: 120px" :disabled="round.status === 'finished'" />
                 <div class="switch-col">
                    <el-switch v-model="round.allowRepeat" :disabled="round.status === 'finished'" />
                 </div>
                 <el-button type="danger" link @click="confirmDeleteRound(round, idx)" :disabled="round.status === 'finished'">删除</el-button>
             </div>

             <el-button class="add-btn" plain type="primary" :icon="Plus" @click="addRoundRow" style="width: 100%; margin-top: 10px;">
                增加一轮
             </el-button>
         </div>
         
         <template #footer>
           <div class="dialog-footer">
             <el-button @click="manageDialogVisible = false">取消</el-button>
             <el-button type="primary" @click="handleManageConfirm">
               保存配置
             </el-button>
           </div>
         </template>
       </el-dialog>
    </div>
  </el-drawer>
</template>

<script setup>
import { ref, computed, onUnmounted } from 'vue'
import { Plus, Refresh, Monitor, Delete, Edit, Setting } from '@element-plus/icons-vue'
import { ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import { io } from 'socket.io-client'

const props = defineProps({
  modelValue: Boolean,
  meetingId: [Number, String],
  meetingTitle: String
})

const emit = defineEmits(['update:modelValue'])
const router = useRouter()

const visible = computed({
  get: () => props.modelValue,
  set: (val) => emit('update:modelValue', val)
})

const loading = ref(false)
const lotteryList = ref([]) 
const totalParticipants = ref(0)
const manageDialogVisible = ref(false)
const socket = ref(null)

// --- Config Form ---
const formRef = ref(null)
const form = ref({
  rounds: []
})

const addRoundRow = () => {
    // 自动计算下一轮序号
    // 逻辑：基于当前form长度 + 已经存在的长度（如果是新增）
    // 但这里form包含所有。
    const nextIdx = form.value.rounds.length + 1
    form.value.rounds.push({
        title: `第 ${nextIdx} 轮`,
        count: 1,
        allowRepeat: false,
        status: 'pending' // new rows are pending
    })
}

// 打开管理弹窗
const openManageDialog = () => {
    // 将现有轮次加载到表单
    form.value.rounds = lotteryList.value.map(item => ({
        id: item.round_id,
        title: item.title,
        count: item.count,
        allowRepeat: item.allow_repeat,
        status: item.status
    }))
    
    // 如果为空，默认加一行
    if (form.value.rounds.length === 0) {
        form.value.rounds.push({
            title: '第 1 轮',
            count: 1,
            allowRepeat: false,
            status: 'pending'
        })
    }
    
    manageDialogVisible.value = true
}

// 管理弹窗中的删除确认
const confirmDeleteRound = async (round, index) => {
    // 如果是新加的行（没有ID），直接删除
    if (!round.id) {
        form.value.rounds.splice(index, 1)
        return
    }

    try {
        await ElMessageBox.confirm(
            `确定要删除抽签轮次"${round.title}"吗？此操作不可恢复。`,
            '删除确认',
            { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
        )
        if(socket.value && round.id) {
            socket.value.emit('lottery_action', {
                action: 'delete',
                meeting_id: props.meetingId,
                lottery_id: round.id
            })
            // 从UI暂时移除（socket回调会刷新）
            form.value.rounds.splice(index, 1)
        }
    } catch {
        // User cancelled
    }
}

// 保存管理配置
const handleManageConfirm = () => {
    if(!socket.value) {
        // Init socket if needed
        initSocket()
    }
    
    // 1. 发送更新 (针对已有ID且enable的)
    const updatableRounds = form.value.rounds.filter(r => r.id && r.status !== 'finished')
    updatableRounds.forEach(round => {
        socket.value.emit('lottery_action', {
            action: 'update',
            meeting_id: props.meetingId,
            lottery_id: round.id,
            title: round.title,
            count: round.count,
            allow_repeat: round.allowRepeat
        })
    })
    
    // 2. 发送批量新增 (针对无ID的)
    const newRounds = form.value.rounds.filter(r => !r.id)
    if (newRounds.length > 0) {
        const roundsPayload = newRounds.map(r => ({
            title: r.title,
            count: r.count,
            allow_repeat: r.allowRepeat
        }))
        
        socket.value.emit('lottery_action', { 
            action: 'batch_add', 
            meeting_id: props.meetingId,
            rounds: roundsPayload
        })
    }
    
    manageDialogVisible.value = false
    loading.value = true
}

// 进入大屏 (不触发 Prepare, 仅打开窗口)
const openBigScreen = () => {
    const routeUrl = router.resolve({
        name: 'LotteryBigScreen',
        params: { meetingId: props.meetingId },
        query: {
             t: Date.now()
        }
    })
    window.open(routeUrl.href, '_blank')
}

// --- Socket Events ---
const initSocket = () => {
    if (socket.value) return
    // Default to localhost:8001 if env not set, solving the 5173 issue
    // Use relative path by default (for production same-origin), or env var
    const url = import.meta.env.VITE_API_URL || ''
    socket.value = io(url, {
        path: '/socket.io',
        transports: ['websocket']
    })
    
    socket.value.on('connect', () => {
        if (props.meetingId) {
             // 加入会议房间以接收广播事件
             socket.value.emit('join_meeting', { meeting_id: props.meetingId })
             socket.value.emit('lottery_action', { action: 'get_history', meeting_id: props.meetingId })
        }
    })

    socket.value.on('lottery_history', (data) => {
        // 保持创建顺序（按round_id升序）
        lotteryList.value = (data.rounds || []).sort((a, b) => a.round_id - b.round_id)
        totalParticipants.value = data.total_participants || 0
        loading.value = false
    })
    
    socket.value.on('lottery_stop', () => {
        // Refresh history when a round stops
        if(socket.value) {
            socket.value.emit('lottery_action', { action: 'get_history', meeting_id: props.meetingId })
        }
    })

    socket.value.on('lottery_list_update', () => {
        fetchLotteries()
    })
    
    // Also refresh on state change to ensure status tags are up-to-date
    socket.value.on('lottery_state_change', () => {
        fetchLotteries()
    })
}

const handleOpen = () => {
    loading.value = true
    initSocket()
    if(socket.value && socket.value.connected) {
         socket.value.emit('lottery_action', { action: 'get_history', meeting_id: props.meetingId })
    }
}

const handleClose = () => {
  if (socket.value) {
      socket.value.disconnect()
      socket.value = null
  }
}

const fetchLotteries = () => {
    loading.value = true
    if(socket.value) {
         socket.value.emit('lottery_action', { action: 'get_history', meeting_id: props.meetingId })
    }
}

const formatDateTime = (date) => {
    if(!date) return ''
    return new Date(date).toLocaleString()
}

onUnmounted(() => {
    if (socket.value) socket.value.disconnect()
})
</script>

<style scoped>
.actions-bar {
  margin-bottom: 20px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.actions-right {
  display: flex;
  gap: 8px;
  align-items: center;
}

.status-hint {
  font-size: 12px;
  color: #909399;
}
.status-hint.active {
  color: #409eff;
}
.status-hint.finished {
  color: #aaa;
}

.lottery-card {
  margin-bottom: 12px;
  border-radius: 8px;
}

.lottery-header {
  display: flex;
  justify-content: space-between;
  margin-bottom: 12px;
}

.lottery-title {
    font-weight: 600;
    margin-right: 8px;
}

.time {
    font-size: 12px;
    color: #999;
}

.lottery-actions {
    border-top: 1px dashed #eee;
    padding-top: 8px;
    text-align: right;
}
.tips {
    font-size: 12px;
    color: #909399;
    line-height: 1.4;
    margin-top: 4px;
}
.winners-preview {
    display: flex;
    flex-wrap: wrap;
    gap: 8px;
    margin-bottom: 12px;
}
.winner-tag {
    background: #f0f9eb;
    color: #67c23a;
    padding: 2px 8px;
    border-radius: 4px;
    font-size: 13px;
}
/* Batch Styles */
.batch-header {
    display: flex;
    font-weight: bold;
    margin-bottom: 10px;
    padding: 0 10px;
    color: #606266;
}
.batch-header span:nth-child(1) { width: 240px; margin-right: 12px; }
.batch-header span:nth-child(2) { width: 120px; margin-right: 12px; }
.batch-header span:nth-child(3) { width: 100px; text-align: center; margin-right: 12px; }
.batch-header span:nth-child(4) { width: 60px; text-align: center; }

.batch-row {
    display: flex;
    align-items: center;
    margin-bottom: 8px;
    background: #f5f7fa;
    padding: 10px;
    border-radius: 4px;
}
.batch-row .el-input { margin-right: 12px; }
.batch-row .el-input-number { margin-right: 12px; }
.switch-col { width: 100px; text-align: center; display: flex; justify-content: center; margin-right: 12px; }
.batch-row .el-button { min-width: 60px; }

/* Dark Mode Overrides */
html.dark .batch-row {
    background: #2d3748;
}
html.dark .batch-header {
    color: #a0aec0;
}
</style>
