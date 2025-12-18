<template>
  <el-card class="meeting-history" shadow="hover">
    <template #header>
      <div class="card-header">
        <h3>会议历史</h3>
        <div class="header-actions">
          <el-input
            v-model="searchTerm"
            placeholder="搜索会议..."
            clearable
            size="small"
            class="search-input"
            :prefix-icon="Search"
          />
        </div>
      </div>
    </template>

    <el-table :data="pagedMeetings" style="width: 100%" size="large" :header-cell-style="{ background: '#f8fafc' }">
      <el-table-column type="index" label="序号" width="80" align="center" />
      <el-table-column prop="title" label="会议主题" min-width="180">
         <template #default="{ row }">
            <span style="font-weight: 500; color: #1e293b;">{{ row.title }}</span>
         </template>
      </el-table-column>
      <el-table-column label="类型" width="120">
        <template #default="{ row }">
          <el-tag 
            size="small" 
            :style="{ 
               color: getTypeColor(row.meeting_type_id),
               backgroundColor: getTypeColor(row.meeting_type_id) + '1a',
               border: 'none'
            }"
            effect="plain" 
            round
          >
            {{ getTypeName(row.meeting_type_id) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="开始时间" width="180">
        <template #default="{ row }">
          {{ formatDate(row.start_time) }}
        </template>
      </el-table-column>
      <el-table-column prop="location" label="地点" width="150">
        <template #default="{ row }">
          {{ row.location || '-' }}
        </template>
      </el-table-column>
      
      <el-table-column label="操作" width="100" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$emit('view', row)">查看</el-button>
        </template>
      </el-table-column>
    </el-table>
    
    <div class="pagination-container">
      <el-pagination
        v-model:current-page="currentPage"
        v-model:page-size="pageSize"
        :page-sizes="[10, 20, 50]"
        layout="total, sizes, prev, pager, next, jumper"
        :total="totalMeetings"
        background
      />
    </div>
  </el-card>
</template>

<script setup>
import { ref, computed, watch } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  meetings: { type: Array, default: () => [] },
  meetingTypes: { type: Array, default: () => [] }
})

defineEmits(['view', 'upload'])

const searchTerm = ref('')
const currentPage = ref(1)
const pageSize = ref(10)

// Reset page on search
watch(searchTerm, () => currentPage.value = 1)
// Reset page on data change
watch(() => props.meetings.length, () => currentPage.value = 1)

const filteredMeetings = computed(() => {
  let result = [...props.meetings] 
  
  if (searchTerm.value) {
     const term = searchTerm.value.toLowerCase()
     result = result.filter(m => m.title.toLowerCase().includes(term))
  }
  
  // Sort by created time (ID desc usually implies creation order, or start_time desc)
  // User requested "New created on top". Assuming ID is auto-inc.
  result.sort((a, b) => b.id - a.id)
  
  return result
})

const totalMeetings = computed(() => filteredMeetings.value.length)

const pagedMeetings = computed(() => {
  const start = (currentPage.value - 1) * pageSize.value
  const end = start + pageSize.value
  return filteredMeetings.value.slice(start, end)
})

const getTypeName = (id) => {
  const found = props.meetingTypes.find(t => t.id === id)
  return found ? found.name : '-'
}

const colors = ['#3b82f6', '#10b981', '#8b5cf6', '#f59e0b', '#ef4444', '#06b6d4', '#ec4899', '#84cc16', '#6366f1', '#14b8a6']
const getTypeColor = (id) => {
   if (!props.meetingTypes) return '#3b82f6'
   const index = props.meetingTypes.findIndex(t => t.id === id)
   if (index === -1) return '#3b82f6'
   return colors[index % colors.length]
}

const formatDate = (iso) => {
  if (!iso) return ''
  return new Date(iso).toLocaleString([], {year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute:'2-digit'})
}
</script>

<style scoped>
.meeting-history {
  border-radius: 16px;
  border: none;
  background-color: #ffffff;
  box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
  padding-bottom: 8px;
}
.card-header h3 {
  margin: 0;
  font-size: 18px;
  font-weight: 700;
  color: #1e293b;
}
.header-actions {
  display: flex;
  gap: 12px;
}
.search-input {
  width: 240px;
}
.pagination-container {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
