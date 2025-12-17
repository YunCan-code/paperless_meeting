<template>
  <div>
    <div class="header">
      <h2>人员管理</h2>
      <el-button type="primary" @click="dialogVisible = true">添加人员</el-button>
    </div>

    <!-- 人员列表表格 -->
    <el-table :data="users" style="width: 100%" v-loading="loading">
      <el-table-column prop="id" label="ID" width="80" />
      <el-table-column prop="name" label="姓名" width="120" />
      <el-table-column prop="department" label="部门" />
      <el-table-column prop="position" label="职位" />
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
.header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 20px;
}
</style>
