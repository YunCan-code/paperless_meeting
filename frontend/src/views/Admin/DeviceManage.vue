<template>
  <div class="device-manage">
    <!-- Header -->
    <div class="page-header">
      <div class="header-left">
        <el-button class="collapse-btn" link @click="toggleSidebar">
          <el-icon size="24" color="#64748b">
             <Fold v-if="!isCollapse" />
             <Expand v-else />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        <div class="title-group">
          <h1 class="page-title">设备管理</h1>
          <p class="page-subtitle">监控连接设备与APP版本发布</p>
        </div>
      </div>
    </div>

    <el-card shadow="hover" class="version-overview-card" :body-style="{ padding: '16px 24px' }">
        <div class="v-card-flex" v-if="latestVersion">
            <!-- Left: Current Info -->
            <div class="v-main-info">
                 <div class="v-icon-box">
                     <el-icon><Iphone /></el-icon>
                 </div>
                 <div class="v-text-info">
                     <div class="v-label">当前最新版本</div>
                     <div class="v-number">{{ latestVersion.version_name }} <span class="v-code">(Build {{ latestVersion.version_code }})</span></div>
                     <div class="v-time">{{ new Date(latestVersion.created_at).toLocaleString() }}</div>
                 </div>
            </div>

            <!-- Middle: Release Notes -->
            <div class="v-notes-box">
                <div class="note-label">更新日志:</div>
                <div class="note-content">{{ latestVersion.release_notes || '暂无日志' }}</div>
            </div>

            <!-- Right: Actions -->
            <div class="v-actions">
                 <el-button @click="openHistory" size="default">
                    <el-icon class="mr-1"><List /></el-icon> 版本历史
                 </el-button>
                 <el-button type="primary" @click="openReleaseDialog" size="default">
                    <el-icon class="mr-1"><Upload /></el-icon> 发布新版
                 </el-button>
            </div>
        </div>
        
        <!-- Empty State -->
        <div class="v-card-empty" v-else>
             <div class="empty-left">
                 <el-icon class="empty-icon"><Iphone /></el-icon>
                 <div class="empty-text">
                     <div class="e-title">暂无发布版本</div>
                     <div class="e-desc">发布第一个版本以开始管理 APP 更新推送</div>
                 </div>
             </div>
             <div class="empty-actions">
                 <el-button @click="openHistory">
                    <el-icon class="mr-1"><List /></el-icon> 版本历史
                 </el-button>
                 <el-button type="primary" @click="openReleaseDialog">
                    <el-icon class="mr-1"><Upload /></el-icon> 立即发布
                 </el-button>
             </div>
        </div>
    </el-card>

    <!-- Version History Drawer -->
    <el-drawer v-model="historyDrawerVisible" title="APP 版本历史管理" size="600px">
        <el-table :data="versionList" stripe>
            <el-table-column prop="version_name" label="版本名" min-width="120">
                <template #default="{ row }">
                   <div style="font-weight: bold;">{{ row.version_name }}</div>
                   <div style="font-size: 12px; color: #999;">Code: {{ row.version_code }}</div>
                </template>
            </el-table-column>
            <el-table-column prop="created_at" label="发布时间" width="160">
                 <template #default="{ row }">
                     {{ new Date(row.created_at).toLocaleString() }}
                 </template>
            </el-table-column>
            <el-table-column prop="is_force_update" label="强制" width="70">
                 <template #default="{ row }">
                     <el-tag size="small" :type="row.is_force_update ? 'danger' : 'info'">{{ row.is_force_update ? '是' : '否' }}</el-tag>
                 </template>
            </el-table-column>
             <el-table-column label="操作" width="100">
                <template #default="{ row }">
                    <el-button type="danger" link :icon="Delete" @click="deleteVersion(row)">删除</el-button>
                </template>
            </el-table-column>
        </el-table>
        <template #footer>
           <el-divider>共 {{ versionList.length }} 个历史版本</el-divider>
        </template>
    </el-drawer>

    <!-- Device List Section -->
    <el-card shadow="hover" class="table-card">
       <template #header>
          <div class="card-header">
             <div class="header-left-group">
                <div class="header-title">
                   <el-icon class="mr-2 text-green-500"><Monitor /></el-icon>
                   设备列表
                   <span class="device-count" v-if="deviceList.length">({{ deviceList.length }})</span>
                </div>
                <!-- Filters -->
                 <div class="filter-group">
                    <el-input 
                        v-model="searchKeyword" 
                        placeholder="搜索别名/名称/IP" 
                        :prefix-icon="Search"
                        clearable
                        style="width: 200px" 
                    />
                    <el-select v-model="statusFilter" placeholder="状态筛选" style="width: 120px">
                        <el-option label="全部状态" value="all" />
                        <el-option label="在线" value="online" />
                        <el-option label="离线" value="offline" />
                    </el-select>
                 </div>
             </div>

             <div class="header-right-group">
                <el-button :loading="loading" circle @click="fetchDevices">
                   <el-icon><Refresh /></el-icon>
                </el-button>
             </div>
          </div>
          
          <!-- Batch Toolbar -->
          <transition name="el-fade-in-linear">
              <div class="batch-toolbar" v-if="selectedRows.length > 0">
                  <div class="batch-info">
                      <el-icon><Tools /></el-icon>
                      <span>已选择 {{ selectedRows.length }} 台设备</span>
                  </div>
                  <div class="batch-actions">
                      <el-button type="primary" size="small" plian @click="handleBatchUpdate">
                          批量更新APP
                      </el-button>
                      <el-button type="warning" size="small" plain @click="handleBatchRestart">
                          批量重启
                      </el-button>
                  </div>
              </div>
          </transition>
       </template>
       
       <el-table 
            :data="filteredDeviceList" 
            style="width: 100%" 
            v-loading="loading"
            @selection-change="handleSelectionChange"
        >
          <el-table-column type="selection" width="50" />
          
          <el-table-column label="设备信息" min-width="240">
              <template #default="{ row }">
                  <div class="device-info-cell">
                      <div class="device-icon-wrapper" :class="isOnline(row) ? 'online' : 'offline'">
                          <el-icon><Platform /></el-icon>
                      </div>
                      <div class="device-text-content">
                          <div class="info-primary">
                              <el-tooltip :content="row.alias ? `原名: ${row.name}` : ''" placement="top" :disabled="!row.alias">
                                  <span class="info-alias">{{ row.alias || row.name || '未命名设备' }}</span>
                              </el-tooltip>
                              <el-icon class="edit-btn" @click.stop="openEditAlias(row)"><Edit /></el-icon>
                          </div>
                          <div class="info-secondary">
                               <span class="info-model">{{ row.model || 'Unknown Model' }}</span>
                               <span class="divider" v-if="row.mac_address">•</span>
                               <span class="info-mac" v-if="row.mac_address">{{ row.mac_address }}</span>
                          </div>
                      </div>
                  </div>
              </template>
          </el-table-column>
          
          <el-table-column label="状态监控" min-width="180">
             <template #default="{ row }">
                 <div class="status-monitor">
                     <!-- Battery -->
                     <el-tooltip :content="`电量: ${row.battery_level}% ${row.is_charging ? '(充电中)' : ''}`" placement="top">
                        <div class="monitor-item" :class="{'text-red-500': row.battery_level < 20 && !row.is_charging}">
                           <el-icon :class="{'text-green-500': row.is_charging}">
                              <Lightning v-if="row.is_charging" />
                              <component :is="getBatteryIcon(row.battery_level)" v-else />
                           </el-icon>
                           <span>{{ row.battery_level !== null ? row.battery_level + '%' : '--' }}</span>
                        </div>
                     </el-tooltip>
                     
                     <!-- Storage -->
                     <el-tooltip :content="getStorageText(row)" placement="top">
                        <div class="monitor-item">
                           <el-icon><CopyDocument /></el-icon>
                           <span>{{ getstoragePercent(row) }}</span>
                        </div>
                     </el-tooltip>
                 </div>
             </template>
          </el-table-column>

          <el-table-column label="网络 / 版本" min-width="200">
              <template #default="{ row }">
                 <div class="col-group">
                    <div class="row-item ip-row">
                        <span class="label-icon"><Connection /></span>
                        <span class="ip-text">{{ row.ip_address || '--' }}</span>
                    </div>
                     <div class="row-item ver-row">
                         <div class="mini-badge" :class="getVersionStatus(row)">
                             <span class="dot"></span>
                             <span>{{ row.app_version || 'Initial' }}</span>
                         </div>
                         <el-tooltip v-if="getVersionStatus(row) === 'outdated'" content="点击批量更新" placement="right">
                             <el-icon class="update-icon text-red-500"><Upload /></el-icon>
                         </el-tooltip>
                    </div>
                 </div>
              </template>
          </el-table-column>
          
          <el-table-column label="活跃状态" width="160">
              <template #default="{ row }">
                 <div class="col-group">
                     <div class="status-row">
                         <span class="status-dot" :class="isOnline(row) ? 'online' : 'offline'"></span>
                         <span class="status-text">{{ isOnline(row) ? '在线' : '离线' }}</span>
                     </div>
                     <div class="time-sub">{{ getRelativeTime(row.last_active_at) }}</div>
                 </div>
              </template>
          </el-table-column>
           <el-table-column label="状态" width="80">
              <template #default="{ row }">
                 <el-tag :type="row.status === 'blocked' ? 'danger' : 'success'" size="small">
                    {{ row.status === 'blocked' ? '暂停' : '正常' }}
                 </el-tag>
              </template>
          </el-table-column>
          <el-table-column label="操作" width="120" fixed="right">
            <template #default="{ row }">
              <el-button 
                v-if="row.status !== 'blocked'"
                link 
                type="warning" 
                size="small" 
                @click="blockDevice(row)"
              >
               <el-icon class="mr-1"><VideoPause /></el-icon> 暂停
              </el-button>
              <el-button 
                v-else
                link 
                type="success" 
                size="small" 
                @click="unblockDevice(row)"
              >
                <el-icon class="mr-1"><VideoPlay /></el-icon> 恢复
              </el-button>
               <el-button
                 link
                 type="danger"
                 size="small"
                 @click="deleteDevice(row)"
               >删除</el-button>
            </template>
          </el-table-column>
       </el-table>
       
       <div class="table-footer" v-if="filteredDeviceList.length === 0">
           <el-empty description="没有找到匹配的设备" :image-size="60" />
       </div>
    </el-card>

    <!-- Release Dialog -->
    <el-dialog
       v-model="releaseDialogVisible"
       title="发布新版本"
       width="500px"
       destroy-on-close
    >
       <el-form :model="releaseForm" label-position="top" :rules="rules" ref="formRef">
           <el-row :gutter="20">
              <el-col :span="12">
                  <el-form-item label="版本号 (Version Code)" prop="version_code">
                     <el-input-number v-model="releaseForm.version_code" :min="1" style="width: 100%" />
                  </el-form-item>
              </el-col>
              <el-col :span="12">
                   <el-form-item label="版本名 (Version Name)" prop="version_name">
                     <el-input v-model="releaseForm.version_name" placeholder="e.g. 1.0.2" />
                  </el-form-item>
              </el-col>
           </el-row>
           
           <el-form-item label="更新日志" prop="release_notes">
              <el-input v-model="releaseForm.release_notes" type="textarea" rows="3" />
           </el-form-item>
           
           <el-form-item label="APK文件" required>
               <el-upload
                  action=""
                  :auto-upload="false"
                  :limit="1"
                  :on-change="handleFileChange"
                  :on-remove="() => releaseFile = null"
                  accept=".apk"
               >
                  <el-button type="primary" plain>选择APK文件</el-button>
               </el-upload>
           </el-form-item>
           
           <el-form-item>
              <el-checkbox v-model="releaseForm.is_force_update">强制更新</el-checkbox>
           </el-form-item>
       </el-form>
       <template #footer>
          <div class="dialog-footer">
             <el-button @click="releaseDialogVisible = false">取消</el-button>
             <el-button type="primary" @click="submitRelease" :loading="submitting">确认发布</el-button>
          </div>
       </template>
    </el-dialog>


    <!-- Alias Edit Dialog -->
    <el-dialog v-model="aliasDialogVisible" title="修改设备别名" width="400px">
        <el-form :model="aliasForm">
            <el-form-item label="设备别名">
                <el-input v-model="aliasForm.alias" placeholder="请输入方便记忆的名称" />
            </el-form-item>
        </el-form>
        <template #footer>
            <span class="dialog-footer">
                <el-button @click="aliasDialogVisible = false">取消</el-button>
                <el-button type="primary" @click="updateAlias">保存</el-button>
            </span>
        </template>
    </el-dialog>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive, computed } from 'vue'
