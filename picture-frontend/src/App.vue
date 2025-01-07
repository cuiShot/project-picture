<template>
  <div id="app">
    <BasicLayout />
  </div>
</template>

<script setup lang="ts">
import BasicLayout from '@/layouts/BasicLayouts.vue'
import { healthUsingGet } from '@/api/mainController.ts'


// 改成了在权限校验的时候获取
// const loginUserStore = useLoginUserStore()
// loginUserStore.fetchLoginUser()

healthUsingGet().then((res) => {
  console.log(res)
})
</script>

<style scoped></style>
