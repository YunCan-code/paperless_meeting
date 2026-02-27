<template>
  <div class="user-manage-container">
    
    <!-- Page Header -->
    <div class="page-header">
      <div class="header-left">
        <el-button 
          class="collapse-btn" 
          link 
          @click="toggleSidebar"
        >
          <el-icon size="24" color="#64748b">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        
        <div class="title-group">
          <h1 class="page-title">人员管理</h1>
          <p class="page-subtitle">管理系统用户、角色与权限</p>
        </div>
      </div>
    </div>

    <!-- Stats Row -->
    <div class="stats-row">
      <el-row :gutter="20">
        <el-col :span="6" v-for="(stat, index) in statsData" :key="index">
          <div class="stat-card" :class="`stat-card-${index}`">
            <div class="stat-icon-wrapper">
              <el-icon :size="24" :class="`text-${stat.color}`"><component :is="stat.icon" /></el-icon>
            </div>
            <div class="stat-info">
              <div class="stat-label">{{ stat.label }}</div>
              <div class="stat-value">
                {{ stat.value }}
              </div>
            </div>
            <div class="stat-bg-decoration"></div>
          </div>
        </el-col>
      </el-row>
    </div>

    <div class="main-card">
      <div class="toolbar-header">
        <div class="toolbar-left">
          <el-input
            v-model="searchQuery"
            placeholder="搜索用户名、姓名、手机号..."
            class="search-input"
            clearable
            @clear="fetchUsers"
            @keyup.enter="fetchUsers"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select v-model="roleFilter" placeholder="所有角色" class="role-select" clearable @change="fetchUsers">
            <el-option label="主讲人" value="主讲人" />
            <el-option label="参会人员" value="参会人员" />
          </el-select>

          <div class="status-tabs">
            <span 
              v-for="tab in statusOptions" 
              :key="tab.value"
              :class="{ active: statusFilter === tab.value }"
              @click="handleStatusFilter(tab.value)"
            >
              {{ tab.label }}
            </span>
          </div>
        </div>

        <div class="toolbar-right">
          <el-button @click="importDialogVisible = true" style="margin-right: 12px">
            <template #icon><Upload /></template> 导入用户
          </el-button>
          <el-button type="primary" class="btn-primary" @click="openDialog('create')">
            <template #icon><Plus /></template> 新增用户
          </el-button>
        </div>
      </div>

      <div class="table-wrapper">
        <el-table
          v-loading="loading"
          :data="userList"
          style="width: 100%"
          :header-cell-style="{ background: 'transparent', color: 'var(--text-secondary)', fontWeight: '600' }"
          row-class-name="user-row"
        >
          <!-- 用户名 / 邮箱 (Enhanced) -->
          <el-table-column label="用户名" min-width="220">
            <template #default="{ row }">
              <div class="user-info-cell">
                <el-avatar 
                  :size="40" 
                  :style="{ background: getAvatarColor(row.name || row.username) }"
                  class="user-avatar"
                >
                  {{ (row.name || row.username).charAt(0) }}
                </el-avatar>
                <div class="user-details">
                  <div 
                    class="user-name" 
                  >
                    {{ row.name || row.username }}
                  </div>
                  <div 
                    class="user-email clickable-email" 
                    @click="copyEmail(row)"
                    title="点击复制邮箱"
                  >
                    {{ row.email || '未设置邮箱' }}
                  </div>
                </div>
              </div>
            </template>
          </el-table-column>



          <!-- 区县 -->
          <el-table-column label="区县" prop="district" min-width="120">
            <template #default="{ row }">
               <span 
                 class="district-badge"
                 :style="{
                    backgroundColor: getDistrictStyle(row.district).bg,
                    color: getDistrictStyle(row.district).text,
                    borderColor: getDistrictStyle(row.district).border
                 }"
               >
                 {{ row.district || '-' }}
               </span>
            </template>
          </el-table-column>
          
           <!-- 部门 -->
          <el-table-column label="部门" prop="department" min-width="120">
             <template #default="{ row }">{{ row.department || '-' }}</template>
          </el-table-column>

          <!-- 角色 -->
          <el-table-column label="角色" min-width="120">
            <template #default="{ row }">
              <el-tag :type="row.role === '主讲人' ? 'primary' : 'success'" effect="light" round>
                {{ row.role }}
              </el-tag>
            </template>
          </el-table-column>

          <!-- 联系方式 -->
          <el-table-column label="联系方式" min-width="160">
            <template #default="{ row }">
              <div class="privacy-cell">
                <span>{{ row.showPhone ? row.phone : maskPhone(row.phone) }}</span>
                <el-icon class="privacy-icon" @click="row.showPhone = !row.showPhone">
                  <component :is="row.showPhone ? 'View' : 'Hide'" />
                </el-icon>
              </div>
            </template>
          </el-table-column>

          <!-- 密码 -->
          <el-table-column label="密码" min-width="120">
             <template #default="{ row }">
                <div class="privacy-cell">
                    <span>{{ row.showPassword ? (row.password || 'password123') : '******' }}</span>
                     <el-icon class="privacy-icon" @click="togglePassword(row)">
                      <component :is="row.showPassword ? 'View' : 'Hide'" />
                    </el-icon>
                </div>
             </template>
          </el-table-column>

          <!-- 状态 -->
          <el-table-column label="状态" width="100" align="center">
            <template #default="{ row }">
              <div 
                class="status-indicator" 
                :class="row.is_active ? 'active' : 'inactive'"
              ></div>
            </template>
          </el-table-column>

          <!-- 最后登录 -->
          <el-table-column label="最后登录" prop="last_login" width="180">
             <template #default="{ row }">
                 <div class="last-login-cell" :title="row.last_login ? new Date(row.last_login).toLocaleString() : ''">
                    {{ row.last_login ? new Date(row.last_login).toLocaleString() : '-' }}
                 </div>
             </template>
          </el-table-column>

          <el-table-column label="操作" width="150" fixed="right" align="right">
            <template #default="{ row }">
              <div class="action-buttons">
                <el-button circle size="small" @click="openDialog('edit', row)">
                  <el-icon><EditPen /></el-icon>
                </el-button>
                <el-button circle size="small" type="danger" plain @click="handleDelete(row)">
                  <el-icon><Delete /></el-icon>
                </el-button>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrapper">
          <el-pagination
            background
            layout="total, prev, pager, next"
            :total="total"
            :page-size="pageSize"
            :current-page="currentPage"
            @current-change="handlePageChange"
          />
        </div>
      </div>
    </div>

    <!-- Dialog -->
    <el-dialog
      v-model="dialogVisible"
      :title="dialogType === 'create' ? '新增用户' : '编辑用户'"
      width="650px"
      align-center
    >
      <el-form :model="form" :rules="rules" ref="formRef" label-position="top">
        <el-row :gutter="24">
           <el-col :span="12">
            <el-form-item label="姓名" prop="name">
              <el-input v-model="form.name" placeholder="真实姓名" />
            </el-form-item>
          </el-col>

          
          <el-col :span="12">
            <el-form-item label="联系方式" prop="phone">
              <el-input v-model="form.phone" placeholder="手机号码" maxlength="11" />
            </el-form-item>
          </el-col>
           <el-col :span="12">
            <el-form-item label="电子邮箱" prop="email">
              <el-input v-model="form.email" placeholder="name@example.com" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="所属区县" prop="district">
               <el-select v-model="form.district" placeholder="请选择" style="width: 100%">
                  <el-option v-for="d in districtOptions" :key="d" :label="d" :value="d" />
               </el-select>
            </el-form-item>
          </el-col>
          <el-col :span="12">
            <el-form-item label="所属部门" prop="department">
              <el-input v-model="form.department" placeholder="部门名称" />
            </el-form-item>
          </el-col>

          <el-col :span="12">
            <el-form-item label="系统角色" prop="role">
              <el-select v-model="form.role" placeholder="选择角色" style="width: 100%">
                <el-option label="主讲人" value="主讲人" />
                <el-option label="参会人员" value="参会人员" />
              </el-select>
            </el-form-item>
          </el-col>
           <el-col :span="12">
            <el-form-item label="启用状态">
               <div style="height: 32px; display: flex; align-items: center;">
                 <el-switch v-model="form.is_active" active-text="启用" inactive-text="禁用" />
               </div>
            </el-form-item>
          </el-col>
          
          <el-col :span="24">
             <el-form-item :label="dialogType === 'create' ? '初始密码' : '重置密码 (留空不修改)'" prop="password">
               <el-input v-model="form.password" type="password" show-password placeholder="请输入密码" />
             </el-form-item>
          </el-col>
        </el-row>
      </el-form>
      <template #footer>
        <span class="dialog-footer">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">确认保存</el-button>
        </span>
      </template>
    </el-dialog>

    <!-- Import Dialog -->
    <el-dialog
       v-model="importDialogVisible"
       title="批量导入用户"
       width="500px"
       align-center
    >
      <div style="text-align: center; margin-bottom: 20px;">
          <p style="color: #64748b; margin-bottom: 12px;">请先下载模板，填写后再上传</p>
          <el-button type="primary" link @click="downloadTemplate">
             <el-icon style="margin-right: 4px"><Download /></el-icon> 下载导入模板
          </el-button>
      </div>
      
      <el-upload
        class="upload-demo"
        drag
        action="#"
        :http-request="handleImport"
        :show-file-list="false"
        accept=".xlsx"
      >
        <el-icon class="el-icon--upload"><upload-filled /></el-icon>
        <div class="el-upload__text">
           将 Excel 文件拖到此处，或 <em>点击上传</em>
        </div>
      </el-upload>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, reactive, onMounted } from 'vue'
