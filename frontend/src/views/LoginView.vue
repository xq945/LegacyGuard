<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, type FormInstance, type FormRules } from 'element-plus'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
const router = useRouter()
const route = useRoute()

const formRef = ref<FormInstance>()
const loading = ref(false)

const form = reactive({ username: '', password: '' })

const rules: FormRules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function handleLogin() {
  if (!formRef.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return

  loading.value = true
  try {
    const user = await auth.login(form.username, form.password)
    ElMessage.success(`欢迎回来，${user.displayName}`)

    const redirect = route.query.redirect as string | undefined
    if (redirect) {
      router.push(redirect)
    } else {
      router.push(user.role === 'ADMIN' ? '/admin/cases' : '/home')
    }
  } catch {
    // 错误提示已由 HTTP 拦截器统一处理
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <div class="auth-page">
    <div class="auth-card">
      <div class="auth-brand">
        <el-icon :size="28"><Lock /></el-icon>
        <h1>LegacyGuard</h1>
        <p class="muted">数字遗产与账号托管平台</p>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent>
        <el-form-item label="用户名" prop="username">
          <el-input v-model="form.username" placeholder="请输入用户名" @keyup.enter="handleLogin" />
        </el-form-item>

        <el-form-item label="密码" prop="password">
          <el-input
            v-model="form.password"
            type="password"
            show-password
            placeholder="请输入密码"
            @keyup.enter="handleLogin"
          />
        </el-form-item>

        <el-button type="primary" :loading="loading" style="width: 100%" @click="handleLogin">
          登录
        </el-button>
      </el-form>

      <div class="auth-footer">
        <span class="muted">还没有账号？</span>
        <el-link type="primary" @click="router.push('/register')">立即注册</el-link>
      </div>

      <el-alert
        type="info"
        :closable="false"
        show-icon
        style="margin-top: 16px"
        title="连续 5 次密码错误将锁定账号 15 分钟"
      />
    </div>
  </div>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: flex;
  align-items: center;
  justify-content: center;
  background: linear-gradient(135deg, #1f2733 0%, #3a4657 100%);
}

.auth-card {
  width: 400px;
  background: #fff;
  border-radius: 12px;
  padding: 36px 32px 28px;
  box-shadow: 0 12px 40px rgba(0, 0, 0, 0.2);
}

.auth-brand {
  text-align: center;
  margin-bottom: 24px;
}

.auth-brand h1 {
  margin: 8px 0 4px;
  font-size: 22px;
  letter-spacing: 0.5px;
}

.auth-brand p {
  margin: 0;
}

.auth-footer {
  margin-top: 16px;
  text-align: center;
  font-size: 13px;
}
</style>