import request from '@/utils/request'
import { useSidebar } from '@/composables/useSidebar'
import { Fold, Expand, View, Document, Refresh, Monitor, Iphone, Upload, Platform, Lightning, CopyDocument, Edit, Search, Connection, VideoPause, VideoPlay, Tools, Delete, Timer, List } from '@element-plus/icons-vue'
import { ElMessage, ElMessageBox } from 'element-plus'

const { isCollapse, toggleSidebar } = useSidebar()

const loading = ref(false)
const deviceList = ref([])
const latestVersion = ref(null)

// Filters & Selection
const searchKeyword = ref('')
const statusFilter = ref('all') // all, online, offline
const selectedRows = ref([])

const handleSelectionChange = (val) => {
    selectedRows.value = val
}

const filteredDeviceList = computed(() => {
    return deviceList.value.filter(item => {
        // Search
        const search = searchKeyword.value.toLowerCase()
        const matchSearch = !search || 
            (item.name && item.name.toLowerCase().includes(search)) || 
            (item.alias && item.alias.toLowerCase().includes(search)) ||
            (item.ip_address && item.ip_address.includes(search))
        
        // Status Filter
        let matchStatus = true
        if (statusFilter.value === 'online') {
            matchStatus = isOnline(item)
        } else if (statusFilter.value === 'offline') {
            matchStatus = !isOnline(item)
        }

        return matchSearch && matchStatus
    })
})

