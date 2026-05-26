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

export interface InterviewQuestionRequest {
  studentId: string
  resumeId: string
  jobId: string
  targetRole: string
  skills: string[]
}

export interface InterviewQuestion {
  questionId: string
  category: string
  difficulty: string
  question: string
  referencePoints: string[]
}

export interface InterviewFeedbackRequest {
  studentId: string
  questionId: string
  question: string
  answer: string
  targetRole: string
}

export interface InterviewFeedback {
  score: number
  strengths: string[]
  gaps: string[]
  suggestions: string[]
  summary: string
  mocked: boolean
}

export type DeliveryStatus = 'SUBMITTED' | 'VIEWED' | 'INTERVIEW' | 'OFFER' | 'REJECTED'

export interface DeliveryRecord {
  deliveryId: string
  studentId: string
  resumeId: string
  jobId: string
  companyId: string
  status: DeliveryStatus
  createdAt: string
}

export interface DeliveryStatistics {
  totalCount: number
  statusCounts: Record<DeliveryStatus, number>
  pendingCount: number
}

export interface DashboardStats {
  studentCount: number
  companyCount: number
  jobCount: number
  deliveryCount: number
  averageMatchScore: number
  deliveryStatusCounts: Record<DeliveryStatus, number>
  pendingDeliveryCount: number
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

const fallbackInterviewQuestions: InterviewQuestion[] = [
  {
    questionId: 'IQ-001',
    category: '项目深挖',
    difficulty: '中等',
    question: '请结合一个项目说明你如何使用 Java 解决核心业务问题，并说明你的个人贡献。',
    referencePoints: ['项目背景和目标', '技术方案与取舍', '个人负责模块', '量化结果或复盘']
  },
  {
    questionId: 'IQ-002',
    category: '技术基础',
    difficulty: '中等',
    question: '如果接口响应突然变慢，你会如何从应用、数据库和缓存三个层面排查？',
    referencePoints: ['先查看监控与日志', '分析 SQL 与索引', '检查缓存命中率', '补充压测复现方式']
  },
  {
    questionId: 'IQ-003',
    category: '行为面试',
    difficulty: '基础',
    question: '请讲一次你在团队协作中推动问题解决的经历。',
    referencePoints: ['使用 STAR 结构', '说明阻塞点', '突出沟通动作', '总结复盘']
  }
]

const fallbackInterviewFeedback: InterviewFeedback = {
  score: 82,
  strengths: ['回答能够围绕题目展开，体现基本岗位理解', '提到了项目和技术关键词，便于继续追问'],
  gaps: ['缺少可验证的数据结果', '技术取舍和个人贡献还不够具体'],
  suggestions: ['按 STAR 结构重组回答', '补充接口耗时、数据量或并发量等指标', '说明遇到的困难与最终复盘'],
  summary: '当前回答基础完整，补充细节与量化结果后更适合正式面试。',
  mocked: true
}

const fallbackDeliveries: DeliveryRecord[] = [
  {
    deliveryId: 'D001',
    studentId: 'S001',
    resumeId: 'R001',
    jobId: 'J001',
    companyId: 'C001',
    status: 'SUBMITTED',
    createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
  },
  {
    deliveryId: 'D002',
    studentId: 'S002',
    resumeId: 'R002',
    jobId: 'J001',
    companyId: 'C001',
    status: 'VIEWED',
    createdAt: new Date(Date.now() - 20 * 60 * 60 * 1000).toISOString()
  },
  {
    deliveryId: 'D003',
    studentId: 'S003',
    resumeId: 'R003',
    jobId: 'J002',
    companyId: 'C001',
    status: 'INTERVIEW',
    createdAt: new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString()
  },
  {
    deliveryId: 'D004',
    studentId: 'S004',
    resumeId: 'R004',
    jobId: 'J003',
    companyId: 'C002',
    status: 'OFFER',
    createdAt: new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString()
  },
  {
    deliveryId: 'D005',
    studentId: 'S005',
    resumeId: 'R005',
    jobId: 'J002',
    companyId: 'C001',
    status: 'REJECTED',
    createdAt: new Date(Date.now() - 4 * 60 * 60 * 1000).toISOString()
  }
]

const fallbackDeliveryStatusCounts: Record<DeliveryStatus, number> = {
  SUBMITTED: 1,
  VIEWED: 1,
  INTERVIEW: 1,
  OFFER: 1,
  REJECTED: 1
}

const fallbackDeliveryStatistics: DeliveryStatistics = {
  totalCount: fallbackDeliveries.length,
  statusCounts: fallbackDeliveryStatusCounts,
  pendingCount: fallbackDeliveryStatusCounts.SUBMITTED
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

export function generateInterviewQuestions(payload: InterviewQuestionRequest) {
  return request<InterviewQuestion[]>('/api/ai/interview/questions', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallbackInterviewQuestions)
}

export function submitInterviewFeedback(payload: InterviewFeedbackRequest) {
  return request<InterviewFeedback>('/api/ai/interview/feedback', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallbackInterviewFeedback)
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
    companyId: fallbackJobs.find((job) => job.jobId === jobId)?.companyId || 'C001',
    status: 'SUBMITTED',
    createdAt: new Date().toISOString()
  })
}

export function listDeliveries() {
  return request<DeliveryRecord[]>('/api/deliveries/my', { method: 'GET' },
    fallbackDeliveries.filter((delivery) => delivery.studentId === 'S001'))
}

export function listCompanyDeliveries(companyId = 'C001') {
  return request<DeliveryRecord[]>(`/api/deliveries/company?companyId=${encodeURIComponent(companyId)}`, { method: 'GET' },
    fallbackDeliveries.filter((delivery) => delivery.companyId === companyId))
}

export function updateDeliveryStatus(delivery: DeliveryRecord, status: DeliveryStatus) {
  const updated = { ...delivery, status }
  return request<DeliveryRecord>(`/api/deliveries/${delivery.deliveryId}/status?status=${status}`, {
    method: 'PUT'
  }, updated)
}

export function getDeliveryStatistics() {
  return request<DeliveryStatistics>('/api/deliveries/statistics', { method: 'GET' }, fallbackDeliveryStatistics)
}

export function getDashboard() {
  return request<DashboardStats>('/api/admin/dashboard', { method: 'GET' }, {
    studentCount: 128,
    companyCount: 24,
    jobCount: 56,
    deliveryCount: 312,
    averageMatchScore: 82,
    deliveryStatusCounts: {
      SUBMITTED: 72,
      VIEWED: 96,
      INTERVIEW: 84,
      OFFER: 28,
      REJECTED: 32
    },
    pendingDeliveryCount: 72
  })
}
