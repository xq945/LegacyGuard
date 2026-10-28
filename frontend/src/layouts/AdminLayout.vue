<script setup lang="ts">
import { computed } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const activeMenu = computed(() => '/admin/' + (route.path.split('/')[2] || 'cases'))

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="210px" class="sidebar">
      <div class="brand">
        <el-icon :size="20"><Setting /></el-icon>
        <span>LegacyGuard 管理端</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="#c0c8d4"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/admin/cases">
          <el-icon><Files /></el-icon><span>案件审核</span>
        </el-menu-item>
        <el-menu-item index="/admin/heartbeat">
          <el-icon><AlarmClock /></el-icon><span>失联核验</span>
        </el-menu-item>
        <el-menu-item index="/admin/jobs">
          <el-icon><Monitor /></el-icon><span>任务与消息</span>
        </el-menu-item>
        <el-menu-item index="/admin/audit">
          <el-icon><Tickets /></el-icon><span>审计日志</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="muted">
          管理员只能推进流程，<strong>无法读取任何资产内容</strong>
        </div>
        <div class="topbar-right">
          <el-button text @click="router.push('/home')">返回用户端</el-button>
          <el-dropdown trigger="click">
            <span class="user-trigger">
              {{ auth.displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item @click="handleLogout">退出登录</el-dropdown-item>
              </el-dropdown-menu>
            </template>
          </el-dropdown>
        </div>
      </el-header>

      <el-main>
        <RouterView />
      </el-main>
    </el-container>
  </el-container>
</template>

<style scoped>
.sidebar {
  background: #232b38;
  display: flex;
  flex-direction: column;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 16px;
  color: #fff;
  font-weight: 600;
  font-size: 14px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar :deep(.el-menu-item.is-active) {
  background: #2f3a4a;
}

.topbar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  background: #fff;
  border-bottom: 1px solid #e8eaef;
}

.topbar-right {
  display: flex;
  align-items: center;
  gap: 16px;
}

.user-trigger {
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 4px;
  font-size: 14px;
}
</style>
