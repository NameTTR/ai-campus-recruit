import type { AiSearchResponse, InterviewSession, KnowledgeAnswerResponse, KnowledgeCitation, MatchResult } from '../../api/client'

/**
 * The evidence context is shared by the resume, match, learning, interview and
 * knowledge modules.  Newer API responses expose `contextStatus` directly;
 * older snapshots only expose `stale` or source availability flags.  Keeping
 * the fallback here lets the student UI explain an old record without treating
 * missing metadata as a current analysis.
 */
export type EvidenceContextStatus = 'CURRENT' | 'STALE' | 'INCOMPLETE' | 'SOURCE_UNAVAILABLE'

type ContextCarrier = {
  contextStatus?: unknown
  analysisStatus?: unknown
  stale?: unknown
  sourceAvailable?: unknown
  sourceUnavailable?: unknown
  contextSnapshot?: { contextStatus?: unknown; sourceAvailable?: unknown; sourceUnavailable?: unknown; evidenceContext?: { status?: unknown; [key: string]: unknown }; [key: string]: unknown }
  evidenceContext?: { status?: unknown; sourceAvailable?: unknown; sourceUnavailable?: unknown; [key: string]: unknown }
  metadata?: { contextStatus?: unknown; sourceAvailable?: unknown; sourceUnavailable?: unknown; evidenceContext?: { status?: unknown; [key: string]: unknown }; [key: string]: unknown }
  analysisMetadata?: { contextStatus?: unknown; sourceAvailable?: unknown; sourceUnavailable?: unknown; evidenceContext?: { status?: unknown; [key: string]: unknown }; [key: string]: unknown }
}

const contextStatuses = new Set<EvidenceContextStatus>(['CURRENT', 'STALE', 'INCOMPLETE', 'SOURCE_UNAVAILABLE'])

function asContextStatus(value: unknown): EvidenceContextStatus | undefined {
  return typeof value === 'string' && contextStatuses.has(value as EvidenceContextStatus) ? value as EvidenceContextStatus : undefined
}

/** Resolve the explicit context state while remaining compatible with legacy snapshots. */
export function evidenceContextStatus(value: unknown): EvidenceContextStatus | undefined {
  if (!value || typeof value !== 'object') return undefined
  const carrier = value as ContextCarrier
  const metadataList = [carrier.metadata, carrier.analysisMetadata].filter(Boolean)
  const snapshot = carrier.contextSnapshot
  const metadataUnavailable = metadataList.some(item => item?.sourceUnavailable === true || item?.sourceAvailable === false
    || item?.evidenceContext?.sourceUnavailable === true || item?.evidenceContext?.sourceAvailable === false)
  const sourceUnavailable = carrier.sourceUnavailable === true || carrier.sourceAvailable === false || metadataUnavailable
    || snapshot?.sourceUnavailable === true || snapshot?.sourceAvailable === false
    || carrier.evidenceContext?.sourceUnavailable === true || carrier.evidenceContext?.sourceAvailable === false
    || snapshot?.evidenceContext?.sourceUnavailable === true || snapshot?.evidenceContext?.sourceAvailable === false
  if (sourceUnavailable) return 'SOURCE_UNAVAILABLE'
  const statuses = [
    asContextStatus(carrier.contextStatus), asContextStatus(carrier.analysisStatus),
    asContextStatus(carrier.evidenceContext?.status), asContextStatus(snapshot?.contextStatus),
    asContextStatus(snapshot?.evidenceContext?.status),
    ...metadataList.flatMap(item => [asContextStatus(item?.contextStatus), asContextStatus(item?.evidenceContext?.status)])
  ].filter((item): item is EvidenceContextStatus => Boolean(item))
  if (statuses.includes('SOURCE_UNAVAILABLE')) return 'SOURCE_UNAVAILABLE'
  if (carrier.stale === true || statuses.includes('STALE')) return 'STALE'
  if (statuses.includes('INCOMPLETE')) return 'INCOMPLETE'
  if (statuses.includes('CURRENT')) return 'CURRENT'
  // A generic context can legitimately omit resume/job versions. Only mark it
  // incomplete when the server sent a context object without a status.
  if (carrier.evidenceContext || metadataList.some(item => item?.evidenceContext) || snapshot?.evidenceContext) return 'INCOMPLETE'
  return undefined
}

export function evidenceContextStatusLabel(status?: EvidenceContextStatus): string {
  return ({
    CURRENT: '资料版本一致',
    STALE: '资料已变化，请重新分析',
    INCOMPLETE: '资料不完整，无法完整判断',
    SOURCE_UNAVAILABLE: '来源不可用，请重新查询'
  } as Record<EvidenceContextStatus, string>)[status || 'INCOMPLETE'] || '状态未记录'
}

export function evidenceContextStatusType(status?: EvidenceContextStatus): 'success' | 'warning' | 'info' | 'danger' {
  return status === 'CURRENT' ? 'success' : status === 'STALE' ? 'warning' : status === 'SOURCE_UNAVAILABLE' ? 'danger' : 'info'
}

export function evidenceContextStatusHint(status?: EvidenceContextStatus): string {
  return ({
    CURRENT: '结果使用当前资料版本；引用可定位仍需核对事实。',
    STALE: '资料、岗位或匹配条件发生变化；历史结果仍可查看，但请重新分析后再作决定。',
    INCOMPLETE: '部分来源或版本信息缺失，下面内容仅供核对，不能当作完整判断。',
    SOURCE_UNAVAILABLE: '原始资料已下架、撤权或暂时不可用；请重新查询后再继续。'
  } as Record<EvidenceContextStatus, string>)[status || 'INCOMPLETE'] || '当前记录没有可用的上下文状态。'
}

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