// History Management
const historyDrawerVisible = ref(false)
const versionList = ref([])

const openHistory = async () => {
    historyDrawerVisible.value = true
    await fetchHistory()
}

const fetchHistory = async () => {
    try {
        versionList.value = await request.get('/updates/')
    } catch(e) {
        ElMessage.error('获取历史版本失败')
    }
}

const deleteVersion = async (row) => {
    try {
        await ElMessageBox.confirm('确定删除该版本记录及文件吗?', '警告', { type: 'warning' })
        await request.delete(`/updates/${row.id}`)
        ElMessage.success('删除成功')
        if (latestVersion.value && latestVersion.value.id === row.id) {
            fetchLatestVersion() // Refresh latest if we deleted it
        }
        fetchHistory()
    } catch(e) {}
}

// Batch Command Handlers
const handleBatchUpdate = async () => {
    if (selectedRows.value.length === 0) return
    
    try {
        await ElMessageBox.confirm(
            `确定向 ${selectedRows.value.length} 台设备发送更新指令吗?`, 
            '批量更新APP', 
            { type: 'info' }
        )
        
        const deviceIds = selectedRows.value.map(row => row.device_id)
        await request.post('/devices/commands', {
            device_ids: deviceIds,
            command_type: 'update_app'
        })
        
        ElMessage.success(`已向 ${selectedRows.value.length} 台设备发送更新指令`)
    } catch(e) {
        if (e !== 'cancel') {
            ElMessage.error('发送指令失败')
        }
    }
}

