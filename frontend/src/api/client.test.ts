import { beforeEach, describe, expect, it, vi } from 'vitest'
import {
  generateInterviewQuestions,
  getDeliveryStatistics,
  listCompanyDeliveries,
  login,
  matchResumeJob,
  submitInterviewFeedback
} from './client'

describe('api fallback behavior', () => {
  beforeEach(() => {
    localStorage.clear()
    vi.restoreAllMocks()
    vi.stubGlobal('fetch', vi.fn(() => Promise.reject(new Error('offline'))))
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
