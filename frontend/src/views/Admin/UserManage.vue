<template>
  <div>
    <div class="page-header">
      <div class="header-left">
        <el-button 
          class="collapse-btn" 
          link 
          @click="toggleSidebar"
        >
          <el-icon size="24" color="#64748b">
            <Fold v-if="!isCollapse" />
            <Expand v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        
        <div class="title-group">
          <h2 class="page-title">人员管理</h2>
          <p class="page-subtitle">管理系统用户及组织架构</p>
        </div>
      </div>
      
      <div class="header-right">
        <el-button type="default" style="margin-right: 12px;">导入人员</el-button>
        <el-button type="primary" @click="dialogVisible = true">
          <el-icon class="el-icon--left"><Plus /></el-icon>
          添加人员
        </el-button>
      </div>
    </div>

    <!-- 人员列表表格 -->
    <el-table :data="users" style="width: 100%" v-loading="loading">
      <el-table-column type="index" label="序号" width="80" align="center" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="department" label="部门" />
      <el-table-column prop="position" label="职位" />
      <el-table-column prop="phone" label="联系方式">
          <template #default> 13800138000 </template>
      </el-table-column>
      <el-table-column label="操作" width="120">
        <template #default="scope">
          <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>

    <!-- 添加人员对话框 -->
    <el-dialog v-model="dialogVisible" title="添加人员" width="30%">
      <el-form :model="form" label-width="80px">
        <el-form-item label="姓名">
          <el-input v-model="form.name" />
        </el-form-item>
        <el-form-item label="部门">
          <el-input v-model="form.department" />
        </el-form-item>
        <el-form-item label="职位">
          <el-input v-model="form.position" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleAdd">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'
import { Fold, Expand, Plus } from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'

const { isCollapse, toggleSidebar } = useSidebar()

const users = ref([])
const loading = ref(false)
const dialogVisible = ref(false)
const form = ref({
  name: '',
  department: '',
  position: ''
})

// 获取用户列表
const fetchUsers = async () => {
  loading.value = true
  try {
    const res = await request.get('/users/')
    users.value = res
  } finally {
    loading.value = false
  }
}

// 添加用户
const handleAdd = async () => {
  if (!form.value.name) return ElMessage.warning('姓名必填')
  try {
    await request.post('/users/', form.value)
    ElMessage.success('添加成功')
    dialogVisible.value = false
    form.value = { name: '', department: '', position: '' } // 重置表单
    fetchUsers()
  } catch (e) {
    // 错误处理在 request.js 已统一处理，这里可略
  }
}

// 删除用户
const handleDelete = (row) => {
  ElMessageBox.confirm(
    `确定要删除 ${row.name} 吗?`,
    '提示',
    { confirmButtonText: '确定', cancelButtonText: '取消', type: 'warning' }
  ).then(async () => {
    await request.delete(`/users/${row.id}`)
    ElMessage.success('删除成功')
    fetchUsers()
  })
}

onMounted(() => {
  fetchUsers()
})
</script>

<style scoped>
/* 头部样式调整 */
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  padding: 0 4px;
}

.header-left {
  display: flex;
  align-items: center;
  gap: 12px;
}

.collapse-btn {
  padding: 8px;
  border-radius: 8px;
  transition: background-color 0.2s;
  height: auto;
}
.collapse-btn:hover {
  background-color: #f1f5f9;
}

.header-divider {
  height: 24px;
  border-color: #cbd5e1;
  margin: 0 4px;
}

.title-group {
  display: flex;
  flex-direction: column;
}

.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: #1e293b;
  line-height: 1.2;
}
.page-subtitle {
  margin: 4px 0 0;
  color: #64748b;
  font-size: 14px;
  line-height: 1.4;
}

.header-right {
  display: flex;
  align-items: center;
}
</style>
