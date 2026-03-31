<template>
  <div class="toolbox-page">
    <div class="page-header">
      <div class="header-left">
        <el-button class="collapse-btn" link @click="toggleSidebar">
          <el-icon size="24" color="#64748b">
            <component :is="isCollapse ? 'Expand' : 'Fold'" />
          </el-icon>
        </el-button>
        <el-divider direction="vertical" class="header-divider" />
        <div class="title-group">
          <h1 class="page-title">快捷功能</h1>
          <p class="page-subtitle">常用会议互动与封面管理入口集合</p>
        </div>
      </div>
    </div>

    <el-row :gutter="20" class="toolbox-grid">
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="tool in tools" :key="tool.id">
        <el-card 
          class="tool-card" 
          shadow="hover" 
          @click="handleToolClick(tool)"
        >
          <div class="tool-icon" :style="{ background: tool.bgColor, color: tool.color }">
            <el-icon :size="32">
              <component :is="tool.icon" />
            </el-icon>
          </div>
          <div class="tool-info">
            <h3 class="tool-title">{{ tool.title }}</h3>
            <p class="tool-desc">{{ tool.desc }}</p>
          </div>
          <div class="tool-action">
            <el-icon><ArrowRight /></el-icon>
          </div>
        </el-card>
      </el-col>
    </el-row>

  </div>
</template>

<script setup>
import { useRouter } from 'vue-router'
import { 
  ChatLineRound, CircleCheck, Notebook,
  ArrowRight, Fold, Expand, PictureFilled
} from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import { ElMessage } from 'element-plus'

const { isCollapse, toggleSidebar } = useSidebar()
const router = useRouter()

// Tools Configuration
const tools = [
  {
    id: 'interaction-center',
    title: '会议互动中心',
    desc: '统一管理投票、抽签、大屏控制与互动历史',
    icon: 'ChatLineRound',
    bgColor: '#eff6ff',
    color: '#2563eb'
  },
  {
    id: 'followup',
    title: '笔记',
    desc: '查看和管理会议笔记（仅 Web 端使用）',
    icon: 'Notebook',
    bgColor: '#f5f3ff', // violet-50
    color: '#8b5cf6'    // violet-500
  },
  {
    id: 'cover-center',
    title: '封面中心',
    desc: '统一管理默认封面、类型封面与登录海报',
    icon: 'PictureFilled',
    bgColor: '#ecfeff',
    color: '#0891b2'
  },
  {
    id: 'signin',
    title: '签到',
    desc: '功能未完成，敬请期待',
    icon: 'CircleCheck',
    bgColor: '#f0fdf4', // green-50
    color: '#22c55e'    // green-500
  }
]

const handleToolClick = (tool) => {
  if (tool.id === 'followup') {
    router.push('/admin/followup')
  } else if (tool.id === 'cover-center') {
    router.push('/admin/toolbox/covers')
  } else if (tool.id === 'interaction-center') {
    router.push('/admin/toolbox/interactions')
  } else {
    ElMessage.info('该功能正在开发中，敬请期待')
  }
}

</script>

<style scoped>
.toolbox-page {
  padding: 0;
}
.page-header {
  display: flex;
  justify-content: space-between;
  align-items: flex-end;
  margin-bottom: 24px;
  padding: 0 4px;
}
.header-left { display: flex; align-items: center; gap: 12px; }
.collapse-btn { padding: 8px; border-radius: 8px; transition: background-color 0.2s; height: auto; }
.collapse-btn:hover { background-color: var(--bg-main, #f8fafc); }
.header-divider { height: 24px; border-color: var(--border-color, #e2e8f0); margin: 0 4px; }
.title-group { display: flex; flex-direction: column; }
.page-title {
  margin: 0;
  font-size: 24px;
  font-weight: 600;
  color: var(--text-main, #0f172a);
  line-height: 1.2;
}
.page-subtitle {
  margin: 4px 0 0;
  color: var(--text-secondary, #64748b);
  font-size: 14px;
  line-height: 1.4;
}

.toolbox-grid {
  row-gap: 20px;
}

.toolbox-grid > :deep(.el-col) {
  display: flex;
}

.tool-card {
  border: none;
  border-radius: 16px;
  cursor: pointer;
  transition: all 0.3s ease;
  height: 100%;
  width: 100%;
  position: relative;
  overflow: hidden;
  background-color: var(--card-bg);
}

.tool-card:hover {
  transform: translateY(-4px);
  box-shadow: 0 10px 15px -3px rgba(0, 0, 0, 0.1), 0 4px 6px -2px rgba(0, 0, 0, 0.05);
}

.tool-card :deep(.el-card__body) {
  padding: 24px;
  display: flex;
  align-items: center;
  gap: 20px;
}

.tool-icon {
  width: 64px;
  height: 64px;
  border-radius: 16px;
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.tool-info {
  flex: 1;
}

.tool-title {
  margin: 0 0 8px 0;
  font-size: 18px;
  font-weight: 600;
  color: var(--text-main);
}

.tool-desc {
  margin: 0;
  font-size: 13px;
  color: var(--text-secondary);
  line-height: 1.5;
}

.tool-action {
  opacity: 0;
  transform: translateX(-10px);
  transition: all 0.3s ease;
  color: var(--text-secondary);
}

.tool-card:hover .tool-action {
  opacity: 1;
  transform: translateX(0);
}

</style>
