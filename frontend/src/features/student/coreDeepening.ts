import type { AiSearchResponse, InterviewSession, KnowledgeAnswerResponse, KnowledgeCitation, MatchResult } from '../../api/client'

export function matchContextIsCurrent(match: MatchResult, resume: { education: string; skills: string[]; projects: string[] }, job: { title: string; requiredSkills: string[]; description: string; city: string }) {
  const details = match.details
  if (!details) return undefined
  if (details.stale) return false
  const profile = details.profileSnapshot
  const snapshot = details.jobSnapshot
  if (!profile || !snapshot) return false
  const same = (left: string[] | undefined, right: string[] | undefined) => JSON.stringify(left || []) === JSON.stringify(right || [])
  return profile.education === resume.education && same(profile.skills, resume.skills) && same(profile.projects, resume.projects)
    && snapshot.title === job.title && snapshot.description === job.description && snapshot.city === job.city && same(snapshot.requiredSkills, job.requiredSkills)
}

export function firstActionableQuestionIndex(session: Pick<InterviewSession, 'questions' | 'answers'>) {
  const index = session.questions.findIndex((question) => {
    const answer = session.answers.find((item) => item.questionId === question.questionId)
    return !answer?.answer.trim() || Boolean(answer.evaluationStatus && answer.evaluationStatus !== 'SUCCEEDED')
  })
  return index < 0 ? session.questions.length : index
}

export function evaluationCanRetry(status?: string) {
  return Boolean(status && status !== 'SUCCEEDED' && status !== 'RUNNING')
}

export function safeReferenceUrl(value: string): string | undefined {
  try {
    const parsed = new URL(value.trim())
    return parsed.protocol === 'https:' || parsed.protocol === 'http:' ? parsed.href : undefined
  } catch { return undefined }
}

export function parseEvidenceLinks(value: string) {
  const lines = value.split(/[\n,，]/).map((line) => line.trim()).filter(Boolean)
  if (lines.some((line) => !safeReferenceUrl(line))) throw new Error('作品链接需要使用完整的 http 或 https 地址。')
  return [...new Set(lines.map((line) => safeReferenceUrl(line)!))]
}

export function citationLocation(citation: KnowledgeCitation) {
  const parts: string[] = []
  if (citation.heading) parts.push(citation.heading)
  if (citation.chunkIndex != null) parts.push(`片段 ${citation.chunkIndex + 1}`)
  if (citation.startOffset != null && citation.endOffset != null) parts.push(`原文位置 ${citation.startOffset}–${citation.endOffset}（字符，右端不含）`)
  return parts.join(' · ') || '历史记录未保存原文位置'
}

export function retrievalModeLabel(mode?: string) {
  if (!mode || mode === 'LEGACY') return '历史检索模式'
  const labels: Record<string, string> = {
    KEYWORD: '关键词检索', KEYWORD_ONLY: '关键词检索', KEYWORD_FALLBACK: '关键词检索（向量暂不可用）',
    HYBRID_RRF: '关键词 + 语义融合检索', HYBRID_RRF_RERANK: '关键词 + 语义融合与重排',
    HYBRID: '关键词 + 语义检索', RETRIEVAL_ONLY: '仅检索'
  }
  return labels[mode] || mode
}

export function retrievalFromAnswer(answer: KnowledgeAnswerResponse): AiSearchResponse {
  return {
    query: answer.query, generatedAt: answer.generatedAt, retrievalMode: answer.retrievalMode,
    evidenceStatus: answer.evidenceStatus, algorithmVersion: answer.algorithmVersion, permissionVersion: answer.permissionVersion,
    metadata: answer.metadata,
    results: (answer.citations || []).map((citation) => ({ id: citation.chunkId, type: 'knowledge',
      title: citation.title, owner: citation.source, summary: citation.snippet, score: citation.score,
      highlights: citation.heading ? [citation.heading] : [], citation }))
  }
}

export function comparableInterviewSessions(current: InterviewSession, sessions: InterviewSession[]) {
  const report = current.report
  if (!report?.rubricVersion) return []
  return sessions.filter((session) => session.sessionId !== current.sessionId && session.status === 'COMPLETED'
    && session.targetRole === current.targetRole && (session.jobId || '') === (current.jobId || '')
    && session.report?.rubricVersion === report.rubricVersion
    && (report.comparableSessionIds || []).includes(session.sessionId))
}
