export type Role = 'STUDENT' | 'COMPANY' | 'ADMIN'

export interface LoginResponse {
  token: string
  userId: string
  displayName: string
  role: Role
}

export interface UserProfile {
  userId: string
  displayName: string
  role: Role
  school: string
  major: string
  skills: string[]
  targetPosition: string
}

export interface ResumeSummary {
  resumeId: string
  studentId: string
  fileName: string
  education: string
  skills: string[]
  projects: string[]
  diagnosis: string
  score: number
}

export interface JobSummary {
  jobId: string
  companyId: string
  companyName: string
  title: string
  city: string
  salaryRange: string
  requiredSkills: string[]
  description: string
  aiSummary: string
}

export interface MatchResult {
  matchId: string
  resumeId: string
  jobId: string
  studentId: string
  score: number
  strengths: string[]
  gaps: string[]
  suggestions: string[]
}

export interface DeliveryRecord {
  deliveryId: string
  studentId: string
  resumeId: string
  jobId: string
  status: string
  createdAt: string
}

export interface DashboardStats {
  studentCount: number
  companyCount: number
  jobCount: number
  deliveryCount: number
  averageMatchScore: number
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

const fallbackProfile: UserProfile = {
  userId: 'S001',
  displayName: '张同学',
  role: 'STUDENT',
  school: '示范大学',
  major: '软件工程',
  skills: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
  targetPosition: 'Java 后端实习生'
}

const fallbackResume: ResumeSummary = {
  resumeId: 'R001',
  studentId: 'S001',
  fileName: 'demo-resume.pdf',
  education: '示范大学 软件工程 本科',
  skills: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'Docker'],
  projects: ['校园二手交易系统', '在线考试平台'],
  diagnosis: '简历结构完整，建议补充量化成果、部署方式和团队协作细节。',
  score: 86
}

const fallbackJobs: JobSummary[] = [
  {
    jobId: 'J001',
    companyId: 'C001',
    companyName: '星河科技',
    title: 'Java 后端实习生',
    city: '杭州',
    salaryRange: '180-260/天',
    requiredSkills: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
    description: '参与招聘平台、数据看板和中台接口开发。',
    aiSummary: '适合具备 Java Web 项目经验的应届生。'
  }
]

const fallbackMatch: MatchResult = {
  matchId: 'M001',
  resumeId: 'R001',
  jobId: 'J001',
  studentId: 'S001',
  score: 88,
  strengths: ['技能栈与岗位要求高度一致', '项目经历覆盖后端接口、缓存和数据库'],
  gaps: ['微服务项目经验需要进一步强化', '简历中缺少可验证成果指标'],
  suggestions: ['补充微服务部署图', '把项目难点写成 STAR 结构', '准备 RocketMQ 与 Redis 场景题']
}

async function request<T>(path: string, init: RequestInit, fallback: T): Promise<T> {
  try {
    const response = await fetch(path, {
      ...init,
      headers: {
        ...(init.body instanceof FormData ? {} : { 'Content-Type': 'application/json' }),
        Authorization: localStorage.getItem('token') || '',
        ...(init.headers || {})
      }
    })
    if (!response.ok) {
      throw new Error(`HTTP ${response.status}`)
    }
    const payload = (await response.json()) as ApiResponse<T>
    return payload.data
  } catch {
    return fallback
  }
}

export function login(username: string, password: string) {
  const role: Role = username === 'company' ? 'COMPANY' : username === 'admin' ? 'ADMIN' : 'STUDENT'
  const userId = role === 'COMPANY' ? 'C001' : role === 'ADMIN' ? 'A001' : 'S001'
  const displayName = role === 'COMPANY' ? '星河科技 HR' : role === 'ADMIN' ? '就业办管理员' : '张同学'
  return request<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }, { token: `demo-${role.toLowerCase()}-token`, userId, displayName, role })
}

export function getProfile() {
  return request<UserProfile>('/api/students/profile', { method: 'GET' }, fallbackProfile)
}

export function uploadResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return request<ResumeSummary>('/api/resumes/upload', { method: 'POST', body: formData }, {
    ...fallbackResume,
    fileName: file.name
  })
}

export function analyzeResume(resumeId: string) {
  return request<ResumeSummary>(`/api/resumes/${resumeId}/analyze`, { method: 'POST' }, fallbackResume)
}

export function listJobs() {
  return request<JobSummary[]>('/api/jobs', { method: 'GET' }, fallbackJobs)
}

export function createJob(job: Partial<JobSummary>) {
  const created = { ...fallbackJobs[0], ...job, jobId: `J${Date.now().toString().slice(-6)}` }
  return request<JobSummary>('/api/jobs', { method: 'POST', body: JSON.stringify(job) }, created)
}

export function analyzeJob(jobId: string) {
  return request<JobSummary>(`/api/jobs/${jobId}/analyze`, { method: 'POST' }, fallbackJobs[0])
}

export function matchResumeJob(resumeId = 'R001', jobId = 'J001') {
  return request<MatchResult>('/api/matches/resume-job', {
    method: 'POST',
    body: JSON.stringify({ resumeId, jobId, studentId: 'S001' })
  }, fallbackMatch)
}

export function createDelivery(resumeId = 'R001', jobId = 'J001') {
  return request<DeliveryRecord>('/api/deliveries', {
    method: 'POST',
    body: JSON.stringify({ studentId: 'S001', resumeId, jobId })
  }, {
    deliveryId: `D${Date.now().toString().slice(-6)}`,
    studentId: 'S001',
    resumeId,
    jobId,
    status: 'SUBMITTED',
    createdAt: new Date().toISOString()
  })
}

export function listDeliveries() {
  return request<DeliveryRecord[]>('/api/deliveries/my', { method: 'GET' }, [{
    deliveryId: 'D001',
    studentId: 'S001',
    resumeId: 'R001',
    jobId: 'J001',
    status: 'SUBMITTED',
    createdAt: new Date().toISOString()
  }])
}

export function getDashboard() {
  return request<DashboardStats>('/api/admin/dashboard', { method: 'GET' }, {
    studentCount: 128,
    companyCount: 24,
    jobCount: 56,
    deliveryCount: 312,
    averageMatchScore: 82
  })
}

