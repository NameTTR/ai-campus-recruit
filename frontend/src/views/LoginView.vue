<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { ArrowUpRight, Compass, LogIn, Route } from 'lucide-vue-next'
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
      <aside class="login-brand-panel" aria-labelledby="brand-title">
        <div class="brand-lockup">
          <div class="brand-symbol" aria-hidden="true"><Route :size="22" :stroke-width="2.4" /></div>
          <span>Campus Recruit</span>
        </div>

        <div class="brand-intro">
          <p class="overline">CAMPUS RECRUIT</p>
          <h1 id="brand-title">清晰地走向<br>下一站。</h1>
          <p>让每一次准备，都有迹可循。</p>
        </div>

        <div class="path-illustration" aria-hidden="true">
          <span class="path-orbit orbit-one"></span>
          <span class="path-orbit orbit-two"></span>
          <span class="path-line line-one"></span>
          <span class="path-line line-two"></span>
          <span class="path-node node-one"></span>
          <span class="path-node node-two"></span>
          <span class="path-node node-three"></span>
          <div class="path-card card-back"></div>
          <div class="path-card card-front"><Compass :size="30" :stroke-width="1.8" /></div>
          <ArrowUpRight class="path-arrow" :size="22" :stroke-width="2" />
        </div>

        <div class="brand-footer"><span></span><p>校园招聘工作台</p></div>
      </aside>

      <div class="login-card">
        <div class="login-card-inner">
          <p class="overline form-overline">SIGN IN</p>
          <h2 id="login-title">欢迎回来</h2>
          <p class="login-lead">使用账号继续你的工作。</p>

          <el-form class="login-form" label-position="top" @submit.prevent="submit">
            <el-form-item label="账号">
              <el-input v-model="form.username" autocomplete="username" placeholder="输入账号" />
            </el-form-item>
            <el-form-item label="密码">
              <el-input v-model="form.password" type="password" autocomplete="current-password" show-password placeholder="输入密码" />
            </el-form-item>
            <el-button class="login-submit" type="primary" size="large" :loading="loading" @click="submit">
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
.login-experience{--login-canvas:var(--canvas,#f6f7f9);--login-surface:var(--surface,#fff);--login-ink:var(--ink,#18212f);--login-muted:var(--muted,#667085);--login-line:var(--line,#dce4e0);--login-accent:var(--accent,#28664f);--login-mint:var(--accent-soft,#c8f1df);width:min(1160px,100%);min-height:min(680px,calc(100dvh - 40px));display:grid;place-items:center;color:var(--login-ink)}
.login-shell{display:grid;grid-template-columns:minmax(360px,.9fr) minmax(420px,1.1fr);width:100%;min-height:640px;overflow:hidden;border:1px solid var(--login-line);border-radius:8px;background:var(--login-surface);box-shadow:0 22px 56px rgba(22,48,40,.11)}
.login-brand-panel{position:relative;display:flex;min-width:0;flex-direction:column;overflow:hidden;padding:40px 44px;background:var(--login-mint)}.login-brand-panel::after{position:absolute;inset:auto -88px -116px auto;width:310px;height:310px;border:1px solid rgba(40,102,79,.18);border-radius:50%;content:""}.brand-lockup{position:relative;z-index:1;display:flex;align-items:center;gap:10px;color:var(--login-accent);font-size:14px;font-weight:800}.brand-symbol{display:grid;width:36px;height:36px;place-items:center;border-radius:8px;background:var(--login-accent);color:#fff}
.brand-intro{position:relative;z-index:1;margin-top:68px}.overline{margin:0;color:var(--login-accent);font-size:12px;font-weight:800;letter-spacing:.12em;line-height:1.2}.brand-intro h1{margin:14px 0;color:var(--login-accent);font-size:42px;font-weight:760;letter-spacing:0;line-height:1.15}.brand-intro>p:last-child{max-width:270px;margin:0;color:rgba(27,79,59,.8);font-size:16px;line-height:1.7}
.path-illustration{position:relative;z-index:1;align-self:center;width:min(320px,100%);height:246px;margin:44px 0 18px}.path-orbit{position:absolute;border:1px solid rgba(40,102,79,.18);border-radius:50%}.orbit-one{top:8px;left:20px;width:196px;height:196px}.orbit-two{right:0;bottom:0;width:140px;height:140px}.path-line{position:absolute;height:2px;transform-origin:left;background:var(--login-accent)}.line-one{top:123px;left:45px;width:174px;transform:rotate(-24deg);opacity:.68}.line-two{top:124px;left:171px;width:103px;transform:rotate(50deg);opacity:.42}.path-node{position:absolute;z-index:2;width:14px;height:14px;border:3px solid var(--login-mint);border-radius:50%;background:var(--login-accent);box-shadow:0 0 0 1px rgba(40,102,79,.32)}.node-one{top:109px;left:39px}.node-two{top:45px;left:196px}.node-three{right:32px;bottom:43px}.path-card{position:absolute;display:grid;width:80px;height:80px;place-items:center;border-radius:8px;color:var(--login-accent);box-shadow:0 14px 24px rgba(40,102,79,.12)}.card-back{top:91px;left:120px;transform:rotate(-9deg);border:1px solid rgba(40,102,79,.2);background:rgba(255,255,255,.52)}.card-front{top:78px;left:132px;border:1px solid rgba(40,102,79,.13);background:#fff}.path-arrow{position:absolute;top:34px;right:29px;color:var(--login-accent)}
.brand-footer{position:relative;z-index:1;display:flex;align-items:center;gap:10px;margin-top:auto;color:rgba(27,79,59,.76);font-size:13px}.brand-footer span{width:24px;height:1px;background:currentColor}.brand-footer p{margin:0}
.login-card{display:grid;min-width:0;place-items:center;padding:48px 64px;background:var(--login-surface)}.login-card-inner{width:min(380px,100%)}.form-overline{margin-bottom:15px;color:var(--login-muted);font-size:11px}.login-card h2{margin:0;color:var(--login-ink);font-size:30px;font-weight:760;letter-spacing:0;line-height:1.2}.login-lead{margin:10px 0 34px;color:var(--login-muted);font-size:15px;line-height:1.6}.login-form{display:grid;gap:3px}.login-form :deep(.el-form-item){margin-bottom:15px}.login-form :deep(.el-form-item__label){padding-bottom:7px;color:var(--login-ink);font-size:14px;font-weight:700;line-height:1.4}.login-form :deep(.el-input__wrapper){min-height:46px;border-radius:6px;background:#fff;box-shadow:0 0 0 1px var(--login-line) inset}.login-form :deep(.el-input__wrapper.is-focus){box-shadow:0 0 0 2px rgba(40,102,79,.2),0 0 0 1px var(--login-accent) inset}.login-form :deep(.el-input__inner){color:var(--login-ink)}.login-submit{width:100%;min-height:46px;margin-top:8px;border-color:var(--login-accent);border-radius:6px;background:var(--login-accent);font-weight:750}.login-submit:hover,.login-submit:focus-visible{border-color:#1e513d;background:#1e513d}
@media (max-width:800px){.login-experience{min-height:calc(100dvh - 28px)}.login-shell{grid-template-columns:1fr;min-height:0}.login-brand-panel{min-height:282px;padding:28px 30px}.brand-intro{margin-top:32px}.brand-intro h1{font-size:34px}.path-illustration{position:absolute;right:12px;bottom:-78px;width:230px;transform:scale(.82);transform-origin:bottom right}.brand-footer{margin-top:38px}.login-card{padding:38px 30px 42px}}
@media (max-width:480px){.login-experience{min-height:calc(100dvh - 28px)}.login-brand-panel{min-height:252px;padding:24px}.brand-intro h1{font-size:31px}.brand-intro>p:last-child{font-size:14px}.path-illustration{right:-29px;bottom:-87px;transform:scale(.68)}.brand-footer{margin-top:34px}.login-card{padding:32px 24px 36px}.login-card h2{font-size:27px}.login-lead{margin-bottom:28px}}
</style>
