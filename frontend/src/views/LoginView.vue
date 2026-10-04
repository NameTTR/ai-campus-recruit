<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { LogIn, Route } from 'lucide-vue-next'
import { login, saveAuthSession } from '../api/client'

const router = useRouter()
const form = reactive({
  username: 'student',
  password: '123456'
})
const loading = ref(false)

async function submit() {
  if (!form.username.trim() || !form.password) {
    ElMessage.warning('请输入账号和密码')
    return
  }
  loading.value = true
  try {
    const result = await login(form.username.trim(), form.password)
    saveAuthSession(result)
    ElMessage.success('登录成功')
    const target = result.role === 'COMPANY'
      ? '/company/jobs'
      : result.role === 'ADMIN'
        ? '/admin/ai?tab=documents'
        : '/student/resume'
    router.push(target)
  } catch (error) {
    ElMessage.error(error instanceof Error ? error.message : '登录失败')
  } finally {
    loading.value = false
  }
}
</script>

<template>
  <section class="login-experience" aria-labelledby="login-title">
    <div class="login-shell">
      <header class="brand-lockup">
        <div class="brand-symbol" aria-hidden="true"><Route :size="23" :stroke-width="1.8" /></div>
        <div><h1>Campus Recruit</h1><p>校园招聘工作台</p></div>
      </header>
      <div class="login-card">
        <div class="login-card-inner">
          <h2 id="login-title">账号登录</h2>

          <el-form class="login-form" label-position="top" @submit.prevent="submit">
            <el-form-item label="账号">
              <el-input v-model="form.username" autocomplete="username" placeholder="输入账号" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" autocomplete="current-password" show-password placeholder="输入密码" />
            </el-form-item>
            <el-button class="login-submit" type="primary" size="large" native-type="submit" :loading="loading">
              <LogIn :size="18" />
              登录
            </el-button>
          </el-form>
        </div>
      </div>
    </div>
  </section>
</template>

<style scoped>
.login-experience { --login-ink: var(--ink, #25302b); --login-muted: var(--muted, #59665e); --login-line: var(--line, #dce3df); --login-accent: var(--accent, #28664f); display: grid; align-items: center; width: min(380px, 100%); min-height: min(620px, calc(100dvh - 48px)); color: var(--login-ink); font-size: 14px; letter-spacing: 0; }
.login-shell { display: grid; width: 100%; gap: 32px; padding-bottom: 48px; }
.brand-lockup { display: flex; align-items: center; min-width: 0; gap: 13px; }
.brand-symbol { display: grid; width: 44px; height: 44px; flex: 0 0 44px; place-items: center; border-radius: 5px; background: #183d30; color: #fff; }
.brand-lockup h1 { margin: 0; font-size: 23px; line-height: 1.3; font-weight: 650; }
.brand-lockup p { margin: 4px 0 0; color: var(--login-muted); font-size: 13px; }
.login-card { min-width: 0; padding-top: 24px; border-top: 1px solid var(--login-line); }
.login-card h2 { margin: 0 0 22px; font-size: 18px; line-height: 1.4; font-weight: 600; }
.login-form { display: grid; gap: 2px; }
.login-form :deep(.el-form-item) { margin-bottom: 18px; }
.login-form :deep(.el-form-item__label) { padding-bottom: 7px; color: var(--login-ink); font-size: 14px; font-weight: 600; line-height: 1.4; }
.login-form :deep(.el-input__wrapper) { min-height: 42px; padding: 1px 12px; border-radius: 4px; background: #fff; box-shadow: 0 0 0 1px var(--login-line) inset; }
.login-form :deep(.el-input__wrapper.is-focus) { box-shadow: 0 0 0 1px var(--login-accent) inset, 0 0 0 3px #e5eee9; }
.login-form :deep(.el-input__inner) { color: var(--login-ink); font-size: 14px; }
.login-submit { width: 100%; min-height: 42px; margin-top: 5px; border-color: var(--login-accent); border-radius: 4px; background: var(--login-accent); font-size: 14px; font-weight: 600; }
.login-submit:hover, .login-submit:focus-visible { border-color: #1e513d; background: #1e513d; }
@media (max-width: 480px) {
  .login-experience { min-height: calc(100dvh - 40px); }
  .login-shell { gap: 28px; padding-bottom: 32px; }
  .brand-lockup h1 { font-size: 22px; }
}
</style>
