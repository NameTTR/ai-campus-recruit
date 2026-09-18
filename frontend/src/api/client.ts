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

export interface ResumeAnalyzeRequest {
  targetJob?: string
}

export interface ResumeProfileUpdateRequest {
  education?: string
  skills?: string[]
  projects?: string[]
}

export interface ResumeDiagnosis {
  diagnosisId: string
  resumeId: string
  studentId: string
  targetJob?: string
  diagnosis: string
  score: number
  source?: string
  createdAt: string
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
  status?: string
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
  matchedSkills?: string[]
  missingSkills?: string[]
  analysisSource?: string
  resumeSkillsSnapshot?: string[]
  requiredSkillsSnapshot?: string[]
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
  questionCount?: number
  useRag?: boolean
  knowledgeLimit?: number
}

export interface InterviewQuestion {
  questionId: string
  category: string
  difficulty: string
  question: string
  referencePoints: string[]
  knowledgeReferences?: string[]
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

export interface ResumeRewriteRequest {
  studentId: string
  resumeId: string
  targetRole: string
  resumeSummary: string
  skills: string[]
  projects: string[]
}

export interface ResumeRewriteResponse {
  studentId: string
  resumeId: string
  targetRole: string
  improvedSummary: string
  rewrittenProjects: string[]
  keywordSuggestions: string[]
  missingEvidence: string[]
  actionChecklist: string[]
  mocked: boolean
}

export interface CareerPlanRequest {
  studentId: string
  targetRole: string
  skills: string[]
  interests: string[]
  resumeSummary: string
  timeframeWeeks: number
}

export interface CareerPlanMilestone {
  title: string
  timeframe: string
  goals: string[]
}

export interface CareerPlanResponse {
  studentId: string
  targetRole: string
  readinessScore: number
  summary: string
  milestones: CareerPlanMilestone[]
  skillGaps: string[]
  weeklyActions: string[]
  portfolioTasks: string[]
  interviewFocus: string[]
  mocked: boolean
}

export interface LearningPlanRequest {
  studentId?: string
  resumeId?: string
  jobId?: string
  matchId?: string
  targetRole?: string
  weeklyHours?: number
  durationWeeks?: number
}

export interface LearningTask {
  taskId: string
  week: number
  title: string
  description: string
  estimatedHours: number
  status: string
  feedback?: string
  skillGap?: string
  stage?: string
  acceptanceCriteria?: string
  practiceDeliverable?: string
  completedAt?: string
  updatedAt: string
}

export interface LearningPlan {
  planId: string
  studentId: string
  resumeId?: string
  jobId?: string
  matchId?: string
  targetRole: string
  weeklyHours: number
  durationWeeks: number
  status: string
  version: number
  revisionOfPlanId?: string
  contextSnapshot?: Record<string, unknown>
  tasks: LearningTask[]
  mocked?: boolean
  createdAt: string
  updatedAt: string
}

export interface LearningTaskUpdateRequest {
  status: string
  feedback?: string
}

export interface LearningPlanReplanRequest {
  reason: string
  weeklyHours?: number
  durationWeeks?: number
  interviewSessionId?: string
}

export interface InterviewSessionRequest {
  studentId?: string
  resumeId?: string
  jobId?: string
  matchId?: string
  targetRole?: string
  questionCount?: number
}

export interface InterviewSessionQuestion {
  questionId: string
  order?: number
  mainQuestionId?: string
  followUp?: boolean
  question: string
  category?: string
  difficulty?: string
  referencePoints?: string[]
  source?: string
  generationSource?: string
}

export interface InterviewSessionAnswer {
  questionId: string
  answer: string
  updatedAt?: string
}

export interface InterviewSession {
  sessionId: string
  studentId: string
  resumeId?: string
  jobId?: string
  matchId?: string
  targetRole: string
  status: string
  questions: InterviewSessionQuestion[]
  answers: InterviewSessionAnswer[]
  report?: InterviewSessionReport
  createdAt: string
  updatedAt: string
  completedAt?: string
  contextSnapshot?: Record<string, unknown>
  mocked?: boolean
}

export interface InterviewQuestionFeedback {
  questionId: string
  mocked?: boolean
  score?: number
  strengths?: string[]
  gaps?: string[]
  suggestions?: string[]
  summary?: string
}

export interface InterviewSessionReport {
  sessionId: string
  overallScore: number
  strengths: string[]
  gaps: string[]
  recommendations: string[]
  questionFeedback: InterviewQuestionFeedback[]
  generatedAt: string
  mocked: boolean
}

export interface AiCoachAdviceRequest {
  studentId: string
  targetRole: string
  skills: string[]
  recentDeliveries: string[]
  interviewWeaknesses: string[]
  careerGoal: string
  weeks: number
}

export interface AiCoachAdviceResponse {
  studentId: string
  targetRole: string
  readinessScore: number
  headline: string
  priorityActions: string[]
  riskWarnings: string[]
  learningPath: string[]
  interviewDrills: string[]
  searchKeywords: string[]
  mocked: boolean
}

export interface AiPlanningRecord {
  recordId: string
  studentId: string
  operation: 'resume-rewrite' | 'career-plan' | string
  resumeId?: string | null
  targetRole: string
  resumeRewrite?: ResumeRewriteResponse | null
  careerPlan?: CareerPlanResponse | null
  mocked: boolean
  createdAt: string
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

export interface NotificationMessage {
  notificationId: string
  targetRole: 'STUDENT' | 'COMPANY' | 'ADMIN' | string
  targetUserId: string
  title: string
  content: string
  sourceType: string
  sourceId: string
  read: boolean
  createdAt: string
}

export type InterviewScheduleStatus = 'PROPOSED' | 'CONFIRMED' | 'DECLINED' | 'COMPLETED' | 'CANCELLED'

export interface InterviewScheduleRequest {
  deliveryId: string
  companyId?: string
  studentId?: string
  jobId?: string
  title: string
  startTime: string
  durationMinutes: number
  location: string
  meetingUrl: string
  note: string
}

export interface InterviewSchedule {
  scheduleId: string
  deliveryId: string
  companyId: string
  studentId: string
  jobId: string
  title: string
  startTime: string
  durationMinutes: number
  location: string
  meetingUrl: string
  note: string
  status: InterviewScheduleStatus
  createdAt: string
  updatedAt: string
}

export interface KnowledgeDocumentRequest {
  title: string
  content: string
  category: string
  source: string
  tags: string[]
  roles: string[]
}

export interface KnowledgeDocumentRolesRequest {
  roles: string[]
}

export interface KnowledgeDocumentBatchDeleteResult {
  requestedCount: number
  deletedCount: number
  deletedDocumentIds: string[]
  missingDocumentIds: string[]
}

export interface KnowledgeDocument extends KnowledgeDocumentRequest {
  documentId: string
  createdBy: string
  createdAt: string
}

export type KnowledgeIngestionStatus = 'UPLOADED' | 'PARSING' | 'INDEXING' | 'READY' | 'FAILED' | 'DUPLICATE'

export interface KnowledgeFileUploadRequest {
  file: File
  title: string
  category: string
  source: string
  tags: string[]
  roles: string[]
}

export type KnowledgeFileUploadPhase = 'idle' | 'uploading' | 'processing' | 'completed' | 'failed'

export interface KnowledgeFileUploadProgress {
  phase: KnowledgeFileUploadPhase
  percent: number
  loaded?: number
  total?: number
  message: string
}

export interface KnowledgeFileUploadOptions {
  onProgress?: (progress: KnowledgeFileUploadProgress) => void
}

export interface KnowledgeIngestionQuery {
  status?: KnowledgeIngestionStatus | ''
  limit?: number
}

export interface KnowledgeIngestionJob {
  jobId: string
  fileName: string
  title: string
  category: string
  source: string
  status: KnowledgeIngestionStatus
  message: string
  documentId?: string | null
  chunkCount: number
  vectorCount: number
  error?: string | null
  createdAt: string
  updatedAt: string
}

export interface KnowledgeVectorStatus {
  provider: string
  configured: boolean
  connected: boolean
  collectionName: string
  indexName: string
  status: string
  indexStatus?: string
  metricType: string
  dimension: number
  documentCount: number
  chunkCount: number
  vectorCount: number
  lastIngestedAt?: string | null
  generatedAt: string
  warnings: string[]
}

interface BackendKnowledgeVectorStatus {
  provider: string
  enabled: boolean
  available: boolean
  endpoint: string
  collection: string
  dimension: number
  indexedChunkCount: number
  fallbackReason?: string | null
  checkedAt: string
}

export interface KnowledgeBaseStats {
  documentCount: number
  chunkCount: number
  categoryCounts: Record<string, number>
  roleCounts: Record<string, number>
  sourceCounts: Record<string, number>
  tagCounts: Record<string, number>
  corpusVersion: string
  seedEnabled: boolean
  persistentStore: boolean
  generatedAt: string
}

export interface KnowledgeSearchRequest {
  query: string
  role?: string
  limit?: number
}

export interface KnowledgeAnswerRequest extends KnowledgeSearchRequest {
  useAi?: boolean
}

export interface KnowledgeCitation {
  documentId: string
  chunkId: string
  title: string
  source: string
  score: number
  snippet: string
}

export interface KnowledgeAnswerResponse {
  query: string
  answer: string
  citations: KnowledgeCitation[]
  mocked: boolean
  provider: string
  generatedAt: string
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
  interviewRate: number
  offerRate: number
  activeStudentCount: number
  highPotentialCandidateCount: number
  weeklyDeliveryTrend: DashboardTrendPoint[]
  skillDemandTop: SkillDemand[]
  conversionFunnel: ConversionFunnelStage[]
  riskAlerts: string[]
}

export interface DashboardTrendPoint {
  label: string
  deliveryCount: number
  interviewCount: number
  offerCount: number
}

export interface SkillDemand {
  skill: string
  jobCount: number
  matchedStudentCount: number
  demandScore: number
}

export interface ConversionFunnelStage {
  stage: string
  label: string
  count: number
  conversionRate: number
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

type FallbackJobSeed = readonly [
  jobId: string,
  companyId: string,
  companyName: string,
  title: string,
  city: string,
  salaryRange: string,
  requiredSkills: readonly string[],
  description: string,
  aiSummary: string
]

function createFallbackJob([
  jobId,
  companyId,
  companyName,
  title,
  city,
  salaryRange,
  requiredSkills,
  description,
  aiSummary
]: FallbackJobSeed): JobSummary {
  return {
    jobId,
    companyId,
    companyName,
    title,
    city,
    salaryRange,
    requiredSkills: [...requiredSkills],
    description,
    aiSummary
  }
}

const fallbackJobs: JobSummary[] = ([
  [
    'J001',
    'C001',
    '星河科技',
    'Java 后端实习生',
    '杭州',
    '180-260/天',
    ['Java', 'Spring Boot', 'MySQL', 'Redis'],
    '参与招聘平台、数据看板和中台接口开发。',
    '适合具备 Java Web 项目经验的应届生。'
  ],
  [
    'J002',
    'C001',
    '星河科技',
    'Java 微服务开发实习生',
    '杭州',
    '200-280/天',
    ['Java', 'Spring Cloud', 'Nacos', 'RocketMQ'],
    '参与校园招聘核心交易链路、服务拆分和接口治理。',
    '适合了解 Spring Cloud、消息队列和服务注册的后端方向学生。'
  ],
  [
    'J003',
    'C002',
    '云栖数智',
    'Spring Boot 后端开发实习生',
    '南京',
    '180-260/天',
    ['Java', 'Spring Boot', 'MyBatis', 'PostgreSQL'],
    '负责企业管理后台接口、权限模型和报表数据服务开发。',
    '适合有 Java CRUD、数据库建模和接口联调经验的候选人。'
  ],
  [
    'J004',
    'C003',
    '海棠云',
    'Go 云原生后端实习生',
    '深圳',
    '220-320/天',
    ['Go', 'Gin', 'Kubernetes', 'Docker'],
    '参与云资源编排、任务调度和容器平台 API 开发。',
    '适合熟悉 Go 基础、容器化和云原生概念的工程实践型学生。'
  ],
  [
    'J005',
    'C004',
    '青鸾智联',
    'Python 后端开发实习生',
    '北京',
    '180-260/天',
    ['Python', 'FastAPI', 'Celery', 'Redis'],
    '建设数据采集、异步任务和运营工具后台能力。',
    '适合熟悉 Python Web 开发、异步任务和接口调试的学生。'
  ],
  [
    'J061',
    'C041',
    '启航教育集团',
    '小学数学教师',
    '杭州',
    '160-240/天',
    ['数学基础', '课程设计', '课堂管理', '家校沟通'],
    '负责小学数学班课授课、作业批改、家校沟通和阶段测评反馈。',
    '适合数学基础扎实、有耐心、表达清楚并愿意长期从事教学工作的候选人。'
  ],
  [
    'J062',
    'C041',
    '启航教育集团',
    '初中英语教师',
    '南京',
    '170-260/天',
    ['英语口语', '语法教学', '阅读训练', '课堂互动'],
    '负责初中英语语法、阅读和听说课程教学，跟进学生学习效果。',
    '适合英语表达流利，能设计课堂互动和阶段性学习计划的同学。'
  ],
  [
    'J063',
    'C042',
    '新芽培训学校',
    '课程顾问',
    '上海',
    '150-230/天',
    ['客户沟通', '需求挖掘', '课程介绍', '销售转化'],
    '接待家长咨询，了解学生学习情况，匹配课程方案并跟进报名转化。',
    '适合表达亲和、抗压能力强，愿意在教育行业做销售咨询的候选人。'
  ],
  [
    'J064',
    'C043',
    '远航销售服务',
    '销售管培生',
    '深圳',
    '180-280/天',
    ['客户开发', '电话沟通', '商务谈判', '销售漏斗'],
    '参与客户线索收集、电话邀约、方案介绍、合同跟进和销售复盘。',
    '适合目标感强、表达清晰，能接受结果导向和外部客户沟通的应届生。'
  ],
  [
    'J065',
    'C043',
    '远航销售服务',
    '大客户销售实习生',
    '北京',
    '180-260/天',
    ['行业研究', '客户拜访', '方案讲解', '商机跟进'],
    '协助销售经理梳理行业客户、准备拜访材料、跟进商机和回款节点。',
    '适合逻辑清晰、执行力强，愿意学习企业级销售流程的候选人。'
  ],
  [
    'J066',
    'C044',
    '嘉禾零售',
    '电商运营实习生',
    '广州',
    '140-220/天',
    ['商品运营', '平台规则', 'Excel', '活动报名'],
    '维护商品上下架、活动报名、价格库存和竞品数据，跟进店铺指标。',
    '适合熟悉电商平台规则，能处理表格数据和活动节奏的同学。'
  ],
  [
    'J067',
    'C045',
    '青橙传媒',
    '新媒体运营实习生',
    '成都',
    '140-220/天',
    ['内容选题', '文案写作', '短视频', '数据复盘'],
    '负责公众号、短视频和小红书内容选题、发布排期、数据复盘和评论互动。',
    '适合网感好、文字表达稳定，能根据数据调整内容方向的候选人。'
  ],
  [
    'J068',
    'C045',
    '青橙传媒',
    '市场推广实习生',
    '武汉',
    '140-220/天',
    ['校园推广', '活动执行', '社群拉新', '物料管理'],
    '执行校园宣讲、社群拉新、物料投放和活动数据统计。',
    '适合外向主动、组织能力强，能落地执行线下活动的候选人。'
  ],
  [
    'J069',
    'C046',
    '锦程人力',
    'HR 招聘实习生',
    '杭州',
    '130-210/天',
    ['简历筛选', '面试邀约', '招聘系统', '候选人沟通'],
    '发布职位、筛选简历、邀约面试、维护招聘系统并跟进候选人体验。',
    '适合沟通稳定、责任心强，对人力资源和招聘流程感兴趣的候选人。'
  ],
  [
    'J070',
    'C046',
    '锦程人力',
    '人力资源助理',
    '苏州',
    '130-210/天',
    ['员工关系', '培训组织', '档案管理', '流程跟进'],
    '协助入离转调、培训组织、档案维护和员工活动执行。',
    '适合细心、有服务意识，能处理流程性事务和跨部门协作的同学。'
  ],
  [
    'J071',
    'C047',
    '瑞禾会计师事务所',
    '财务助理',
    '上海',
    '140-220/天',
    ['会计基础', '费用报销', '凭证整理', 'Excel'],
    '协助审核报销单据、整理凭证、核对往来账和输出月度基础报表。',
    '适合会计基础扎实、细致严谨，熟悉 Excel 的候选人。'
  ],
  [
    'J072',
    'C047',
    '瑞禾会计师事务所',
    '审计实习生',
    '北京',
    '150-230/天',
    ['审计底稿', '凭证抽查', '内控测试', '访谈记录'],
    '协助完成凭证抽查、数据核对、访谈记录和审计底稿整理。',
    '适合财会专业、逻辑严谨，能接受出差和项目节奏的同学。'
  ],
  [
    'J073',
    'C048',
    '明德咨询',
    '行政前台实习生',
    '广州',
    '120-190/天',
    ['访客接待', '会议室管理', '行政流程', '物资管理'],
    '负责访客接待、会议室管理、办公用品登记和基础行政流程跟进。',
    '适合形象亲和、服务意识好，做事细致有条理的候选人。'
  ],
  [
    'J074',
    'C048',
    '明德咨询',
    '法务助理',
    '深圳',
    '150-230/天',
    ['合同审查', '法律检索', '合规', '文档归档'],
    '协助整理合同台账、检索法规案例、审核标准条款和归档法律文件。',
    '适合法学基础扎实，文字严谨并能处理大量文档的同学。'
  ],
  [
    'J075',
    'C049',
    '智服云',
    '客服专员实习生',
    '成都',
    '120-200/天',
    ['在线客服', '电话沟通', '工单处理', '服务意识'],
    '通过在线客服和电话处理用户咨询，记录问题分类并推动闭环解决。',
    '适合情绪稳定、表达清楚，愿意从一线用户问题理解业务的候选人。'
  ],
  [
    'J076',
    'C049',
    '智服云',
    '用户运营实习生',
    '杭州',
    '140-220/天',
    ['用户分层', '社群运营', '触达策略', '留存分析'],
    '维护用户社群，设计触达话术，跟进活跃、留存和转化指标。',
    '适合喜欢和用户交流，能用数据复盘运营动作的同学。'
  ],
  [
    'J077',
    'C050',
    '星澜设计云',
    'UI 设计实习生',
    '上海',
    '150-240/天',
    ['Figma', '界面设计', '设计规范', '组件库'],
    '协助完成移动端和后台页面设计、组件整理、设计走查和素材交付。',
    '适合审美稳定，熟悉 Figma，能兼顾可用性和视觉一致性的同学。'
  ],
  [
    'J078',
    'C050',
    '星澜设计云',
    '平面设计实习生',
    '南京',
    '130-210/天',
    ['Photoshop', 'Illustrator', '海报设计', '排版'],
    '设计活动海报、宣传单页、社媒配图和线下物料。',
    '适合掌握基础设计软件，能根据品牌调性快速产出视觉方案的候选人。'
  ],
  [
    'J079',
    'C051',
    '星火直播',
    '直播运营实习生',
    '杭州',
    '150-230/天',
    ['直播排期', '场控', '脚本准备', '互动运营'],
    '协助直播排期、讲解脚本、场控互动和数据复盘。',
    '适合反应快、执行力强，能处理直播现场节奏和转化数据的候选人。'
  ],
  [
    'J080',
    'C052',
    '嘉禾零售',
    '门店储备干部',
    '杭州',
    '140-220/天',
    ['门店运营', '库存管理', '顾客服务', '排班'],
    '轮岗学习收银、陈列、库存、会员运营和门店人员排班。',
    '适合愿意从一线业务做起，有服务意识和现场管理潜力的应届生。'
  ],
  [
    'J081',
    'C053',
    '蓝海供应链',
    '供应链计划实习生',
    '苏州',
    '150-230/天',
    ['需求预测', '库存计划', 'Excel', '订单跟进'],
    '协助整理销量预测、库存水位、补货计划和异常订单跟进。',
    '适合数据敏感、逻辑严谨，愿意学习供应链计划方法的同学。'
  ],
  [
    'J082',
    'C053',
    '蓝海供应链',
    '物流调度实习生',
    '武汉',
    '140-220/天',
    ['运输调度', '路线跟进', '异常处理', '时效统计'],
    '协助车辆排班、路线跟进、异常反馈和运输时效统计。',
    '适合抗压能力强，能在多方沟通中保持信息准确的同学。'
  ],
  [
    'J083',
    'C054',
    '云帆外贸',
    '外贸业务员实习生',
    '厦门',
    '150-240/天',
    ['英语邮件', '客户开发', '报价单', '外贸流程'],
    '协助开发海外客户，回复询盘，准备报价单和跟进样品寄送。',
    '适合英语读写良好，愿意学习外贸流程和客户沟通的同学。'
  ],
  [
    'J084',
    'C055',
    '华信金融',
    '银行柜员实习生',
    '上海',
    '150-230/天',
    ['客户服务', '资料审核', '合规意识', '金融基础'],
    '学习网点基础业务、客户接待、资料审核和合规操作流程。',
    '适合细心稳重、服务意识强，愿意从金融一线岗位成长的同学。'
  ],
  [
    'J085',
    'C056',
    '康悦医疗',
    '医药代表实习生',
    '广州',
    '160-260/天',
    ['产品知识', '客户拜访', '市场反馈', '合规推广'],
    '协助拜访终端客户，整理产品资料，跟进会议和市场反馈。',
    '适合医学、药学或市场方向学生，沟通主动并重视合规要求。'
  ],
  [
    'J086',
    'C057',
    '城际文旅',
    '酒店前厅管培生',
    '成都',
    '140-220/天',
    ['前台接待', '客诉处理', '会员服务', '排班'],
    '轮岗学习前台接待、客诉处理、客房协同和会员服务。',
    '适合服务意识好、形象亲和，能适应排班和现场运营的同学。'
  ],
  [
    'J087',
    'C057',
    '城际文旅',
    '旅游产品运营实习生',
    '西安',
    '140-220/天',
    ['线路设计', '供应商沟通', '产品上架', '用户评价'],
    '协助整理线路资源、供应商报价、产品上架和用户评价分析。',
    '适合热爱文旅行业，能把资源信息整理成清晰产品卖点的候选人。'
  ],
  [
    'J088',
    'C058',
    '安居地产',
    '房产销售顾问实习生',
    '深圳',
    '160-260/天',
    ['客户接待', '房源介绍', '带看', '销售转化'],
    '负责客户接待、房源介绍、带看安排和交易流程协助。',
    '适合目标感强、沟通主动，能接受外勤和客户跟进节奏的同学。'
  ],
  [
    'J089',
    'C059',
    '知行公益',
    '乡村振兴项目专员',
    '合肥',
    '130-210/天',
    ['基层调研', '项目执行', '资料整理', '活动组织'],
    '参与乡村项目调研、资料整理、活动组织和项目成效记录。',
    '适合愿意走进基层，具备调研、沟通和文字整理能力的候选人。'
  ],
  [
    'J090',
    'C060',
    '博雅学校',
    '幼儿园教师',
    '南京',
    '150-230/天',
    ['幼儿照护', '游戏活动', '家园沟通', '班级管理'],
    '负责幼儿日常照护、游戏活动、家园沟通和班级环境创设。',
    '适合学前教育方向，耐心细致并具备安全责任意识的候选人。'
  ],
  [
    'J006',
    'C005',
    '灵犀互动',
    'Vue 前端开发实习生',
    '广州',
    '160-240/天',
    ['Vue', 'TypeScript', 'Vite', 'Pinia'],
    '参与招聘门户、学生端投递流程和组件化页面开发。',
    '适合熟悉 Vue 生态、关注交互细节和工程化规范的前端学生。'
  ],
  [
    'J007',
    'C005',
    '灵犀互动',
    'React 前端开发实习生',
    '上海',
    '180-260/天',
    ['React', 'TypeScript', 'Next.js', 'CSS'],
    '负责运营中台、数据筛选页和可复用业务组件开发。',
    '适合具备 React Hooks、状态管理和响应式布局经验的学生。'
  ],
  [
    'J008',
    'C006',
    '千帆科技',
    'Web 可视化开发实习生',
    '杭州',
    '200-300/天',
    ['TypeScript', 'ECharts', 'Canvas', 'D3'],
    '开发招聘漏斗、人才画像和业务指标可视化看板。',
    '适合对数据可视化、图表性能和交互体验感兴趣的前端学生。'
  ],
  [
    'J009',
    'C007',
    '南山生活',
    '小程序前端开发实习生',
    '深圳',
    '160-230/天',
    ['微信小程序', 'TypeScript', 'Taro', 'REST API'],
    '参与校园服务小程序、活动报名和消息通知模块开发。',
    '适合有小程序或跨端框架项目经验的前端方向学生。'
  ],
  [
    'J010',
    'C008',
    '星澜设计云',
    '低代码平台前端实习生',
    '成都',
    '180-260/天',
    ['Vue', 'Monaco Editor', 'Schema', '拖拽编辑器'],
    '建设表单设计器、流程配置器和低代码运行时页面。',
    '适合熟悉组件抽象、Schema 配置和复杂交互的前端学生。'
  ],
  [
    'J011',
    'C009',
    '问知智能',
    'AI 应用开发实习生',
    '北京',
    '220-320/天',
    ['Python', 'LangChain', 'FastAPI', 'LLM'],
    '落地简历解析、智能问答和候选人推荐等 AI 应用能力。',
    '适合了解大模型 API、提示词调优和工程集成的学生。'
  ],
  [
    'J012',
    'C009',
    '问知智能',
    'RAG 知识库工程实习生',
    '北京',
    '220-320/天',
    ['RAG', '向量数据库', 'Embedding', 'Python'],
    '负责知识文档切分、向量检索、召回评估和问答链路优化。',
    '适合对 RAG、Milvus 或 Elasticsearch 检索增强有实践兴趣的学生。'
  ],
  [
    'J013',
    'C010',
    '启明模型工场',
    '大模型提示词工程实习生',
    '上海',
    '180-260/天',
    ['Prompt Engineering', 'LLM', '评测集', 'A/B Test'],
    '设计招聘问答、面试题生成和简历润色场景的提示词与评测样例。',
    '适合表达清晰、能把业务规则转化为模型提示和评价标准的学生。'
  ],
  [
    'J014',
    'C010',
    '启明模型工场',
    '机器学习算法实习生',
    '上海',
    '250-380/天',
    ['Python', 'PyTorch', '特征工程', '模型评估'],
    '参与候选人匹配、点击率预估和模型离线评估实验。',
    '适合有机器学习课程项目、PyTorch 训练和指标分析经验的学生。'
  ],
  [
    'J015',
    'C011',
    '视界智能',
    '计算机视觉算法实习生',
    '杭州',
    '250-380/天',
    ['Python', 'OpenCV', 'PyTorch', '目标检测'],
    '参与证件识别、面试视频质量检测和图像算法实验。',
    '适合熟悉视觉模型训练、数据标注和误差分析的算法方向学生。'
  ],
  [
    'J016',
    'C012',
    '语义引擎',
    'NLP 算法实习生',
    '北京',
    '240-360/天',
    ['NLP', 'Transformers', '文本分类', '信息抽取'],
    '优化职位标签抽取、简历实体识别和搜索相关性模型。',
    '适合有中文 NLP、Transformer 微调和数据清洗经验的学生。'
  ],
  [
    'J017',
    'C013',
    '数桥科技',
    '数据开发实习生',
    '杭州',
    '180-260/天',
    ['SQL', 'Python', 'ETL', 'Airflow'],
    '建设招聘业务数据同步、指标宽表和定时调度任务。',
    '适合 SQL 扎实、理解数据分层和任务调度的学生。'
  ],
  [
    'J018',
    'C013',
    '数桥科技',
    '数据仓库实习生',
    '杭州',
    '190-280/天',
    ['Hive', 'Spark SQL', '数据建模', 'DWD'],
    '参与校园招聘数仓主题域建模、质量校验和指标口径治理。',
    '适合了解离线数仓分层、维度建模和数据质量规则的学生。'
  ],
  [
    'J019',
    'C014',
    '灯塔分析',
    'BI 数据分析实习生',
    '上海',
    '160-240/天',
    ['SQL', 'Tableau', '指标分析', 'Excel'],
    '负责招聘转化、渠道效率和企业活跃度分析看板。',
    '适合能用 SQL 拆解业务问题并输出清晰分析结论的学生。'
  ],
  [
    'J020',
    'C014',
    '灯塔分析',
    '产品数据分析实习生',
    '上海',
    '180-260/天',
    ['SQL', 'Python', '漏斗分析', 'A/B Test'],
    '分析学生投递、企业筛选和面试预约流程的转化瓶颈。',
    '适合关注产品体验、具备统计思维和数据表达能力的学生。'
  ],
  [
    'J021',
    'C015',
    '稳测软件',
    '测试开发实习生',
    '南京',
    '160-240/天',
    ['Java', 'JUnit', '接口测试', 'Selenium'],
    '参与接口自动化、UI 回归和测试平台能力建设。',
    '适合熟悉测试用例设计、自动化脚本和缺陷定位的学生。'
  ],
  [
    'J022',
    'C015',
    '稳测软件',
    '自动化测试实习生',
    '苏州',
    '150-220/天',
    ['Python', 'Pytest', 'Playwright', 'CI'],
    '维护 Web 端自动化回归、测试数据构造和流水线执行。',
    '适合有 Pytest 或 Playwright 实践、能稳定复现问题的学生。'
  ],
  [
    'J023',
    'C015',
    '稳测软件',
    '性能测试实习生',
    '武汉',
    '170-250/天',
    ['JMeter', 'Linux', 'MySQL', '性能分析'],
    '负责接口压测、容量评估和慢查询初步定位。',
    '适合了解性能指标、压测脚本和基础系统监控的学生。'
  ],
  [
    'J024',
    'C016',
    '北辰云服',
    'SRE 运维开发实习生',
    '北京',
    '200-300/天',
    ['Linux', 'Prometheus', 'Python', 'Kubernetes'],
    '参与服务监控、告警规则、故障演练和自动化运维脚本开发。',
    '适合对稳定性工程、可观测性和自动化排障感兴趣的学生。'
  ],
  [
    'J025',
    'C016',
    '北辰云服',
    '云平台运维实习生',
    '北京',
    '180-260/天',
    ['Linux', 'Docker', 'Nginx', 'Shell'],
    '维护测试环境、容器部署、域名网关和基础资源巡检。',
    '适合掌握 Linux 基础命令、网络排障和脚本编写的学生。'
  ],
  [
    'J026',
    'C017',
    '流水线科技',
    'DevOps 平台实习生',
    '成都',
    '190-280/天',
    ['GitLab CI', 'Docker', 'Helm', '脚本开发'],
    '建设代码扫描、镜像构建、灰度发布和发布审批流水线。',
    '适合熟悉 CI/CD、容器镜像和工程效率工具的学生。'
  ],
  [
    'J027',
    'C018',
    '盾安网络',
    '信息安全实习生',
    '杭州',
    '180-260/天',
    ['Web 安全', 'OWASP', '日志分析', 'Python'],
    '参与安全基线检查、漏洞验证、风险台账和安全自动化脚本。',
    '适合了解常见 Web 漏洞、能规范记录验证过程的安全方向学生。'
  ],
  [
    'J028',
    'C018',
    '盾安网络',
    '安全运营实习生',
    '深圳',
    '170-250/天',
    ['SOC', 'SIEM', '威胁情报', '应急响应'],
    '监控安全告警、梳理攻击链路并协助完成应急响应复盘。',
    '适合关注安全运营、日志检索和事件分析的学生。'
  ],
  [
    'J029',
    'C018',
    '盾安网络',
    '渗透测试实习生',
    '广州',
    '190-280/天',
    ['渗透测试', 'Burp Suite', 'Linux', '漏洞验证'],
    '对 Web 业务、API 和管理后台进行授权渗透测试与修复验证。',
    '适合掌握漏洞原理、报告撰写和合规测试流程的学生。'
  ],
  [
    'J030',
    'C019',
    '掌上校园',
    'Android 开发实习生',
    '深圳',
    '180-260/天',
    ['Kotlin', 'Android', 'Jetpack', 'REST API'],
    '参与校园招聘 App 投递、消息、日程和离线缓存模块开发。',
    '适合熟悉 Kotlin、Jetpack 组件和移动端调试的学生。'
  ],
  [
    'J031',
    'C019',
    '掌上校园',
    'iOS 开发实习生',
    '深圳',
    '180-260/天',
    ['Swift', 'iOS', 'UIKit', 'SwiftUI'],
    '开发 iOS 端岗位浏览、简历投递和面试日程体验。',
    '适合有 Swift 项目、移动端网络请求和界面布局经验的学生。'
  ],
  [
    'J032',
    'C019',
    '掌上校园',
    'Flutter 跨端开发实习生',
    '广州',
    '180-260/天',
    ['Flutter', 'Dart', '状态管理', '移动端'],
    '参与学生端跨端页面、组件沉淀和性能优化。',
    '适合熟悉 Flutter 布局、路由和移动端基础能力的学生。'
  ],
  [
    'J033',
    'C020',
    '星流数据',
    '大数据开发实习生',
    '北京',
    '220-320/天',
    ['Spark', 'Flink', 'Kafka', 'Hive'],
    '负责日志采集、离线计算、实时指标和数据链路监控。',
    '适合了解大数据生态、批流计算和数据稳定性保障的学生。'
  ],
  [
    'J034',
    'C020',
    '星流数据',
    '实时计算开发实习生',
    '北京',
    '230-340/天',
    ['Flink', 'Kafka', 'Java', '实时数仓'],
    '开发招聘行为实时指标、告警规则和流式数据清洗任务。',
    '适合熟悉 Flink 窗口、状态管理和 Kafka 消费模型的学生。'
  ],
  [
    'J035',
    'C021',
    '灵推荐',
    '搜索推荐工程实习生',
    '杭州',
    '240-360/天',
    ['Python', '召回排序', 'Elasticsearch', '特征工程'],
    '优化岗位搜索、候选人推荐和个性化排序策略。',
    '适合理解搜索召回、排序评估和推荐系统基础的学生。'
  ],
  [
    'J036',
    'C022',
    '职路产品实验室',
    '产品经理实习生',
    '上海',
    '150-220/天',
    ['需求分析', '原型设计', 'Axure', '用户故事'],
    '负责学生求职流程、企业筛选工具和后台配置需求梳理。',
    '适合能把用户问题拆成可交付需求、沟通清晰的产品方向学生。'
  ],
  [
    'J037',
    'C022',
    '职路产品实验室',
    '用户研究实习生',
    '上海',
    '140-220/天',
    ['访谈', '问卷', '可用性测试', '用户画像'],
    '开展学生和企业用户访谈，沉淀招聘平台体验问题与优化建议。',
    '适合具备调研设计、访谈整理和洞察表达能力的学生。'
  ],
  [
    'J038',
    'C023',
    '增长引擎',
    '运营数据分析实习生',
    '广州',
    '150-230/天',
    ['SQL', '活动分析', '增长指标', 'Excel'],
    '分析校园活动、内容触达和企业运营策略的效果数据。',
    '适合关注增长运营、指标拆解和数据复盘的学生。'
  ],
  [
    'J039',
    'C024',
    '芯联实验室',
    '嵌入式软件实习生',
    '苏州',
    '190-280/天',
    ['C', 'C++', 'RTOS', '串口调试'],
    '参与传感器驱动、设备通信协议和嵌入式测试工具开发。',
    '适合具备 C/C++ 基础、单片机或 RTOS 项目经验的学生。'
  ],
  [
    'J040',
    'C024',
    '芯联实验室',
    'IoT 平台开发实习生',
    '苏州',
    '190-280/天',
    ['MQTT', 'Java', '物联网平台', '时序数据'],
    '开发设备接入、消息解析、告警规则和物联网数据看板。',
    '适合了解 IoT 协议、后端接口和设备数据处理的学生。'
  ],
  [
    'J041',
    'C025',
    '行知车联',
    '车载软件开发实习生',
    '武汉',
    '200-300/天',
    ['C++', 'Linux', 'CAN', '车载以太网'],
    '参与车端通信、诊断工具和日志采集模块开发测试。',
    '适合对智能汽车软件、C++ 和嵌入式 Linux 感兴趣的学生。'
  ],
  [
    'J042',
    'C026',
    '容器云工场',
    '云原生平台开发实习生',
    '杭州',
    '220-320/天',
    ['Kubernetes', 'Go', 'Operator', 'Helm'],
    '开发集群管理、应用发布和资源编排平台功能。',
    '适合熟悉 Kubernetes 基础对象、Go 开发和云原生生态的学生。'
  ],
  [
    'J043',
    'C026',
    '容器云工场',
    'Kubernetes 运维开发实习生',
    '杭州',
    '210-300/天',
    ['Kubernetes', 'Prometheus', 'Shell', '故障排查'],
    '负责集群巡检、告警治理、自动扩缩容和故障脚本沉淀。',
    '适合对集群稳定性、监控告警和自动化运维有兴趣的学生。'
  ],
  [
    'J044',
    'C027',
    '星库数据库',
    '数据库内核测试实习生',
    '北京',
    '220-320/天',
    ['MySQL', 'PostgreSQL', '测试开发', 'SQL'],
    '设计数据库兼容性、事务、索引和性能场景测试。',
    '适合数据库基础扎实、愿意深入 SQL 执行和测试工具的学生。'
  ],
  [
    'J045',
    'C027',
    '星库数据库',
    'DBA 数据库运维实习生',
    '北京',
    '190-280/天',
    ['MySQL', 'Redis', '备份恢复', '慢查询'],
    '协助数据库巡检、备份恢复演练、容量评估和慢 SQL 分析。',
    '适合熟悉数据库基础运维、索引优化和高可用概念的学生。'
  ],
  [
    'J046',
    'C028',
    '企服云',
    '低代码平台开发实习生',
    '深圳',
    '180-260/天',
    ['Java', 'Vue', '规则引擎', '流程引擎'],
    '参与审批流、动态表单、权限配置和租户隔离能力开发。',
    '适合兼具前后端基础、理解配置化平台设计的学生。'
  ],
  [
    'J047',
    'C028',
    '企服云',
    'CRM 后端开发实习生',
    '深圳',
    '180-260/天',
    ['Java', 'Spring Boot', 'MySQL', 'ElasticSearch'],
    '开发客户线索、销售跟进、权限审计和报表接口。',
    '适合有企业 SaaS 后端开发、搜索或权限模型实践的学生。'
  ],
  [
    'J048',
    'C029',
    '安付科技',
    '支付风控开发实习生',
    '上海',
    '220-320/天',
    ['Java', '风控规则', 'Redis', 'Kafka'],
    '参与交易风控规则、风险特征计算和实时拦截链路开发。',
    '适合了解高并发接口、规则引擎和实时消息处理的后端学生。'
  ],
  [
    'J049',
    'C029',
    '安付科技',
    '金融科技 Java 实习生',
    '上海',
    '200-300/天',
    ['Java', 'Spring Cloud', '分布式事务', 'MySQL'],
    '参与账户、清结算和对账系统的后端服务开发。',
    '适合对金融业务一致性、事务和服务治理感兴趣的学生。'
  ],
  [
    'J050',
    'C030',
    '医数云',
    '医疗数据工程实习生',
    '成都',
    '190-280/天',
    ['Python', 'SQL', '数据脱敏', 'ETL'],
    '处理医疗结构化数据、脱敏规则、质控报表和数据接口。',
    '适合关注数据合规、ETL 和行业数据治理的学生。'
  ],
  [
    'J051',
    'C031',
    '学伴 SaaS',
    '教育 SaaS 前端实习生',
    '武汉',
    '160-240/天',
    ['Vue', 'TypeScript', '组件库', '移动适配'],
    '开发课程管理、学习任务和教师工作台前端页面。',
    '适合关注教育产品体验、组件复用和移动端适配的前端学生。'
  ],
  [
    'J052',
    'C032',
    '游境网络',
    '游戏服务端实习生',
    '广州',
    '200-300/天',
    ['Go', 'TCP', 'Redis', '房间服务'],
    '参与游戏大厅、匹配队列、房间状态和运营活动服务开发。',
    '适合有网络编程、并发模型或实时服务兴趣的学生。'
  ],
  [
    'J053',
    'C033',
    '声画科技',
    '音视频开发实习生',
    '深圳',
    '220-340/天',
    ['C++', 'WebRTC', 'FFmpeg', '网络协议'],
    '参与在线面试音视频链路、录制转码和质量监控能力建设。',
    '适合了解音视频基础、网络传输和 C++ 开发的学生。'
  ],
  [
    'J054',
    'C034',
    '图行天下',
    '地图 GIS 开发实习生',
    '南京',
    '180-260/天',
    ['GIS', 'PostGIS', 'JavaScript', '空间数据'],
    '开发校招地图、通勤圈分析和地理围栏相关功能。',
    '适合熟悉 GIS 基础、空间查询和 Web 地图展示的学生。'
  ],
  [
    'J055',
    'C035',
    '隐算科技',
    '隐私计算工程实习生',
    '北京',
    '240-360/天',
    ['Python', '联邦学习', '安全多方计算', '数据合规'],
    '参与跨机构数据协作、隐私保护建模和实验评估工具开发。',
    '适合对隐私计算、机器学习和数据安全合规有兴趣的学生。'
  ],
  [
    'J056',
    'C036',
    '链信实验室',
    '区块链应用开发实习生',
    '杭州',
    '200-300/天',
    ['Solidity', 'Go', '智能合约', 'Web3'],
    '开发证书存证、合约调用服务和链上数据查询工具。',
    '适合了解智能合约、区块链基础和后端服务集成的学生。'
  ],
  [
    'J057',
    'C037',
    '运筹智能',
    '供应链算法实习生',
    '上海',
    '240-360/天',
    ['Python', '运筹优化', '启发式算法', '数据建模'],
    '参与仓配调度、路径规划和库存补货策略的算法实验。',
    '适合有数学建模、优化算法和 Python 实验经验的学生。'
  ],
  [
    'J058',
    'C038',
    '智服云',
    '客户成功技术顾问实习生',
    '北京',
    '150-220/天',
    ['SQL', 'API 调试', 'SaaS', '沟通协作'],
    '支持企业客户接入招聘平台，定位配置、数据和接口问题。',
    '适合技术基础扎实、沟通清楚并愿意贴近客户场景的学生。'
  ],
  [
    'J059',
    'C039',
    '方案桥',
    '售前解决方案实习生',
    '深圳',
    '160-240/天',
    ['解决方案', '云服务', '需求调研', '原型演示'],
    '协助准备校园招聘数字化方案、演示环境和技术答疑材料。',
    '适合兼具技术理解、表达能力和业务抽象能力的学生。'
  ],
  [
    'J060',
    'C040',
    '开源协作社',
    '技术文档工程师实习生',
    '远程',
    '120-200/天',
    ['Markdown', 'API 文档', 'Git', '技术写作'],
    '维护开发者文档、接口示例、部署说明和产品更新日志。',
    '适合表达准确、能阅读代码并输出清晰技术文档的学生。'
  ]
] as const).map(createFallbackJob)

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
    referencePoints: ['项目背景和目标', '技术方案与取舍', '个人负责模块', '量化结果或复盘'],
    knowledgeReferences: ['Campus recruitment Java backend interview guide：Spring Boot、MySQL、Redis 和微服务排障是 Java 后端面试重点。']
  },
  {
    questionId: 'IQ-002',
    category: '技术基础',
    difficulty: '中等',
    question: '如果接口响应突然变慢，你会如何从应用、数据库和缓存三个层面排查？',
    referencePoints: ['先查看监控与日志', '分析 SQL 与索引', '检查缓存命中率', '补充压测复现方式'],
    knowledgeReferences: ['Campus RAG bulk handbook：压测和 P95 延迟可用于定位网关、数据库和缓存瓶颈。']
  },
  {
    questionId: 'IQ-003',
    category: '行为面试',
    difficulty: '基础',
    question: '请讲一次你在团队协作中推动问题解决的经历。',
    referencePoints: ['使用 STAR 结构', '说明阻塞点', '突出沟通动作', '总结复盘'],
    knowledgeReferences: ['Resume evidence checklist：强简历需要把项目主张关联到可验证证据。']
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

const fallbackResumeRewriteResponse: ResumeRewriteResponse = {
  studentId: 'S001',
  resumeId: 'R001',
  targetRole: 'Java 后端实习生',
  improvedSummary: '软件工程本科，具备 Java、Spring Boot、MySQL、Redis 与 Docker 项目经验，能够完成后端接口开发、数据库设计和基础部署。建议在正式简历中补充接口性能、数据规模和个人负责模块。',
  rewrittenProjects: [
    '校园二手交易系统：负责商品、订单与用户模块接口设计，使用 Spring Boot + MyBatis Plus 完成核心 CRUD 与状态流转，补充 Redis 缓存后可突出响应时间优化结果。',
    '在线考试平台：参与题库、试卷和成绩统计模块开发，建议补充并发答题、批量阅卷或慢 SQL 优化等可验证指标。'
  ],
  keywordSuggestions: ['Spring Boot', 'MyBatis Plus', 'Redis 缓存', 'MySQL 索引', 'Docker 部署', 'RESTful API'],
  missingEvidence: ['接口性能指标', '数据库表规模', '个人负责模块边界', '部署环境或访问截图', '压测或日志排障证据'],
  actionChecklist: ['把项目描述改成“场景-动作-结果”结构', '每个项目补充 1 个量化指标', '加入 GitHub 或部署说明', '准备 Redis/MySQL/微服务追问材料'],
  mocked: true
}

const fallbackCareerPlanResponse: CareerPlanResponse = {
  studentId: 'S001',
  targetRole: 'Java 后端实习生',
  readinessScore: 78,
  summary: '当前基础技能与 Java 后端实习方向匹配，短板集中在可验证项目成果、微服务组件实践和面试表达结构。建议用 8 周完成简历证据补强、项目部署和专项面试训练。',
  milestones: [
    {
      title: '第 1-2 周：简历证据补强',
      timeframe: 'Week 1-2',
      goals: ['梳理 2 个核心项目的个人贡献', '补充接口、SQL、缓存优化指标', '完善 GitHub README 和部署截图']
    },
    {
      title: '第 3-5 周：微服务项目强化',
      timeframe: 'Week 3-5',
      goals: ['补充 Spring Cloud Alibaba 注册发现流程', '整理 Redis、RocketMQ、Docker 场景题', '完成一次本地 Docker Compose 联调']
    },
    {
      title: '第 6-8 周：投递与面试闭环',
      timeframe: 'Week 6-8',
      goals: ['每周投递 10-15 个匹配岗位', '完成 3 次模拟面试复盘', '根据反馈迭代简历关键词']
    }
  ],
  skillGaps: ['Spring Cloud Alibaba 实战', 'RocketMQ 异步场景', '线上排障指标', '项目量化表达'],
  weeklyActions: ['每周改写 1 次项目描述', '每周完成 2 组 Java/MySQL/Redis 面试题', '每周复盘投递转化数据', '每周补充 1 条项目证据材料'],
  portfolioTasks: ['为核心项目补充架构图和部署步骤', '上传接口文档或 Swagger 截图', '准备数据库设计和缓存设计说明'],
  interviewFocus: ['项目难点与取舍', 'MySQL 索引和事务', 'Redis 缓存穿透/击穿/雪崩', 'Spring Boot 接口排障', 'Docker 部署流程'],
  mocked: true
}

const fallbackAiCoachAdviceResponse: AiCoachAdviceResponse = {
  studentId: 'S001',
  targetRole: 'Java 后端实习生',
  readinessScore: 82,
  headline: '当前能力接近目标岗位，下一步要用量化证据和面试表达提高转化率。',
  priorityActions: [
    '把核心项目补充为 STAR 结构，并写清接口、数据表、缓存和消息队列职责。',
    '补充 3 个可验证证据：GitHub 链接、部署截图、接口压测或测试报告。',
    '围绕 MySQL、Redis、RocketMQ、Spring Cloud Alibaba 完成一轮专项模拟面试。',
    '优先投递要求 Java/Spring Boot/MySQL/Redis 的校招岗位。'
  ],
  riskWarnings: [
    '如果简历没有量化结果，HR 可能无法判断项目真实深度。',
    '如果面试只停留在组件名，技术追问时容易暴露排障经验不足。',
    '投递后需要跟踪状态，避免只投递不复盘。'
  ],
  learningPath: [
    '第 1 周：完善简历证据和项目架构图。',
    '第 2 周：复盘 Java 集合、并发基础和 JVM 排查。',
    '第 3 周：专项练习 MySQL 索引、事务和慢 SQL。',
    '第 4 周：专项练习 Redis 缓存一致性、穿透、击穿和雪崩。',
    '第 5 周：复盘 Gateway、Nacos、RocketMQ 的三机部署链路。',
    '第 6 周：完成两次限时模拟面试并修正弱项回答。'
  ],
  interviewDrills: [
    '说明一次慢接口排查：日志、指标、SQL 执行计划、缓存命中率。',
    '解释为什么投递后异步触发 AI 初筛要用 RocketMQ。',
    '比较 Gateway 路由和服务直连在三机部署中的取舍。',
    '讲清楚一个数据库表设计和索引优化理由。'
  ],
  searchKeywords: ['Java 后端实习', 'Spring Boot 校招', 'MySQL Redis', 'RocketMQ 微服务', '校园招聘 Java'],
  mocked: true
}

const fallbackAiPlanningRecords: AiPlanningRecord[] = [
  {
    recordId: 'AIP-DEMO-PLAN-001',
    studentId: 'S001',
    operation: 'career-plan',
    resumeId: null,
    targetRole: fallbackCareerPlanResponse.targetRole,
    resumeRewrite: null,
    careerPlan: fallbackCareerPlanResponse,
    mocked: true,
    createdAt: new Date(Date.now() - 30 * 60 * 1000).toISOString()
  },
  {
    recordId: 'AIP-DEMO-REWRITE-001',
    studentId: 'S001',
    operation: 'resume-rewrite',
    resumeId: fallbackResumeRewriteResponse.resumeId,
    targetRole: fallbackResumeRewriteResponse.targetRole,
    resumeRewrite: fallbackResumeRewriteResponse,
    careerPlan: null,
    mocked: true,
    createdAt: new Date(Date.now() - 45 * 60 * 1000).toISOString()
  }
]

const fallbackAiModuleStatus: AiModuleStatus = {
  provider: 'dashscope',
  model: 'qwen-plus',
  configured: false,
  baseUrl: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
  capabilities: ['resume-analysis', 'resume-rewrite', 'career-planning', 'planning-history', 'coach-advice', 'job-analysis', 'match-analysis', 'candidate-screening', 'interview-question-generation', 'interview-feedback', 'observability', 'intelligent-search'],
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

const fallbackKnowledgeDocuments: KnowledgeDocument[] = [
  {
    documentId: 'KB-DEMO-001',
    title: 'Campus recruitment Java backend interview guide',
    content: 'Focus on Spring Boot layering, MySQL indexes, Redis cache consistency, Gateway routing, RocketMQ async delivery events, and three-VM deployment troubleshooting.',
    category: 'interview',
    source: 'seed',
    tags: ['Java', 'Spring Boot', 'MySQL', 'Redis', 'RocketMQ'],
    roles: ['STUDENT', 'COMPANY', 'ADMIN'],
    createdBy: 'system',
    createdAt: new Date(Date.now() - 2 * 24 * 60 * 60 * 1000).toISOString()
  },
  {
    documentId: 'KB-DEMO-002',
    title: 'Resume evidence checklist',
    content: 'Connect every resume claim to project ownership, API behavior, latency, data volume, screenshots, test reports, and deployment proof.',
    category: 'resume',
    source: 'seed',
    tags: ['resume', 'evidence', 'metrics'],
    roles: ['STUDENT', 'ADMIN'],
    createdBy: 'system',
    createdAt: new Date(Date.now() - 24 * 60 * 60 * 1000).toISOString()
  },
  {
    documentId: 'KB-DEMO-003',
    title: 'Company candidate screening playbook',
    content: 'Screening should combine delivery status, parsed resume quality, skill overlap, interview risk questions, and auditable AI recommendation records.',
    category: 'screening',
    source: 'seed',
    tags: ['screening', 'AI', 'audit'],
    roles: ['COMPANY', 'ADMIN'],
    createdBy: 'system',
    createdAt: new Date(Date.now() - 12 * 60 * 60 * 1000).toISOString()
  }
]

const fallbackKnowledgeIngestionJobs: KnowledgeIngestionJob[] = [
  {
    jobId: 'KBI-DEMO-003',
    fileName: 'campus-rag-bulk-handbook.pdf',
    title: 'Campus RAG bulk handbook',
    category: 'rag',
    source: 'admin-upload',
    status: 'READY',
    message: 'File parsed, chunked, and indexed into the local demo vector store.',
    documentId: 'KB-DEMO-901',
    chunkCount: 18,
    vectorCount: 18,
    error: null,
    createdAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
  },
  {
    jobId: 'KBI-DEMO-002',
    fileName: 'screening-playbook.docx',
    title: 'Screening playbook batch import',
    category: 'screening',
    source: 'admin-upload',
    status: 'INDEXING',
    message: 'Embedding chunks and writing vectors.',
    documentId: null,
    chunkCount: 9,
    vectorCount: 6,
    error: null,
    createdAt: new Date(Date.now() - 70 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 12 * 60 * 1000).toISOString()
  },
  {
    jobId: 'KBI-DEMO-001',
    fileName: 'legacy-faq.txt',
    title: 'Legacy FAQ import',
    category: 'faq',
    source: 'legacy-import',
    status: 'FAILED',
    message: 'Unsupported encoding detected before chunking.',
    documentId: null,
    chunkCount: 0,
    vectorCount: 0,
    error: 'Unsupported encoding',
    createdAt: new Date(Date.now() - 8 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 7 * 60 * 60 * 1000).toISOString()
  }
]

function countKnowledgeValues(values: string[]) {
  return values.reduce<Record<string, number>>((counts, value) => {
    const key = value.trim() || 'unknown'
    counts[key] = (counts[key] || 0) + 1
    return counts
  }, {})
}

function fallbackKnowledgeBaseStats(): KnowledgeBaseStats {
  return {
    documentCount: fallbackKnowledgeDocuments.length,
    chunkCount: fallbackKnowledgeDocuments.length,
    categoryCounts: countKnowledgeValues(fallbackKnowledgeDocuments.map((document) => document.category)),
    roleCounts: countKnowledgeValues(fallbackKnowledgeDocuments.flatMap((document) => document.roles)),
    sourceCounts: countKnowledgeValues(fallbackKnowledgeDocuments.map((document) => document.source)),
    tagCounts: countKnowledgeValues(fallbackKnowledgeDocuments.flatMap((document) => document.tags)),
    corpusVersion: 'local-demo-fallback',
    seedEnabled: true,
    persistentStore: false,
    generatedAt: new Date().toISOString()
  }
}

function fallbackKnowledgeVectorStatus(): KnowledgeVectorStatus {
  const completedJobs = fallbackKnowledgeIngestionJobs.filter((job) => job.status === 'READY')
  return {
    provider: 'milvus',
    configured: false,
    connected: false,
    collectionName: 'campus_recruit_knowledge_demo',
    indexName: 'idx_campus_knowledge_embedding',
    status: 'DEMO',
    indexStatus: 'LOCAL_FALLBACK',
    metricType: 'COSINE',
    dimension: 1536,
    documentCount: fallbackKnowledgeDocuments.length + completedJobs.length,
    chunkCount: fallbackKnowledgeBaseStats().chunkCount + completedJobs.reduce((sum, job) => sum + job.chunkCount, 0),
    vectorCount: completedJobs.reduce((sum, job) => sum + job.vectorCount, 0),
    lastIngestedAt: completedJobs[0]?.updatedAt || null,
    generatedAt: new Date().toISOString(),
    warnings: ['Milvus is not configured in the local frontend fallback.']
  }
}

function normalizeKnowledgeVectorStatus(value: KnowledgeVectorStatus | BackendKnowledgeVectorStatus): KnowledgeVectorStatus {
  if ('collectionName' in value) {
    return value
  }
  const status = value.available ? 'READY' : value.enabled ? 'DEGRADED' : 'LOCAL_FALLBACK'
  return {
    provider: value.provider,
    configured: value.enabled,
    connected: value.available,
    collectionName: value.collection,
    indexName: `${value.collection}:embedding`,
    status,
    indexStatus: status,
    metricType: 'COSINE',
    dimension: value.dimension,
    documentCount: 0,
    chunkCount: value.indexedChunkCount,
    vectorCount: value.indexedChunkCount,
    lastIngestedAt: null,
    generatedAt: value.checkedAt,
    warnings: value.fallbackReason ? [value.fallbackReason] : []
  }
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

const fallbackNotifications: NotificationMessage[] = [
  {
    notificationId: 'N-DEMO-STUDENT-001',
    targetRole: 'STUDENT',
    targetUserId: 'S001',
    title: 'Interview invitation',
    content: 'C001 invited you to a Java backend interview. Confirm the schedule before the deadline.',
    sourceType: 'INTERVIEW',
    sourceId: 'IS-DEMO-001',
    read: false,
    createdAt: new Date(Date.now() - 5 * 60 * 60 * 1000).toISOString()
  },
  {
    notificationId: 'N-DEMO-COMPANY-001',
    targetRole: 'COMPANY',
    targetUserId: 'C001',
    title: 'New candidate delivery',
    content: 'A new resume was delivered to J001 and is waiting for screening.',
    sourceType: 'DELIVERY',
    sourceId: 'D001',
    read: false,
    createdAt: new Date(Date.now() - 3 * 60 * 60 * 1000).toISOString()
  }
]

const fallbackInterviewSchedules: InterviewSchedule[] = [
  {
    scheduleId: 'IS-DEMO-001',
    deliveryId: 'D003',
    companyId: 'C001',
    studentId: 'S003',
    jobId: 'J002',
    title: 'Java backend technical interview',
    startTime: new Date(Date.now() + 2 * 24 * 60 * 60 * 1000).toISOString(),
    durationMinutes: 45,
    location: 'Online',
    meetingUrl: 'https://meet.example.com/demo-java-backend',
    note: 'Prepare one backend project and one MySQL troubleshooting case.',
    status: 'PROPOSED',
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
    updatedAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString()
  }
]

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
    status: 'READY',
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
  summary: '按 VM1 Nacos 引导、VM3 数据与 AI、VM2 业务服务、VM1 接入层、最终验收的顺序完成三虚拟机部署。',
  steps: [
    {
      order: 1,
      nodeId: 'vm1',
      nodeName: 'VM1 Nacos Bootstrap',
      title: '启动注册中心',
      purpose: '先启动 Nacos，避免 VM3 AI 服务和 VM2 业务服务注册时出现启动竞争。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d nacos',
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps nacos'
      ],
      verifyUrls: ['http://192.168.56.11:8848/nacos/'],
      expectedResult: 'Nacos 控制台可访问，VM2 和 VM3 能连接 VM1 的 8848/9848 端口。',
      troubleshooting: ['Nacos 不可达时检查 VM1 防火墙端口', '服务无法注册时检查 VM2/VM3 到 VM1 的网络']
    },
    {
      order: 2,
      nodeId: 'vm3',
      nodeName: 'VM3 Data and AI Node',
      title: '启动数据与 AI 节点',
      purpose: '在 Nacos 可用后启动 MySQL、Redis、MinIO、RocketMQ 和 AI 服务。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml up -d --build',
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm3.yml ps'
      ],
      verifyUrls: [
        'tcp://192.168.56.13:3306',
        'tcp://192.168.56.13:6379',
        'http://192.168.56.13:9000/minio/health/live',
        'tcp://192.168.56.13:9876',
        'http://192.168.56.13:8106/actuator/health'
      ],
      expectedResult: 'MySQL、Redis、MinIO、RocketMQ 和 ai-service 均可达。',
      troubleshooting: ['先确认 VM3 防火墙端口开放', 'AI 服务异常时检查 DASHSCOPE_API_KEY 和 Nacos 连接']
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
      nodeId: 'vm1',
      nodeName: 'VM1 Gateway and Frontend',
      title: '启动接入层',
      purpose: '在 VM2 和 VM3 服务可用后启动 Gateway 和前端入口。',
      commands: [
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml up -d --build gateway-service frontend',
        'docker compose --env-file deploy/three-vm.env -f deploy/docker-compose.vm1.yml ps'
      ],
      verifyUrls: [
        'http://192.168.56.11:8080/actuator/health',
        'http://192.168.56.11/'
      ],
      expectedResult: 'Gateway 健康检查通过，前端页面可打开。',
      troubleshooting: ['Gateway 异常时检查 VM2/VM3 地址是否写入 deploy/three-vm.env', '前端打不开时检查 80 端口是否被占用']
    },
    {
      order: 5,
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

async function strictRequest<T>(path: string, init: RequestInit, fallback: T): Promise<T> {
  if (!shouldUseApi(path)) {
    return fallback
  }

  return authenticatedRequest<T>(path, init)
}

async function authenticatedRequest<T>(path: string, init: RequestInit): Promise<T> {
  let response: Response
  try {
    response = await fetch(resolveRequestPath(path), { ...init, headers: requestHeaders(init) })
  } catch (error) {
    if (error instanceof Error && error.name === 'AbortError') throw error
    throw new Error('无法连接服务，请检查网络或服务状态后重试；本次操作未确认成功')
  }
  if (!response.ok) {
    if (response.status === 401) {
      if (!path.startsWith('/api/auth/login')) {
        clearAuthSession()
      }
      throw new Error(path.startsWith('/api/auth/login') ? '账号或密码错误，请重新输入' : '登录已失效，请重新登录')
    }
    throw new Error(await responseErrorMessage(response, `HTTP ${response.status}`))
  }
  const payload = (await response.json()) as ApiResponse<T>
  if (payload.code !== 0) {
    if (payload.code === 401) {
      if (!path.startsWith('/api/auth/login')) {
        clearAuthSession()
      }
      throw new Error(path.startsWith('/api/auth/login') ? '账号或密码错误，请重新输入' : '登录已失效，请重新登录')
    }
    throw new Error(readableApiError(payload.message || '请求失败'))
  }
  return payload.data
}

async function responseErrorMessage(response: Response, fallback: string) {
  try {
    const payload = await response.json() as Partial<ApiResponse<unknown>>
    return readableApiError(payload.message || fallback)
  } catch {
    return fallback
  }
}

function readableApiError(message: string) {
  const messages: Record<string, string> = {
    'Resume not found': '简历不存在或无权访问，请重新选择简历',
    'Job not found': '岗位不存在或已下线，请重新选择岗位',
    'The requested job is not open': '该岗位已暂停招聘，请选择其他开放岗位',
    'Learning plan not found': '学习计划不存在或无权访问，请刷新列表',
    'Interview session not found': '面试会话不存在或无权访问，请刷新记录',
    'Only active learning plans can be updated': '该学习计划已完成或已被新版本替代，不能修改任务',
    'Only active learning plans can be replanned': '请选择正在进行的学习计划后重新规划',
    'Interview session is already finished': '本次面试已结束，可以查看报告或开始新的面试',
    'Interview question already has an answer': '本题回答已保存，不能重复修改，请继续下一题',
    'Interview answers must be submitted in question order': '请先保存前面的题目，再回答本题',
    'All interview questions must be answered before finish': '请先保存所有题目的回答（包括追问），再生成报告',
    'answer must not exceed 8000 characters': '每题回答最多 8000 字，请精简后再保存',
    'feedback must not exceed 2000 characters': '复盘备注最多 2000 字，请精简后再保存',
    'Selected interview session is not completed': '请先完成所选面试并生成报告',
    'Selected interview session has a different target role': '请选择与学习计划目标岗位一致的面试报告',
    'Selected interview session does not match the learning plan context': '面试报告需来自同一份简历、岗位和匹配记录，请重新选择',
    'AI data service is temporarily unavailable': '数据服务暂时不可用，请刷新确认最新记录后重试',
    'targetRole or jobId is required': '请填写目标岗位或选择一个招聘岗位',
    'targetJob is required for a resume diagnosis': '请先填写诊断的目标岗位',
    'Interview question generation did not return enough questions': 'AI 返回的题目不完整，请稍后重新生成',
    'weeklyHours must be between 2 and 40': '每周投入时间需在 2 到 40 小时之间',
    'durationWeeks must be between 1 and 24': '计划周期需在 1 到 24 周之间',
    'questionCount must be between 1 and 8': '面试主问题数量需在 1 到 8 题之间',
    'HTTP 403': '没有权限执行此操作，请检查登录账号',
    'HTTP 429': '操作过于频繁，请稍后再试',
    'HTTP 502': '服务暂时不可用，请稍后重试',
    'HTTP 503': '服务暂时不可用，请稍后重试',
    'HTTP 504': '服务响应超时，请先刷新记录确认结果，再决定是否重试'
  }
  if (message.startsWith('Cannot shorten the plan past a completed task in week ')) return '新周期不能短于已完成任务所在周，请保留已完成的学习成果'
  if (message.startsWith('weeklyHours is lower than completed work in week ')) return '每周时间不能低于该周已完成任务的时长，请增加投入时间'
  return messages[message] || message
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
  return strictRequest<LoginResponse>('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify({ username, password })
  }, { token: `demo-${role.toLowerCase()}-token`, userId, displayName, role })
}

export function getProfile() {
  return strictRequest<UserProfile>('/api/students/profile', { method: 'GET' }, fallbackProfile)
}

export function uploadResume(file: File) {
  const formData = new FormData()
  formData.append('file', file)
  return strictRequest<ResumeSummary>('/api/resumes/upload', { method: 'POST', body: formData }, {
    ...fallbackResume,
    fileName: file.name
  })
}

export function listResumes() {
  return strictRequest<ResumeSummary[]>('/api/resumes', { method: 'GET' }, [fallbackResume])
}

export function getResume(resumeId = 'R001') {
  return strictRequest<ResumeSummary>(`/api/resumes/${resumeId}`, { method: 'GET' }, fallbackResume)
}

export function analyzeResume(resumeId: string, payload: ResumeAnalyzeRequest = {}) {
  const targetJob = payload.targetJob?.trim()
  return strictRequest<ResumeSummary>(`/api/resumes/${resumeId}/analyze`, {
    method: 'POST',
    ...(targetJob ? { body: JSON.stringify({ targetJob }) } : {})
  }, fallbackResume)
}

export function deleteResume(resumeId: string) {
  return strictRequest<boolean>(`/api/resumes/${encodeURIComponent(resumeId)}`, { method: 'DELETE' }, true)
}

export function updateResumeProfile(resumeId: string, payload: ResumeProfileUpdateRequest) {
  const fallback = { ...fallbackResume, ...payload, resumeId }
  return strictRequest<ResumeSummary>(`/api/resumes/${encodeURIComponent(resumeId)}/profile`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, fallback)
}

export function listResumeDiagnoses(resumeId: string) {
  const fallback: ResumeDiagnosis[] = [{
    diagnosisId: 'DIAG-DEMO-001',
    resumeId,
    studentId: currentStudentId(),
    targetJob: fallbackProfile.targetPosition,
    diagnosis: fallbackResume.diagnosis,
    score: fallbackResume.score,
    source: 'local-demo',
    createdAt: new Date().toISOString()
  }]
  return strictRequest<ResumeDiagnosis[]>(`/api/resumes/${encodeURIComponent(resumeId)}/diagnoses`, {
    method: 'GET'
  }, fallback)
}

export function listJobs() {
  return strictRequest<JobSummary[]>('/api/jobs', { method: 'GET' }, fallbackJobs)
}

export function createJob(job: Partial<JobSummary>) {
  const payload = { ...job, companyId: job.companyId || currentCompanyId() }
  const created = { ...fallbackJobs[0], ...payload, jobId: `J${Date.now().toString().slice(-6)}` }
  return strictRequest<JobSummary>('/api/jobs', { method: 'POST', body: JSON.stringify(payload) }, created)
}

export function analyzeJob(jobId: string) {
  return strictRequest<JobSummary>(`/api/jobs/${jobId}/analyze`, { method: 'POST' }, fallbackJobs[0])
}

export function updateJob(jobId: string, payload: Partial<JobSummary>) {
  const fallback = { ...fallbackJobs.find((job) => job.jobId === jobId), ...payload, jobId }
  return strictRequest<JobSummary>(`/api/jobs/${encodeURIComponent(jobId)}`, {
    method: 'PUT',
    body: JSON.stringify(payload)
  }, fallback as JobSummary)
}

export function updateJobStatus(jobId: string, status: string) {
  const fallback = { ...fallbackJobs.find((job) => job.jobId === jobId), jobId, status }
  return strictRequest<JobSummary>(`/api/jobs/${encodeURIComponent(jobId)}/status`, {
    method: 'POST',
    body: JSON.stringify({ status })
  }, fallback as JobSummary)
}

export function matchResumeJob(resumeId = 'R001', jobId = 'J001') {
  const studentId = currentStudentId()
  return strictRequest<MatchResult>('/api/matches/resume-job', {
    method: 'POST',
    body: JSON.stringify({ resumeId, jobId, studentId })
  }, { ...fallbackMatch, resumeId, jobId, studentId })
}

export function listMyMatches(studentId = currentStudentId()) {
  return strictRequest<MatchResult[]>(`/api/matches/student/${encodeURIComponent(studentId)}`, {
    method: 'GET'
  }, [fallbackMatch])
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
  const count = Math.max(1, Math.min(20, payload.questionCount || fallbackInterviewQuestions.length))
  const fallback = Array.from({ length: count }, (_, index) => {
    const base = fallbackInterviewQuestions[index % fallbackInterviewQuestions.length]
    return {
      ...base,
      questionId: `IQ-DEMO-${String(index + 1).padStart(3, '0')}`
    }
  })
  return request<InterviewQuestion[]>('/api/ai/interview/questions', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallback)
}

export function submitInterviewFeedback(payload: InterviewFeedbackRequest) {
  return request<InterviewFeedback>('/api/ai/interview/feedback', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallbackInterviewFeedback)
}

export function rewriteResume(payload: ResumeRewriteRequest) {
  return strictRequest<ResumeRewriteResponse>('/api/ai/resume/rewrite', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, {
    ...fallbackResumeRewriteResponse,
    studentId: payload.studentId || fallbackResumeRewriteResponse.studentId,
    resumeId: payload.resumeId || fallbackResumeRewriteResponse.resumeId,
    targetRole: payload.targetRole || fallbackResumeRewriteResponse.targetRole
  })
}

export function generateCareerPlan(payload: CareerPlanRequest) {
  return request<CareerPlanResponse>('/api/ai/career/plan', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, {
    ...fallbackCareerPlanResponse,
    studentId: payload.studentId || fallbackCareerPlanResponse.studentId,
    targetRole: payload.targetRole || fallbackCareerPlanResponse.targetRole
  })
}

export function generateCoachAdvice(payload: AiCoachAdviceRequest) {
  return request<AiCoachAdviceResponse>('/api/ai/coach/advice', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, {
    ...fallbackAiCoachAdviceResponse,
    studentId: payload.studentId || fallbackAiCoachAdviceResponse.studentId,
    targetRole: payload.targetRole || fallbackAiCoachAdviceResponse.targetRole
  })
}

export function listAiPlanningHistory(studentId = currentStudentId(), limit = 20) {
  const normalizedLimit = Math.max(1, Math.min(limit, 100))
  const params = new URLSearchParams({
    studentId,
    limit: String(normalizedLimit)
  })
  const fallback = fallbackAiPlanningRecords
    .filter((record) => record.studentId === studentId)
    .slice(0, normalizedLimit)
  return request<AiPlanningRecord[]>(`/api/ai/career/history?${params.toString()}`, { method: 'GET' }, fallback)
}

const localLearningPlans = new Map<string, LearningPlan>()
const localInterviewSessions = new Map<string, InterviewSession>()

function localId(prefix: string) {
  return `${prefix}-DEMO-${Date.now().toString(36).toUpperCase()}`
}

function buildLocalLearningPlan(payload: LearningPlanRequest, version = 1, revisionOfPlanId?: string): LearningPlan {
  const now = new Date().toISOString()
  const durationWeeks = Math.max(1, Math.min(16, payload.durationWeeks || 8))
  const weeklyHours = Math.max(1, Math.min(40, payload.weeklyHours || 6))
  const targetRole = payload.targetRole?.trim() || getProfileFallbackTargetRole()
  return {
    planId: localId('PLAN'),
    studentId: payload.studentId || currentStudentId(),
    resumeId: payload.resumeId,
    jobId: payload.jobId,
    matchId: payload.matchId,
    targetRole,
    weeklyHours,
    durationWeeks,
    status: 'ACTIVE',
    version,
    revisionOfPlanId,
    tasks: Array.from({ length: durationWeeks }, (_, index) => ({
      taskId: `TASK-${index + 1}`,
      week: index + 1,
      title: `第 ${index + 1} 周专项练习`,
      description: index === 0
        ? `补齐 ${targetRole} 所需的简历证据与基础知识。`
        : `围绕 ${targetRole} 完成项目复盘、专项训练和复盘记录。`,
      estimatedHours: weeklyHours,
      status: 'TODO',
      updatedAt: now
    })),
    createdAt: now,
    updatedAt: now
  }
}

function getProfileFallbackTargetRole() {
  return getAuthSession()?.role === 'STUDENT' ? fallbackProfile.targetPosition : '目标岗位'
}

export function createLearningPlan(payload: LearningPlanRequest) {
  const path = '/api/ai/learning/plans'
  const init: RequestInit = { method: 'POST', body: JSON.stringify(payload) }
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningPlan>(path, init)
  }
  const plan = buildLocalLearningPlan(payload)
  localLearningPlans.set(plan.planId, plan)
  return Promise.resolve(plan)
}

export function listLearningPlans() {
  const path = '/api/ai/learning/plans'
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningPlan[]>(path, { method: 'GET' })
  }
  return Promise.resolve([...localLearningPlans.values()].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt)))
}

