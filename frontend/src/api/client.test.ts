import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  createDelivery,
  generateInterviewQuestions,
  getAiStatus,
  getProfile,
  getResume,
  getDeliveryStatistics,
  getSystemStatus,
  listCandidateScreenRecords,
  listInterviewRecords,
  listCompanyDeliveries,
  login,
  matchResumeJob,
  screenCandidate,
  submitInterviewFeedback
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

  it('returns role-aware login fallback when gateway is offline', async () => {
    const result = await login('company', '123456')

    expect(result.role).toBe('COMPANY')
    expect(result.userId).toBe('C001')
    expect(result.token).toBe('demo-company-token')
  })

  it('returns match fallback when gateway is offline', async () => {
    const result = await matchResumeJob('R001', 'J001')

    expect(result.score).toBe(88)
    expect(result.suggestions.length).toBeGreaterThan(0)
  })

  it('sends resume parse metadata when creating a delivery', async () => {
    vi.stubEnv('VITE_API_BASE_URL', 'http://localhost:18080/')
    vi.mocked(fetch).mockResolvedValueOnce({
      ok: true,
      json: () => Promise.resolve({
        code: 0,
        message: 'ok',
        data: {
          deliveryId: 'D900',
          studentId: 'S001',
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
      studentId: 'S001',
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

  it('returns ai module status fallback when gateway is offline', async () => {
    const result = await getAiStatus()

    expect(result.provider).toBe('dashscope')
    expect(result.configured).toBe(false)
    expect(result.capabilities.length).toBeGreaterThan(0)
    expect(result.fallbackReason).toContain('DASHSCOPE_API_KEY')
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
})
