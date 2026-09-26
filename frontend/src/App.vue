<template>
  <el-container class="shell">
    <!-- 顶部横向菜单：整店四个业务模块一屏直达，不做侧边栏 -->
    <el-header class="topbar">
      <div class="brand">
        <span class="brand-mark">♪</span>
        <span class="brand-text">
          <strong>知音琴行</strong>
          <em>乐器台账 · 租赁 · 调律维修 · 学员课程</em>
        </span>
      </div>

      <el-menu
        class="topnav"
        mode="horizontal"
        :default-active="activePath"
        :ellipsis="false"
        router
      >
        <el-menu-item v-for="item in menus" :key="item.path" :index="item.path">
          {{ item.label }}
        </el-menu-item>
      </el-menu>

      <div class="topbar-tail">
        <el-tag effect="plain" round>总店</el-tag>
        <span class="hint">点任意一行左侧箭头即可展开历史明细</span>
      </div>
    </el-header>

    <el-main class="main">
      <router-view />
    </el-main>
  </el-container>
</template>

<script setup>
import { computed, getCurrentInstance } from 'vue'
import { useRoute } from 'vue-router'
import router from './router'

// main.js 是仓里既定的启动文件（不修改），所以在这里把 router 挂到 app 上。
getCurrentInstance().appContext.app.use(router)

const route = useRoute()

const menus = [
  { path: '/instruments', label: '乐器台账' },
  { path: '/rentals', label: '租赁订单' },
  { path: '/services', label: '调律与维修' },
  { path: '/courses', label: '学员与课程' }
]

const activePath = computed(() => {
  const hit = menus.find((item) => route.path.startsWith(item.path))
  return hit ? hit.path : '/instruments'
})
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.topbar {
  display: flex;
  align-items: center;
  gap: 28px;
  height: 68px;
  padding: 0 28px;
  background: linear-gradient(90deg, #1e857b 0%, #26a69a 55%, #4db6ac 100%);
  color: #fff;
  box-shadow: 0 2px 10px rgba(38, 166, 154, 0.28);
}

.brand {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
}

.brand-mark {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 38px;
  height: 38px;
  border-radius: 12px;
  background: rgba(255, 255, 255, 0.18);
  font-size: 22px;
}

.brand-text {
  display: flex;
  flex-direction: column;
  line-height: 1.25;
}

.brand-text strong {
  font-size: 17px;
  letter-spacing: 1px;
}

.brand-text em {
  font-style: normal;
  font-size: 11px;
  opacity: 0.82;
}

.topnav {
  flex: 1;
  border-bottom: none;
  background: transparent;
}

.topnav :deep(.el-menu-item) {
  height: 68px;
  line-height: 68px;
  color: rgba(255, 255, 255, 0.86);
  font-size: 15px;
  border-bottom: 3px solid transparent;
}

.topnav :deep(.el-menu-item:hover),
.topnav :deep(.el-menu-item.is-active) {
  color: #ffffff;
  background: rgba(255, 255, 255, 0.12);
  border-bottom-color: #ffffff;
}

.topbar-tail {
  display: flex;
  align-items: center;
  gap: 10px;
  flex: none;
  font-size: 12px;
  opacity: 0.92;
}

.topbar-tail .hint {
  max-width: 220px;
  line-height: 1.3;
}

.main {
  padding: 20px 28px 36px;
}
</style>
