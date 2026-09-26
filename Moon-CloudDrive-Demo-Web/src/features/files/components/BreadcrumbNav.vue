<!--
  面包屑导航组件
  显示当前文件夹路径，支持点击任意层级返回
-->
<template>
  <div class="breadcrumb-nav">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item
        v-for="item in items"
        :key="item.id ?? 'root'"
        :to="undefined"
      >
        <span
          class="breadcrumb-link"
          @click="$emit('navigate', item.id)"
        >{{ item.name }}</span>
      </el-breadcrumb-item>
    </el-breadcrumb>
  </div>
</template>

<script setup lang="ts">
defineProps<{
  items: { id: number | null; name: string }[]
}>()

defineEmits<{
  (e: 'navigate', id: number | null): void
}>()
</script>

<style scoped>
.breadcrumb-nav {
  padding: 8px 0;
}
.breadcrumb-link {
  cursor: pointer;
  color: var(--el-color-primary);
}
.breadcrumb-link:hover {
  text-decoration: underline;
}
</style>