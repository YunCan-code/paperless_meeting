import { ref } from 'vue'

// 使用全局单例状态，确保 App.vue (菜单) 和 页面 (按钮) 共享同一个状态
const isCollapse = ref(false)

export function useSidebar() {
  const toggleSidebar = () => {
    isCollapse.value = !isCollapse.value
  }

  return {
    isCollapse,
    toggleSidebar
  }
}