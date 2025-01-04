<template>
  <div id="basicLayout">
    <a-layout>
      <a-layout-header class="header">
        <GlobalHeader/>
      </a-layout-header>
      <a-layout-content class = "content">
        <router-view/>
      </a-layout-content>
      <a-layout-footer class="footer">
        <a href=" " target="_blank">cuicui的主页</a>
      </a-layout-footer>

    </a-layout>
  </div>
</template>

<script setup lang="ts">

import GlobalHeader from '@/components/GlobalHeader.vue'
</script>

<style scoped>
#basicLayout .header{
  padding-bottom: 10px;
  background: white;
  color: unset;
  margin-bottom: 15px;
}

#basicLayout .content{
  padding: 20px;
  background: linear-gradient(to right,#efefef,#fff);
  margin-bottom: 30px;
}
#basicLayout .footer {
  background: #efefef;
  padding: 16px;
  position: fixed;
  bottom: 0;
  left: 0;
  right: 0;
  text-align: center;
}
</style>
