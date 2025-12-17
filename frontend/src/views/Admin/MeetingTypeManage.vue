<template>
  <div class="type-manage">
    <!-- 单一容器卡片 -->
    <el-card class="main-card" shadow="never">
      <template #header>
        <div class="card-header">
          <div class="header-left">
            <h2>会议类型管理</h2>
            <p class="subtitle">管理会议的分类类型，为每种会议赋予独特标识</p>
          </div>
          <el-button type="primary" @click="openDialog()">
            <el-icon class="el-icon--left"><Plus /></el-icon>
            添加类型
          </el-button>
        </div>
      </template>

      <!-- 类型列表 -->
      <div class="type-list" v-loading="loading">
        <div 
          v-for="(item, index) in types" 
          :key="item.id" 
          class="type-item"
          :style="{ '--accent-color': getColor(index) }"
        >
          <div class="color-bar"></div>
          <div class="item-content">
            <div class="item-main">
              <span class="type-badge" :style="{ backgroundColor: getColor(index) + '20', color: getColor(index) }">
                {{ item.name }}
              </span>
              <p class="type-desc">{{ item.description || '暂无描述' }}</p>
            </div>
            <div class="item-meta">
              <span class="meta-time">
                <el-icon><Clock /></el-icon>
                {{ formatDate(item.created_at) }}
              </span>
            </div>
          </div>
          <div class="item-actions">
            <el-button link @click="openDialog(item)">
              <el-icon><Edit /></el-icon>
            </el-button>
            <el-button link type="danger" @click="handleDelete(item)">
              <el-icon><Delete /></el-icon>
            </el-button>
          </div>
        </div>

        <!-- 空状态 -->
        <el-empty v-if="types.length === 0 && !loading" description="暂无类型" :image-size="100">
          <el-button type="primary" @click="openDialog()">创建第一个类型</el-button>
        </el-empty>
      </div>
    </el-card>

    <!-- 添加/编辑 对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑类型' : '添加类型'" width="420px" destroy-on-close>
      <el-form :model="form" label-position="top">
        <el-form-item label="类型名称" required>
          <el-input v-model="form.name" placeholder="如：党组会、办公会" maxlength="20" show-word-limit />
        </el-form-item>
        <el-form-item label="描述说明">
          <el-input v-model="form.description" type="textarea" :rows="3" placeholder="可选，对该类型的简要说明" maxlength="100" show-word-limit />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSave" :loading="saving">{{ isEdit ? '保存修改' : '添加类型' }}</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, computed } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Plus, Clock, Edit, Delete } from '@element-plus/icons-vue'

// 预定义的一组和谐的颜色
const colors = [
  '#3b82f6', // blue
  '#10b981', // emerald
  '#8b5cf6', // violet
  '#f59e0b', // amber
  '#ef4444', // red
  '#06b6d4', // cyan
  '#ec4899', // pink
  '#84cc16', // lime
  '#6366f1', // indigo
  '#14b8a6', // teal
]

const types = ref([])
const loading = ref(false)
const saving = ref(false)
const dialogVisible = ref(false)
const editingId = ref(null)
const form = ref({ name: '', description: '' })

const isEdit = computed(() => editingId.value !== null)

const getColor = (index) => colors[index % colors.length]

const fetchTypes = async () => {
  loading.value = true
  try {
    types.value = await request.get('/meeting_types/')
  } finally {
    loading.value = false
  }
}

const openDialog = (item = null) => {
  if (item) {
    editingId.value = item.id
    form.value = { name: item.name, description: item.description || '' }
  } else {
    editingId.value = null
    form.value = { name: '', description: '' }
  }
  dialogVisible.value = true
}

const handleSave = async () => {
  if (!form.value.name.trim()) return ElMessage.warning('类型名称不能为空')
  saving.value = true
  try {
    if (isEdit.value) {
      await request.put(`/meeting_types/${editingId.value}`, form.value)
      ElMessage.success('更新成功')
    } else {
      await request.post('/meeting_types/', form.value)
      ElMessage.success('添加成功')
    }
    dialogVisible.value = false
    fetchTypes()
  } catch (e) {
    ElMessage.error('操作失败')
  } finally {
    saving.value = false
  }
}

const handleDelete = (item) => {
  ElMessageBox.confirm(`确定要删除类型「${item.name}」吗？删除后不可恢复。`, '删除确认', {
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
    type: 'warning',
    confirmButtonClass: 'el-button--danger'
  }).then(async () => {
    await request.delete(`/meeting_types/${item.id}`)
    ElMessage.success('已删除')
    fetchTypes()
  }).catch(() => {})
}

const formatDate = (iso) => {
  if (!iso) return '-'
  const d = new Date(iso)
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`
}

onMounted(fetchTypes)
</script>

<style scoped>
.type-manage {
  max-width: 900px;
  margin: 0 auto;
}

.main-card {
  border-radius: 16px;
  border: 1px solid var(--color-slate-200);
}
.main-card :deep(.el-card__header) {
  padding: 24px 28px;
  border-bottom: 1px solid var(--color-slate-100);
}
.main-card :deep(.el-card__body) {
  padding: 0;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
}
.header-left h2 {
  margin: 0 0 4px 0;
  font-size: 20px;
  font-weight: 600;
  color: var(--color-slate-900);
}
.subtitle {
  margin: 0;
  font-size: 13px;
  color: var(--color-slate-500);
}

/* Type List */
.type-list {
  min-height: 200px;
}
.type-item {
  display: flex;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid var(--color-slate-100);
  transition: background 0.2s;
  position: relative;
}
.type-item:last-child {
  border-bottom: none;
}
.type-item:hover {
  background-color: #fafafa;
}

.color-bar {
  width: 4px;
  height: 40px;
  border-radius: 2px;
  background-color: var(--accent-color);
  margin-right: 16px;
  flex-shrink: 0;
}

.item-content {
  flex: 1;
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}
.item-main {
  flex: 1;
}
.type-badge {
  display: inline-block;
  padding: 4px 12px;
  border-radius: 20px;
  font-size: 14px;
  font-weight: 600;
  margin-bottom: 4px;
}
.type-desc {
  margin: 4px 0 0;
  font-size: 13px;
  color: var(--color-slate-500);
  max-width: 400px;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}

.item-meta {
  flex-shrink: 0;
}
.meta-time {
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 12px;
  color: var(--color-slate-400);
}

.item-actions {
  display: flex;
  gap: 4px;
  margin-left: 16px;
  opacity: 0;
  transition: opacity 0.2s;
}
.type-item:hover .item-actions {
  opacity: 1;
}
.item-actions .el-button {
  padding: 8px;
}
.item-actions .el-icon {
  font-size: 16px;
}
</style>
