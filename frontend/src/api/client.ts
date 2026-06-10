export type Role = 'STUDENT' | 'COMPANY' | 'ADMIN'

export interface LoginResponse {
  token: string
  userId: string
  displayName: string
  role: Role
}

export interface AuthSession {
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

export type CandidateScreenTaskStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED'
export type CandidateScreenTaskSource = 'DEMO' | 'RUNTIME' | 'ROCKETMQ'

export interface CandidateScreenTask extends ResumeParseMetadata {
  taskId: string
  deliveryId: string
  companyId: string
  studentId: string
  resumeId: string
  jobId: string
  status: CandidateScreenTaskStatus
  source: CandidateScreenTaskSource
  message: string
  result?: CandidateScreenResult
  createdAt: string
  updatedAt: string
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

export interface AiObservabilitySummary {
  provider: string
  model: string
  configured: boolean
  totalCalls: number
  successCalls: number
  failedCalls: number
  mockedCalls: number
  successRate: number
  averageLatencyMs: number
  recentCalls: AiCallRecord[]
  generatedAt: string
}

export interface AiCallRecord {
  callId: string
  operation: string
  provider: string
  model: string
  success: boolean
  mocked: boolean
  durationMs: number
  promptChars: number
  responseChars: number
  fallbackReason?: string | null
  createdAt: string
}

export interface AiCallListQuery {
  limit?: number
  provider?: string
  success?: boolean
}

export interface AiSearchRequest {
  query: string
  role?: string
  limit?: number
}

export interface AiSearchResult {
  id: string
  type: string
  title: string
  owner: string
  summary: string
  score: number
  highlights: string[]
}

export interface AiSearchResponse {
  query: string
  results: AiSearchResult[]
  generatedAt: string
}

export type AdminAuditEntityType = 'STUDENT' | 'JOB' | 'DELIVERY' | 'AI_SCREENING' | 'AI_INTERVIEW'
export type AdminAuditRiskLevel = 'LOW' | 'MEDIUM' | 'HIGH'

export interface AdminAuditQuery {
  keyword?: string
  entityType?: AdminAuditEntityType | ''
  studentId?: string
  companyId?: string
  jobId?: string
  limit?: number
}

export interface AdminAuditMetric {
  key: string
  label: string
  value: number
  unit?: string
}

export interface AdminAuditRecord {
  auditId: string
  entityType: AdminAuditEntityType
  entityId: string
  title: string
  ownerId: string
  studentId?: string
  companyId?: string
  jobId?: string
  service: string
  status: string
  riskLevel: AdminAuditRiskLevel
  score?: number
  summary: string
  tags: string[]
  occurredAt: string
}

export interface AdminAuditOverview {
  generatedAt: string
  source: string
  query: AdminAuditQuery
  metrics: AdminAuditMetric[]
  records: AdminAuditRecord[]
  warnings: string[]
}

export interface AdminAuditExportResult {
  exportId: string
  format: 'CSV'
  fileName: string
  downloadUrl: string
  expiresAt: string
  rowCount: number
  generatedAt: string
  query: AdminAuditQuery
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
export type AccountStatus = 'ACTIVE' | 'DISABLED' | 'LOCKED'
export type PermissionCode =
  | 'student:profile:read'
  | 'student:resume:write'
  | 'student:delivery:write'
  | 'student:interview:write'
  | 'company:job:write'
  | 'company:delivery:read'
  | 'company:screening:write'
  | 'admin:dashboard:read'
  | 'admin:account:read'
  | 'admin:account:write'
  | 'admin:rbac:read'
  | 'admin:audit:read'
  | 'admin:audit:export'

export interface AccountSummary {
  accountId: string
  username: string
  displayName: string
  role: Role
  status: AccountStatus
  permissions: PermissionCode[]
  createdAt: string
  updatedAt: string
}

export interface AccountListQuery {
  role?: Role
  status?: AccountStatus
  keyword?: string
}

export interface CreateAccountRequest {
  username: string
  password: string
  displayName: string
  role: Role
  status?: AccountStatus
  permissions?: PermissionCode[]
}

export interface ChangePasswordRequest {
  accountId: string
  oldPassword?: string
  newPassword: string
}

export interface CurrentPermissions {
  userId: string
  role: Role
  permissions: PermissionCode[]
}

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

export interface DeploymentGuideStep {
  order: number
  nodeId: string
  nodeName: string
  title: string
  purpose: string
  commands: string[]
  verifyUrls: string[]
  expectedResult: string
  troubleshooting: string[]
}

export interface DeploymentAcceptanceCheck {
  name: string
  command: string
  expectedResult: string
}

export interface DeploymentGuide {
  generatedAt: string
  environment: string
  summary: string
  steps: DeploymentGuideStep[]
  acceptanceChecks: DeploymentAcceptanceCheck[]
  warnings: string[]
}

interface ApiResponse<T> {
  code: number
  message: string
  data: T
}

type DeliveryResumeInput = string | (ResumeParseMetadata & { resumeId?: string })
const authStorageKeys = ['token', 'userId', 'role', 'displayName'] as const
const roleValues: Role[] = ['STUDENT', 'COMPANY', 'ADMIN']

function trimTrailingSlash(value: string) {
  return value.replace(/\/+$/, '')
}

function getEnvValue(key: string) {
  return (import.meta.env[key] || '').trim()
}

function isRole(value: string | null): value is Role {
  return Boolean(value && roleValues.includes(value as Role))
}

function fallbackUserIdForRole(role: Role) {
  if (role === 'COMPANY') {
    return 'C001'
  }
  if (role === 'ADMIN') {
    return 'A001'
  }
  return 'S001'
}

export function saveAuthSession(result: LoginResponse) {
  localStorage.setItem('token', result.token)
  localStorage.setItem('userId', result.userId)
  localStorage.setItem('role', result.role)
  localStorage.setItem('displayName', result.displayName)
}

export function getAuthSession(): AuthSession | null {
  const token = localStorage.getItem('token')?.trim()
  const roleValue = localStorage.getItem('role')
  if (!token || !isRole(roleValue)) {
    return null
  }
  const userId = localStorage.getItem('userId')?.trim() || fallbackUserIdForRole(roleValue)
  const displayName = localStorage.getItem('displayName')?.trim() || userId
  return {
    token,
    userId,
    displayName,
    role: roleValue
  }
}

export function clearAuthSession() {
  authStorageKeys.forEach((key) => localStorage.removeItem(key))
}

export function currentRole() {
  return getAuthSession()?.role
}

export function currentStudentId(defaultValue = 'S001') {
  const session = getAuthSession()
  return session?.role === 'STUDENT' ? session.userId : defaultValue
}

export function currentCompanyId(defaultValue = 'C001') {
  const session = getAuthSession()
  return session?.role === 'COMPANY' ? session.userId : defaultValue
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

function buildFallbackCandidateScreenTask(
  payload: CandidateScreenRequest,
  status: CandidateScreenTaskStatus = 'COMPLETED'
): CandidateScreenTask {
  const now = new Date().toISOString()
  const result: CandidateScreenResult = {
    ...fallbackCandidateScreen,
    deliveryId: payload.deliveryId,
    studentId: payload.studentId,
    jobId: payload.jobId,
    resumeSourceFormat: payload.resumeSourceFormat || fallbackCandidateScreen.resumeSourceFormat,
    resumeParseStatus: payload.resumeParseStatus || fallbackCandidateScreen.resumeParseStatus,
    resumeParsedTextLength: payload.resumeParsedTextLength ?? fallbackCandidateScreen.resumeParsedTextLength
  }
  return {
    taskId: `TASK-DEMO-${payload.deliveryId}`,
    deliveryId: payload.deliveryId,
    companyId: payload.companyId || currentCompanyId(),
    studentId: payload.studentId,
    resumeId: payload.resumeId,
    jobId: payload.jobId,
    status,
    source: 'DEMO',
    message: status === 'COMPLETED' ? 'Demo async screening task completed.' : 'Demo async screening task accepted.',
    result: status === 'COMPLETED' ? result : undefined,
    resumeSourceFormat: result.resumeSourceFormat,
    resumeParseStatus: result.resumeParseStatus,
    resumeParsedTextLength: result.resumeParsedTextLength,
    createdAt: now,
    updatedAt: now
  }
}

const fallbackCandidateScreenTasks: CandidateScreenTask[] = [
  {
    ...buildFallbackCandidateScreenTask({
      deliveryId: 'D001',
      companyId: 'C001',
      studentId: 'S001',
      resumeId: 'R001',
      jobId: 'J001',
      targetRole: 'Java backend intern',
      skills: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      projects: ['Campus recruitment platform'],
      jobRequirements: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      resumeSummary: 'Demo candidate with Java Web project experience.',
      jobDescription: 'Backend API development internship.',
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 62
    }),
    taskId: 'TASK-DEMO-001',
    createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 42 * 60 * 1000).toISOString()
  },
  {
    ...buildFallbackCandidateScreenTask({
      deliveryId: 'D002',
      companyId: 'C001',
      studentId: 'S002',
      resumeId: 'R002',
      jobId: 'J001',
      targetRole: 'Java backend intern',
      skills: ['Java', 'MySQL'],
      projects: ['Online exam platform'],
      jobRequirements: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      resumeSummary: 'Demo candidate task still running.',
      jobDescription: 'Backend API development internship.',
      resumeSourceFormat: 'DOCX',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 48
    }, 'RUNNING'),
    taskId: 'TASK-DEMO-002',
    message: 'Demo async screening task is running.',
    createdAt: new Date(Date.now() - 8 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 2 * 60 * 1000).toISOString()
  },
  {
    ...buildFallbackCandidateScreenTask({
      deliveryId: 'D003',
      companyId: 'C001',
      studentId: 'S003',
      resumeId: 'R003',
      jobId: 'J001',
      targetRole: 'Java backend intern',
      skills: ['Java'],
      projects: ['Course management platform'],
      jobRequirements: ['Java', 'Spring Boot', 'MySQL', 'Redis'],
      resumeSummary: 'Demo candidate task failed before model execution.',
      jobDescription: 'Backend API development internship.',
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'UNPARSED',
      resumeParsedTextLength: 0
    }, 'FAILED'),
    taskId: 'TASK-DEMO-003',
    message: 'Demo async screening task failed. Retry is available.',
    createdAt: new Date(Date.now() - 25 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 24 * 60 * 1000).toISOString()
  }
]

function findFallbackCandidateScreenTask(taskId: string, companyId = currentCompanyId()) {
  const normalizedTaskId = taskId.trim()
  return fallbackCandidateScreenTasks.find((task) =>
    task.taskId === normalizedTaskId && (!companyId || task.companyId === companyId))
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

const fallbackAiModuleStatus: AiModuleStatus = {
  provider: 'dashscope',
  model: 'qwen-plus',
  configured: false,
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  capabilities: ['resume-analysis', 'job-analysis', 'match-analysis', 'candidate-screening', 'interview-question-generation', 'interview-feedback', 'observability', 'intelligent-search'],
  fallbackReason: 'AI service is offline or DASHSCOPE_API_KEY is not configured'
}

const fallbackAiObservabilitySummary: AiObservabilitySummary = {
  provider: 'dashscope',
  model: 'qwen-plus',
  configured: false,
  totalCalls: 128,
  successCalls: 119,
  failedCalls: 9,
  mockedCalls: 20,
  successRate: 92.97,
  averageLatencyMs: 860,
  recentCalls: [],
  generatedAt: new Date().toISOString()
}

const fallbackAiCallRecords: AiCallRecord[] = [
  {
    callId: 'AI-CALL-001',
    operation: 'candidate-screening',
    provider: 'dashscope',
    model: 'qwen-plus',
    success: true,
    mocked: false,
    durationMs: 742,
    promptChars: 1180,
    responseChars: 620,
    createdAt: new Date(Date.now() - 12 * 60 * 1000).toISOString()
  },
  {
    callId: 'AI-CALL-002',
    operation: 'semantic-search',
    provider: 'local-semantic-search',
    model: 'keyword-ranker-v1',
    success: true,
    mocked: false,
    durationMs: 18,
    promptChars: 19,
    responseChars: 2,
    createdAt: new Date(Date.now() - 32 * 60 * 1000).toISOString()
  },
  {
    callId: 'AI-CALL-003',
    operation: 'analyze',
    provider: 'dashscope',
    model: 'qwen-plus',
    success: true,
    mocked: true,
    durationMs: 43,
    promptChars: 260,
    responseChars: 160,
    fallbackReason: 'DASHSCOPE_API_KEY is not configured',
    createdAt: new Date(Date.now() - 68 * 60 * 1000).toISOString()
  }
]

const fallbackAiSearchResults: AiSearchResult[] = [
  {
    id: 'S001',
    type: 'student',
    title: 'Java backend internship candidate',
    owner: 'Demo Student',
    summary: 'Resume shows Java, Spring Boot, MySQL, Redis, and delivery experience for backend internship matching.',
    score: 91,
    highlights: ['Java', 'Spring Boot', 'Redis']
  },
  {
    id: 'J001',
    type: 'job',
    title: 'Java backend intern',
    owner: 'Demo Company HR',
    summary: 'Position requires Java Web development, database basics, cache usage, and API implementation.',
    score: 87,
    highlights: ['backend', 'MySQL', 'API']
  },
  {
    id: 'D001',
    type: 'delivery',
    title: 'S001 delivery to J001',
    owner: 'C001',
    summary: 'Submitted delivery with parsed PDF resume and candidate screening recommendation available.',
    score: 83,
    highlights: ['submitted', 'screening', 'PDF']
  }
]

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

const fallbackAdminAuditRecords: AdminAuditRecord[] = [
  {
    auditId: 'AUD-STUDENT-001',
    entityType: 'STUDENT',
    entityId: 'S001',
    title: 'Demo Student resume profile',
    ownerId: 'S001',
    studentId: 'S001',
    service: 'user-service',
    status: 'ACTIVE',
    riskLevel: 'LOW',
    score: 86,
    summary: 'Student profile and resume parse metadata are available for recruitment review.',
    tags: ['profile', 'resume', 'PDF'],
    occurredAt: '2026-06-10T08:00:00Z'
  },
  {
    auditId: 'AUD-JOB-001',
    entityType: 'JOB',
    entityId: 'J001',
    title: 'Java backend intern',
    ownerId: 'C001',
    companyId: 'C001',
    jobId: 'J001',
    service: 'job-service',
    status: 'PUBLISHED',
    riskLevel: 'LOW',
    score: 87,
    summary: 'Job description has AI summary, required skills, and active delivery traffic.',
    tags: ['Java', 'Spring Boot', 'published'],
    occurredAt: '2026-06-10T08:08:00Z'
  },
  {
    auditId: 'AUD-DELIVERY-001',
    entityType: 'DELIVERY',
    entityId: 'D001',
    title: 'S001 delivery to J001',
    ownerId: 'C001',
    studentId: 'S001',
    companyId: 'C001',
    jobId: 'J001',
    service: 'delivery-service',
    status: 'SUBMITTED',
    riskLevel: 'MEDIUM',
    summary: 'Delivery keeps resume parse snapshot for downstream candidate screening.',
    tags: ['submitted', 'TEXT_EXTRACTED', 'screening-ready'],
    occurredAt: '2026-06-10T08:16:00Z'
  },
  {
    auditId: 'AUD-AI-SCREEN-001',
    entityType: 'AI_SCREENING',
    entityId: 'CS-DEMO-001',
    title: 'Candidate screening recommendation',
    ownerId: 'C001',
    studentId: 'S001',
    companyId: 'C001',
    jobId: 'J001',
    service: 'ai-service',
    status: 'MOCKED',
    riskLevel: 'MEDIUM',
    score: 86,
    summary: 'AI screening used deterministic fallback because the AI provider is not configured.',
    tags: ['candidate-screening', 'mocked', 'DashScope'],
    occurredAt: '2026-06-10T08:24:00Z'
  },
  {
    auditId: 'AUD-AI-INTERVIEW-001',
    entityType: 'AI_INTERVIEW',
    entityId: 'IR-DEMO-001',
    title: 'Mock interview feedback',
    ownerId: 'S001',
    studentId: 'S001',
    jobId: 'J001',
    service: 'ai-service',
    status: 'COMPLETED',
    riskLevel: 'LOW',
    score: 82,
    summary: 'Interview answer feedback is stored without exposing raw prompt or credential data.',
    tags: ['interview', 'feedback', 'redacted'],
    occurredAt: '2026-06-10T08:32:00Z'
  }
]

const fallbackAdminAuditOverviewBase = {
  generatedAt: '2026-06-10T08:40:00Z',
  source: 'frontend-demo',
  warnings: ['Gateway is not configured; showing deterministic frontend audit fallback data.']
}

const rolePermissions: Record<Role, PermissionCode[]> = {
  STUDENT: ['student:profile:read', 'student:resume:write', 'student:delivery:write', 'student:interview:write'],
  COMPANY: ['company:job:write', 'company:delivery:read', 'company:screening:write'],
  ADMIN: ['admin:dashboard:read', 'admin:account:read', 'admin:account:write', 'admin:rbac:read', 'admin:audit:read', 'admin:audit:export']
}

const fallbackAccounts: AccountSummary[] = [
  {
    accountId: 'S001',
    username: 'student',
    displayName: 'Demo Student',
    role: 'STUDENT',
    status: 'ACTIVE',
    permissions: rolePermissions.STUDENT,
    createdAt: new Date(Date.now() - 10 * 24 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
  },
  {
    accountId: 'C001',
    username: 'company',
    displayName: 'Demo Company HR',
    role: 'COMPANY',
    status: 'ACTIVE',
    permissions: rolePermissions.COMPANY,
    createdAt: new Date(Date.now() - 9 * 24 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
  },
  {
    accountId: 'A001',
    username: 'admin',
    displayName: 'Demo Admin',
    role: 'ADMIN',
    status: 'ACTIVE',
    permissions: rolePermissions.ADMIN,
    createdAt: new Date(Date.now() - 8 * 24 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 4 * 60 * 60 * 1000).toISOString()
  }
]

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

const fallbackDeploymentGuide: DeploymentGuide = {
  generatedAt: new Date().toISOString(),
  environment: 'frontend-demo',
  summary: '按 VM3 基础设施、VM1 接入层、VM2 业务服务、最终验收的顺序完成三虚拟机部署。',
  steps: [
    {
      order: 1,
      nodeId: 'vm3',
      nodeName: 'VM3 Data and AI Node',
      title: '启动数据与 AI 节点',
      purpose: '先启动 MySQL、Redis、MinIO、RocketMQ 和 AI 服务，保证业务服务依赖可用。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d',
        'docker compose -f deploy/docker-compose.vm3.yml ps'
      ],
      verifyUrls: [
        'tcp://192.168.56.13:3306',
        'tcp://192.168.56.13:6379',
        'http://192.168.56.13:9000/minio/health/live',
        'http://192.168.56.13:8106/actuator/health'
      ],
      expectedResult: 'MySQL、Redis、MinIO、RocketMQ 和 ai-service 均可达。',
      troubleshooting: ['先确认 VM3 防火墙端口开放', 'AI 服务异常时检查 AI 服务凭证是否已在 .env 中配置']
    },
    {
      order: 2,
      nodeId: 'vm1',
      nodeName: 'VM1 Edge Node',
      title: '启动注册中心与接入层',
      purpose: '启动 Nacos、Gateway 和前端入口，建立统一访问入口。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d',
        'docker compose -f deploy/docker-compose.vm1.yml ps'
      ],
      verifyUrls: [
        'http://192.168.56.11:8848/nacos',
        'http://192.168.56.11:8080/actuator/health',
        'http://192.168.56.11/'
      ],
      expectedResult: 'Nacos、Gateway 和前端容器处于 running 状态。',
      troubleshooting: ['Gateway 异常时检查 VM2/VM3 地址是否写入 deploy/three-vm.env', '前端打不开时检查 80 端口是否被占用']
    },
    {
      order: 3,
      nodeId: 'vm2',
      nodeName: 'VM2 Business Services Node',
      title: '启动业务微服务',
      purpose: '启动认证、用户、简历、岗位、匹配和投递服务。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm2.yml up -d',
        'docker compose -f deploy/docker-compose.vm2.yml ps'
      ],
      verifyUrls: [
        'http://192.168.56.12:8101/actuator/health',
        'http://192.168.56.12:8102/actuator/health',
        'http://192.168.56.12:8103/actuator/health',
        'http://192.168.56.12:8104/actuator/health',
        'http://192.168.56.12:8105/actuator/health',
        'http://192.168.56.12:8107/actuator/health'
      ],
      expectedResult: 'VM2 六个业务服务健康检查返回 UP。',
      troubleshooting: ['业务服务无法连接数据库时检查 VM3 MySQL 地址和账号环境变量', '服务未注册时检查 NACOS_SERVER_ADDR 指向 VM1']
    },
    {
      order: 4,
      nodeId: 'acceptance',
      nodeName: 'Deployment Acceptance',
      title: '执行部署验收',
      purpose: '用健康检查和 API smoke 验证三机链路可以用于演示。',
      commands: [
        '.\\scripts\\check-three-vm-health.ps1 -EnvFile .\\deploy\\three-vm.env -TimeoutSeconds 5',
        '.\\scripts\\check-api-smoke.ps1 -BaseUrl http://192.168.56.11:8080 -TimeoutSeconds 8',
        'bash scripts/check-three-vm-health.sh --env-file deploy/three-vm.env --timeout 5',
        'bash scripts/check-api-smoke.sh --base-url http://192.168.56.11:8080 --timeout 8'
      ],
      verifyUrls: ['http://192.168.56.11:8080/api/admin/system/status', 'http://192.168.56.11:8080/api/admin/system/topology'],
      expectedResult: '健康检查和关键 API smoke 均成功退出。',
      troubleshooting: ['若 smoke 失败，先打开系统状态页定位失败模块', '若跨 VM 不通，优先检查虚拟机网络模式和 IP 配置']
    }
  ],
  acceptanceChecks: [
    {
      name: '三机健康检查',
      command: '.\\scripts\\check-three-vm-health.ps1 -EnvFile .\\deploy\\three-vm.env -TimeoutSeconds 5',
      expectedResult: '所有配置的前端、网关、业务服务、AI 服务和基础设施检查通过。'
    },
    {
      name: '关键 API smoke',
      command: '.\\scripts\\check-api-smoke.ps1 -BaseUrl http://192.168.56.11:8080 -TimeoutSeconds 8',
      expectedResult: '登录、简历、岗位、匹配、投递、AI 与管理端接口返回成功响应。'
    }
  ],
  warnings: ['前端未连接网关时展示默认部署向导；真实部署请以后端返回的 host/port 为准']
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
      headers: requestHeaders(init)
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

function requestHeaders(init: RequestInit) {
  const headers = new Headers()
  if (!(init.body instanceof FormData)) {
    headers.set('Content-Type', 'application/json')
  }
  Object.entries(authorizationHeader()).forEach(([key, value]) => headers.set(key, value))
  new Headers(init.headers).forEach((value, key) => headers.set(key, value))
  return headers
}

function authorizationHeader(): Record<string, string> {
  const token = getAuthSession()?.token || localStorage.getItem('token')?.trim()
  if (!token) {
    return {}
  }
  return {
    Authorization: token.toLowerCase().startsWith('bearer ') ? token : `Bearer ${token}`
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
  const payload = { ...job, companyId: job.companyId || currentCompanyId() }
  const created = { ...fallbackJobs[0], ...payload, jobId: `J${Date.now().toString().slice(-6)}` }
  return request<JobSummary>('/api/jobs', { method: 'POST', body: JSON.stringify(payload) }, created)
}

export function analyzeJob(jobId: string) {
  return request<JobSummary>(`/api/jobs/${jobId}/analyze`, { method: 'POST' }, fallbackJobs[0])
}

export function matchResumeJob(resumeId = 'R001', jobId = 'J001') {
  const studentId = currentStudentId()
  return request<MatchResult>('/api/matches/resume-job', {
    method: 'POST',
    body: JSON.stringify({ resumeId, jobId, studentId })
  }, { ...fallbackMatch, resumeId, jobId, studentId })
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

export function createCandidateScreenTask(payload: CandidateScreenRequest) {
  return request<CandidateScreenTask>('/api/ai/candidates/screen/tasks', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, buildFallbackCandidateScreenTask(payload))
}

export function listCandidateScreenTasks(companyId = currentCompanyId(), deliveryId?: string) {
  const params = new URLSearchParams()
  if (companyId) {
    params.set('companyId', companyId)
  }
  if (deliveryId) {
    params.set('deliveryId', deliveryId)
  }
  const query = params.toString()
  const path = `/api/ai/candidates/screen/tasks${query ? `?${query}` : ''}`
  return request<CandidateScreenTask[]>(path, { method: 'GET' },
    fallbackCandidateScreenTasks.filter((task) =>
      (!companyId || task.companyId === companyId) && (!deliveryId || task.deliveryId === deliveryId)))
}

export function getCandidateScreenTask(taskId: string, companyId = currentCompanyId()) {
  const params = new URLSearchParams()
  if (companyId) {
    params.set('companyId', companyId)
  }
  const query = params.toString()
  const fallback = findFallbackCandidateScreenTask(taskId, companyId)
    || { ...fallbackCandidateScreenTasks[0], taskId }
  return request<CandidateScreenTask>(
    `/api/ai/candidates/screen/tasks/${encodeURIComponent(taskId)}${query ? `?${query}` : ''}`,
    { method: 'GET' },
    fallback)
}

export function retryCandidateScreenTask(taskId: string, companyId = currentCompanyId()) {
  const params = new URLSearchParams()
  if (companyId) {
    params.set('companyId', companyId)
  }
  const query = params.toString()
  const original = findFallbackCandidateScreenTask(taskId, companyId) || fallbackCandidateScreenTasks[0]
  const fallback = original.status === 'FAILED'
    ? {
        ...original,
        taskId: `TASK-DEMO-RETRY-${original.deliveryId}`,
        status: 'PENDING' as CandidateScreenTaskStatus,
        source: 'DEMO' as CandidateScreenTaskSource,
        message: 'Demo retry task accepted.',
        result: undefined,
        updatedAt: new Date().toISOString()
      }
    : {
        ...original,
        message: 'Only failed async screening tasks can be retried.',
        updatedAt: new Date().toISOString()
      }
  return request<CandidateScreenTask>(
    `/api/ai/candidates/screen/tasks/${encodeURIComponent(taskId)}/retry${query ? `?${query}` : ''}`,
    { method: 'POST' },
    fallback)
}

export function listCandidateScreenRecords(companyId = currentCompanyId(), deliveryId?: string) {
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

export function listMyCandidateScreenRecords(studentId = currentStudentId()) {
  const path = `/api/ai/screenings/my?studentId=${encodeURIComponent(studentId)}`
  return request<CandidateScreenRecord[]>(path, { method: 'GET' },
    fallbackCandidateScreenRecords.filter((record) => record.studentId === studentId))
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

export function getAiObservabilitySummary() {
  return request<AiObservabilitySummary>('/api/ai/observability/summary', { method: 'GET' }, fallbackAiObservabilitySummary)
}

export function listAiCallRecords(query: AiCallListQuery = {}) {
  const params = new URLSearchParams()
  const limit = query.limit ?? 20
  params.set('limit', String(limit))
  if (query.provider?.trim()) {
    params.set('provider', query.provider.trim())
  }
  if (query.success !== undefined) {
    params.set('success', String(query.success))
  }
  const fallback = fallbackAiCallRecords
    .filter((record) => !query.provider?.trim() || record.provider === query.provider.trim())
    .filter((record) => query.success === undefined || record.success === query.success)
    .slice(0, limit)
  return request<AiCallRecord[]>(`/api/ai/observability/calls?${params.toString()}`, { method: 'GET' }, fallback)
}

export function searchAiKnowledge(payload: AiSearchRequest) {
  const query = payload.query.trim()
  const limit = payload.limit ?? 5
  const normalized = query.toLowerCase()
  const results = fallbackAiSearchResults
    .filter((item) => !normalized
      || item.title.toLowerCase().includes(normalized)
      || item.summary.toLowerCase().includes(normalized)
      || item.highlights.some((highlight) => highlight.toLowerCase().includes(normalized)))
    .slice(0, limit)
  return request<AiSearchResponse>('/api/ai/search', {
    method: 'POST',
    body: JSON.stringify({ ...payload, query, limit })
  }, {
    query,
    results,
    generatedAt: new Date().toISOString()
  })
}

export function listInterviewRecords(studentId = currentStudentId()) {
  return request<InterviewRecord[]>(`/api/ai/interview/records?studentId=${encodeURIComponent(studentId)}`, {
    method: 'GET'
  }, fallbackInterviewRecords.filter((record) => record.studentId === studentId))
}

export async function createDelivery(resume: DeliveryResumeInput = 'R001', jobId = 'J001') {
  const resumeId = deliveryResumeId(resume)
  const studentId = currentStudentId()
  const resumeMetadata = normalizeResumeParseMetadata(typeof resume === 'string' ? undefined : resume)
  const payloadMetadata = deliveryResumePayloadMetadata(resume)
  const record = await request<DeliveryRecord>('/api/deliveries', {
    method: 'POST',
    body: JSON.stringify({ studentId, resumeId, jobId, ...payloadMetadata })
  }, {
    deliveryId: `D${Date.now().toString().slice(-6)}`,
    studentId,
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
  const studentId = currentStudentId()
  return request<DeliveryRecord[]>('/api/deliveries/my', { method: 'GET' },
    fallbackDeliveries.filter((delivery) => delivery.studentId === studentId))
    .then((records) => records.map((record) => withResumeParseMetadata(record)))
}

export function listCompanyDeliveries(companyId = currentCompanyId()) {
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

export function listAccounts(query: AccountListQuery = {}) {
  const params = new URLSearchParams()
  if (query.role) {
    params.set('role', query.role)
  }
  if (query.status) {
    params.set('status', query.status)
  }
  if (query.keyword?.trim()) {
    params.set('keyword', query.keyword.trim())
  }
  const queryString = params.toString()
  const fallback = fallbackAccounts.filter((account) =>
    (!query.role || account.role === query.role)
    && (!query.status || account.status === query.status)
    && (!query.keyword?.trim()
      || account.username.toLowerCase().includes(query.keyword.trim().toLowerCase())
      || account.displayName.toLowerCase().includes(query.keyword.trim().toLowerCase())))
  return request<AccountSummary[]>(`/api/admin/accounts${queryString ? `?${queryString}` : ''}`, { method: 'GET' }, fallback)
}

export function createAccount(payload: CreateAccountRequest) {
  const account: AccountSummary = {
    accountId: `${payload.role.charAt(0)}${Date.now().toString().slice(-6)}`,
    username: payload.username,
    displayName: payload.displayName,
    role: payload.role,
    status: payload.status || 'ACTIVE',
    permissions: payload.permissions || rolePermissions[payload.role],
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
  return request<AccountSummary>('/api/admin/accounts', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, account)
}

export function updateAccountStatus(accountId: string, status: AccountStatus) {
  const fallback = {
    ...(fallbackAccounts.find((account) => account.accountId === accountId) || fallbackAccounts[0]),
    accountId,
    status,
    updatedAt: new Date().toISOString()
  }
  return request<AccountSummary>(`/api/admin/accounts/${encodeURIComponent(accountId)}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status })
  }, fallback)
}

export function changeAccountPassword(payload: ChangePasswordRequest) {
  return request<boolean>(`/api/accounts/${encodeURIComponent(payload.accountId)}/password`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, true)
}

export function getCurrentPermissions() {
  const session = getAuthSession()
  const role = session?.role || 'STUDENT'
  return request<CurrentPermissions>('/api/auth/permissions', { method: 'GET' }, {
    userId: session?.userId || fallbackUserIdForRole(role),
    role,
    permissions: rolePermissions[role]
  })
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

function normalizeAdminAuditQuery(query: AdminAuditQuery = {}): AdminAuditQuery {
  return {
    ...(query.keyword?.trim() ? { keyword: query.keyword.trim() } : {}),
    ...(query.entityType ? { entityType: query.entityType } : {}),
    ...(query.studentId?.trim() ? { studentId: query.studentId.trim() } : {}),
    ...(query.companyId?.trim() ? { companyId: query.companyId.trim() } : {}),
    ...(query.jobId?.trim() ? { jobId: query.jobId.trim() } : {}),
    limit: query.limit ?? 20
  }
}

function adminAuditQueryString(query: AdminAuditQuery) {
  const params = new URLSearchParams()
  if (query.keyword) {
    params.set('keyword', query.keyword)
  }
  if (query.entityType) {
    params.set('entityType', query.entityType)
  }
  if (query.studentId) {
    params.set('studentId', query.studentId)
  }
  if (query.companyId) {
    params.set('companyId', query.companyId)
  }
  if (query.jobId) {
    params.set('jobId', query.jobId)
  }
  params.set('limit', String(query.limit ?? 20))
  return params.toString()
}

function filterAdminAuditRecords(query: AdminAuditQuery) {
  const keyword = query.keyword?.toLowerCase()
  return fallbackAdminAuditRecords
    .filter((record) => !query.entityType || record.entityType === query.entityType)
    .filter((record) => !query.studentId || record.studentId === query.studentId)
    .filter((record) => !query.companyId || record.companyId === query.companyId)
    .filter((record) => !query.jobId || record.jobId === query.jobId)
    .filter((record) => !keyword
      || record.auditId.toLowerCase().includes(keyword)
      || record.entityId.toLowerCase().includes(keyword)
      || record.title.toLowerCase().includes(keyword)
      || record.summary.toLowerCase().includes(keyword)
      || record.tags.some((tag) => tag.toLowerCase().includes(keyword)))
    .slice(0, query.limit ?? 20)
}

function buildAdminAuditOverviewFallback(query: AdminAuditQuery): AdminAuditOverview {
  const records = filterAdminAuditRecords(query)
  const highRiskCount = records.filter((record) => record.riskLevel === 'HIGH').length
  const aiRecordCount = records.filter((record) => record.entityType.startsWith('AI_')).length
  return {
    ...fallbackAdminAuditOverviewBase,
    query,
    metrics: [
      { key: 'records', label: 'Records', value: records.length },
      { key: 'students', label: 'Students', value: new Set(records.map((record) => record.studentId).filter(Boolean)).size },
      { key: 'jobs', label: 'Jobs', value: new Set(records.map((record) => record.jobId).filter(Boolean)).size },
      { key: 'aiRecords', label: 'AI Records', value: aiRecordCount },
      { key: 'highRisk', label: 'High Risk', value: highRiskCount }
    ],
    records,
    warnings: query.keyword || query.entityType || query.studentId || query.companyId || query.jobId
      ? fallbackAdminAuditOverviewBase.warnings
      : []
  }
}

export function getAdminAuditOverview(query: AdminAuditQuery = {}) {
  const normalizedQuery = normalizeAdminAuditQuery(query)
  const queryString = adminAuditQueryString(normalizedQuery)
  return request<AdminAuditOverview>(`/api/admin/audit/overview?${queryString}`, {
    method: 'GET'
  }, buildAdminAuditOverviewFallback(normalizedQuery))
}

export function exportAdminAudit(query: AdminAuditQuery = {}) {
  const normalizedQuery = normalizeAdminAuditQuery(query)
  const rowCount = filterAdminAuditRecords(normalizedQuery).length
  return request<AdminAuditExportResult>('/api/admin/audit/export', {
    method: 'POST',
    body: JSON.stringify({ ...normalizedQuery, format: 'CSV' })
  }, {
    exportId: 'AUDIT-EXPORT-DEMO-001',
    format: 'CSV',
    fileName: 'admin-audit-overview-demo.csv',
    downloadUrl: '/downloads/admin-audit-overview-demo.csv',
    expiresAt: '2026-06-10T10:40:00Z',
    rowCount,
    generatedAt: fallbackAdminAuditOverviewBase.generatedAt,
    query: normalizedQuery
  })
}

export function getSystemStatus() {
  return request<SystemStatus>('/api/admin/system/status', { method: 'GET' }, fallbackSystemStatus)
}

export function getDeploymentTopology() {
  return request<DeploymentTopology>('/api/admin/system/topology', { method: 'GET' }, fallbackDeploymentTopology)
}

export function getDeploymentGuide() {
  return request<DeploymentGuide>('/api/admin/system/deployment-guide', { method: 'GET' }, fallbackDeploymentGuide)
}
