import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  changeAccountPassword,
  createAccount,
  createCandidateScreenTask,
  createDelivery,
  exportAdminAudit,
  getAuthSession,
  generateInterviewQuestions,
  getAdminAuditOverview,
  getAiObservabilitySummary,
  getAiStatus,
  getCandidateScreenTask,
  getCurrentPermissions,
  getDeploymentGuide,
  getProfile,
  getResume,
  getDeliveryStatistics,
  getDeploymentTopology,
  getSystemStatus,
  listAiCallRecords,
  listAccounts,
  listCandidateScreenRecords,
  listCandidateScreenTasks,
  listMyCandidateScreenRecords,
  listInterviewRecords,
  listCompanyDeliveries,
  login,
  matchResumeJob,
  saveAuthSession,
  screenCandidate,
  searchAiKnowledge,
  submitInterviewFeedback,
  retryCandidateScreenTask,
  updateAccountStatus
} from './client'

describe('api fallback behavior', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.unstubAllEnvs()
    vi.stubEnv('VITE_API_BASE_URL', '')
    vi.stubEnv('VITE_API_PROXY_TARGET', '')
    vi.stubEnv('VITE_AI_PROXY_TARGET', '')
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))))
  })

  it('uses fallback data without calling an unconfigured development gateway', async () => {
    const result = await getProfile()

    expect(result.userId).toBe('S001')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('returns account list fallback with filters when gateway is offline', async () => {
    const result = await listAccounts({ role: 'ADMIN', status: 'ACTIVE', keyword: 'admin' })

    expect(result).toHaveLength(1)
    expect(result[0].accountId).toBe('A001')
    expect(result[0].permissions).toContain('admin:account:read')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('creates account fallback with role permissions when gateway is offline', async () => {
    const result = await createAccount({
      username: 'new-company',
      password: 'change-me',
      displayName: 'New Company',
      role: 'COMPANY'
    })

    expect(result.role).toBe('COMPANY')
    expect(result.status).toBe('ACTIVE')
    expect(result.permissions).toContain('company:job:write')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls account status endpoint when api base url is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          accountId: 'S001',
          username: 'student',
          displayName: 'Student',
          role: 'STUDENT',
          status: 'DISABLED',
          permissions: ['student:profile:read'],
          createdAt: '2026-06-10T00:00:00Z',
          updatedAt: '2026-06-10T01:00:00Z'
        }
      })
    } as Response)

    const result = await updateAccountStatus('S001', 'DISABLED')

    expect(result.status).toBe('DISABLED')
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/admin/accounts/S001/status', expect.any(Object))
    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(requestInit.body))).toEqual({ status: 'DISABLED' })
  })

  it('calls password change endpoint when api base url is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: true
      })
    } as Response)

    const result = await changeAccountPassword({
      accountId: 'S001',
      oldPassword: 'old-pass',
      newPassword: 'new-pass'
    })

    expect(result).toBe(true)
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/accounts/S001/password', expect.any(Object))
  })

  it('returns current permissions fallback from the saved session', async () => {
    saveAuthSession({
      token: 'admin-token',
      userId: 'A777',
      displayName: 'Admin User',
      role: 'ADMIN'
    })

    const result = await getCurrentPermissions()

    expect(result.userId).toBe('A777')
    expect(result.role).toBe('ADMIN')
    expect(result.permissions).toContain('admin:rbac:read')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('uses configured api base url when provided', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080/')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: { userId: 'S900', displayName: 'Test', role: 'STUDENT', school: 'A', major: 'B', skills: [], targetPosition: 'C' }
      })
    } as Response)

    const result = await getProfile()

    expect(result.userId).toBe('S900')
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/students/profile', expect.any(Object))
  })

  it('sends localStorage token as a Bearer authorization header', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    localStorage.setItem('token', 'test-jwt-token')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: { userId: 'S901', displayName: 'Token User', role: 'STUDENT', school: 'A', major: 'B', skills: [], targetPosition: 'C' }
      })
    } as Response)

    await getProfile()

    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(new Headers(requestInit.headers).get('Authorization')).toBe('Bearer test-jwt-token')
  })

  it('returns role-aware login fallback when gateway is offline', async () => {
    const result = await login('company', '123456')

    expect(result.role).toBe('COMPANY')
    expect(result.userId).toBe('C001')
    expect(result.token).toBe('demo-company-token')
  })

  it('saves login session with user identity', () => {
    saveAuthSession({
      token: 'session-token',
      userId: 'S777',
      displayName: 'Session Student',
      role: 'STUDENT'
    })

    expect(localStorage.getItem('userId')).toBe('S777')
    expect(getAuthSession()).toEqual({
      token: 'session-token',
      userId: 'S777',
      displayName: 'Session Student',
      role: 'STUDENT'
    })
  })

  it('returns match fallback when gateway is offline', async () => {
    const result = await matchResumeJob('R001', 'J001')

    expect(result.score).toBe(88)
    expect(result.suggestions.length).toBeGreaterThan(0)
  })

  it('sends current student identity when matching resume and job', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080/')
    saveAuthSession({
      token: 'student-token',
      userId: 'S777',
      displayName: 'Session Student',
      role: 'STUDENT'
    })
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          matchId: 'M777',
          resumeId: 'R777',
          jobId: 'J001',
          studentId: 'S777',
          score: 91,
          strengths: ['fit'],
          gaps: [],
          suggestions: []
        }
      })
    } as Response)

    await matchResumeJob('R777', 'J001')

    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(requestInit.body))).toMatchObject({
      studentId: 'S777',
      resumeId: 'R777',
      jobId: 'J001'
    })
  })

  it('sends resume parse metadata when creating a delivery', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080/')
    saveAuthSession({
      token: 'student-token',
      userId: 'S777',
      displayName: 'Session Student',
      role: 'STUDENT'
    })
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          deliveryId: 'D900',
          studentId: 'S777',
          resumeId: 'R900',
          jobId: 'J001',
          companyId: 'C001',
          status: 'SUBMITTED',
          resumeSourceFormat: 'PDF',
          resumeParseStatus: 'TEXT_EXTRACTED',
          resumeParsedTextLength: 123,
          createdAt: '2026-06-04T00:00:00Z'
        }
      })
    } as Response)

    const result = await createDelivery({
      resumeId: 'R900',
      sourceFormat: 'PDF',
      parseStatus: 'TEXT_EXTRACTED',
      parsedTextLength: 123
    }, 'J001')

    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    const body = JSON.parse(String(requestInit.body))
    expect(body).toEqual({
      studentId: 'S777',
      resumeId: 'R900',
      jobId: 'J001',
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 123
    })
    expect(body.sourceFormat).toBeUndefined()
    expect(result.sourceFormat).toBe('PDF')
    expect(result.parseStatus).toBe('TEXT_EXTRACTED')
    expect(result.parsedTextLength).toBe(123)
  })

  it('keeps resume parse metadata on delivery fallback', async () => {
    const result = await createDelivery({
      resumeId: 'R901',
      resumeSourceFormat: 'DOCX',
      resumeParseStatus: 'UNPARSED',
      resumeParsedTextLength: 0
    }, 'J001')

    expect(result.resumeId).toBe('R901')
    expect(result.sourceFormat).toBe('DOCX')
    expect(result.resumeSourceFormat).toBe('DOCX')
    expect(result.parseStatus).toBe('UNPARSED')
    expect(result.resumeParseStatus).toBe('UNPARSED')
    expect(result.parsedTextLength).toBe(0)
    expect(result.resumeParsedTextLength).toBe(0)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('returns resume fallback with storage status when gateway is offline', async () => {
    const result = await getResume()

    expect(result.resumeId).toBe('R001')
    expect(result.objectKey).toBe('resumes/R001/demo-resume.pdf')
    expect(result.storageProvider).toBe('local-demo')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('returns interview question fallback when gateway is offline', async () => {
    const result = await generateInterviewQuestions({
      studentId: 'S001',
      resumeId: 'R001',
      jobId: 'J001',
      targetRole: 'Java backend intern',
      skills: ['Java', 'Spring Boot']
    })

    expect(result.length).toBeGreaterThan(1)
    expect(result[0].referencePoints.length).toBeGreaterThan(0)
  })

  it('returns interview feedback fallback when gateway is offline', async () => {
    const result = await submitInterviewFeedback({
      studentId: 'S001',
      questionId: 'IQ-001',
      question: 'Describe a key technical decision in your project.',
      answer: 'I handled API development and database design, then improved query performance with cache.',
      targetRole: 'Java backend intern'
    })

    expect(result.mocked).toBe(true)
    expect(result.score).toBeGreaterThan(0)
    expect(result.suggestions.length).toBeGreaterThan(0)
  })

  it('returns candidate screen fallback when ai proxy is not configured', async () => {
    const result = await screenCandidate({
      deliveryId: 'D100',
      studentId: 'S100',
      resumeId: 'R100',
      jobId: 'J100',
      companyId: 'C001',
      targetRole: 'Java backend intern',
      skills: ['Java', 'Spring Boot'],
      projects: ['Recruitment platform'],
      jobRequirements: ['Java', 'MySQL'],
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 88,
      resumeSummary: 'Java Web project experience.',
      jobDescription: 'Backend API development.'
    })

    expect(result.deliveryId).toBe('D100')
    expect(result.resumeSourceFormat).toBe('PDF')
    expect(result.resumeParseStatus).toBe('TEXT_EXTRACTED')
    expect(result.resumeParsedTextLength).toBe(88)
    expect(result.recommendation.length).toBeGreaterThan(0)
    expect(result.risks.length).toBeGreaterThan(0)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls ai candidate screen endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          deliveryId: 'D200',
          studentId: 'S200',
          jobId: 'J200',
          score: 91,
          recommendation: 'Proceed to technical interview',
          strengths: ['Skill match'],
          risks: ['Confirm project depth'],
          interviewQuestions: ['Describe cache design.'],
          nextActions: ['Schedule first interview'],
          mocked: false
        }
      })
    } as Response)

    const result = await screenCandidate({
      deliveryId: 'D200',
      studentId: 'S200',
      resumeId: 'R200',
      jobId: 'J200',
      companyId: 'C001',
      targetRole: 'Java backend intern',
      skills: ['Java'],
      projects: ['Recruitment platform'],
      jobRequirements: ['Java'],
      resumeSummary: 'Java project experience.',
      jobDescription: 'Backend development.'
    })

    expect(result.mocked).toBe(false)
    expect(result.score).toBe(91)
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen', expect.any(Object))
  })

  it('returns async candidate screen task fallback when ai proxy is not configured', async () => {
    const result = await createCandidateScreenTask({
      deliveryId: 'D-TASK-100',
      studentId: 'S100',
      resumeId: 'R100',
      jobId: 'J100',
      companyId: 'C001',
      targetRole: 'Java backend intern',
      skills: ['Java', 'Spring Boot'],
      projects: ['Recruitment platform'],
      jobRequirements: ['Java', 'MySQL'],
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 88,
      resumeSummary: 'Java Web project experience.',
      jobDescription: 'Backend API development.'
    })

    expect(result.taskId).toBe('TASK-DEMO-D-TASK-100')
    expect(result.status).toBe('COMPLETED')
    expect(result.source).toBe('DEMO')
    expect(result.result?.deliveryId).toBe('D-TASK-100')
    expect(result.result?.resumeParsedTextLength).toBe(88)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls async candidate screen task endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          taskId: 'TASK-200',
          deliveryId: 'D200',
          companyId: 'C001',
          studentId: 'S200',
          resumeId: 'R200',
          jobId: 'J200',
          status: 'PENDING',
          source: 'ROCKETMQ',
          message: 'queued',
          createdAt: '2026-06-10T00:00:00Z',
          updatedAt: '2026-06-10T00:00:00Z'
        }
      })
    } as Response)

    const result = await createCandidateScreenTask({
      deliveryId: 'D200',
      studentId: 'S200',
      resumeId: 'R200',
      jobId: 'J200',
      companyId: 'C001',
      targetRole: 'Java backend intern',
      skills: ['Java'],
      projects: ['Recruitment platform'],
      jobRequirements: ['Java'],
      resumeSummary: 'Java project experience.',
      jobDescription: 'Backend development.'
    })

    expect(result.taskId).toBe('TASK-200')
    expect(result.status).toBe('PENDING')
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen/tasks', expect.any(Object))
  })

  it('returns async candidate screen task list fallback when ai proxy is not configured', async () => {
    const result = await listCandidateScreenTasks('C001')

    expect(result.length).toBeGreaterThan(0)
    expect(result.every((task) => task.companyId === 'C001')).toBe(true)
    expect(result.some((task) => task.status === 'COMPLETED' && task.result)).toBe(true)
    expect(result.some((task) => task.status === 'RUNNING')).toBe(true)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls async candidate screen task list endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: [{
          taskId: 'TASK-300',
          deliveryId: 'D300',
          companyId: 'C001',
          studentId: 'S300',
          resumeId: 'R300',
          jobId: 'J300',
          status: 'COMPLETED',
          source: 'RUNTIME',
          message: 'completed',
          createdAt: '2026-06-10T00:00:00Z',
          updatedAt: '2026-06-10T00:01:00Z'
        }]
      })
    } as Response)

    const result = await listCandidateScreenTasks('C001', 'D300')

    expect(result[0].taskId).toBe('TASK-300')
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen/tasks?companyId=C001&deliveryId=D300', expect.any(Object))
  })

  it('returns async candidate screen task detail fallback when ai proxy is not configured', async () => {
    const result = await getCandidateScreenTask('TASK-DEMO-003', 'C001')

    expect(result.taskId).toBe('TASK-DEMO-003')
    expect(result.status).toBe('FAILED')
    expect(result.message).toContain('Retry')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls async candidate screen task detail endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          taskId: 'TASK-DETAIL-001',
          deliveryId: 'D301',
          companyId: 'C001',
          studentId: 'S301',
          resumeId: 'R301',
          jobId: 'J301',
          status: 'RUNNING',
          source: 'RUNTIME',
          message: 'running',
          createdAt: '2026-06-10T00:00:00Z',
          updatedAt: '2026-06-10T00:01:00Z'
        }
      })
    } as Response)

    const result = await getCandidateScreenTask('TASK-DETAIL-001', 'C001')

    expect(result.status).toBe('RUNNING')
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen/tasks/TASK-DETAIL-001?companyId=C001', expect.any(Object))
  })

  it('returns retry fallback for failed async candidate screen task', async () => {
    const result = await retryCandidateScreenTask('TASK-DEMO-003', 'C001')

    expect(result.taskId).toBe('TASK-DEMO-RETRY-D003')
    expect(result.status).toBe('PENDING')
    expect(result.message).toContain('retry')
    expect(result.result).toBeUndefined()
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls async candidate screen task retry endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          taskId: 'TASK-RETRY-001',
          deliveryId: 'D303',
          companyId: 'C001',
          studentId: 'S303',
          resumeId: 'R303',
          jobId: 'J303',
          status: 'PENDING',
          source: 'RUNTIME',
          message: 'retry queued',
          createdAt: '2026-06-10T00:00:00Z',
          updatedAt: '2026-06-10T00:00:00Z'
        }
      })
    } as Response)

    const result = await retryCandidateScreenTask('TASK-FAILED-001', 'C001')

    expect(result.taskId).toBe('TASK-RETRY-001')
    expect(result.status).toBe('PENDING')
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen/tasks/TASK-FAILED-001/retry?companyId=C001', expect.any(Object))
  })

  it('returns candidate screen history fallback when ai proxy is not configured', async () => {
    const result = await listCandidateScreenRecords('C001')

    expect(result.length).toBeGreaterThan(0)
    expect(result[0].screeningId).toBe('CS-DEMO-001')
    expect(result[0].companyId).toBe('C001')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('uses current company identity when loading candidate screen history', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    saveAuthSession({
      token: 'company-token',
      userId: 'C777',
      displayName: 'Company HR',
      role: 'COMPANY'
    })
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: []
      })
    } as Response)

    await listCandidateScreenRecords()

    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screenings?companyId=C777', expect.any(Object))
  })

  it('calls ai candidate screen history endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: [{
          screeningId: 'CS200',
          companyId: 'C001',
          deliveryId: 'D200',
          studentId: 'S200',
          jobId: 'J200',
          score: 91,
          recommendation: 'Proceed to technical interview',
          strengths: ['Skill match'],
          risks: ['Confirm project depth'],
          interviewQuestions: ['Describe cache design.'],
          nextActions: ['Schedule first interview'],
          mocked: false,
          createdAt: '2026-06-02T01:00:00Z'
        }]
      })
    } as Response)

    const result = await listCandidateScreenRecords('C001', 'D200')

    expect(result[0].screeningId).toBe('CS200')
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screenings?companyId=C001&deliveryId=D200', expect.any(Object))
  })

  it('returns my candidate screen records fallback for the current student', async () => {
    const result = await listMyCandidateScreenRecords('S001')

    expect(result.length).toBeGreaterThan(0)
    expect(result.every((record) => record.studentId === 'S001')).toBe(true)
    expect(result[0].screeningId).toBe('CS-DEMO-001')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('uses current student identity when loading my candidate screen records', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    saveAuthSession({
      token: 'student-token',
      userId: 'S777',
      displayName: 'Session Student',
      role: 'STUDENT'
    })
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: []
      })
    } as Response)

    await listMyCandidateScreenRecords()

    expect(fetch).toHaveBeenCalledWith('/api/ai/screenings/my?studentId=S777', expect.any(Object))
  })

  it('calls my candidate screening endpoint with encoded student id when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: [{
          screeningId: 'CS300',
          companyId: 'C001',
          deliveryId: 'D300',
          studentId: 'S 300',
          jobId: 'J300',
          score: 89,
          recommendation: 'Proceed to technical interview',
          strengths: ['Java backend match'],
          risks: ['Confirm project metrics'],
          interviewQuestions: ['Introduce an API optimization.'],
          nextActions: ['Schedule first interview'],
          mocked: false,
          createdAt: '2026-06-10T01:00:00Z'
        }]
      })
    } as Response)

    const result = await listMyCandidateScreenRecords('S 300')

    expect(result[0].screeningId).toBe('CS300')
    expect(fetch).toHaveBeenCalledWith('/api/ai/screenings/my?studentId=S%20300', expect.any(Object))
  })

  it('returns ai module status fallback when gateway is offline', async () => {
    const result = await getAiStatus()

    expect(result.provider).toBe('dashscope')
    expect(result.configured).toBe(false)
    expect(result.capabilities.length).toBeGreaterThan(0)
    expect(result.fallbackReason).toContain('DASHSCOPE_API_KEY')
  })

  it('returns ai observability summary fallback when ai proxy is not configured', async () => {
    const result = await getAiObservabilitySummary()

    expect(result.totalCalls).toBeGreaterThan(0)
    expect(result.successCalls + result.failedCalls).toBe(result.totalCalls)
    expect(result.mockedCalls).toBeGreaterThan(0)
    expect(result.provider).toBe('dashscope')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls ai observability summary endpoint when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          provider: 'dashscope',
          model: 'qwen-plus',
          configured: true,
          totalCalls: 10,
          successCalls: 9,
          failedCalls: 1,
          mockedCalls: 0,
          successRate: 90,
          averageLatencyMs: 700,
          recentCalls: [],
          generatedAt: '2026-06-10T00:00:00Z'
        }
      })
    } as Response)

    const result = await getAiObservabilitySummary()

    expect(result.totalCalls).toBe(10)
    expect(fetch).toHaveBeenCalledWith('/api/ai/observability/summary', expect.any(Object))
  })

  it('filters ai call records fallback by provider and success', async () => {
    const result = await listAiCallRecords({ provider: 'local-semantic-search', success: true, limit: 20 })

    expect(result).toHaveLength(1)
    expect(result[0].provider).toBe('local-semantic-search')
    expect(result[0].operation).toBe('semantic-search')
    expect(result[0].success).toBe(true)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls ai call records endpoint with query parameters when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: []
      })
    } as Response)

    await listAiCallRecords({ limit: 10, provider: 'dashscope', success: true })

    expect(fetch).toHaveBeenCalledWith('/api/ai/observability/calls?limit=10&provider=dashscope&success=true', expect.any(Object))
  })

  it('returns ai search fallback results when ai proxy is not configured', async () => {
    const result = await searchAiKnowledge({ query: 'java', role: 'ADMIN', limit: 2 })

    expect(result.query).toBe('java')
    expect(result.results.length).toBeGreaterThan(0)
    expect(result.results.length).toBeLessThanOrEqual(2)
    expect(result.results[0].highlights.length).toBeGreaterThan(0)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls ai search endpoint with trimmed query and limit when ai proxy is configured', async () => {
    vi.stubEnv('VITE_AI_PROXY_TARGET', 'http://127.0.0.1:8106')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          query: 'backend',
          results: [],
          generatedAt: '2026-06-10T00:00:00Z'
        }
      })
    } as Response)

    const result = await searchAiKnowledge({ query: ' backend ', role: 'ADMIN', limit: 8 })

    expect(result.query).toBe('backend')
    expect(fetch).toHaveBeenCalledWith('/api/ai/search', expect.any(Object))
    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(requestInit.body))).toEqual({
      query: 'backend',
      role: 'ADMIN',
      limit: 8
    })
  })

  it('returns interview record fallback when gateway is offline', async () => {
    const result = await listInterviewRecords('S001')

    expect(result.length).toBeGreaterThan(0)
    expect(result[0].score).toBeGreaterThan(0)
    expect(result[0].suggestions.length).toBeGreaterThan(0)
  })

  it('returns company delivery fallback when gateway is offline', async () => {
    const result = await listCompanyDeliveries('C001')

    expect(result.length).toBeGreaterThan(0)
    expect(result.every((delivery) => delivery.companyId === 'C001')).toBe(true)
    expect(result[0].status).toBe('SUBMITTED')
    expect(result[0].resumeSourceFormat).toBe('PDF')
    expect(result[0].resumeParseStatus).toBe('TEXT_EXTRACTED')
    expect(result[0].resumeParsedTextLength).toBe(62)
  })

  it('returns delivery statistics fallback when gateway is offline', async () => {
    const result = await getDeliveryStatistics()

    expect(result.totalCount).toBe(5)
    expect(result.pendingCount).toBe(1)
    expect(result.statusCounts.INTERVIEW).toBe(1)
  })

  it('returns system status fallback when gateway is offline', async () => {
    const result = await getSystemStatus()

    expect(result.applicationName).toBe('user-service')
    expect(result.services.some((service) => service.name === 'resume-service')).toBe(true)
    expect(result.persistence.some((item) => item.database === 'ai_campus_recruit')).toBe(true)
    expect(result.warnings.length).toBeGreaterThan(0)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls admin system status endpoint when api base url is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          generatedAt: '2026-06-07T00:00:00Z',
          applicationName: 'user-service',
          environment: 'test',
          services: [],
          persistence: [],
          infrastructure: [],
          warnings: []
        }
      })
    } as Response)

    const result = await getSystemStatus()

    expect(result.environment).toBe('test')
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/admin/system/status', expect.any(Object))
  })

  it('returns deployment topology fallback when gateway is offline', async () => {
    const result = await getDeploymentTopology()

    expect(result.nodes).toHaveLength(3)
    expect(result.nodes[0].host).toBe('192.168.56.11')
    expect(result.nodes[1].services.some((service) => service.name === 'user-service')).toBe(true)
    expect(result.nodes[2].services.some((service) => service.name === 'ai-service')).toBe(true)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls deployment topology endpoint when api base url is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          generatedAt: '2026-06-07T00:00:00Z',
          environment: 'test',
          nodes: [
            { id: 'vm1', name: 'VM1', host: '10.0.0.11', role: 'entry', services: [] }
          ],
          warnings: []
        }
      })
    } as Response)

    const result = await getDeploymentTopology()

    expect(result.nodes[0].host).toBe('10.0.0.11')
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/admin/system/topology', expect.any(Object))
  })

  it('returns deployment guide fallback when gateway is offline', async () => {
    const result = await getDeploymentGuide()

    expect(result.steps).toHaveLength(4)
    expect(result.steps[0].nodeId).toBe('vm3')
    expect(result.steps[1].nodeId).toBe('vm1')
    expect(result.steps[2].nodeId).toBe('vm2')
    expect(result.acceptanceChecks.length).toBeGreaterThan(0)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls deployment guide endpoint when api base url is configured', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          generatedAt: '2026-06-08T00:00:00Z',
          environment: 'test',
          summary: 'guide',
          steps: [],
          acceptanceChecks: [],
          warnings: []
        }
      })
    } as Response)

    const result = await getDeploymentGuide()

    expect(result.summary).toBe('guide')
    expect(fetch).toHaveBeenCalledWith('http://localhost:18080/api/admin/system/deployment-guide', expect.any(Object))
  })

  it('returns admin audit overview fallback with filters when gateway is offline', async () => {
    const result = await getAdminAuditOverview({
      entityType: 'AI_SCREENING',
      studentId: 'S001',
      keyword: 'screening',
      limit: 10
    })

    expect(result.source).toBe('frontend-demo')
    expect(result.records).toHaveLength(1)
    expect(result.records[0].entityType).toBe('AI_SCREENING')
    expect(result.records[0].studentId).toBe('S001')
    expect(result.metrics.some((metric) => metric.key === 'aiRecords' && metric.value === 1)).toBe(true)
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls admin audit overview endpoint when api proxy is configured', async () => {
    vi.stubEnv('VITE_API_PROXY_TARGET', 'http://localhost:8080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          generatedAt: '2026-06-10T00:00:00Z',
          source: 'gateway',
          query: { entityType: 'DELIVERY', companyId: 'C001', limit: 50 },
          metrics: [],
          records: [],
          warnings: []
        }
      })
    } as Response)

    const result = await getAdminAuditOverview({ entityType: 'DELIVERY', companyId: 'C001', limit: 50 })

    expect(result.source).toBe('gateway')
    expect(fetch).toHaveBeenCalledWith('/api/admin/audit/overview?entityType=DELIVERY&companyId=C001&limit=50', expect.any(Object))
  })

  it('returns admin audit export fallback without calling fetch when gateway is offline', async () => {
    const result = await exportAdminAudit({ entityType: 'AI_INTERVIEW', studentId: 'S001' })

    expect(result.exportId).toBe('AUDIT-EXPORT-DEMO-001')
    expect(result.format).toBe('CSV')
    expect(result.rowCount).toBe(1)
    expect(result.query.entityType).toBe('AI_INTERVIEW')
    expect(fetch).not.toHaveBeenCalled()
  })

  it('calls admin audit export endpoint with normalized body when api proxy is configured', async () => {
    vi.stubEnv('VITE_API_PROXY_TARGET', 'http://localhost:8080')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          exportId: 'EXP-001',
          format: 'CSV',
          fileName: 'audit.csv',
          downloadUrl: '/api/admin/audit/export/EXP-001',
          expiresAt: '2026-06-10T02:00:00Z',
          rowCount: 3,
          generatedAt: '2026-06-10T00:00:00Z',
          query: { keyword: 'java', limit: 20 }
        }
      })
    } as Response)

    const result = await exportAdminAudit({ keyword: ' java ', limit: 20 })

    expect(result.exportId).toBe('EXP-001')
    expect(fetch).toHaveBeenCalledWith('/api/admin/audit/export', expect.any(Object))
    const requestInit = vi.mocked(fetch).mock.calls[0][1] as RequestInit
    expect(JSON.parse(String(requestInit.body))).toEqual({
      keyword: 'java',
      limit: 20,
      format: 'CSV'
    })
  })
})