import { 
  User, UserFilled, DataLine, Trophy, 
  Search, Plus, Upload, Download, UploadFilled,
  Top, Bottom, EditPen, Delete,
  Fold, Expand, View, Hide
} from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import request from '@/utils/request'
import { ElMessage, ElMessageBox } from 'element-plus'

const { isCollapse, toggleSidebar } = useSidebar()

// --- State ---
const loading = ref(false)
const submitting = ref(false)
const searchQuery = ref('')
const roleFilter = ref('')
const statusFilter = ref(null) 
const statusOptions = [
    { label: '全部', value: null },
    { label: '启用', value: true },
    { label: '禁用', value: false }
]

const userList = ref([])
const total = ref(0)
const currentPage = ref(1)
const pageSize = ref(10)

const districtOptions = ['市辖区', '高新区', '呈贡区', '盘龙区', '官渡区', '西山区', '五华区']

// Stats (Reactive)
const statsData = ref([
  { label: '总用户数', value: '-', icon: 'User', color: 'blue', key: 'total' },
  { label: '主讲人', value: '-', icon: 'DataLine', color: 'green', key: 'speakers' },
  { label: '参会人员', value: '-', icon: 'UserFilled', color: 'purple', key: 'attendees' },
  { label: '今日登录', value: '-', icon: 'Trophy', color: 'orange', key: 'active_today' },
])