export function getLearningPlan(planId: string) {
  const path = `/api/ai/learning/plans/${encodeURIComponent(planId)}`
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningPlan>(path, { method: 'GET' })
  }
  const plan = localLearningPlans.get(planId)
  return plan ? Promise.resolve(plan) : Promise.reject(new Error('学习计划不存在'))
}

export function updateLearningTask(planId: string, taskId: string, payload: LearningTaskUpdateRequest) {
  const path = `/api/ai/learning/plans/${encodeURIComponent(planId)}/tasks/${encodeURIComponent(taskId)}`
  const init: RequestInit = { method: 'PUT', body: JSON.stringify(payload) }
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningTask>(path, init)
  }
  const plan = localLearningPlans.get(planId)
  const task = plan?.tasks.find((item) => item.taskId === taskId)
  if (!plan || !task) {
    return Promise.reject(new Error('学习任务不存在'))
  }
  const updatedAt = new Date().toISOString()
  const updated = {
    ...task,
    status: payload.status,
    feedback: payload.feedback?.trim() || undefined,
    completedAt: payload.status === 'COMPLETED' ? updatedAt : undefined,
    updatedAt
  }
  localLearningPlans.set(planId, {
    ...plan,
    tasks: plan.tasks.map((item) => item.taskId === taskId ? updated : item),
    updatedAt
  })
  return Promise.resolve(updated)
}

