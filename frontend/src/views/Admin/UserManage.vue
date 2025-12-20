<template>
  <div class="user-manage-container">
    <!-- 顶部操作栏 -->
    <el-card class="header-card" shadow="hover">
      <div class="header-actions">
         <div class="left-panel">
            <el-button class="collapse-btn" link @click="toggleSidebar">
              <el-icon size="24" color="#64748b">
                <Fold v-if="!isCollapse" />
                <Expand v-else />
              </el-icon>
            </el-button>
            <h2 class="page-title">人员管理</h2>
         </div>
         <div class="right-panel">
            <el-input
              v-model="searchQuery"
              placeholder="搜索姓名/职位"
              prefix-icon="Search"
              clearable
              style="width: 240px; margin-right: 12px;"
              @input="handleSearch"
            />
            <el-button type="primary" @click="openDialog()">
              <el-icon class="el-icon--left"><Plus /></el-icon>
              新增人员
            </el-button>
         </div>
      </div>
    </el-card>

    <!-- 表格区域 -->
    <el-card class="table-card" shadow="hover">
      <el-table :data="filteredData" style="width: 100%" stripe v-loading="loading">
        <el-table-column type="index" label="序号" width="80" align="center" />
        <el-table-column prop="name" label="姓名" width="120" />
        <el-table-column prop="district" label="区县" width="150">
            <template #default="scope">
                <el-tag size="small" effect="plain">{{ scope.row.district }}</el-tag>
            </template>
        </el-table-column>
        <el-table-column prop="position" label="职位" width="150" />
        <el-table-column prop="phone" label="联系方式" width="180" />
        <el-table-column prop="remark" label="备注" show-overflow-tooltip />
        <el-table-column label="操作" width="180" fixed="right">
          <template #default="scope">
            <el-button link type="primary" size="small" @click="openDialog(scope.row)">编辑</el-button>
            <el-button link type="danger" size="small" @click="handleDelete(scope.row)">删除</el-button>
          </template>
        </el-table-column>
      </el-table>
      
      <!-- 分页 (模拟) -->
      <div class="pagination-container">
        <el-pagination
          background
          layout="prev, pager, next"
          :total="total"
          :page-size="pageSize"
          @current-change="handlePageChange"
        />
      </div>
    </el-card>

    <!-- 编辑/新增对话框 -->
    <el-dialog v-model="dialogVisible" :title="isEdit ? '编辑人员' : '新增人员'" width="500px">
      <el-form :model="form" label-width="80px" :rules="rules" ref="formRef">
        <el-form-item label="姓名" prop="name">
          <el-input v-model="form.name" placeholder="请输入姓名" />
        </el-form-item>
        <el-form-item label="区县" prop="district">
             <el-select v-model="form.district" placeholder="请选择区县" style="width: 100%">
                <el-option label="市辖区" value="市辖区" />
                <el-option label="高新区" value="高新区" />
                <el-option label="呈贡区" value="呈贡区" />
                <el-option label="盘龙区" value="盘龙区" />
                <el-option label="官渡区" value="官渡区" />
                <el-option label="西山区" value="西山区" />
             </el-select>
        </el-form-item>
        <el-form-item label="职位" prop="position">
          <el-input v-model="form.position" placeholder="请输入职位 (如: 经理)" />
        </el-form-item>
        <el-form-item label="联系方式" prop="phone">
          <el-input v-model="form.phone" placeholder="请输入手机号" maxlength="11" />
        </el-form-item>
        <el-form-item label="密码" prop="password" v-if="!isEdit">
          <el-input v-model="form.password" type="password" placeholder="默认密码: 123456" show-password />
        </el-form-item>
        <el-form-item label="备注" prop="remark">
          <el-input v-model="form.remark" type="textarea" :rows="3" placeholder="请输入备注信息" />
        </el-form-item>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" @click="handleSubmit">确定</el-button>
        </span>
      </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, reactive } from 'vue'
import { Fold, Expand, Plus, Search } from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import { ElMessage, ElMessageBox } from 'element-plus'

const { isCollapse, toggleSidebar } = useSidebar()

// 数据定义
const loading = ref(false)
const searchQuery = ref('')
const tableData = ref([])
const currentPage = ref(1)
const pageSize = 10
const total = ref(0) // 模拟总数