const fetchStats = async () => {
    try {
        const res = await request.get('/users/stats')
        statsData.value.forEach(item => {
            if(res[item.key] !== undefined) {
                item.value = res[item.key]
            }
        })
    } catch (error) {
        console.error("Failed to fetch stats", error)
    }
}

// --- Logic ---
const getAvatarColor = (name) => {
  if(!name) return '#cbd5e1'
  const colors = ['#3b82f6', '#10b981', '#f59e0b', '#8b5cf6', '#ec4899', '#6366f1']
  let hash = 0
  for (let i = 0; i < name.length; i++) {
    hash = name.charCodeAt(i) + ((hash << 5) - hash)
  }
  return colors[Math.abs(hash) % colors.length]
}

// District Badges
// District Badges (Auto-generated consistent colors)
const getDistrictStyle = (name) => {
    if(!name) return { bg: '#f8fafc', text: '#64748b', border: '#e2e8f0' }
    
    // Palette of themes (bg, text, border)
    const themes = [
        { bg: '#eff6ff', text: '#2563eb', border: '#bfdbfe' }, // Blue
        { bg: '#f0fdf4', text: '#16a34a', border: '#bbf7d0' }, // Green
        { bg: '#fff7ed', text: '#ea580c', border: '#fed7aa' }, // Orange
        { bg: '#fdf4ff', text: '#c026d3', border: '#f0abfc' }, // Fuchsia
        { bg: '#faf5ff', text: '#7c3aed', border: '#ddd6fe' }, // Violet
        { bg: '#ecfeff', text: '#0891b2', border: '#a5f3fc' }, // Cyan
        { bg: '#fff1f2', text: '#e11d48', border: '#fecdd3' }, // Rose
        { bg: '#fefce8', text: '#ca8a04', border: '#fef08a' }, // Yellow
        { bg: '#f5f3ff', text: '#7c3aed', border: '#ddd6fe' }, // Indigo
    ]
    
    let hash = 0
    for (let i = 0; i < name.length; i++) {
        hash = name.charCodeAt(i) + ((hash << 5) - hash)
    }
    
    const index = Math.abs(hash) % themes.length
    return themes[index]
}

