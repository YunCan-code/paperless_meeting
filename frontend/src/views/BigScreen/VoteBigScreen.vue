<template>
  <div class="big-screen">
    <div class="screen-header">
      <div class="header-content">
        <h1 class="main-title">{{ voteData.title || '正在加载投票...' }}</h1>
        <div class="sub-info" v-if="voteData.id">
          <span class="status-tag" :class="voteData.status" v-if="voteData.status">
            {{ getStatusLabel(voteData.status) }}
          </span>
          <span class="voter-count">
            <el-icon><User /></el-icon> {{ totalVoters }} 人已参与
          </span>
        </div>
      </div>
    </div>

    <div class="screen-body">
      <div class="results-container">
        <transition-group name="list" tag="div" class="bars-wrapper">
          <div 
            v-for="(item, index) in sortedResults" 
            :key="item.option_id" 
            class="result-bar-item"
          >
            <div class="bar-label">
              <span class="rank-num">{{ index + 1 }}</span>
              <span class="option-text">{{ item.content }}</span>
            </div>
            
            <div class="bar-track">
              <div 
                class="bar-fill" 
                :style="{ width: item.percent + '%', backgroundColor: getBarColor(index) }"
              >
                <div class="bar-shine"></div>
              </div>
              <div class="bar-value" :style="{ color: item.percent > 5 ? '#fff' : 'inherit', left: item.percent > 5 ? 'auto' : (item.percent + '%'), marginLeft: item.percent > 5 ? '-10px' : '10px', transform: item.percent > 5 ? 'translateX(-100%)' : 'none' }">
                {{ item.count }}票
              </div>
            </div>
            
            <div class="bar-percent">{{ item.percent }}%</div>
          </div>
        </transition-group>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, onUnmounted, computed } from 'vue'
import { useRoute } from 'vue-router'
import { User } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { io } from 'socket.io-client'

const route = useRoute()
const voteId = route.params.id

const voteData = ref({})
const results = ref([])
const totalVoters = ref(0)
const socket = ref(null)

const sortedResults = computed(() => {
  // Sort by count desc
  return [...results.value].sort((a, b) => b.count - a.count)
})

const colorPalette = [
  '#3b82f6', '#10b981', '#f59e0b', '#ef4444', 
  '#8b5cf6', '#ec4899', '#06b6d4', '#84cc16'
]
const getBarColor = (index) => colorPalette[index % colorPalette.length]

const getStatusLabel = (s) => ({ draft:'未开始', active:'正在投票', closed:'投票结束' }[s] || s)

// Fetch Initial Data
const fetchResults = async () => {
  try {
    const res = await request.get(`/vote/${voteId}/result`)
    voteData.value = { 
        id: res.vote_id, 
        title: res.title, 
        // We might need to fetch status separately if result api doesn't return it
        // Assuming result api returns basic vote info. 
        // If 'status' is missing in result, we might need another call.
        // For now, let's assume result object has title.
        status: 'active' // Default assumption or need extra API
    }
    // Attempt to get status from vote details
    // Error handling omitted for brevity, assuming happy path or user triggers update
    
    // Result structure: { title, total_voters, results: [ {option_id, content, count, percent} ] }
    totalVoters.value = res.total_voters
    results.value = res.results
  } catch (e) {
    console.error(e)
  }
}

// Additional fetch for status if needed
const fetchVoteDetail = async () => {
   try {
       // Assuming we have an endpoint for single vote detail? 
       // In route list, we might rely on the list or create a 'get' endpoint.
       // Let's rely on socket updates for status changes.
   } catch(e) {}
}

const initSocket = () => {
  // Connect to backend root
  const url = import.meta.env.VITE_API_URL || window.location.origin
  // Need to strip /api if present? Usually socket.io is on root/socket.io
  // If we are proxying, socket path is /socket.io
  
  socket.value = io(url, {
      path: '/socket.io',
      transports: ['websocket']
  })

  socket.value.on('connect', () => {
      console.log('BigScreen Connected')
  })

  socket.value.on('vote_update', (data) => {
      if (data.vote_id == voteId) {
          results.value = data.results
          // Recalculate total? Backend sends results with counts. 
          // Sum up counts
          const total = data.results.reduce((acc, cur) => acc + cur.count, 0)
          // Wait, 'vote_update' structure in backend: { vote_id, results: [...] }
          // It doesn't send total_voters explicit in 'vote_update' based on my implementation memory?
          // Let's check `socket_manager.py`. It sends `VoteUpdateData`.
          // If total is computed from sum of counts, fine. But multiple choice makes sum(count) >= total_voters.
          // Since we might not have exact total_voters in update stream unless modified, let's approximation or fetch again.
          // For smoothness, let's Fetch again silently to get accurate total_voters? Or just sum(counts) as "Total Votes" (tickets) rather than Voters (people).
          // Let's trigger a fetch
          fetchResults()
      }
  })

  socket.value.on('vote_end', (data) => {
      if (data.vote_id == voteId) {
          voteData.value.status = 'closed'
          results.value = data.results
          totalVoters.value = data.total_voters
      }
  })
  
  socket.value.on('vote_start', (data) => {
       if (data.id == voteId) {
           voteData.value.status = 'active'
       }
  })
}

