import { describe, expect, it } from 'vitest'
import {
  citationLocation,
  comparableInterviewSessions,
  evaluationCanRetry,
  firstActionableQuestionIndex,
  matchContextIsCurrent,
  parseEvidenceLinks,
  retrievalFromAnswer,
  retrievalModeLabel
} from './coreDeepening'

describe('deepened student workflow evidence helpers', () => {
  it('detects stale match context instead of treating an old score as current', () => {
    const match = {
      details: {
        skillsCoverage: 50, evidenceCoverage: 25, requirements: [], conditions: [], stale: false,
        metadata: { inputFingerprint: 'x', algorithmVersion: 'v1' },
        profileSnapshot: { education: '本科', skills: ['Java'], projects: ['订单项目'] },
        jobSnapshot: { jobId: 'j1', companyId: 'c1', companyName: 'A', title: 'Java', city: '上海', salaryRange: '', requiredSkills: ['Java'], description: '后端', aiSummary: '' }
      }
    } as any
    expect(matchContextIsCurrent(match, { education: '本科', skills: ['Java'], projects: ['订单项目'] }, match.details.jobSnapshot)).toBe(true)
    expect(matchContextIsCurrent(match, { education: '本科', skills: ['Redis'], projects: ['订单项目'] }, match.details.jobSnapshot)).toBe(false)
    match.details.stale = true
    expect(matchContextIsCurrent(match, { education: '本科', skills: ['Java'], projects: ['订单项目'] }, match.details.jobSnapshot)).toBe(false)
  })

  it('requires answer evaluation before moving to the next interview question', () => {
    const session = { questions: [{ questionId: 'q1', question: '1' }, { questionId: 'q2', question: '2' }], answers: [{ questionId: 'q1', answer: 'ok', evaluationStatus: 'FAILED' }] } as any
    expect(firstActionableQuestionIndex(session)).toBe(0)
    session.answers[0].evaluationStatus = 'SUCCEEDED'
    expect(firstActionableQuestionIndex(session)).toBe(1)
    expect(evaluationCanRetry('FAILED')).toBe(true)
    expect(evaluationCanRetry('SUCCEEDED')).toBe(false)
  })

  it('validates submitted evidence links and preserves source locations', () => {
    expect(parseEvidenceLinks('https://example.com/a\nhttps://example.com/a')).toEqual(['https://example.com/a'])
    expect(() => parseEvidenceLinks('javascript:alert(1)')).toThrow()
    expect(citationLocation({ title: '指南', source: 'docs', chunkIndex: 2, startOffset: 10, endOffset: 18 } as any)).toContain('片段 3')
  })

  it('turns answer citations into retrievable evidence cards and labels fallback modes', () => {
    const answer = { query: 'Redis', answer: '...', citations: [{ documentId: 'd1', chunkId: 'd1-c1', title: 'Redis', source: '官方', score: 94, snippet: 'TTL', chunkIndex: 0 }], generatedAt: '2026-10-03T00:00:00Z', retrievalMode: 'HYBRID_RRF_RERANK', evidenceStatus: 'RETRIEVED' } as any
    const retrieval = retrievalFromAnswer(answer)
    expect(retrieval.results[0].citation?.documentId).toBe('d1')
    expect(retrievalModeLabel(answer.retrievalMode)).toContain('重排')
  })

  it('only compares sessions with the same role, job, and rubric version', () => {
    const current = { sessionId: 's1', targetRole: 'Java', jobId: 'j1', report: { rubricVersion: 'r2', comparableSessionIds: ['s2'] } } as any
    const good = { sessionId: 's2', status: 'COMPLETED', targetRole: 'Java', jobId: 'j1', report: { rubricVersion: 'r2' } } as any
    const bad = { sessionId: 's3', status: 'COMPLETED', targetRole: 'Java', jobId: 'j1', report: { rubricVersion: 'r1' } } as any
    expect(comparableInterviewSessions(current, [good, bad])).toEqual([good])
  })
})