const maskPhone = (phone) => {
    if (!phone || phone.length < 7) return phone
    return phone.replace(/(\d{3})\d{4}(\d{4})/, '$1****$2')
}

const togglePassword = (row) => {
    row.showPassword = !row.showPassword
}

// Copy Email Logic
const copyEmail = async (row) => {
    if (!row.email) {
        ElMessage.info('该用户暂无邮箱')
        return
    }
    try {
        await navigator.clipboard.writeText(row.email)
        ElMessage.success({
            message: `已复制邮箱: ${row.email}`,
            duration: 2000
        })
    } catch (err) {
        ElMessage.error('复制失败，请手动复制')
    }
}

// --- API Calls ---
const fetchUsers = async () => {
    loading.value = true
    try {
        const params = {
            page: currentPage.value,
            page_size: pageSize.value,
            q: searchQuery.value || undefined,
            role: roleFilter.value || undefined,
            is_active: statusFilter.value
        }
        
        // Real API Call
        const res = await request.get('/users/', { params })
        
        // Adapt response
        const items = res.items || []
        total.value = res.total || 0
        
        userList.value = items.map(u => ({
            ...u,
            showPhone: false,
            showPassword: false
        }))
    } catch (error) {
        console.error(error)
        ElMessage.error('获取列表失败，请检查后端服务')
    } finally {
        loading.value = false
    }
}

const handleStatusChange = async (row) => {
    try {
        await request.put(`/users/${row.id}`, { is_active: row.is_active })
        ElMessage.success('状态更新成功')
    } catch (error) {
        row.is_active = !row.is_active 
        ElMessage.error('更新失败')
    }
}

const handleStatusFilter = (val) => {
    statusFilter.value = val
    fetchUsers()
}

const handlePageChange = (val) => {
    currentPage.value = val
    fetchUsers()
}

// --- Import ---
const importDialogVisible = ref(false)

const downloadTemplate = () => {
    window.location.href = '/api/users/template'
}

