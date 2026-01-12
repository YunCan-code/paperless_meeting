<template>
  <div class="common-layout">
    <!-- Mobile Backdrop -->
    <div 
      class="mobile-backdrop" 
      v-if="!isCollapse && isMobile" 
      @click="toggleCollapse"
    ></div>

    <el-container>
      <el-aside :width="isCollapse ? '64px' : '260px'" class="custom-aside" :class="{ 'collapsed': isCollapse }">
        <div class="sidebar-header" @click="$router.push('/')" style="cursor: pointer;">
          <div class="logo-icon">
            <el-icon><Monitor /></el-icon>
          </div>
          <div class="logo-text" v-show="!isCollapse">
            <h1>无纸化</h1>
            <p>会议系统</p>
          </div>
        </div>


        <el-menu
          router
          :default-active="$route.path"
          class="custom-menu"
          :collapse="isCollapse"
          :collapse-transition="false"
        >

          <el-menu-item index="/admin/meetings">
            <el-icon><Calendar /></el-icon>
            <template #title>会议管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/users">
            <el-icon><User /></el-icon>
            <template #title>人员管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/types">
            <el-icon><List /></el-icon>
            <template #title>类型管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/devices">
            <el-icon><Cellphone /></el-icon>
            <template #title>设备管理</template>
          </el-menu-item>
          <el-menu-item index="/admin/toolbox">
            <el-icon><MagicStick /></el-icon>
            <template #title>快捷功能</template>
          </el-menu-item>
          <el-menu-item index="/admin/followup">
            <el-icon><Notebook /></el-icon>
            <template #title>笔记</template>
          </el-menu-item>
          <div class="menu-divider"></div>
          
          <!-- Theme Toggle Button -->
          <div class="theme-toggle-wrapper" @click="toggleDark">
            <el-icon :size="20">
              <MoonNight v-if="!isDark" />
              <Sunrise v-else />
            </el-icon>
            <span v-show="!isCollapse" class="theme-toggle-label">{{ isDark ? '浅色模式' : '深色模式' }}</span>
          </div>
          
          <el-menu-item index="/admin/settings">
            <el-icon><Setting /></el-icon>
            <template #title>系统设置</template>
          </el-menu-item>
        </el-menu>
        
        <!-- Sidebar Footer Removed -->
      </el-aside>
      <el-main class="custom-main">
        <router-view></router-view>
      </el-main>
    </el-container>
  </div>
</template>

<script setup>
import { Calendar, User, List, Monitor, Fold, Expand, Setting, Notebook, Cellphone, MoonNight, Sunrise, PieChart, MagicStick } from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'
import { useTheme } from '@/composables/useTheme'
import { ref, onMounted, onUnmounted } from 'vue'

// 使用全局侧边栏状态，与子页面共享
const { isCollapse, toggleSidebar: toggleCollapse } = useSidebar()

// 使用主题切换
const { isDark, toggleDark } = useTheme()

const isMobile = ref(false)

const checkMobile = () => {
  const mobile = window.innerWidth <= 768
  if (mobile !== isMobile.value) {
    isMobile.value = mobile
    // If switching to mobile, collapse automatically
    if (mobile) {
        if (!isCollapse.value) toggleCollapse()
    }
  }
}

onMounted(() => {
  checkMobile()
  window.addEventListener('resize', checkMobile)
})

onUnmounted(() => {
  window.removeEventListener('resize', checkMobile)
})
</script>

<style scoped>
.common-layout, .el-container {
  height: 100vh;
}

/* Sidebar Background */
.custom-aside {
  background: var(--bg-sidebar);
  border-right: 1px solid var(--border-color-layout);
  display: flex;
  flex-direction: column;
  transition: width 0.3s ease, transform 0.3s ease;
  overflow: hidden;
  z-index: 100;
}