export function replanLearningPlan(planId: string, payload: LearningPlanReplanRequest) {
  const path = `/api/ai/learning/plans/${encodeURIComponent(planId)}/replan`
  const init: RequestInit = { method: 'POST', body: JSON.stringify(payload) }
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningPlan>(path, init)
  }
  const current = localLearningPlans.get(planId)
  if (!current) {
    return Promise.reject(new Error('学习计划不存在'))
  }
  const revised = buildLocalLearningPlan({
    ...current,
    weeklyHours: payload.weeklyHours || current.weeklyHours,
    durationWeeks: payload.durationWeeks || current.durationWeeks
  }, current.version + 1, current.planId)
  localLearningPlans.set(revised.planId, revised)
  return Promise.resolve(revised)
}

export function listLearningPlanVersions(planId: string) {
  const path = `/api/ai/learning/plans/${encodeURIComponent(planId)}/versions`
  if (shouldUseApi(path)) {
    return authenticatedRequest<LearningPlan[]>(path, { method: 'GET' })
  }
  const versions = [...localLearningPlans.values()]
    .filter((plan) => plan.planId === planId || plan.revisionOfPlanId === planId)
    .sort((left, right) => left.version - right.version)
  return Promise.resolve(versions)
}

function buildLocalInterviewSession(payload: InterviewSessionRequest): InterviewSession {
  const now = new Date().toISOString()
  const questionCount = Math.max(1, Math.min(10, payload.questionCount || 5))
  return {
    sessionId: localId('SESSION'),
    studentId: payload.studentId || currentStudentId(),
    resumeId: payload.resumeId,
    jobId: payload.jobId,
    matchId: payload.matchId,
    targetRole: payload.targetRole?.trim() || getProfileFallbackTargetRole(),
    status: 'IN_PROGRESS',
    questions: Array.from({ length: questionCount }, (_, index) => {
      const source = fallbackInterviewQuestions[index % fallbackInterviewQuestions.length]
      return {
        questionId: `Q-${index + 1}`,
        question: source.question,
        category: source.category,
        difficulty: source.difficulty,
        referencePoints: source.referencePoints
      }
    }),
    answers: [],
    createdAt: now,
    updatedAt: now
  }
}