const handleImport = async (options) => {
    const formData = new FormData()
    formData.append('file', options.file)
    
    try {
        const res = await request.post('/users/import', formData, {
            headers: { 'Content-Type': 'multipart/form-data' }
        })
        ElMessage.success(res.message || '导入成功')
        importDialogVisible.value = false
        fetchUsers()
        fetchStats()
    } catch (error) {
        ElMessage.error('导入失败，请检查文件格式')
    }
}

// --- Form ---
const dialogVisible = ref(false)
const dialogType = ref('create')
const formRef = ref(null)
const form = reactive({
    id: null,
    name: '',
    username: '',
    email: '',
    phone: '',
    district: '',
    department: '',
    role: '参会人员',
    is_active: true,
    password: ''
})

const rules = {
    name: [{ required: true, message: '请输入姓名', trigger: 'blur' }],
    role: [{ required: true, message: '请选择角色', trigger: 'change' }],
}

const openDialog = (type, row = null) => {
    dialogType.value = type
    if(type === 'edit' && row) {
        Object.assign(form, row)
        form.password = '' 
    } else { // Reset
        form.id = null
        form.name = ''
        form.username = ''
        form.email = ''
        form.phone = ''
        form.district = ''
        form.department = ''
        form.role = '参会人员'
        form.is_active = true
        form.password = ''
    }
    dialogVisible.value = true
}

const handleSubmit = () => {
    formRef.value.validate(async (valid) => {
        if(!valid) return
        
        submitting.value = true
        try {
            const payload = { ...form }
            if(!payload.password) delete payload.password 
            // If create, need password. If backend generates one, user needs to know. 
            // Here we assume form has it.

            if(dialogType.value === 'create') {
                await request.post('/users/', payload)
                ElMessage.success('用户创建成功')
            } else {
                await request.put(`/users/${form.id}`, payload)
                ElMessage.success('用户更新成功')
            }
            dialogVisible.value = false
            fetchUsers()
        } catch (error) {
            ElMessage.error(error.response?.data?.detail || '操作失败')
        } finally {
            submitting.value = false
        }
    })
}

const handleDelete = (row) => {
    ElMessageBox.confirm(`确认删除用户 "${row.name || row.username}" 吗?`, '警告', {
        type: 'warning',
        confirmButtonText: '删除',
        cancelButtonText: '取消'
    }).then(async () => {
        try {
            await request.delete(`/users/${row.id}`)
            ElMessage.success('删除成功')
            fetchUsers()
        } catch (error) {
            ElMessage.error('删除失败')
        }
    })
}

onMounted(() => {
    fetchUsers()
    fetchStats()
})

</script>

<style scoped>
/* Removed padding from container to reduce gap, now relies on parent layout or minimal padding */
.user-manage-container {
  display: flex;
  flex-direction: column;
  gap: 24px;
  /* padding: 24px; REMOVED to match MeetingManage compact style if needed. 
     If MeetingManage has no padding on root, then this is fine. 
     If MeetingManage implies the parent 'router-view' container handles it, we follow suit.
     However, usually pages DO need padding. 
     The user requested "reduce margin with left sidebar". 
     Left sidebar is usually external to this view. 
     If the margin is "too big", it might be double padding.
     I'll set padding to 0 or small. 
  */
}

