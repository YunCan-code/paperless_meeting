<template>
  <div class="common-layout">
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
          <div class="menu-group-title" v-show="!isCollapse">MAIN MENU</div>
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
          <div class="menu-divider"></div>
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
import { Calendar, User, List, Monitor, Fold, Expand, Setting } from '@element-plus/icons-vue'
import { useSidebar } from '@/composables/useSidebar'

// 使用全局侧边栏状态，与子页面共享
const { isCollapse, toggleSidebar: toggleCollapse } = useSidebar()
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
  transition: width 0.3s ease;
  overflow: hidden;
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

</style>