const handleBatchRestart = async () => {
    if (selectedRows.value.length === 0) return
    
    try {
        await ElMessageBox.confirm(
            `确定向 ${selectedRows.value.length} 台设备发送重启指令吗?`, 
            '批量重启', 
            { type: 'warning' }
        )
        
        const deviceIds = selectedRows.value.map(row => row.device_id)
        await request.post('/devices/commands', {
            device_ids: deviceIds,
            command_type: 'restart'
        })
        
        ElMessage.success(`已向 ${selectedRows.value.length} 台设备发送重启指令`)
    } catch(e) {
        if (e !== 'cancel') {
            ElMessage.error('发送指令失败')
        }
    }
}

const releaseDialogVisible = ref(false)
const submitting = ref(false)
const releaseFile = ref(null)
const formRef = ref(null)

// Alias Edit
const aliasDialogVisible = ref(false)
const aliasForm = reactive({
    id: null,
    alias: ''
})

const openEditAlias = (row) => {
    aliasForm.id = row.id
    aliasForm.alias = row.alias || ''
    aliasDialogVisible.value = true
}

const updateAlias = async () => {
    if(!aliasForm.id) return
    try {
        await request.put(`/devices/${aliasForm.id}`, {
            alias: aliasForm.alias
        })
        ElMessage.success('修改成功')
        aliasDialogVisible.value = false
        // Update local list
        const d = deviceList.value.find(d => d.id === aliasForm.id)
        if(d) d.alias = aliasForm.alias
    } catch(e) {
        ElMessage.error('修改失败')
    }
}