/* Page Header */
.page-header { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 4px; /* margin-bottom removed to prevent double gap */ }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main, #f8fafc); }
.header-divider { height: 24px; border-color: var(--border-color, #e2e8f0); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-main, #0f172a); line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary, #64748b); font-size: 14px; line-height: 1.4; }

/* Stats */
.stat-card {
  background: var(--card-bg, #ffffff);
  border-radius: 12px;
  padding: 20px;
  display: flex; align-items: center; position: relative; overflow: hidden;
  box-shadow: 0 1px 3px rgba(0,0,0,0.05);
  border: 1px solid var(--border-color, #e2e8f0);
}
.stat-icon-wrapper {
  width: 48px; height: 48px; border-radius: 12px; display: flex; align-items: center; justify-content: center;
  margin-right: 16px; background: rgba(0,0,0,0.03);
}
.text-blue { color: #3b82f6; } .text-green { color: #10b981; }
.text-purple { color: #8b5cf6; } .text-orange { color: #f59e0b; }
.stat-info { flex: 1; }
.stat-label { font-size: 13px; color: var(--text-secondary); margin-bottom: 4px; }
.stat-value { font-size: 24px; font-weight: 700; color: var(--text-main); display: flex; align-items: flex-end; gap: 8px; line-height: 1; }
.stat-trend { font-size: 12px; font-weight: 500; display: flex; align-items: center; }
.stat-trend.up { color: #10b981; } .stat-trend.down { color: #ef4444; }

/* Main Card */
.main-card {
  background: var(--card-bg, #ffffff);
  border-radius: 12px;
  padding: 24px;
  box-shadow: 0 1px 2px rgba(0,0,0,0.05);
  border: 1px solid var(--border-color, #e2e8f0);
}
.toolbar-header { display: flex; justify-content: space-between; align-items: center; margin-bottom: 24px; flex-wrap: wrap; gap: 16px; }
.toolbar-left { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
.search-input { width: 240px; }
.role-select { width: 140px; }
.status-tabs { background: var(--color-slate-100, #f1f5f9); padding: 4px; border-radius: 8px; display: flex; gap: 4px; }
.status-tabs span { padding: 4px 12px; font-size: 13px; border-radius: 6px; cursor: pointer; color: var(--text-secondary); transition: all 0.2s; }
.status-tabs span.active { background: white; color: var(--color-primary); font-weight: 600; box-shadow: 0 1px 2px rgba(0,0,0,0.05); }

/* Table */
.user-info-cell { display: flex; align-items: center; gap: 12px; padding: 4px 0; }
.user-avatar { font-weight: 600; border: 2px solid white; box-shadow: 0 2px 4px rgba(0,0,0,0.1); color: #fff; flex-shrink: 0;}
.user-details { display: flex; flex-direction: column; min-width: 0; }
.user-name { font-weight: 600; color: var(--text-main); font-size: 14px; }
/* .user-name:hover removed as it is no longer clickable */

.user-email { font-size: 12px; color: var(--text-secondary); margin-top: 2px; transition: all 0.2s; }
.clickable-email { cursor: pointer; }
.clickable-email:hover { color: var(--color-primary); text-decoration: underline; }

.font-mono { font-family: 'SF Mono', 'Roboto Mono', monospace; font-size: 13px; color: #64748b; }
.privacy-cell { display: flex; align-items: center; gap: 8px; font-family: monospace; }
.privacy-icon { cursor: pointer; color: var(--text-secondary); transition: color 0.2s; }
.privacy-icon:hover { color: var(--color-primary); }

.status-indicator {
  width: 8px; height: 8px; border-radius: 50%; display: inline-block; vertical-align: middle;
}
.status-indicator.active {
  background-color: #10b981;
  box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7);
  animation: pulse-green 2s infinite;
}
.status-indicator.inactive {
  background-color: #ef4444; 
  opacity: 0.6;
}
@keyframes pulse-green {
  0% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0.7); }
  70% { transform: scale(1); box-shadow: 0 0 0 6px rgba(16, 185, 129, 0); }
  100% { transform: scale(0.95); box-shadow: 0 0 0 0 rgba(16, 185, 129, 0); }
}

.last-login-cell {
  white-space: nowrap; 
  overflow: hidden; 
  text-overflow: ellipsis; 
  font-family: 'SF Mono', monospace; 
  font-size: 12px; 
  color: var(--text-secondary);
}

.action-buttons { opacity: 0.4; transition: opacity 0.2s; display: flex; justify-content: flex-end; gap: 8px; }
:deep(.el-table__row:hover) .action-buttons { opacity: 1; }
.pagination-wrapper { margin-top: 24px; display: flex; justify-content: flex-end; }
</style>
