<template>
  <div class="follow-up-page">
    <div class="page-header">
      <div class="header-left">
        <h1 class="page-title">后续事项</h1>
        <p class="page-subtitle">记录会议纪要后续跟进任务</p>
      </div>
      <div class="header-right">
        <el-button type="primary" @click="openCreate">
          <el-icon><Plus /></el-icon> 新建事项
        </el-button>
      </div>
    </div>

    <!-- Note Cards Grid -->
    <div class="notes-grid" v-if="notes.length > 0">
      <div v-for="note in notes" :key="note.id" class="note-card" :class="{ 'is-done': note.status === 'done' }">
        <div class="note-header">
          <div class="note-title" :title="note.title">{{ note.title }}</div>
          <el-dropdown trigger="click" @command="(cmd) => handleCommand(cmd, note)">
            <span class="more-btn"><el-icon><MoreFilled /></el-icon></span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item command="edit">编辑</el-dropdown-item>
                <el-dropdown-item command="toggle">{{ note.status === 'done' ? '标记未完成' : '标记完成' }}</el-dropdown-item>
                <el-dropdown-item command="delete" divided style="color: #ef4444">删除</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
        
        <div class="note-content">
          {{ note.content || '暂无内容' }}
        </div>
        
        <div class="note-footer">
          <span class="note-date">{{ formatDate(note.updated_at || note.created_at) }}</span>
          <el-tag size="small" :type="note.status === 'done' ? 'success' : 'warning'">
            {{ note.status === 'done' ? '已完成' : '待处理' }}
          </el-tag>
        </div>
      </div>
    </div>
    
    <el-empty v-else description="暂无后续事项" />

    <!-- Dialog -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑事项' : '新建事项'" width="500px">
      <el-form label-position="top">
        <el-form-item label="标题">
          <el-input v-model="form.title" placeholder="输入事项标题..." />
        </el-form-item>
        <el-form-item label="详细内容">
          <el-input 
            v-model="form.content" 
            type="textarea" 
            :rows="6" 
            placeholder="记录详细内容..." 
            resize="none"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="dialogVisible = false">取消</el-button>
        <el-button type="primary" @click="handleSubmit" :loading="submitting">保存</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { Plus, MoreFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const notes = ref([])
const dialogVisible = ref(false)
const isEdit = ref(false)
const submitting = ref(false)
const form = ref({ title: '', content: '' })
const editingId = ref(null)

const fetchNotes = async () => {
    try {
        const res = await request.get('/notes/')
        notes.value = res
    } catch(e) {
        console.error(e)
    }
}

const formatDate = (iso) => {
    if(!iso) return ''
    return new Date(iso).toLocaleString()
}

const openCreate = () => {
    isEdit.value = false
    form.value = { title: '', content: '' }
    dialogVisible.value = true
}

const handleCommand = (cmd, note) => {
    if (cmd === 'edit') {
        isEdit.value = true
        editingId.value = note.id
        form.value = { title: note.title, content: note.content }
        dialogVisible.value = true
    } else if (cmd === 'delete') {
        ElMessageBox.confirm('确定删除该事项吗？', '提示', { type: 'warning' }).then(async () => {
            await request.delete(`/notes/${note.id}`)
            ElMessage.success('已删除')
            fetchNotes()
        })
    } else if (cmd === 'toggle') {
        toggleStatus(note)
    }
}

const toggleStatus = async (note) => {
    const newStatus = note.status === 'done' ? 'pending' : 'done'
    try {
        await request.put(`/notes/${note.id}`, { status: newStatus })
        ElMessage.success('状态已更新')
        fetchNotes()
    } catch(e) {}
}

const handleSubmit = async () => {
    if(!form.value.title) return ElMessage.warning('请输入标题')
    submitting.value = true
    try {
        if(isEdit.value) {
            await request.put(`/notes/${editingId.value}`, form.value)
        } else {
            await request.post('/notes/', form.value)
        }
        dialogVisible.value = false
        fetchNotes()
        ElMessage.success('保存成功')
    } catch(e) {
    } finally {
        submitting.value = false
    }
}

onMounted(fetchNotes)
</script>

<style scoped>
.follow-up-page { padding: 0 10px; }
.page-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; padding: 0 4px; }
.page-title { margin: 0; font-size: 24px; color: var(--text-main); font-weight: 700; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 13px; }

.notes-grid {
    display: grid;
    grid-template-columns: repeat(auto-fill, minmax(300px, 1fr));
    gap: 20px;
}

.note-card {
    background: var(--bg-main);
    border: 1px solid var(--border-color);
    border-radius: 12px;
    padding: 16px;
    display: flex;
    flex-direction: column;
    height: 220px;
    transition: all 0.2s;
    position: relative;
}
.note-card:hover { transform: translateY(-4px); box-shadow: 0 10px 15px -3px rgba(0,0,0,0.05); }
.note-card.is-done { opacity: 0.7; background: var(--bg-body); }

.note-header { display: flex; justify-content: space-between; align-items: flex-start; margin-bottom: 12px; }
.note-title { font-weight: 700; font-size: 16px; color: var(--text-main); flex: 1; display: -webkit-box; -webkit-line-clamp: 1; -webkit-box-orient: vertical; overflow: hidden; margin-right: 8px;}
.more-btn { cursor: pointer; color: var(--text-secondary); padding: 4px; border-radius: 4px; }
.more-btn:hover { background: var(--bg-hover); color: var(--text-main); }

.note-content { flex: 1; font-size: 14px; color: var(--text-regular); line-height: 1.6; white-space: pre-wrap; overflow-y: auto; margin-bottom: 12px; }

.note-footer { display: flex; justify-content: space-between; align-items: center; border-top: 1px solid var(--border-color-light); padding-top: 12px; margin-top: auto; }
.note-date { font-size: 12px; color: var(--text-placeholder); }
</style>
