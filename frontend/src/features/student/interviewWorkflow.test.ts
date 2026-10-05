import { describe, expect, it } from 'vitest'
import type { InterviewSession } from '../../api/client'
import { comparableInterviewResults, firstUnansweredInterviewQuestion, interviewAttempts,
  interviewAttemptFeedback, interviewDraftQuestionIndex, interviewElapsedSeconds, interviewEvaluationCanRetry, interviewFeedbackVisible, interviewPracticeQuestions, interviewReportContinuationIndex, timeLabel } from './interviewWorkflow'

const session = (overrides: Partial<InterviewSession> = {}): InterviewSession => ({
  sessionId: 's1', studentId: 'u1', targetRole: 'Java', status: 'IN_PROGRESS',
  questions: [{ questionId: 'q1', question: '项目' }, { questionId: 'q2', question: '方法' }], answers: [],
  createdAt: '2026-10-06T00:00:00Z', updatedAt: '2026-10-06T00:00:00Z', ...overrides
})

describe('interview practice workflow', () => {
  it('preserves selected first answer separately from the latest reattempt', () => {
    const current = session({ attempts: [
      { questionId: 'q1', attemptId: 'a2', attemptNo: 2, answer: '改进版', submittedAt: '', selectedForReport: false },
      { questionId: 'q1', attemptId: 'a1', attemptNo: 1, answer: '原回答', submittedAt: '', selectedForReport: true },
      { questionId: 'q2', attemptId: 'b1', attemptNo: 1, answer: '其他题', submittedAt: '', selectedForReport: true }
    ] })
    const attempts = interviewAttempts(current, 'q1')
    expect(attempts.map(attempt => attempt.attemptId)).toEqual(['a1', 'a2'])
    expect(attempts.find(attempt => attempt.selectedForReport)?.answer).toBe('原回答')
    expect(current.attempts?.[0].attemptId).toBe('a2')
  })

  it('keeps old answers readable as an adopted single attempt', () => {
    const current = session({ answers: [{ questionId: 'q1', answer: '旧回答', evaluationStatus: 'FAILED' }] })
    expect(interviewAttempts(current, 'q1')[0]).toMatchObject({ attemptNo: 1, answer: '旧回答', selectedForReport: true, evaluationStatus: 'FAILED' })
    expect(interviewAttempts(current, 'missing')).toEqual([])
  })

  it('keeps retry available when a failed evaluation still contains fallback feedback', () => {
    const failed = session({ mode: 'COACHING', attempts: [{ questionId: 'q1', attemptId: 'failed-a1', attemptNo: 1,
      answer: '回答已经保存', submittedAt: '', selectedForReport: true, evaluationStatus: 'FAILED',
      evaluation: { questionId: 'q1', score: 60, mocked: true, summary: '规则反馈' } }] })
    const coaching = { mode: 'COACHING' as const, status: 'IN_PROGRESS' }
    expect(interviewAttemptFeedback(failed, failed.attempts![0])?.mocked).toBe(true)
    expect(interviewEvaluationCanRetry(failed, failed.attempts![0])).toBe(true)
    expect(interviewEvaluationCanRetry(coaching, { evaluationStatus: 'PENDING' })).toBe(true)
    expect(interviewEvaluationCanRetry(coaching, { evaluationStatus: 'SUCCEEDED' })).toBe(false)
    expect(interviewEvaluationCanRetry({ ...coaching, status: 'COMPLETED' }, { evaluationStatus: 'FAILED' })).toBe(false)
    expect(interviewEvaluationCanRetry({ mode: 'MOCK', status: 'IN_PROGRESS' }, { evaluationStatus: 'FAILED' })).toBe(false)
  })

  it('moves mock practice forward without requiring hidden evaluation', () => {
    const current = session({ mode: 'MOCK', answers: [{ questionId: 'q1', answer: '已保存', evaluationStatus: 'PENDING' }] })
    expect(firstUnansweredInterviewQuestion(current)).toBe(1)
    expect(interviewFeedbackVisible(current, 'practice')).toBe(false)
    expect(interviewFeedbackVisible(current, 'report')).toBe(true)
    expect(interviewFeedbackVisible({ ...current, status: 'COMPLETED' }, 'practice')).toBe(true)
    expect(interviewFeedbackVisible({ mode: 'COACHING', status: 'IN_PROGRESS' }, 'practice')).toBe(true)
  })

  it('restores a pending reanswer after refresh rather than jumping to another question', () => {
    const current = session({ answers: [{ questionId: 'q1', answer: '已保存' }] })
    expect(interviewDraftQuestionIndex(current, { q1: '未提交的新回答', 'retry:q1': '1' })).toBe(0)
    expect(interviewDraftQuestionIndex(current, { q1: '旧残留回答' })).toBe(1)
    expect(interviewDraftQuestionIndex(current, { q2: '第二题草稿' })).toBe(1)
  })

  it('continues a partial report at the pending draft or first unanswered instead of the saved report question', () => {
    const current = session({ answers: [{ questionId: 'q1', answer: '已保存的回答' }] })
    expect(interviewReportContinuationIndex(current, {}, 0)).toBe(1)
    expect(interviewReportContinuationIndex(current, { q2: '未提交的草稿' }, 0)).toBe(1)
    expect(interviewReportContinuationIndex(current, { q1: '待提交重答', 'retry:q1': '1' }, 1)).toBe(0)
    expect(interviewReportContinuationIndex({ ...current, status: 'COMPLETED' }, {}, 0)).toBe(0)
    expect(interviewReportContinuationIndex({ ...current, status: 'COMPLETED' }, { q1: '旧残留草稿' }, 1)).toBe(1)
  })

  it('uses the server running interval without double counting after resume', () => {
    const current = session({ timer: { startedAt: '2026-10-06T00:00:00Z', runningSince: '2026-10-06T00:03:00Z',
      accumulatedSeconds: 60, timerMinutes: 5, pausedSeconds: 120, timeoutReached: false } })
    expect(interviewElapsedSeconds(current, Date.parse('2026-10-06T00:03:10Z'))).toBe(70)
    current.timer!.pausedAt = '2026-10-06T00:03:10Z'
    current.timer!.accumulatedSeconds = 70
    expect(interviewElapsedSeconds(current, Date.parse('2026-10-06T00:15:00Z'))).toBe(70)
    expect(interviewElapsedSeconds({ ...current, status: 'COMPLETED' }, Date.now())).toBe(70)
    expect(timeLabel(70)).toBe('01:10')
    expect(timeLabel(-10)).toBe('00:00')
  })

  it('reads feedback from a legacy report only for the adopted completed answer', () => {
    const feedback = { questionId: 'q1', score: 75 }
    const current = session({ status: 'COMPLETED', report: { questionFeedback: [feedback] } as InterviewSession['report'],
      answers: [{ questionId: 'q1', answer: '旧回答' }] })
    const adopted = interviewAttempts(current, 'q1')[0]
    expect(interviewAttemptFeedback(current, adopted)).toEqual(feedback)
    expect(interviewAttemptFeedback(current, { ...adopted, attemptId: 'a2', selectedForReport: false })).toBeUndefined()
    expect(interviewAttemptFeedback({ ...current, status: 'IN_PROGRESS' }, adopted)).toBeUndefined()
    current.report!.selectedAttempts = [{ ...adopted, attemptId: 'another' }]
    expect(interviewAttemptFeedback(current, adopted)).toBeUndefined()
  })

  it('excludes mock report follow-ups from practice navigation and restored answer position', () => {
    const current = session({ mode: 'MOCK', status: 'COMPLETED',
      questions: [{ questionId: 'q1', question: '主问题一' }, { questionId: 'f1', mainQuestionId: 'q1', question: '追问', followUp: true },
        { questionId: 'q2', question: '主问题二' }],
      answers: [{ questionId: 'q1', answer: '已回答' }] })
    expect(interviewPracticeQuestions(current).map(question => question.questionId)).toEqual(['q1', 'q2'])
    expect(firstUnansweredInterviewQuestion(current)).toBe(1)
    expect(interviewDraftQuestionIndex(current, { q2: '草稿' })).toBe(1)
    expect(interviewPracticeQuestions({ ...current, mode: 'COACHING' })).toHaveLength(3)
  })

  it('only compares completed reports with matching mode, job, rubric and server-approved IDs', () => {
    const report = { rubricVersion: 'v3', reportType: 'FINAL', comparableSessionIds: ['s2', 's3', 's4', 's5'] } as InterviewSession['report']
    const current = session({ mode: 'COACHING', jobId: 'j1', report })
    const good = session({ sessionId: 's2', status: 'COMPLETED', jobId: 'j1', report })
    const mock = session({ sessionId: 's3', status: 'COMPLETED', mode: 'MOCK', jobId: 'j1', report })
    const differentJob = session({ sessionId: 's4', status: 'COMPLETED', jobId: 'j2', report })
    const partial = session({ sessionId: 's5', status: 'COMPLETED', jobId: 'j1', report: { ...report, reportType: 'PARTIAL' } as InterviewSession['report'] })
    expect(comparableInterviewResults(current, [good, mock, differentJob, partial])).toEqual([good])
    expect(comparableInterviewResults(partial, [good])).toEqual([])
  })
})
