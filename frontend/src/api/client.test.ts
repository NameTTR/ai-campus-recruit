import { beforeEach, describe, expect, it, vi } from 'vitest'
import { getDeliveryStatistics, listCompanyDeliveries, login, matchResumeJob } from './client'

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
