<template>
  <div style="padding: 24px; text-align: center;">
    <h1>📱 移动端首页</h1>
    <p>欢迎, {{ username }}</p>
    <el-empty description="平板端主页正在开发中..." />
    <el-button @click="logout">退出登录</el-button>
  </div>
</template>

<script setup>
import { ref } from 'vue'
import { useRouter } from 'vue-router'

const router = useRouter()
const username = ref(localStorage.getItem('user_name') || 'Guest')

const logout = () => {
  localStorage.clear()
  router.push('/mobile/login')
}
</script>