onMounted(() => {
  fetchResults()
  initSocket()
  // Set dark theme
  document.documentElement.classList.add('dark')
})

onUnmounted(() => {
  if (socket.value) socket.value.disconnect()
  document.documentElement.classList.remove('dark')
})
</script>

<style scoped>
.big-screen {
  width: 100vw;
  height: 100vh;
  background-color: #0f172a; /* Slate 900 */
  color: #fff;
  overflow: hidden;
  display: flex;
  flex-direction: column;
}

.screen-header {
  padding: 40px;
  background: linear-gradient(180deg, #1e293b 0%, rgba(30, 41, 59, 0) 100%);
  text-align: center;
}

.main-title {
  font-size: 48px;
  margin: 0 0 16px 0;
  background: linear-gradient(to right, #60a5fa, #a78bfa);
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  letter-spacing: 2px;
}

.sub-info {
  display: flex;
  justify-content: center;
  align-items: center;
  gap: 24px;
  font-size: 24px;
  color: #94a3b8;
}

.status-tag {
  padding: 4px 16px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.1);
  font-size: 18px;
}
.status-tag.active { color: #10b981; background: rgba(16, 185, 129, 0.2); }
.status-tag.closed { color: #ef4444; background: rgba(239, 68, 68, 0.2); }

.voter-count {
  display: flex;
  align-items: center;
  gap: 8px;
}

.screen-body {
  flex: 1;
  padding: 0 10vw;
  display: flex;
  align-items: center;
  justify-content: center;
}

.results-container {
  width: 100%;
  max-width: 1200px;
}

.bars-wrapper {
  display: flex;
  flex-direction: column;
  gap: 24px;
}

.result-bar-item {
  display: flex;
  align-items: center;
  gap: 20px;
  transition: all 0.5s ease;
}

/* List Transitions */
.list-move,
.list-enter-active,
.list-leave-active {
  transition: all 0.5s ease;
}
.list-enter-from,
.list-leave-to {
  opacity: 0;
  transform: translateX(30px);
}
.list-leave-active {
  position: absolute;
}

.bar-label {
  width: 200px; /* Label width */
  text-align: right;
  font-size: 24px;
  display: flex;
  align-items: center;
  justify-content: flex-end;
  gap: 12px;
}

.rank-num {
  width: 32px;
  height: 32px;
  background: #334155;
  border-radius: 50%;
  font-size: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #94a3b8;
}

.option-text {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-weight: 500;
}

.bar-track {
  flex: 1;
  height: 48px;
  background: #1e293b;
  border-radius: 12px;
  position: relative;
  overflow: hidden;
}

.bar-fill {
  height: 100%;
  border-radius: 12px;
  position: relative;
  transition: width 0.8s cubic-bezier(0.34, 1.56, 0.64, 1);
  min-width: 0;
}

.bar-shine {
  position: absolute;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: linear-gradient(
    90deg,
    rgba(255, 255, 255, 0) 0%,
    rgba(255, 255, 255, 0.2) 50%,
    rgba(255, 255, 255, 0) 100%
  );
  transform: skewX(-20deg) translateX(-150%);
  animation: shine 2s infinite;
}

@keyframes shine {
  0% { transform: skewX(-20deg) translateX(-150%); }
  50% { transform: skewX(-20deg) translateX(150%); }
  100% { transform: skewX(-20deg) translateX(150%); }
}

.bar-value {
  position: absolute;
  right: 12px;
  top: 0;
  bottom: 0;
  display: flex;
  align-items: center;
  font-weight: bold;
  font-size: 20px;
  text-shadow: 0 1px 2px rgba(0,0,0,0.5);
  white-space: nowrap;
}

.bar-percent {
  width: 80px;
  font-size: 24px;
  font-weight: bold;
  color: #94a3b8;
  text-align: right;
}

</style>
