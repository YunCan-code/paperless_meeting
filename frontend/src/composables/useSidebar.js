// frontend/src/composables/useSidebar.js
import { ref } from 'vue'

// 使用全局单例状态，保证所有页面共享同一个状态
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