/* Mobile Styles */
@media (max-width: 768px) {
  .custom-aside {
    position: fixed;
    z-index: 1001;
    height: 100vh;
    left: 0;
    top: 0;
    /* Force fixed width on mobile for proper drawer usage */
    width: 260px !important; 
    transform: translateX(0);
    box-shadow: 4px 0 16px rgba(0,0,0,0.1);
  }

  .custom-aside.collapsed {
    width: 260px !important;
    transform: translateX(-100%);
  }

  /* When collapsed on mobile, the main logo should be hidden in header if it relies on width, 
     but since we translate out, it doesn't matter. */
  
  .mobile-backdrop {
    position: fixed;
    inset: 0;
    background: rgba(0, 0, 0, 0.5);
    z-index: 1000;
    backdrop-filter: blur(2px);
    animation: fadeIn 0.3s ease;
  }

  .custom-main {
    padding: 16px;
  }
}

@keyframes fadeIn {
  from { opacity: 0; }
  to { opacity: 1; }
}


/* Header */
.sidebar-header {
  height: 64px;
  padding: 0 16px;
  display: flex;
  align-items: center;
  gap: 12px;
  border-bottom: 1px solid var(--border-color-layout);
}
.custom-aside.collapsed .sidebar-header {
  justify-content: center;
  padding: 0;
}

.logo-icon {
  width: 36px;
  height: 36px;
  flex-shrink: 0;
  background: linear-gradient(135deg, var(--color-primary), #06b6d4);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: white;
  font-size: 18px;
}
.logo-text {
  flex: 1;
  overflow: hidden;
  white-space: nowrap;
}
.logo-text h1 {
  margin: 0;
  font-size: 16px;
  font-weight: 700;
  color: var(--text-main);
}
.logo-text p {
  margin: 0;
  font-size: 11px;
  color: var(--text-secondary);
}

/* Collapse Trigger in Header */
.collapse-trigger {
  display: flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  cursor: pointer;
  border-radius: 6px;
  color: var(--color-slate-400);
  transition: all 0.2s;
}
.collapse-trigger:hover {
  background-color: var(--color-slate-100);
  color: var(--color-slate-700);
}
.collapse-trigger.centered {
  margin: 8px auto;
}

/* Menu */
.custom-menu {
  background: transparent;
  border-right: none;
  flex: 1;
  padding: 8px 12px;
  display: flex;
  flex-direction: column;
}
.menu-divider {
  flex: 1;
}
.custom-aside.collapsed .custom-menu {
  padding: 8px 4px;
}

/* Sidebar Footer */
.sidebar-footer {
  padding: 16px;
  border-top: 1px solid var(--border-color-layout);
  display: flex;
  justify-content: center; 
}

/* Main Content */
.custom-main {
  background-color: var(--bg-main);
  padding: 24px;
}

/* Global Manual Overrides (if any remain) */
/* (Most are now handled by variables above) */
:deep(.el-menu-item) {
  height: 40px;
  line-height: 40px;
  border-radius: 6px;
  margin-bottom: 2px;
  color: var(--color-slate-600);
  font-weight: 500;
}
:deep(.el-menu-item:hover) {
  background-color: var(--color-slate-100);
  color: var(--color-slate-900);
}
:deep(.el-menu-item.is-active) {
  background-color: #eff6ff !important;
  color: #1d4ed8 !important; /* Deeper blue */
  font-weight: 700;
  box-shadow: inset 3px 0 0 #1d4ed8; /* Left border effect */
}
:deep(.el-menu-item .el-icon) {
  margin-right: 10px;
  font-size: 18px;
}
:deep(.el-menu--collapse .el-menu-item .el-icon) {
  margin: 0;
}

/* Main Content Styles Removed Duplicate */

/* Theme Toggle Button - Match el-menu-item exactly */
.theme-toggle-wrapper {
  display: flex;
  align-items: center;
  height: 40px;
  padding: 0 20px;
  margin: 0 0 2px 0;
  border-radius: 6px;
  cursor: pointer;
  color: var(--color-slate-600);
  font-weight: 500;
  font-size: 14px;
  transition: all 0.2s;
}
.theme-toggle-wrapper .el-icon {
  margin-right: 10px;
  font-size: 18px;
  flex-shrink: 0;
}
.theme-toggle-wrapper:hover {
  background-color: var(--color-slate-100);
  color: var(--color-slate-900);
}
.custom-aside.collapsed .theme-toggle-wrapper {
  justify-content: center;
  padding: 0;
}
.custom-aside.collapsed .theme-toggle-wrapper .el-icon {
  margin-right: 0;
}
.theme-toggle-label {
  white-space: nowrap;
}

</style>
