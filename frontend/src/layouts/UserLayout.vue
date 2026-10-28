<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { useAuthStore } from '@/stores/auth'
import { notificationApi } from '@/api'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()

const unread = ref(0)

const activeMenu = computed(() => '/' + (route.path.split('/')[1] || 'home'))

async function loadUnread() {
  try {
    const result = await notificationApi.unreadCount()
    unread.value = result.count
  } catch {
    // 未读数失败不影响主流程
  }
}

async function handleLogout() {
  await auth.logout()
  ElMessage.success('已退出登录')
  router.push({ name: 'login' })
}

onMounted(loadUnread)
// 切换页面时顺带刷新未读数，做到"看过就没红点"的效果
watch(() => route.path, loadUnread)
</script>

<template>
  <el-container style="height: 100vh">
    <el-aside width="210px" class="sidebar">
      <div class="brand">
        <el-icon :size="20"><Lock /></el-icon>
        <span>LegacyGuard</span>
      </div>
      <el-menu
        :default-active="activeMenu"
        router
        background-color="transparent"
        text-color="#c0c8d4"
        active-text-color="#ffffff"
      >
        <el-menu-item index="/home">
          <el-icon><HomeFilled /></el-icon><span>首页</span>
        </el-menu-item>
        <el-menu-item index="/assets">
          <el-icon><Document /></el-icon><span>我的资产</span>
        </el-menu-item>
        <el-menu-item index="/contacts">
          <el-icon><User /></el-icon><span>可信联系人</span>
        </el-menu-item>
        <el-menu-item index="/heartbeat">
          <el-icon><AlarmClock /></el-icon><span>状态确认</span>
        </el-menu-item>
        <el-menu-item index="/cases">
          <el-icon><Files /></el-icon><span>授权案件</span>
        </el-menu-item>
        <el-menu-item index="/notifications">
          <el-icon><Bell /></el-icon><span>通知中心</span>
        </el-menu-item>
        <el-menu-item index="/audit">
          <el-icon><Tickets /></el-icon><span>安全审计</span>
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="topbar">
        <div class="topbar-left muted">
          数字遗产与账号托管 · 所有操作均有审计留痕
        </div>
        <div class="topbar-right">
          <el-badge :value="unread" :hidden="unread === 0" :max="99">
            <el-button text @click="router.push('/notifications')">
              <el-icon :size="18"><Bell /></el-icon>
            </el-button>
          </el-badge>

          <el-dropdown trigger="click">
            <span class="user-trigger">
              {{ auth.displayName }}
              <el-icon><ArrowDown /></el-icon>
            </span>
            <template #dropdown>
              <el-dropdown-menu>
                <el-dropdown-item v-if="auth.isAdmin" @click="router.push('/admin/cases')">
                  进入管理端
                </el-dropdown-item>
                <el-dropdown-item divided @click="handleLogout">退出登录</el-dropdown-item>
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
  background: var(--lg-sidebar-bg);
  display: flex;
  flex-direction: column;
}

.brand {
  height: 60px;
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 0 20px;
  color: #fff;
  font-weight: 600;
  letter-spacing: 0.5px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.06);
}

.sidebar :deep(.el-menu-item.is-active) {
  background: var(--lg-sidebar-active);
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
