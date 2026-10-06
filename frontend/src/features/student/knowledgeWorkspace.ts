export type KnowledgeStudyState = 'TO_LEARN' | 'LEARNING' | 'SELF_MASTERED' | 'TO_REVIEW'

export function knowledgeStateLabel(state?: string) {
  const labels: Record<string, string> = { TO_LEARN: '待学习', LEARNING: '学习中', SELF_MASTERED: '自报掌握', TO_REVIEW: '待复习' }
  return labels[state || 'TO_LEARN'] || '待学习'
}

export function knowledgeDateLabel(value?: string | null) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', { month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' }).format(date)
}

export function knowledgeDirection(value?: string) {
  if (/运营|内容|社群|用户|活动/.test(value || '')) return 'OPERATIONS'
  if (/前端|frontend|vue|react|javascript|typescript/i.test(value || '')) return 'FRONTEND'
  if (/java|后端|backend|spring/i.test(value || '')) return 'JAVA'
  return ''
}

export function knowledgeExcerpt(value?: string, maxLength = 150) {
  const text = (value || '').replace(/\s+/g, ' ').trim()
  return text.length > maxLength ? `${text.slice(0, maxLength)}…` : text
}

export function knowledgeSearchExcerpt(value?: string, maxLength = 180) {
  let inHeading = false
  const parts: string[] = []
  for (const token of excerptMarkdown.parse(value || '', {})) {
    if (token.type === 'heading_open') { inHeading = true; continue }
    if (token.type === 'heading_close') { inHeading = false; continue }
    if (inHeading) continue
    if (token.type === 'inline') {
      parts.push((token.children || []).map(child => ['text', 'code_inline'].includes(child.type) ? child.content : ['softbreak', 'hardbreak'].includes(child.type) ? ' ' : '').join(''))
    } else if (['fence', 'code_block'].includes(token.type)) parts.push(token.content)
  }
  const text = knowledgeExcerpt(parts.join(' '), maxLength)
  return /^[#*_`>\s-]*$/.test(text) ? '' : text
}

export function knowledgeReadableFeedback(value: string | undefined, authorizedChunkIds: string[]) {
  let text = value || ''
  for (const id of [...new Set(authorizedChunkIds)].filter(Boolean).sort((a, b) => b.length - a.length)) text = text.split(id).join('引用资料')
  return text
}

export function knowledgePosition(value: { pageNumber?: number | null; heading?: string; startOffset?: number | null; endOffset?: number | null; chunkIndex?: number | null }) {
  if (value.pageNumber && value.pageNumber > 0) return `第 ${value.pageNumber} 页`
  if (value.heading) return value.heading
  if (typeof value.startOffset === 'number' && typeof value.endOffset === 'number') return `正文 ${value.startOffset + 1}～${value.endOffset} 字`
  return typeof value.chunkIndex === 'number' ? `第 ${value.chunkIndex + 1} 段` : '正文'
}

export interface KnowledgeTextPart { text: string; highlighted: boolean }

// Offsets are validated against the quote before highlighting historical references.
export function knowledgeHighlightParts(text: string, start?: number | null, end?: number | null, quote?: string): KnowledgeTextPart[] {
  const valid = typeof start === 'number' && typeof end === 'number' && start >= 0 && end > start && end <= text.length
    && (!quote || text.slice(start, end) === quote)
  const found = quote ? text.indexOf(quote) : -1
  const from = valid ? start : found
  const to = valid ? end : found >= 0 && quote ? found + quote.length : -1
  if (from < 0 || to <= from) return [{ text, highlighted: false }]
  return [
    { text: text.slice(0, from), highlighted: false },
    { text: text.slice(from, to), highlighted: true },
    { text: text.slice(to), highlighted: false }
  ].filter(part => part.text.length > 0)
}

export function knowledgeSafeLink(value?: string) {
  if (!value) return ''
  try {
    const parsed = new URL(value)
    return ['https:', 'http:'].includes(parsed.protocol) ? parsed.href : ''
  } catch { return '' }
}

export function knowledgeDue(value?: string | null, now = Date.now()) {
  if (!value) return false
  const date = Date.parse(value)
  return Number.isFinite(date) && date <= now
}

export function knowledgeNoteStorageKey(userId: string, documentId: string) {
  return `aicampus.knowledge-note.${userId}.${documentId}`
}

export function knowledgePracticeStorageKey(userId: string, practiceId: string) {
  return `aicampus.knowledge-practice.${userId}.${practiceId}`
}

export function knowledgePracticeQuestionIndex(questions: { questionId: string }[], attempts: { questionId: string }[], drafts: Record<string, string>) {
  const reanswer = questions.findIndex(question => drafts[`retry:${question.questionId}`] === '1')
  if (reanswer >= 0) return reanswer
  const next = questions.findIndex(question => !attempts.some(attempt => attempt.questionId === question.questionId))
  return Math.max(0, next)
}

export function knowledgeContext(query: Record<string, unknown>) {
  const result: Record<string, string> = {}
  for (const key of ['q', 'ai', 'jobId', 'resumeId', 'matchId', 'planId', 'sessionId', 'targetRole', 'historyId', 'direction', 'skill', 'difficulty', 'contentType', 'topicId', 'practiceId', 'documentId', 'source', 'recommendationId']) {
    if (typeof query[key] === 'string' && query[key]) result[key] = String(query[key])
  }
  return result
}

export function knowledgeErrorMessage(cause: unknown) {
  const message = cause instanceof Error ? cause.message : '暂时无法读取，请重试'
  if (/version|conflict|版本|冲突|已被修改/i.test(message)) return '笔记已在其他页面更新。当前输入已保留，请读取最新版本后合并。'
  return message
}

export function knowledgeIsExplanation(mode?: string) {
  return ['AI', 'AI_VERIFIED', 'GROUNDED_EXPLANATION'].includes(mode || '')
}
import MarkdownIt from 'markdown-it'

const excerptMarkdown = new MarkdownIt({ html: false, linkify: false })