// 模拟数据生成
const districts = ['市辖区', '高新区', '呈贡区', '盘龙区', '官渡区', '西山区']
const positions = ['项目经理', '技术总监', '行政专员', '销售代表', '高级工程师', '部门主管']
const firstNames = ['赵', '钱', '孙', '李', '周', '吴', '郑', '王', '冯', '陈']
const lastNames = ['伟', '芳', '娜', '敏', '静', '秀英', '丽', '强', '磊', '军', '洋', '勇']

const generateMockData = () => {
    const data = []
    for(let i=0; i<25; i++) {
        data.push({
            id: i + 1,
            name: firstNames[Math.floor(Math.random() * firstNames.length)] + lastNames[Math.floor(Math.random() * lastNames.length)],
            district: districts[Math.floor(Math.random() * districts.length)],
            position: positions[Math.floor(Math.random() * positions.length)],
            phone: '138' + Math.floor(Math.random() * 100000000).toString().padStart(8, '0'),
            remark: '这是一条模拟备注数据',
            createTime: new Date().toISOString()
        })
    }
    return data
}

// 初始化
onMounted(() => {
    loading.value = true
    setTimeout(() => {
        const mock = generateMockData()
        tableData.value = mock
        total.value = mock.length
        loading.value = false
    }, 500)
})

// 搜索过滤
const filteredData = computed(() => {
    const start = (currentPage.value - 1) * pageSize
    const end = start + pageSize
    
    if(!searchQuery.value) return tableData.value.slice(start, end)
    
    const filter = tableData.value.filter(item => 
        item.name.includes(searchQuery.value) || 
        item.position.includes(searchQuery.value)
    )
    return filter.slice(0, pageSize) // 简化处理，搜索时不分页全显也行，这里简单切片
})

const handleSearch = () => {
    currentPage.value = 1
}
const handlePageChange = (val) => {
    currentPage.value = val
}

// 表单处理
const dialogVisible = ref(false)
const isEdit = ref(false)
const formRef = ref(null)
const form = reactive({
    id: null,
    name: '',
    district: '',
    position: '',
    phone: '',
    password: '',
    remark: ''
})

const rules = {
    name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    district: [{ required: true, message: '请选择区县', trigger: 'change' }],
    // department: [{ required: true, message: '请输入部门', trigger: 'blur' }], // 移除部门
}

const openDialog = (row = null) => {
    if(row) {
        isEdit.value = true
        Object.assign(form, row)
        form.password = '' // 编辑不回显密码
    } else {
        isEdit.value = false
        form.id = null
        form.name = ''
        form.district = ''
        form.position = ''
        form.phone = ''
        form.password = ''
        form.remark = ''
    }
    dialogVisible.value = true
}

const handleSubmit = () => {
    formRef.value.validate((valid) => {
        if(valid) {
            if(isEdit.value) {
                // Mock Update
                const index = tableData.value.findIndex(item => item.id === form.id)
                if(index !== -1) {
                    tableData.value[index] = { ...tableData.value[index], ...form }
                }
                ElMessage.success('更新成功')
            } else {
                // Mock Add
                tableData.value.unshift({
                    id: tableData.value.length + 1,
                    ...form,
                    createTime: new Date().toISOString()
                })
                ElMessage.success('添加成功')
            }
            dialogVisible.value = false
        }
    })
}

const handleDelete = (row) => {
    ElMessageBox.confirm('确认删除该用户吗?', '提示', { type: 'warning' })
    .then(() => {
        tableData.value = tableData.value.filter(item => item.id !== row.id)
        ElMessage.success('删除成功')
    })
}

</script>

<style scoped>
.user-manage-container {
    height: 100%;
    display: flex;
    flex-direction: column;
    gap: 16px;
}

.header-card {
    border: none;
    border-radius: 8px;
}

.header-actions {
    display: flex;
    justify-content: space-between;
    align-items: center;
}

.left-panel {
    display: flex;
    align-items: center;
    gap: 12px;
}

.page-title {
    font-size: 20px;
    font-weight: 600;
    color: var(--text-main);
    margin: 0;
}

.table-card {
    flex: 1;
    border: none;
    border-radius: 8px;
    display: flex;
    flex-direction: column;
    overflow: hidden;
}

.pagination-container {
    display: flex;
    justify-content: flex-end;
    margin-top: 16px;
}

:deep(.el-card__body) {
    height: 100%;
    display: flex;
    flex-direction: column;
}
</style>
