<script setup lang="ts">
import { reactive } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus/es/components/message/index'
import { LogIn } from 'lucide-vue-next'
import { login } from '../api/client'

const router = useRouter()
const form = reactive({
  username: 'student',
  password: '123456'
})

async function submit() {
  const result = await login(form.username, form.password)
  localStorage.setItem('token', result.token)
  localStorage.setItem('role', result.role)
  localStorage.setItem('displayName', result.displayName)
  ElMessage.success('登录成功')
  const target = result.role === 'COMPANY'
    ? '/company/publish'
    : result.role === 'ADMIN'
      ? '/admin/overview'
      : '/student/resume'
  router.push(target)
}
</script>

<template>
  <section class="login-card">
    <h1>AI Campus Recruit</h1>
    <p>校园招聘匹配工作台</p>
    <el-form label-position="top" @submit.prevent="submit">
      <el-form-item label="账号">
        <el-segmented v-model="form.username" :options="['student', 'company', 'admin']" />
      </el-form-item>
      <el-form-item label="密码">
        <el-input v-model="form.password" type="password" show-password />
      </el-form-item>
      <el-button type="primary" size="large" style="width: 100%" @click="submit">
        <LogIn :size="18" />
        登录
      </el-button>
    </el-form>
  </section>
</template>
