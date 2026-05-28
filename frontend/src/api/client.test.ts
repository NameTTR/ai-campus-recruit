import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  generateInterviewQuestions,
  getAiStatus,
  getProfile,
  getDeliveryStatistics,
  listInterviewRecords,
  listCompanyDeliveries,
  login,
  matchResumeJob,
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
  })

  it('returns delivery statistics fallback when gateway is offline', async () => {
    const result = await getDeliveryStatistics()

    expect(result.totalCount).toBe(5)
    expect(result.pendingCount).toBe(1)
    expect(result.statusCounts.INTERVIEW).toBe(1)
  })
})