export function createInterviewSession(payload: InterviewSessionRequest) {
  const path = '/api/ai/interview/sessions'
  const init: RequestInit = { method: 'POST', body: JSON.stringify(payload) }
  if (shouldUseApi(path)) {
    return authenticatedRequest<InterviewSession>(path, init)
  }
  const session = buildLocalInterviewSession(payload)
  localInterviewSessions.set(session.sessionId, session)
  return Promise.resolve(session)
}

export function listInterviewSessions() {
  const path = '/api/ai/interview/sessions'
  if (shouldUseApi(path)) {
    return authenticatedRequest<InterviewSession[]>(path, { method: 'GET' })
  }
  return Promise.resolve([...localInterviewSessions.values()].sort((left, right) => right.updatedAt.localeCompare(left.updatedAt)))
}

export function getInterviewSession(sessionId: string) {
  const path = `/api/ai/interview/sessions/${encodeURIComponent(sessionId)}`
  if (shouldUseApi(path)) {
    return authenticatedRequest<InterviewSession>(path, { method: 'GET' })
  }
  const session = localInterviewSessions.get(sessionId)
  return session ? Promise.resolve(session) : Promise.reject(new Error('模拟面试会话不存在'))
}

export function saveInterviewSessionAnswer(sessionId: string, questionId: string, answer: string) {
  const path = `/api/ai/interview/sessions/${encodeURIComponent(sessionId)}/answers/${encodeURIComponent(questionId)}`
  const init: RequestInit = { method: 'PUT', body: JSON.stringify({ questionId, answer }) }
  if (shouldUseApi(path)) {
    return authenticatedRequest<InterviewSession>(path, init)
  }
  const session = localInterviewSessions.get(sessionId)
  if (!session) {
    return Promise.reject(new Error('模拟面试会话不存在'))
  }
  const now = new Date().toISOString()
  const nextAnswer = { questionId, answer: answer.trim(), updatedAt: now }
  const answers = session.answers.some((item) => item.questionId === questionId)
    ? session.answers.map((item) => item.questionId === questionId ? nextAnswer : item)
    : [...session.answers, nextAnswer]
  const updated = { ...session, answers, updatedAt: now }
  localInterviewSessions.set(sessionId, updated)
  return Promise.resolve(updated)
}