const releaseForm = reactive({
    version_code: 1,
    version_name: '',
    release_notes: '',
    is_force_update: false
})

const rules = {
    version_code: [{ required: true, message: '必填', trigger: 'blur' }],
    version_name: [{ required: true, message: '必填', trigger: 'blur' }],
}

const fetchLatestVersion = async () => {
    try {
        latestVersion.value = await request.get('/updates/latest')
        if (latestVersion.value) {
            releaseForm.version_code = latestVersion.value.version_code + 1
        }
    } catch(e) {}
}

const fetchDevices = async () => {
    loading.value = true
    try {
        deviceList.value = await request.get('/devices/')
    } catch (e) {
        ElMessage.error('获取设备列表失败')
    } finally {
        loading.value = false
    }
}

const isOutdated = (currentVer) => {
    if(!latestVersion.value || !currentVer) return false
    return currentVer !== latestVersion.value.version_name
}

const openReleaseDialog = () => {
    releaseDialogVisible.value = true
}

const handleFileChange = (file) => {
    releaseFile.value = file.raw
}

const submitRelease = async () => {
    if (!releaseFile.value) {
        return ElMessage.warning('请选择APK文件')
    }
    await formRef.value.validate(async (valid) => {
        if(valid) {
            submitting.value = true
            try {
                const formData = new FormData()
                formData.append('version_code', releaseForm.version_code)
                formData.append('version_name', releaseForm.version_name)
                formData.append('release_notes', releaseForm.release_notes)
                formData.append('is_force_update', releaseForm.is_force_update)
                formData.append('file', releaseFile.value)
                
                await request.post('/updates/', formData, {
                    headers: { 'Content-Type': 'multipart/form-data' }
                })
                ElMessage.success('发布成功')
                releaseDialogVisible.value = false
                fetchLatestVersion()
            } catch(e) {
                ElMessage.error('发布失败')
            } finally {
                submitting.value = false
            }
        }
    })
}

const blockDevice = async (row) => {
    await request.put(`/devices/${row.id}/block`)
    row.status = 'blocked'
    ElMessage.success('设备已禁用')
}
const unblockDevice = async (row) => {
    await request.put(`/devices/${row.id}/unblock`)
    row.status = 'active'
    ElMessage.success('设备已启用')
}
const deleteDevice = async (row) => {
    try {
        await ElMessageBox.confirm('确定删除该设备记录吗?', '提示', { type: 'warning' })
        await request.delete(`/devices/${row.id}`)
        fetchDevices()
        ElMessage.success('删除成功')
    } catch(e) {}
}

const formatTime = (isoStr) => {
    if(!isoStr) return '--'
    const d = new Date(isoStr)
    return d.toLocaleString(undefined, {
       month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit'
    })
}

const isOnline = (row) => {
    if(!row.last_active_at) return false
    const last = new Date(row.last_active_at).getTime()
    const now = Date.now()
    // < 5 mins
    return (now - last) < 5 * 60 * 1000
}

const getBatteryIcon = (level) => {
    return Iphone // Return the component object directly
}

const getStorageText = (row) => {
    if (!row.storage_total) return '未知'
    const avail = formatBytes(row.storage_available)
    const total = formatBytes(row.storage_total)
    return `可用: ${avail} / 总共: ${total}`
}

const getstoragePercent = (row) => {
     if (!row.storage_total) return '--'
     const used = row.storage_total - (row.storage_available || 0)
     const p = Math.round((used / row.storage_total) * 100)
     return `${p}% 已用`
}

