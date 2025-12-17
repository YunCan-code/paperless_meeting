<template>
  <el-card class="meeting-history" shadow="never">
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
          <el-select v-model="statusFilter" size="small" placeholder="状态" style="width: 100px;">
            <el-option label="全部" value="" />
            <el-option label="计划中" value="scheduled" />
            <el-option label="进行中" value="active" />
            <el-option label="已结束" value="finished" />
          </el-select>
        </div>
      </div>
    </template>

    <el-table :data="filteredMeetings" style="width: 100%" size="default">
      <el-table-column prop="title" label="会议主题" min-width="180" />
      <el-table-column label="类型" width="100">
        <template #default="{ row }">
          <el-tag size="small" effect="plain">{{ getTypeName(row.meeting_type_id) }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="时间" width="160">
        <template #default="{ row }">
          {{ formatDate(row.start_time) }}
        </template>
      </el-table-column>
      <el-table-column prop="location" label="地点" width="120" />
      <el-table-column label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="getStatusType(row.status)" size="small" effect="dark" round>
            {{ getStatusText(row.status) }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="120" fixed="right">
        <template #default="{ row }">
          <el-button link type="primary" size="small" @click="$emit('view', row)">查看</el-button>
          <el-button link type="primary" size="small" @click="$emit('upload', row)">附件</el-button>
        </template>
      </el-table-column>
    </el-table>
  </el-card>
</template>

<script setup>
import { ref, computed } from 'vue'
import { Search } from '@element-plus/icons-vue'

const props = defineProps({
  meetings: { type: Array, default: () => [] },
  meetingTypes: { type: Array, default: () => [] }
})

defineEmits(['view', 'upload'])

const searchTerm = ref('')
const statusFilter = ref('')

const filteredMeetings = computed(() => {
  return props.meetings.filter(m => {
    const matchesSearch = m.title.toLowerCase().includes(searchTerm.value.toLowerCase())
    const matchesStatus = !statusFilter.value || m.status === statusFilter.value
    return matchesSearch && matchesStatus
  })
})

const getTypeName = (id) => {
  const found = props.meetingTypes.find(t => t.id === id)
  return found ? found.name : ''
}

const formatDate = (iso) => {
  if (!iso) return ''
  return new Date(iso).toLocaleString([], {year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute:'2-digit'})
}

const getStatusType = (status) => {
  const map = { scheduled: 'info', active: 'success', finished: 'warning' }
  return map[status] || 'info'
}

const getStatusText = (status) => {
  const map = { scheduled: '计划中', active: '进行中', finished: '已结束' }
  return map[status] || status
}
</script>

<style scoped>
.meeting-history {
  border-radius: 12px;
  border: 1px solid var(--color-slate-200);
}
.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 12px;
}
.card-header h3 {
  margin: 0;
  font-size: 16px;
  font-weight: 600;
  color: var(--color-slate-800);
}
.header-actions {
  display: flex;
  gap: 8px;
}
.search-input {
  width: 200px;
}
</style>
