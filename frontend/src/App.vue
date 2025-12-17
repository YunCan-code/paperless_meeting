<template>
  <router-view />
</template>

<script setup>
  import { useSidebar } from '@/composables/useSidebar'
const { isCollapse } = useSidebar()
</script>
<template>
  <el-aside :width="isCollapse ? '64px' : '240px'">
    <el-menu :collapse="isCollapse">
      </el-menu>
  </el-aside>
  </template>
<style>
/* Global reset or minimal styles */
body {
  margin: 0;
  padding: 0;
  background-color: #f4f4f5;
}
</style>