const formatBytes = (bytes) => {
   if (bytes === 0) return '0 B'
   const k = 1024
   const sizes = ['B', 'KB', 'MB', 'GB']
   const i = Math.floor(Math.log(bytes) / Math.log(k))
   return parseFloat((bytes / Math.pow(k, i)).toFixed(1)) + ' ' + sizes[i]
}

const getRelativeTime = (isoStr) => {
    if(!isoStr) return '从未'
    const date = new Date(isoStr)
    const now = new Date()
    const diff = now - date
    
    if (diff < 60 * 1000) return '刚刚'
    if (diff < 60 * 60 * 1000) return `${Math.floor(diff / (60 * 1000))}分钟前`
    if (diff < 24 * 60 * 60 * 1000) return `${Math.floor(diff / (60 * 60 * 1000))}小时前`
    if (diff < 7 * 24 * 60 * 60 * 1000) return `${Math.floor(diff / (24 * 60 * 60 * 1000))}天前`
    
    return formatTime(isoStr).split(' ')[0] // Return Date only
}

const getVersionStatus = (row) => {
    if (!latestVersion.value || !row.app_version) return 'unknown'
    return row.app_version === latestVersion.value.version_name ? 'latest' : 'outdated'
}

onMounted(() => {
    fetchLatestVersion()
    fetchDevices()
})
</script>

<style scoped>
.device-manage {
  display: flex;
  flex-direction: column;
  gap: 24px;
}
.page-header { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 4px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; height: auto; transition: background-color 0.2s; }
.collapse-btn:hover { background-color: var(--bg-main); }
.header-divider { height: 24px; border-color: var(--border-color); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-main); line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 14px; line-height: 1.4; }

