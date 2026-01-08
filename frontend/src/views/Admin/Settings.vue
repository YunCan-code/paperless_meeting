<template>
  <div class="settings-manage">
    <!-- 页面头部 -->
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
          <h1 class="page-title">系统设置</h1>
          <p class="page-subtitle">配置系统全局参数与规则</p>
        </div>
      </div>
    </div>

    <!-- 主要内容区域 -->
    <div class="settings-content">
      <el-row :gutter="24">
        <el-col :span="16" :xs="24">
          <!-- 会议默认配置卡片 -->
          <el-card shadow="hover" class="setting-card">
            <template #header>
              <div class="card-header">
                <div class="header-icon bg-blue-50 text-blue-500">
                  <el-icon><Location /></el-icon>
                </div>
                <div class="header-title">
                  <h3>会议默认配置</h3>
                  <p>设置发起新会议时的默认参数</p>
                </div>
              </div>
            </template>
            
            <el-form label-position="top" class="setting-form">
              <el-form-item label="默认会议地点">
                <el-input 
                  v-model="settings.default_meeting_location" 
                  placeholder="例如：第一会议室" 
                  clearable
                />
                <div class="form-help">发起新会议时将自动填充此地点。</div>
              </el-form-item>
            </el-form>
          </el-card>

          <!-- 安全与可见性卡片 -->
          <el-card shadow="hover" class="setting-card" style="margin-top: 24px;">
            <template #header>
              <div class="card-header">
                <div class="header-icon bg-orange-50 text-orange-500">
                  <el-icon><Hide /></el-icon>
                </div>
                <div class="header-title">
                  <h3>会议可见性 (安全)</h3>
                  <p>控制历史会议在终端设备上的保留时间</p>
                </div>
              </div>
            </template>
            
            <el-form label-position="top" class="setting-form">
              <el-form-item label="会议可见时限 (小时)">
                <el-input-number 
                  v-model="settings.meeting_visibility_hide_after_hours" 
                  :min="0" 
                  :max="8760"
                  controls-position="right"
                  style="width: 100%;"
                />
                <div class="form-help">
                  <el-icon class="help-icon"><InfoFilled /></el-icon>
                  设置会议在开始后多少小时自动对普通终端隐藏。
                  <br/>
                  <span class="highlight">设置为 0 表示永不隐藏。</span>
                  建议设置为 72 (3天) 或 168 (1周)。
                </div>
              </el-form-item>
            </el-form>
          </el-card>
        </el-col>

        <!-- 右侧操作栏 -->
        <el-col :span="8" :xs="24">
          <el-card shadow="never" class="action-card">
            <div class="action-summary">
              <h4>保存更改</h4>
              <p>所有更改将立即对系统生效，请谨慎操作。</p>
            </div>
            <el-button type="primary" size="large" @click="saveSettings" :loading="saving" style="width: 100%;">
              保存所有设置
            </el-button>
          </el-card>
        </el-col>
      </el-row>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Fold, Expand, Location, Hide, InfoFilled } from '@element-plus/icons-vue'
import request from '@/utils/request'
import { useSidebar } from '@/composables/useSidebar'

const { isCollapse, toggleSidebar } = useSidebar()
const saving = ref(false)

// Data Model matches backend SystemSetting keys
const settings = ref({
  default_meeting_location: '',
  meeting_visibility_hide_after_hours: 0
})

const fetchSettings = async () => {
  try {
    const res = await request.get('/settings/')
    // Merge result into settings
    if (res) {
      settings.value.default_meeting_location = res.default_meeting_location || ''
      settings.value.meeting_visibility_hide_after_hours = res.meeting_visibility_hide_after_hours 
        ? parseInt(res.meeting_visibility_hide_after_hours) 
        : 0
    }
  } catch (e) {
    ElMessage.error('加载设置失败')
  }
}

const saveSettings = async () => {
  try {
    saving.value = true
    // Convert numbers to strings for backend storage
    const payload = {
      default_meeting_location: settings.value.default_meeting_location,
      meeting_visibility_hide_after_hours: String(settings.value.meeting_visibility_hide_after_hours)
    }
    
    await request.post('/settings/', payload)
    
    // Also sync localStorage for fallback/legacy compatibility if needed
    localStorage.setItem('defaultMeetingLocation', settings.value.default_meeting_location)
    
    ElMessage.success('设置已保存')
  } catch (e) {
    ElMessage.error('保存失败')
  } finally {
    saving.value = false
  }
}

onMounted(() => {
  fetchSettings()
})
</script>

<style scoped>
.settings-manage {
  display: flex;
  flex-direction: column;
  gap: 24px;
  max-width: 1200px;
  margin: 0 auto;
}

/* Page Header - Consistent with MeetingManage */
.page-header { display: flex; justify-content: space-between; align-items: flex-end; padding: 0 4px; }
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main); }
.header-divider { height: 24px; border-color: var(--border-color); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title { margin: 0; font-size: 24px; font-weight: 600; color: var(--text-main); line-height: 1.2; }
.page-subtitle { margin: 4px 0 0; color: var(--text-secondary); font-size: 14px; line-height: 1.4; }

/* Cards */
.setting-card {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  overflow: visible; /* For shadows */
}

.card-header {
  display: flex;
  align-items: center;
  gap: 16px;
}
.header-icon {
  width: 40px; height: 40px;
  border-radius: 10px;
  display: flex; align-items: center; justify-content: center;
  font-size: 20px;
}
.header-title h3 { margin: 0; font-size: 16px; font-weight: 600; color: var(--text-main); }
.header-title p { margin: 2px 0 0; font-size: 13px; color: var(--text-secondary); }

/* Form Styles */
.setting-form { padding: 8px 0; }
.form-help {
  font-size: 13px;
  color: #94a3b8;
  margin-top: 8px;
  line-height: 1.5;
}
.highlight { color: #f59e0b; font-weight: 500; }
.help-icon { vertical-align: text-bottom; margin-right: 4px; }

/* Action Card */
.action-card {
  border-radius: 12px;
  border: 1px solid var(--border-color);
  background: var(--card-bg);
  position: sticky;
  top: 24px;
}
.action-summary { margin-bottom: 20px; }
.action-summary h4 { margin: 0 0 8px; font-size: 16px; color: var(--text-main); }
.action-summary p { margin: 0; font-size: 13px; color: var(--text-secondary); line-height: 1.4; }

/* Colors Utility */
.bg-blue-50 { background-color: #eff6ff; } .text-blue-500 { color: #3b82f6; }
.bg-orange-50 { background-color: #fff7ed; } .text-orange-500 { color: #f97316; }
</style>
