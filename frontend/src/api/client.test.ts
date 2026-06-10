import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  changeAccountPassword,
  createAccount,
  createDelivery,
  getAuthSession,
  generateInterviewQuestions,
  getAiObservabilitySummary,
  getAiStatus,
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
  listMyCandidateScreenRecords,
  listInterviewRecords,
  listCompanyDeliveries,
  login,
  matchResumeJob,
  saveAuthSession,
  screenCandidate,
  searchAiKnowledge,
  submitInterviewFeedback,
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
      targetRole: 'Java 后端实习生',
      skills: ['Java', 'Spring Boot']
    })

    expect(result.length).toBeGreaterThan(1)
    expect(result[0].referencePoints.length).toBeGreaterThan(0)
  })

  it('returns interview feedback fallback when gateway is offline', async () => {
    const result = await submitInterviewFeedback({
      studentId: 'S001',
      questionId: 'IQ-001',
      question: '请说明项目中的关键技术方案。',
      answer: '我负责接口开发和数据库设计，并通过缓存优化查询性能。',
      targetRole: 'Java 后端实习生'
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
      targetRole: 'Java 后端实习生',
      skills: ['Java', 'Spring Boot'],
      projects: ['招聘平台'],
      jobRequirements: ['Java', 'MySQL'],
      resumeSourceFormat: 'PDF',
      resumeParseStatus: 'TEXT_EXTRACTED',
      resumeParsedTextLength: 88,
      resumeSummary: '具备 Java Web 项目经历。',
      jobDescription: '参与后端接口开发。'
    })

    expect(result.deliveryId).toBe('D100')
    expect(result.resumeSourceFormat).toBe('PDF')
    expect(result.resumeParseStatus).toBe('TEXT_EXTRACTED')
    expect(result.resumeParsedTextLength).toBe(88)
    expect(result.recommendation).toContain('一面')
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
          recommendation: '优先进入技术一面',
          strengths: ['技能匹配'],
          risks: ['需要确认项目深度'],
          interviewQuestions: ['请说明缓存设计。'],
          nextActions: ['安排一面'],
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
      targetRole: 'Java 后端实习生',
      skills: ['Java'],
      projects: ['招聘平台'],
      jobRequirements: ['Java'],
      resumeSummary: 'Java 项目经历。',
      jobDescription: '后端开发。'
    })

    expect(result.mocked).toBe(false)
    expect(result.score).toBe(91)
    expect(fetch).toHaveBeenCalledWith('/api/ai/candidates/screen', expect.any(Object))
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
          recommendation: '优先进入技术一面',
          strengths: ['技能匹配'],
          risks: ['需要确认项目深度'],
          interviewQuestions: ['请说明缓存设计。'],
          nextActions: ['安排一面'],
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
          recommendation: '进入技术面',
          strengths: ['匹配 Java 后端岗位'],
          risks: ['项目指标需要确认'],
          interviewQuestions: ['介绍一次接口优化'],
          nextActions: ['安排一面'],
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
})