/* Filters & Header */
.card-header { display: flex; justify-content: space-between; align-items: flex-end; }
.header-left-group { display: flex; flex-direction: column; gap: 12px; }
.header-right-group { display: flex; align-items: flex-end; }
.header-title { display: flex; align-items: center; font-size: 16px; font-weight: 600; color: var(--text-main); }
.device-count { font-size: 14px; color: #94a3b8; font-weight: 400; margin-left: 8px; }

.filter-group { display: flex; gap: 12px; align-items: center; }

/* Batch Toolbar */
.batch-toolbar { 
   margin-top: 16px; 
   padding: 8px 16px; 
   background: var(--bg-main); 
   border-radius: 8px; 
   border: 1px solid #e2e8f0; 
   display: flex; 
   justify-content: space-between; 
   align-items: center;
}
.batch-info { display: flex; align-items: center; gap: 8px; font-size: 13px; color: var(--text-main); font-weight: 500; }
.batch-actions { display: flex; gap: 12px; }

/* Device Info Column */
.device-info-cell { display: flex; align-items: center; gap: 12px; }

.device-icon-wrapper { 
    width: 40px; height: 40px; border-radius: 8px; 
    display: flex; align-items: center; justify-content: center; 
    font-size: 20px;
    transition: all 0.3s;
}
.device-icon-wrapper.online { background: #dcfce7; color: #166534; }
.device-icon-wrapper.offline { background: #f1f5f9; color: #94a3b8; }

.device-text-content { display: flex; flex-direction: column; gap: 2px; }

.info-primary { display: flex; align-items: center; gap: 8px; }
.info-alias { font-weight: 600; color: var(--text-main); font-size: 14px; }
.edit-btn { 
    cursor: pointer; color: #94a3b8; font-size: 14px; opacity: 0; transition: all 0.2s; 
}
.device-info-cell:hover .edit-btn { opacity: 1; }
.edit-btn:hover { color: #3b82f6; transform: scale(1.1); }

.info-secondary { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--text-secondary); }
.info-mac { font-family: monospace; opacity: 0.8; }
.divider { color: #cbd5e1; font-weight: bold; }

/* Network & Version & Active Columns */
.col-group { display: flex; flex-direction: column; gap: 6px; }
.row-item { display: flex; align-items: center; gap: 8px; }

.ip-text { font-family: 'JetBrains Mono', Consolas, monospace; font-size: 13px; color: var(--text-main); font-weight: 500; }
.label-icon { color: #94a3b8; display: flex; align-items: center; font-size: 14px; }

.mini-badge { 
    display: inline-flex; align-items: center; gap: 4px; 
    padding: 2px 8px; border-radius: 4px; 
    font-size: 12px; font-weight: 500; 
    transition: all 0.2s;
}
.mini-badge.latest { background: #f0fdf4; color: #15803d; border: 1px solid #bbf7d0; }
.mini-badge.outdated { background: #fff7ed; color: #c2410c; border: 1px solid #fed7aa; }
.mini-badge.unknown { background: #f8fafc; color: #64748b; border: 1px solid #e2e8f0; }

.dot { width: 6px; height: 6px; border-radius: 50%; background: currentColor; }
.update-icon { cursor: pointer; font-size: 14px; animation: bounce 2s infinite; }

.status-row { display: flex; align-items: center; gap: 8px; }
.status-text { font-weight: 600; font-size: 14px; color: var(--text-main); }
.status-dot { width: 8px; height: 8px; border-radius: 50%; background: #cbd5e1; }
.status-dot.online { background: #22c55e; box-shadow: 0 0 0 2px rgba(34, 197, 94, 0.2); }

.time-sub { font-size: 12px; color: #94a3b8; padding-left: 16px; }

@keyframes bounce {
  0%, 100% { transform: translateY(0); }
  50% { transform: translateY(-2px); }
}

.table-footer { padding: 40px; display: flex; justify-content: center; }

/* Common Helpers */
.mr-2 { margin-right: 8px; }
.mr-1 { margin-right: 4px; }
.text-green-500 { color: #22c55e; }
.text-red-500 { color: #ef4444; }
.text-blue-500 { color: #3b82f6; }
.text-gray-400 { color: #94a3b8; }
.text-green-500 { color: #22c55e; }

/* Version Card Re-design */
.version-overview-card { border-radius: 12px; }
.v-card-flex { display: flex; align-items: center; justify-content: space-between; gap: 32px; flex-wrap: wrap; }

.v-main-info { display: flex; align-items: center; gap: 16px; min-width: 240px; }
.v-icon-box { 
    width: 48px; height: 48px; border-radius: 12px; 
    background: linear-gradient(135deg, #3b82f6, #2563eb); 
    display: flex; align-items: center; justify-content: center; 
    color: white; font-size: 24px; box-shadow: 0 4px 6px -1px rgba(59, 130, 246, 0.3);
}
.v-text-info { display: flex; flex-direction: column; }
.v-label { font-size: 12px; color: var(--text-secondary); text-transform: uppercase; letter-spacing: 0.5px; }
.v-number { font-size: 20px; font-weight: 700; color: var(--text-main); line-height: 1.2; }
.v-code { font-size: 13px; font-weight: 400; color: var(--text-secondary); margin-left: 4px; }
.v-time { font-size: 12px; color: #94a3b8; margin-top: 2px; }

.v-notes-box { 
    flex: 1; 
    background: var(--el-fill-color-lighter); 
    border-radius: 8px; 
    padding: 12px 16px; 
    border-left: 3px solid var(--el-border-color);
    min-height: 50px;
    display: flex; flex-direction: column; justify-content: center;
}
.note-label { font-size: 11px; font-weight: 600; color: #64748b; margin-bottom: 2px; }
.note-content { font-size: 13px; color: #334155; line-height: 1.4; display: -webkit-box; -webkit-line-clamp: 2; -webkit-box-orient: vertical; overflow: hidden; }

.v-actions { display: flex; gap: 12px; align-items: center; }

/* Empty State Card */
.v-card-empty { 
    display: flex; align-items: center; justify-content: space-between; 
    padding: 8px 0;
}
.empty-left { display: flex; align-items: center; gap: 16px; }
.empty-icon { 
    font-size: 32px; color: #cbd5e1; 
    background: #f1f5f9; padding: 12px; border-radius: 12px; 
}
.empty-text { display: flex; flex-direction: column; }
.e-title { font-size: 16px; font-weight: 600; color: var(--text-main); }
.e-desc { font-size: 13px; color: var(--text-secondary); margin-top: 2px; }
.empty-actions { display: flex; gap: 12px; }

</style>
