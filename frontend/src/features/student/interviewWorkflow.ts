import type { InterviewAnswerAttempt, InterviewSession } from '../../api/client'

export function interviewAttempts(session: InterviewSession, questionId: string): InterviewAnswerAttempt[] {
  const attempts = (session.attempts || []).filter(item => item.questionId === questionId)
  if (attempts.length) return attempts.sort((left, right) => left.attemptNo - right.attemptNo)
  const answer = session.answers.find(item => item.questionId === questionId)
  return answer ? [{ ...answer, attemptId: `legacy:${questionId}`, attemptNo: 1,
    submittedAt: answer.answeredAt || answer.updatedAt || session.updatedAt, selectedForReport: true }] : []
}

export function interviewElapsedSeconds(session: InterviewSession, now = Date.now()) {
  const timer = session.timer || session
  const elapsed = Math.max(0, timer.accumulatedSeconds || 0)
  if (session.status === 'COMPLETED' || timer.pausedAt) return Math.floor(elapsed)
  const since = Date.parse(timer.runningSince || (session.timer ? '' : session.startedAt) || '')
  return Math.floor(elapsed + (Number.isFinite(since) ? Math.max(0, now - since) / 1000 : 0))
}

export function interviewFeedbackVisible(session: Pick<InterviewSession, 'mode' | 'status'>, page: string) {
  return session.mode !== 'MOCK' || page === 'report' || session.status === 'COMPLETED'
}

export function interviewPracticeQuestions(session: InterviewSession) {
  return session.mode === 'MOCK' ? session.questions.filter(question => !question.followUp) : session.questions
}

export function interviewAttemptFeedback(session: InterviewSession, attempt?: InterviewAnswerAttempt) {
  if (attempt?.evaluation) return attempt.evaluation
  if (!attempt?.selectedForReport || session.status !== 'COMPLETED') return undefined
  const snapshot = session.report?.selectedAttempts?.find(item => item.questionId === attempt.questionId)
  if (snapshot && snapshot.attemptId !== attempt.attemptId) return undefined
  return session.report?.questionFeedback.find(feedback => feedback.questionId === attempt.questionId)
}

export function interviewEvaluationCanRetry(
  session: Pick<InterviewSession, 'mode' | 'status'>,
  attempt?: Pick<InterviewAnswerAttempt, 'evaluationStatus'>
) {
  if (session.mode === 'MOCK' || session.status === 'COMPLETED') return false
  const status = attempt?.evaluationStatus
  return Boolean(status && !['SUCCEEDED', 'RUNNING', 'EVALUATING'].includes(status))
}

export function firstUnansweredInterviewQuestion(session: InterviewSession) {
  const questions = interviewPracticeQuestions(session)
  const index = questions.findIndex(question => !session.answers.some(answer => answer.questionId === question.questionId && answer.answer.trim()))
  return index < 0 ? Math.max(0, questions.length - 1) : index
}

export function interviewDraftQuestionIndex(session: InterviewSession, drafts: Record<string, string>) {
  const questions = interviewPracticeQuestions(session)
  const pending = questions.findIndex(question => Boolean(drafts[question.questionId])
    && (drafts[`retry:${question.questionId}`] || !session.answers.some(answer => answer.questionId === question.questionId)))
  return pending < 0 ? firstUnansweredInterviewQuestion(session) : pending
}

export function interviewReportContinuationIndex(session: InterviewSession, drafts: Record<string, string>, currentIndex: number) {
  return session.status === 'COMPLETED' ? currentIndex : interviewDraftQuestionIndex(session, drafts)
}

export function comparableInterviewResults(current: InterviewSession, all: InterviewSession[]) {
  const report = current.report
  if (!report?.rubricVersion || report.reportType === 'PARTIAL' || current.feedbackViewedAfterPartial) return []
  return all.filter(session => session.sessionId !== current.sessionId && session.status === 'COMPLETED'
    && session.report?.reportType !== 'PARTIAL'
    && !session.feedbackViewedAfterPartial
    && (session.mode || 'COACHING') === (current.mode || 'COACHING')
    && session.targetRole === current.targetRole && (session.jobId || '') === (current.jobId || '')
    && session.report?.rubricVersion === report.rubricVersion
    && (report.comparableSessionIds || []).includes(session.sessionId))
}

export function timeLabel(seconds: number) {
  const value = Math.max(0, Math.floor(seconds))
  return `${Math.floor(value / 60).toString().padStart(2, '0')}:${(value % 60).toString().padStart(2, '0')}`
}

export function interviewResumeLabel(resume: { fileName: string; targetRole?: string; createdAt?: string }) {
  if (!/^workspace-.+\.docx$/i.test(resume.fileName)) return resume.fileName
  const label = resume.targetRole?.trim() || '岗位简历'
  const created = Date.parse(resume.createdAt || '')
  return Number.isFinite(created) ? `${label} · ${new Date(created).toLocaleString('zh-CN', {
    month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit'
  })}` : label
}