export function finishInterviewSession(sessionId: string) {
  const path = `/api/ai/interview/sessions/${encodeURIComponent(sessionId)}/finish`
  if (shouldUseApi(path)) {
    return authenticatedRequest<InterviewSessionReport>(path, { method: 'POST' })
  }
  const session = localInterviewSessions.get(sessionId)
  if (!session) {
    return Promise.reject(new Error('模拟面试会话不存在'))
  }
  const now = new Date().toISOString()
  const completed = { ...session, status: 'COMPLETED', completedAt: now, updatedAt: now }
  localInterviewSessions.set(sessionId, completed)
  const answered = completed.answers.filter((item) => item.answer).length
  const overallScore = Math.max(60, Math.min(92, 62 + answered * 6))
  return Promise.resolve({
    sessionId,
    overallScore,
    strengths: answered ? ['能够完成核心问题作答', '回答内容可用于后续复盘'] : [],
    gaps: answered < completed.questions.length ? ['仍有题目未完成作答'] : ['建议补充量化成果和技术取舍'],
    recommendations: ['使用 STAR 结构补全项目回答', '每次练习后记录一个可验证的量化结果'],
    questionFeedback: completed.questions.map((question) => ({
      questionId: question.questionId,
      score: completed.answers.some((answer) => answer.questionId === question.questionId && answer.answer) ? overallScore : 0,
      suggestions: ['补充背景、个人动作和最终结果']
    })),
    generatedAt: now,
    mocked: true
  } satisfies InterviewSessionReport)
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

function canReadKnowledgeDocument(document: KnowledgeDocument, role?: string) {
  const normalizedRole = role?.trim().toUpperCase()
  return !normalizedRole || document.roles.includes(normalizedRole) || document.roles.includes('ALL')
}

function fallbackKnowledgeResults(payload: KnowledgeSearchRequest): AiSearchResponse {
  const query = payload.query.trim()
  const limit = payload.limit ?? 5
  const normalized = query.toLowerCase()
  const role = payload.role || currentRole() || 'STUDENT'
  const results = fallbackKnowledgeDocuments
    .filter((document) => canReadKnowledgeDocument(document, role))
    .map((document) => {
      const text = `${document.title} ${document.content} ${document.tags.join(' ')}`.toLowerCase()
      const score = normalized
        ? (text.includes(normalized) ? 92 : query.split(/\s+/).reduce((sum, term) => sum + (text.includes(term.toLowerCase()) ? 12 : 0), 0))
        : 55
      return {
        id: document.documentId,
        type: 'knowledge',
        title: document.title,
        owner: document.createdBy,
        summary: document.content,
        score,
        highlights: document.tags.length ? document.tags : [document.category]
      }
    })
    .filter((result) => !normalized || result.score > 0)
    .sort((left, right) => right.score - left.score)
    .slice(0, limit)
  return {
    query,
    results,
    generatedAt: new Date().toISOString()
  }
}

export function searchKnowledgeBase(payload: KnowledgeSearchRequest) {
  const query = payload.query.trim()
  const limit = payload.limit ?? 5
  const role = payload.role || currentRole() || 'STUDENT'
  return strictRequest<AiSearchResponse>('/api/ai/knowledge/search', {
    method: 'POST',
    body: JSON.stringify({ ...payload, query, role, limit })
  }, fallbackKnowledgeResults({ ...payload, query, role, limit }))
}

function fallbackKnowledgeAnswer(payload: KnowledgeAnswerRequest): KnowledgeAnswerResponse {
  const query = payload.query.trim()
  const retrieval = fallbackKnowledgeResults({ ...payload, query, limit: payload.limit ?? 5 })
  const citations = retrieval.results.slice(0, payload.limit ?? 5).map((result, index) => ({
    documentId: result.id.replace(/-CH-\d+$/, ''),
    chunkId: result.id.includes('-CH-') ? result.id : `${result.id}-CH-${String(index + 1).padStart(3, '0')}`,
    title: result.title,
    source: result.owner,
    score: result.score,
    snippet: result.summary
  }))
  const answer = citations.length
    ? [
        '## 结论',
        '',
        `针对“${query || '当前问题'}”，本地演示知识库命中了以下资料，可按证据归纳回答。`,
        '',
        '## 关键证据',
        '',
        ...citations.map((citation, index) => (
          `${index + 1}. **${citation.title}** [${index + 1}]\n   - ${citation.snippet}`
        )),
        '',
        '## 引用依据',
        '',
        ...citations.map((citation, index) => `- [${index + 1}] ${citation.title} / ${citation.source} / ${citation.score} 分`)
      ].join('\n')
    : [
        '## 暂未找到可读资料',
        '',
        '知识库中暂时没有匹配的可读资料。',
        '',
        '可以尝试换成更短的关键词，或让管理员补充相关文档。'
      ].join('\n')
  return {
    query,
    answer,
    citations,
    mocked: true,
    provider: 'local-rag-fallback',
    generatedAt: new Date().toISOString()
  }
}

export function answerKnowledgeBase(payload: KnowledgeAnswerRequest) {
  const query = payload.query.trim()
  const limit = payload.limit ?? 5
  const role = payload.role || currentRole() || 'STUDENT'
  const body = { ...payload, query, role, limit, useAi: payload.useAi ?? true }
  return strictRequest<KnowledgeAnswerResponse>('/api/ai/knowledge/answer', {
    method: 'POST',
    body: JSON.stringify(body)
  }, fallbackKnowledgeAnswer(body))
}

export function listKnowledgeDocuments(keyword = '', role: string = currentRole() || 'STUDENT', limit = 20) {
  const params = new URLSearchParams()
  const normalizedKeyword = keyword.trim()
  const normalizedRole = role.trim()
  if (normalizedKeyword) {
    params.set('keyword', normalizedKeyword)
  }
  if (normalizedRole) {
    params.set('role', normalizedRole)
  }
  params.set('limit', String(limit))
  const normalized = normalizedKeyword.toLowerCase()
  const fallback = fallbackKnowledgeDocuments
    .filter((document) => canReadKnowledgeDocument(document, normalizedRole))
    .filter((document) => !normalized
      || document.title.toLowerCase().includes(normalized)
      || document.content.toLowerCase().includes(normalized)
      || document.category.toLowerCase().includes(normalized)
      || document.source.toLowerCase().includes(normalized)
      || document.createdBy.toLowerCase().includes(normalized)
      || document.tags.some((tag) => tag.toLowerCase().includes(normalized)))
    .sort((left, right) => new Date(right.createdAt).getTime() - new Date(left.createdAt).getTime())
    .slice(0, limit)
  return strictRequest<KnowledgeDocument[]>(`/api/ai/knowledge/documents?${params.toString()}`, { method: 'GET' }, fallback)
}

export function createKnowledgeDocument(payload: KnowledgeDocumentRequest) {
  const fallback: KnowledgeDocument = {
    ...payload,
    documentId: `KB-DEMO-${Date.now().toString().slice(-6)}`,
    createdBy: getAuthSession()?.userId || 'demo-admin',
    createdAt: new Date().toISOString()
  }
  return strictRequest<KnowledgeDocument>('/api/ai/knowledge/documents', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallback)
}

export function updateKnowledgeDocumentRoles(documentId: string, roles: string[]) {
  const normalizedRoles = roles.map((role) => role.trim()).filter(Boolean)
  const nextRoles = normalizedRoles.length ? normalizedRoles : ['ALL']
  const fallbackSource = fallbackKnowledgeDocuments.find((document) => document.documentId === documentId)
  const fallback: KnowledgeDocument = fallbackSource
    ? { ...fallbackSource, roles: nextRoles }
    : {
        documentId,
        title: 'Unknown knowledge document',
        content: '',
        category: 'general',
        source: 'fallback',
        tags: [],
        roles: nextRoles,
        createdBy: getAuthSession()?.userId || 'demo-admin',
        createdAt: new Date().toISOString()
      }
  const payload: KnowledgeDocumentRolesRequest = { roles: nextRoles }
  return strictRequest<KnowledgeDocument>(`/api/ai/knowledge/documents/${encodeURIComponent(documentId)}/roles`, {
    method: 'PATCH',
    body: JSON.stringify(payload)
  }, fallback)
}

export async function deleteKnowledgeDocument(documentId: string) {
  const path = `/api/ai/knowledge/documents/${encodeURIComponent(documentId)}`
  if (!shouldUseApi(path)) {
    return true
  }
  const init: RequestInit = { method: 'DELETE' }
  const response = await fetch(resolveRequestPath(path), {
    ...init,
    headers: requestHeaders(init)
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  const payload = (await response.json()) as ApiResponse<boolean>
  if (payload.code !== 0 || payload.data !== true) {
    throw new Error(payload.message || 'RAG 文档不存在或删除失败')
  }
  return true
}

export async function batchDeleteKnowledgeDocuments(documentIds: string[]) {
  const normalizedIds = [...new Set(documentIds.map((id) => id.trim()).filter(Boolean))]
  const fallback: KnowledgeDocumentBatchDeleteResult = {
    requestedCount: normalizedIds.length,
    deletedCount: normalizedIds.length,
    deletedDocumentIds: normalizedIds,
    missingDocumentIds: []
  }
  const path = '/api/ai/knowledge/documents/batch-delete'
  if (!shouldUseApi(path)) {
    return fallback
  }
  const init: RequestInit = {
    method: 'POST',
    body: JSON.stringify({ documentIds: normalizedIds })
  }
  const response = await fetch(resolveRequestPath(path), {
    ...init,
    headers: requestHeaders(init)
  })
  if (!response.ok) {
    throw new Error(`HTTP ${response.status}`)
  }
  const payload = (await response.json()) as ApiResponse<KnowledgeDocumentBatchDeleteResult>
  if (payload.code !== 0 || !payload.data) {
    throw new Error(payload.message || 'RAG 文档批量删除失败')
  }
  return payload.data
}

export function uploadKnowledgeFile(payload: KnowledgeFileUploadRequest, options: KnowledgeFileUploadOptions = {}) {
  const title = payload.title.trim() || payload.file.name
  const category = payload.category.trim() || 'general'
  const source = payload.source.trim() || 'admin-upload'
  const tags = payload.tags.map((tag) => tag.trim()).filter(Boolean)
  const roles = payload.roles.map((role) => role.trim()).filter(Boolean)
  const fileSize = payload.file.size
  const formData = new FormData()
  formData.set('file', payload.file)
  formData.set('title', title)
  formData.set('category', category)
  formData.set('source', source)
  formData.set('tags', tags.join(','))
  formData.set('roles', roles.join(','))
  const now = new Date().toISOString()
  const fallback: KnowledgeIngestionJob = {
    jobId: `KBI-DEMO-${Date.now().toString().slice(-6)}`,
    fileName: payload.file.name,
    title,
    category,
    source,
    status: 'READY',
    message: 'File accepted by local fallback ingestion.',
    documentId: `KB-DEMO-FILE-${Date.now().toString().slice(-6)}`,
    chunkCount: Math.max(1, Math.ceil(payload.file.size / 1200)),
    vectorCount: Math.max(1, Math.ceil(payload.file.size / 1200)),
    error: null,
    createdAt: now,
    updatedAt: now
  }
  if (!shouldUseApi('/api/ai/knowledge/files')) {
    options.onProgress?.({
      phase: 'completed',
      percent: 100,
      loaded: payload.file.size,
      total: payload.file.size,
      message: '本地演示模式已创建 RAG 导入任务。'
    })
    return Promise.resolve(fallback)
  }
  if (options.onProgress) {
    return uploadKnowledgeFileWithProgress(formData, fallback, options, fileSize)
  }
  return strictRequest<KnowledgeIngestionJob>('/api/ai/knowledge/files', {
    method: 'POST',
    body: formData
  }, fallback)
}

function uploadKnowledgeFileWithProgress(
  formData: FormData,
  fallback: KnowledgeIngestionJob,
  options: KnowledgeFileUploadOptions,
  fileSize: number
) {
  return new Promise<KnowledgeIngestionJob>((resolve, reject) => {
    const xhr = new XMLHttpRequest()
    xhr.open('POST', resolveRequestPath('/api/ai/knowledge/files'))
    requestHeaders({ method: 'POST', body: formData }).forEach((value, key) => {
      xhr.setRequestHeader(key, value)
    })
    xhr.timeout = 10 * 60 * 1000

    options.onProgress?.({
      phase: 'uploading',
      percent: 0,
      loaded: 0,
      total: fileSize,
      message: '正在上传文件到 RAG 知识库。'
    })

    xhr.upload.onprogress = (event) => {
      if (!event.lengthComputable) {
        options.onProgress?.({
          phase: 'uploading',
          percent: 15,
          message: '正在上传文件，浏览器暂时无法计算总大小。'
        })
        return
      }
      const percent = Math.min(90, Math.max(1, Math.round((event.loaded / event.total) * 90)))
      options.onProgress?.({
        phase: 'uploading',
        percent,
        loaded: event.loaded,
        total: event.total,
        message: '正在上传文件到 RAG 知识库。'
      })
    }

    xhr.upload.onload = () => {
      options.onProgress?.({
        phase: 'processing',
        percent: 92,
        loaded: fileSize,
        total: fileSize,
        message: '文件已上传，服务器正在创建解析和向量入库任务。'
      })
    }

    xhr.onload = () => {
      if (xhr.status < 200 || xhr.status >= 300) {
        options.onProgress?.({
          phase: 'failed',
          percent: 100,
          message: `RAG 文件上传失败：HTTP ${xhr.status}`
        })
        reject(new Error(`HTTP ${xhr.status}`))
        return
      }
      try {
        const payload = JSON.parse(xhr.responseText || '{}') as ApiResponse<KnowledgeIngestionJob>
        const job = payload.data || fallback
        options.onProgress?.({
          phase: 'completed',
          percent: 100,
          loaded: fileSize,
          total: fileSize,
          message: `导入任务已创建：${job.jobId}（${job.status}）`
        })
        resolve(job)
      } catch (error) {
        options.onProgress?.({
          phase: 'failed',
          percent: 100,
          message: 'RAG 文件上传响应解析失败。'
        })
        reject(error instanceof Error ? error : new Error('RAG 文件上传响应解析失败'))
      }
    }

    xhr.onerror = () => {
      options.onProgress?.({
        phase: 'failed',
        percent: 100,
        message: 'RAG 文件上传网络失败，请确认后端服务和网关已启动。'
      })
      reject(new Error('RAG 文件上传网络失败'))
    }

    xhr.ontimeout = () => {
      options.onProgress?.({
        phase: 'failed',
        percent: 100,
        message: 'RAG 文件上传超时，大文件可稍后刷新导入任务列表确认是否已进入后台处理。'
      })
      reject(new Error('RAG 文件上传超时'))
    }

    xhr.send(formData)
  })
}

export function listKnowledgeIngestions(query: KnowledgeIngestionQuery = {}) {
  const params = new URLSearchParams()
  const status = query.status?.trim().toUpperCase() as KnowledgeIngestionStatus | ''
  const limit = query.limit ?? 10
  if (status) {
    params.set('status', status)
  }
  params.set('limit', String(limit))
  const fallback = fallbackKnowledgeIngestionJobs
    .filter((job) => !status || job.status === status)
    .sort((left, right) => new Date(right.updatedAt).getTime() - new Date(left.updatedAt).getTime())
    .slice(0, limit)
  return strictRequest<KnowledgeIngestionJob[]>(`/api/ai/knowledge/ingestions?${params.toString()}`, {
    method: 'GET'
  }, fallback)
}

export function getKnowledgeVectorStatus() {
  return request<KnowledgeVectorStatus | BackendKnowledgeVectorStatus>('/api/ai/knowledge/vector/status', {
    method: 'GET'
  }, fallbackKnowledgeVectorStatus()).then(normalizeKnowledgeVectorStatus)
}

export function getKnowledgeBaseStats() {
  return strictRequest<KnowledgeBaseStats>('/api/ai/knowledge/stats', {
    method: 'GET'
  }, fallbackKnowledgeBaseStats())
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

export function listMyNotifications(studentId = currentStudentId()) {
  return request<NotificationMessage[]>(
    `/api/notifications/my?studentId=${encodeURIComponent(studentId)}`,
    { method: 'GET' },
    fallbackNotifications.filter((notification) => notification.targetRole === 'STUDENT' && notification.targetUserId === studentId))
}

export function listCompanyNotifications(companyId = currentCompanyId()) {
  return request<NotificationMessage[]>(
    `/api/notifications/company?companyId=${encodeURIComponent(companyId)}`,
    { method: 'GET' },
    fallbackNotifications.filter((notification) => notification.targetRole === 'COMPANY' && notification.targetUserId === companyId))
}

export function markNotificationRead(notification: NotificationMessage) {
  const updated = { ...notification, read: true }
  return request<NotificationMessage>(
    `/api/notifications/${encodeURIComponent(notification.notificationId)}/read`,
    { method: 'POST' },
    updated)
}

export function listMyInterviewSchedules(studentId = currentStudentId()) {
  return request<InterviewSchedule[]>(
    `/api/interviews/schedules/my?studentId=${encodeURIComponent(studentId)}`,
    { method: 'GET' },
    fallbackInterviewSchedules.filter((schedule) => schedule.studentId === studentId))
}

export function listCompanyInterviewSchedules(companyId = currentCompanyId()) {
  return request<InterviewSchedule[]>(
    `/api/interviews/schedules/company?companyId=${encodeURIComponent(companyId)}`,
    { method: 'GET' },
    fallbackInterviewSchedules.filter((schedule) => schedule.companyId === companyId))
}

export function createInterviewSchedule(payload: InterviewScheduleRequest) {
  const delivery = fallbackDeliveries.find((item) => item.deliveryId === payload.deliveryId)
  const fallback: InterviewSchedule = {
    scheduleId: `IS-DEMO-${Date.now().toString().slice(-6)}`,
    deliveryId: payload.deliveryId,
    companyId: payload.companyId || delivery?.companyId || currentCompanyId(),
    studentId: payload.studentId || delivery?.studentId || 'S001',
    jobId: payload.jobId || delivery?.jobId || 'J001',
    title: payload.title,
    startTime: payload.startTime,
    durationMinutes: payload.durationMinutes,
    location: payload.location,
    meetingUrl: payload.meetingUrl,
    note: payload.note,
    status: 'PROPOSED',
    createdAt: new Date().toISOString(),
    updatedAt: new Date().toISOString()
  }
  return request<InterviewSchedule>('/api/interviews/schedules', {
    method: 'POST',
    body: JSON.stringify(payload)
  }, fallback)
}

export function updateInterviewScheduleStatus(schedule: InterviewSchedule, status: InterviewScheduleStatus) {
  const updated = { ...schedule, status, updatedAt: new Date().toISOString() }
  return request<InterviewSchedule>(
    `/api/interviews/schedules/${encodeURIComponent(schedule.scheduleId)}/status?status=${status}`,
    { method: 'PUT' },
    updated)
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
  return strictRequest<AccountSummary[]>(`/api/admin/accounts${queryString ? `?${queryString}` : ''}`, { method: 'GET' }, fallback)
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
  return strictRequest<AccountSummary>('/api/admin/accounts', {
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
  return strictRequest<AccountSummary>(`/api/admin/accounts/${encodeURIComponent(accountId)}/status`, {
    method: 'PUT',
    body: JSON.stringify({ status })
  }, fallback)
}

export function changeAccountPassword(payload: ChangePasswordRequest) {
  return strictRequest<boolean>(`/api/accounts/${encodeURIComponent(payload.accountId)}/password`, {
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
    pendingDeliveryCount: 72,
    interviewRate: 36,
    offerRate: 9,
    activeStudentCount: 96,
    highPotentialCandidateCount: 18,
    weeklyDeliveryTrend: [
      { label: '06-01', deliveryCount: 42, interviewCount: 18, offerCount: 4 },
      { label: '06-02', deliveryCount: 48, interviewCount: 21, offerCount: 5 },
      { label: '06-03', deliveryCount: 56, interviewCount: 24, offerCount: 6 },
      { label: '06-04', deliveryCount: 61, interviewCount: 27, offerCount: 7 },
      { label: '06-05', deliveryCount: 53, interviewCount: 23, offerCount: 6 },
      { label: '06-06', deliveryCount: 52, interviewCount: 19, offerCount: 5 }
    ],
    skillDemandTop: [
      { skill: 'Java', jobCount: 38, matchedStudentCount: 74, demandScore: 92 },
      { skill: 'Spring Boot', jobCount: 34, matchedStudentCount: 61, demandScore: 88 },
      { skill: 'MySQL', jobCount: 31, matchedStudentCount: 58, demandScore: 84 },
      { skill: 'Redis', jobCount: 24, matchedStudentCount: 39, demandScore: 76 },
      { skill: 'Docker', jobCount: 18, matchedStudentCount: 33, demandScore: 68 }
    ],
    conversionFunnel: [
      { stage: 'SUBMITTED', label: '投递', count: 312, conversionRate: 100 },
      { stage: 'VIEWED', label: '已查看', count: 96, conversionRate: 31 },
      { stage: 'INTERVIEW', label: '进入面试', count: 84, conversionRate: 27 },
      { stage: 'OFFER', label: '录用', count: 28, conversionRate: 9 }
    ],
    riskAlerts: ['72 条投递仍处于待处理状态，需要提醒企业及时查看', 'Redis、Docker 等岗位技能需求增长，课程辅导应补充实战内容', '高潜候选人数偏少，建议优先辅导简历证据不足的学生']
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

export async function downloadAdminAuditExport(auditExport: AdminAuditExportResult) {
  const downloadUrl = auditExport.downloadUrl.trim()
  if (!downloadUrl) {
    throw new Error('审计导出下载地址无效')
  }

  const init: RequestInit = { method: 'GET' }
  const response = await fetch(resolveRequestPath(downloadUrl), {
    ...init,
    headers: requestHeaders(init)
  })
  if (!response.ok) {
    throw new Error(await responseErrorMessage(response, `HTTP ${response.status}`))
  }

  const fileName = auditExportCsvFileName(auditExport.fileName, downloadUrl)
  const objectUrl = URL.createObjectURL(await response.blob())
  const anchor = document.createElement('a')
  anchor.href = objectUrl
  anchor.download = fileName
  anchor.style.display = 'none'
  try {
    document.body.appendChild(anchor)
    anchor.click()
  } finally {
    anchor.remove()
    URL.revokeObjectURL(objectUrl)
  }
  return fileName
}

function auditExportCsvFileName(fileName: string, downloadUrl: string) {
  const path = downloadUrl.split(/[?#]/, 1)[0]
  const derivedName = path.slice(path.lastIndexOf('/') + 1)
  const candidate = fileName.trim() || derivedName || 'admin-audit-export'
  return candidate.toLowerCase().endsWith('.csv') ? candidate : `${candidate}.csv`
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
