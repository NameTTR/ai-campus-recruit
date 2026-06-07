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
  objectKey: string
  storageProvider: string
  storageStatus: string
  sourceFormat: string
  parseStatus: string
  parsedTextLength: number
}

export interface ResumeParseMetadata {
  sourceFormat?: string
  parseStatus?: string
  parsedTextLength?: number
  resumeSourceFormat?: string
  resumeParseStatus?: string
  resumeParsedTextLength?: number
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

export interface MatchResult extends ResumeParseMetadata {
  matchId: string
  resumeId: string
  jobId: string
  studentId: string
  score: number
  strengths: string[]
  gaps: string[]
  suggestions: string[]
}

export interface CandidateScreenRequest {
  deliveryId: string
  studentId: string
  resumeId: string
  jobId: string
  companyId?: string
  targetRole: string
  skills: string[]
  projects: string[]
  jobRequirements: string[]
  resumeSummary: string
  jobDescription: string
  resumeSourceFormat?: string
  resumeParseStatus?: string
  resumeParsedTextLength?: number
}

export interface CandidateScreenResult extends ResumeParseMetadata {
  deliveryId: string
  studentId: string
  jobId: string
  score: number
  recommendation: string
  strengths: string[]
  risks: string[]
  interviewQuestions: string[]
  nextActions: string[]
  mocked: boolean
}

export interface CandidateScreenRecord extends CandidateScreenResult {
  screeningId: string
  companyId: string
  createdAt: string
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

export interface AiModuleStatus {
  provider: string
  model: string
  configured: boolean
  baseUrl: string
  capabilities: string[]
  fallbackReason: string | null
}

export interface InterviewRecord {
  recordId: string
  studentId: string
  targetRole: string
  questionId: string
  question: string
  answer: string
  score: number
  summary: string
  suggestions: string[]
  mocked: boolean
  createdAt: string
}

export type DeliveryStatus = 'SUBMITTED' | 'VIEWED' | 'INTERVIEW' | 'OFFER' | 'REJECTED'

export interface DeliveryRecord extends ResumeParseMetadata {
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

export interface SystemServiceStatus {
  name: string
  displayName: string
  defaultPort?: number
  port: number
  healthPath: string
  status: string
  note?: string
}

export interface PersistenceStatus {
  module: string
  enabled: boolean
  database: string
  cacheKeyPrefix: string
  note?: string
}

export interface InfrastructureStatus {
  name: string
  host: string
  port: number
  configured: boolean
  status: string
  note?: string
}

export interface SystemStatus {
  generatedAt: string
  applicationName: string
  environment: string
  services: SystemServiceStatus[]
  persistence: PersistenceStatus[]
  infrastructure: InfrastructureStatus[]
  warnings: string[]
}

export interface TopologyService {
  name: string
  displayName: string
  port: number
  healthUrl: string
  status: string
  note?: string
}

export interface TopologyNode {
  id: string
  name: string
  host: string
  role: string
  services: TopologyService[]
}

export interface DeploymentTopology {
  generatedAt: string
  profile?: string
  environment: string
  nodes: TopologyNode[]
  warnings: string[]
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

type DeliveryResumeInput = string | (ResumeParseMetadata & { resumeId?: string })

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function getEnvValue(key: string) {
  return (import.meta.env[key] || '').trim()
}

function resolveRequestPath(path: string) {
  const apiBaseUrl = getEnvValue('VITE_API_BASE_URL')
  return apiBaseUrl ? `${trimTrailingSlash(apiBaseUrl)}${path}` : path
}

function shouldUseApi(path: string) {
  if (!import.meta.env.DEV) {
    return true
  }
  if (getEnvValue('VITE_API_BASE_URL') || getEnvValue('VITE_API_PROXY_TARGET')) {
    return true
  }
  return path.startsWith('/api/ai') && Boolean(getEnvValue('VITE_AI_PROXY_TARGET'))
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
  score: 86,
  objectKey: 'resumes/R001/demo-resume.pdf',
  storageProvider: 'local-demo',
  storageStatus: 'SEEDED',
  sourceFormat: 'PDF',
  parseStatus: 'SEEDED',
  parsedTextLength: 62
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
  sourceFormat: 'PDF',
  parseStatus: 'TEXT_EXTRACTED',
  parsedTextLength: 62,
  strengths: ['技能栈与岗位要求高度一致', '项目经历覆盖后端接口、缓存和数据库'],
  gaps: ['微服务项目经验需要进一步强化', '简历中缺少可验证成果指标'],
  suggestions: ['补充微服务部署图', '把项目难点写成 STAR 结构', '准备 RocketMQ 与 Redis 场景题']
}

const fallbackCandidateScreen: CandidateScreenResult = {
  deliveryId: 'D001',
  studentId: 'S001',
  jobId: 'J001',
  score: 86,
  resumeSourceFormat: 'PDF',
  resumeParseStatus: 'TEXT_EXTRACTED',
  resumeParsedTextLength: 62,
  recommendation: '建议进入一面',
  strengths: ['Java Web 技术栈与岗位要求匹配', '项目经历覆盖接口开发、数据库和缓存场景', '求职方向与岗位职责一致'],
  risks: ['简历缺少可量化的项目结果', '微服务、消息队列和线上排障经验需要继续确认'],
  interviewQuestions: ['请说明你在项目中如何设计缓存 key，并避免缓存穿透。', '如果接口响应变慢，你会如何定位 SQL、缓存和应用层瓶颈？', '请举例说明一次你负责的后端模块，以及最终交付结果。'],
  nextActions: ['安排 30 分钟技术一面', '重点追问 Redis、MySQL 索引和接口设计', '要求候选人补充项目指标与部署方式'],
  mocked: true
}

const fallbackCandidateScreenRecords: CandidateScreenRecord[] = [
  {
    screeningId: 'CS-DEMO-001',
    companyId: 'C001',
    ...fallbackCandidateScreen,
    createdAt: new Date(Date.now() - 60 * 60 * 1000).toISOString()
  }
]

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

const fallbackAiModuleStatus: AiModuleStatus = {
  provider: 'dashscope',
  model: 'qwen-plus',
  configured: false,
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  capabilities: ['resume-analysis', 'job-analysis', 'match-analysis', 'candidate-screening', 'interview-question-generation', 'interview-feedback'],
  fallbackReason: 'AI service is offline or DASHSCOPE_API_KEY is not configured'
}

const fallbackInterviewRecords: InterviewRecord[] = [
  {
    recordId: 'IR-DEMO-001',
    studentId: 'S001',
    targetRole: 'Java 后端实习生',
    questionId: 'IQ-002',
    question: '如果接口响应突然变慢，你会如何从应用、数据库和缓存三个层面排查？',
    answer: '先查看监控和日志，再分析 SQL、索引和缓存命中率，最后通过压测复现。',
    score: 82,
    summary: '回答覆盖主要排查路径，继续补充指标和工具细节会更完整。',
    suggestions: ['补充具体监控指标', '说明慢 SQL 定位工具', '对比优化前后的响应时间'],
    mocked: true,
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
  }
]

const fallbackDeliveries: DeliveryRecord[] = [
  {
    deliveryId: 'D001',
    studentId: 'S001',
    resumeId: 'R001',
    jobId: 'J001',
    companyId: 'C001',
    status: 'SUBMITTED',
    sourceFormat: 'PDF',
    parseStatus: 'TEXT_EXTRACTED',
    parsedTextLength: 62,
    createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
  },
  {
    deliveryId: 'D002',
    studentId: 'S002',
    resumeId: 'R002',
    jobId: 'J001',
    companyId: 'C001',
    status: 'VIEWED',
    sourceFormat: 'DOCX',
    parseStatus: 'UNPARSED',
    parsedTextLength: 0,
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

const fallbackSystemStatus: SystemStatus = {
  generatedAt: new Date().toISOString(),
  applicationName: 'user-service',
  environment: 'frontend-demo',
  services: [
    { name: 'gateway-service', displayName: 'API 网关', port: 8080, healthPath: '/actuator/health', status: 'CONFIGURED', note: '统一转发前端 API 请求' },
    { name: 'auth-service', displayName: '认证服务', port: 8101, healthPath: '/actuator/health', status: 'CONFIGURED' },
    { name: 'user-service', displayName: '用户与管理服务', port: 8102, healthPath: '/actuator/health', status: 'CONFIGURED' },
    { name: 'resume-service', displayName: '简历服务', port: 8103, healthPath: '/actuator/health', status: 'CONFIGURED' },
    { name: 'job-service', displayName: '岗位服务', port: 8104, healthPath: '/actuator/health', status: 'CONFIGURED' },
    { name: 'match-service', displayName: '匹配服务', port: 8105, healthPath: '/actuator/health', status: 'CONFIGURED' },
    { name: 'ai-service', displayName: 'AI 服务', port: 8106, healthPath: '/actuator/health', status: 'UNKNOWN', note: '未配置网关时使用前端演示数据' },
    { name: 'delivery-service', displayName: '投递服务', port: 8107, healthPath: '/actuator/health', status: 'CONFIGURED' }
  ],
  persistence: [
    { module: 'resume', enabled: false, database: 'ai_campus_recruit', cacheKeyPrefix: 'resume:summaries', note: '表 resume_summary_record；RESUME_PERSISTENCE_ENABLED 默认关闭' },
    { module: 'job', enabled: false, database: 'ai_campus_recruit', cacheKeyPrefix: 'job:records', note: '表 job_record；JOB_PERSISTENCE_ENABLED 默认关闭' },
    { module: 'match', enabled: false, database: 'ai_campus_recruit', cacheKeyPrefix: 'match:results', note: '表 match_result_record；MATCH_PERSISTENCE_ENABLED 默认关闭' },
    { module: 'delivery', enabled: false, database: 'ai_campus_recruit', cacheKeyPrefix: 'delivery:records', note: '表 delivery_record；DELIVERY_PERSISTENCE_ENABLED 默认关闭' },
    { module: 'ai-screening', enabled: false, database: 'ai_campus_recruit', cacheKeyPrefix: 'ai:screening', note: '表 ai_candidate_screen_record；AI_SCREENING_PERSISTENCE_ENABLED 默认关闭' }
  ],
  infrastructure: [
    { name: 'nacos', host: '127.0.0.1', port: 8848, configured: false, status: 'OPTIONAL', note: '本地 demo 可关闭注册中心' },
    { name: 'mysql', host: 'mysql', port: 3306, configured: false, status: 'OPTIONAL' },
    { name: 'redis', host: 'redis', port: 6379, configured: false, status: 'OPTIONAL' },
    { name: 'minio', host: 'minio', port: 9000, configured: false, status: 'OPTIONAL' },
    { name: 'rocketmq', host: '127.0.0.1', port: 9876, configured: false, status: 'OPTIONAL' }
  ],
  warnings: [
    '前端未配置 VITE_API_BASE_URL 时展示演示状态',
    '真实三虚拟机部署请通过 /api/admin/system/status 查看后端环境'
  ]
}

const fallbackDeploymentTopology: DeploymentTopology = {
  generatedAt: new Date().toISOString(),
  profile: 'frontend-demo',
  environment: 'frontend-demo',
  nodes: [
    {
      id: 'vm1',
      name: 'VM1',
      host: '192.168.56.11',
      role: '接入层与注册中心',
      services: [
        { name: 'frontend', displayName: '前端入口', port: 80, healthUrl: 'http://192.168.56.11/', status: 'CONFIGURED', note: 'Nginx 承载前端并反代 API 网关' },
        { name: 'gateway-service', displayName: 'API 网关', port: 8080, healthUrl: 'http://192.168.56.11:8080/actuator/health', status: 'CONFIGURED' },
        { name: 'nacos', displayName: 'Nacos 注册中心', port: 8848, healthUrl: 'http://192.168.56.11:8848/nacos', status: 'CONFIGURED' }
      ]
    },
    {
      id: 'vm2',
      name: 'VM2',
      host: '192.168.56.12',
      role: '业务微服务',
      services: [
        { name: 'auth-service', displayName: '认证服务', port: 8101, healthUrl: 'http://192.168.56.12:8101/actuator/health', status: 'CONFIGURED' },
        { name: 'user-service', displayName: '用户与管理服务', port: 8102, healthUrl: 'http://192.168.56.12:8102/actuator/health', status: 'CONFIGURED' },
        { name: 'resume-service', displayName: '简历服务', port: 8103, healthUrl: 'http://192.168.56.12:8103/actuator/health', status: 'CONFIGURED' },
        { name: 'job-service', displayName: '岗位服务', port: 8104, healthUrl: 'http://192.168.56.12:8104/actuator/health', status: 'CONFIGURED' },
        { name: 'match-service', displayName: '匹配服务', port: 8105, healthUrl: 'http://192.168.56.12:8105/actuator/health', status: 'CONFIGURED' },
        { name: 'delivery-service', displayName: '投递服务', port: 8107, healthUrl: 'http://192.168.56.12:8107/actuator/health', status: 'CONFIGURED' }
      ]
    },
    {
      id: 'vm3',
      name: 'VM3',
      host: '192.168.56.13',
      role: 'AI 与基础设施',
      services: [
        { name: 'mysql', displayName: 'MySQL', port: 3306, healthUrl: 'tcp://192.168.56.13:3306', status: 'CONFIGURED' },
        { name: 'redis', displayName: 'Redis', port: 6379, healthUrl: 'tcp://192.168.56.13:6379', status: 'CONFIGURED' },
        { name: 'minio', displayName: 'MinIO', port: 9000, healthUrl: 'http://192.168.56.13:9000/minio/health/live', status: 'CONFIGURED' },
        { name: 'rocketmq', displayName: 'RocketMQ', port: 9876, healthUrl: 'tcp://192.168.56.13:9876', status: 'CONFIGURED', note: 'Broker 默认开放 10909/10911' },
        { name: 'ai-service', displayName: 'AI 服务', port: 8106, healthUrl: 'http://192.168.56.13:8106/actuator/health', status: 'CONFIGURED' }
      ]
    }
  ],
  warnings: ['前端未配置网关时展示三虚拟机默认部署拓扑']
}

function normalizeMetadataText(value?: string | null) {
  return typeof value === 'string' ? value.trim() : ''
}

function normalizeParsedTextLength(value?: number | null) {
  if (value === undefined || value === null) {
    return undefined
  }
  const parsed = Number(value)
  return Number.isFinite(parsed) && parsed >= 0 ? parsed : undefined
}

function normalizeResumeParseMetadata(
  source?: ResumeParseMetadata | null,
  fallback?: ResumeParseMetadata | null
): ResumeParseMetadata {
  const sourceFormat = normalizeMetadataText(source?.resumeSourceFormat)
    || normalizeMetadataText(source?.sourceFormat)
    || normalizeMetadataText(fallback?.resumeSourceFormat)
    || normalizeMetadataText(fallback?.sourceFormat)
  const parseStatus = normalizeMetadataText(source?.resumeParseStatus)
    || normalizeMetadataText(source?.parseStatus)
    || normalizeMetadataText(fallback?.resumeParseStatus)
    || normalizeMetadataText(fallback?.parseStatus)
  const parsedTextLength = normalizeParsedTextLength(
    source?.resumeParsedTextLength
      ?? source?.parsedTextLength
      ?? fallback?.resumeParsedTextLength
      ?? fallback?.parsedTextLength
  )

  return {
    ...(sourceFormat ? { sourceFormat, resumeSourceFormat: sourceFormat } : {}),
    ...(parseStatus ? { parseStatus, resumeParseStatus: parseStatus } : {}),
    ...(parsedTextLength !== undefined ? { parsedTextLength, resumeParsedTextLength: parsedTextLength } : {})
  }
}

function deliveryResumeId(input: DeliveryResumeInput) {
  return typeof input === 'string' ? input : input.resumeId || 'R001'
}

function deliveryResumePayloadMetadata(input: DeliveryResumeInput) {
  const metadata = typeof input === 'string' ? {} : normalizeResumeParseMetadata(input)
  return {
    ...(metadata.resumeSourceFormat ? { resumeSourceFormat: metadata.resumeSourceFormat } : {}),
    ...(metadata.resumeParseStatus ? { resumeParseStatus: metadata.resumeParseStatus } : {}),
    ...(metadata.resumeParsedTextLength !== undefined ? { resumeParsedTextLength: metadata.resumeParsedTextLength } : {})
  }
}

function withResumeParseMetadata<T extends ResumeParseMetadata>(record: T, fallback?: ResumeParseMetadata | null): T {
  return {
    ...record,
    ...normalizeResumeParseMetadata(record, fallback)
  }
}

async function request<T>(path: string, init: RequestInit, fallback: T): Promise<T> {
  if (!shouldUseApi(path)) {
    return fallback
  }

  try {
    const response = await fetch(resolveRequestPath(path), {
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

export function getResume(resumeId = 'R001') {
  return request<ResumeSummary>(`/api/resumes/${resumeId}`, { method: 'GET' }, fallbackResume)
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

export function screenCandidate(payload: CandidateScreenRequest) {
  return request<CandidateScreenResult>('/api/ai/candidates/screen', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, {
    ...fallbackCandidateScreen,
    deliveryId: payload.deliveryId,
    studentId: payload.studentId,
    jobId: payload.jobId,
    resumeSourceFormat: payload.resumeSourceFormat || fallbackCandidateScreen.resumeSourceFormat,
    resumeParseStatus: payload.resumeParseStatus || fallbackCandidateScreen.resumeParseStatus,
    resumeParsedTextLength: payload.resumeParsedTextLength ?? fallbackCandidateScreen.resumeParsedTextLength
  })
}

export function listCandidateScreenRecords(companyId = 'C001', deliveryId?: string) {
  const params = new URLSearchParams()
  if (companyId) {
    params.set('companyId', companyId)
  }
  if (deliveryId) {
    params.set('deliveryId', deliveryId)
  }
  const query = params.toString()
  const path = `/api/ai/candidates/screenings${query ? `?${query}` : ''}`
  return request<CandidateScreenRecord[]>(path, { method: 'GET' },
    fallbackCandidateScreenRecords.filter((record) =>
      (!companyId || record.companyId === companyId) && (!deliveryId || record.deliveryId === deliveryId)))
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

export function getAiStatus() {
  return request<AiModuleStatus>('/api/ai/status', { method: 'GET' }, fallbackAiModuleStatus)
}

export function listInterviewRecords(studentId = 'S001') {
  return request<InterviewRecord[]>(`/api/ai/interview/records?studentId=${encodeURIComponent(studentId)}`, {
    method: 'GET'
  }, fallbackInterviewRecords.filter((record) => record.studentId === studentId))
}

export async function createDelivery(resume: DeliveryResumeInput = 'R001', jobId = 'J001') {
  const resumeId = deliveryResumeId(resume)
  const resumeMetadata = normalizeResumeParseMetadata(typeof resume === 'string' ? undefined : resume)
  const payloadMetadata = deliveryResumePayloadMetadata(resume)
  const record = await request<DeliveryRecord>('/api/deliveries', {
    method: 'POST',
    body: JSON.stringify({ studentId: 'S001', resumeId, jobId, ...payloadMetadata })
  }, {
    deliveryId: `D${Date.now().toString().slice(-6)}`,
    studentId: 'S001',
    resumeId,
    jobId,
    companyId: fallbackJobs.find((job) => job.jobId === jobId)?.companyId || 'C001',
    status: 'SUBMITTED',
    ...resumeMetadata,
    createdAt: new Date().toISOString()
  })
  return withResumeParseMetadata(record, resumeMetadata)
}

export function listDeliveries() {
  return request<DeliveryRecord[]>('/api/deliveries/my', { method: 'GET' },
    fallbackDeliveries.filter((delivery) => delivery.studentId === 'S001'))
    .then((records) => records.map((record) => withResumeParseMetadata(record)))
}

export function listCompanyDeliveries(companyId = 'C001') {
  return request<DeliveryRecord[]>(`/api/deliveries/company?companyId=${encodeURIComponent(companyId)}`, { method: 'GET' },
    fallbackDeliveries.filter((delivery) => delivery.companyId === companyId))
    .then((records) => records.map((record) => withResumeParseMetadata(record)))
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

export function getSystemStatus() {
  return request<SystemStatus>('/api/admin/system/status', { method: 'GET' }, fallbackSystemStatus)
}

export function getDeploymentTopology() {
  return request<DeploymentTopology>('/api/admin/system/topology', { method: 'GET' }, fallbackDeploymentTopology)
}
