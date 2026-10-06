import { describe, expect, it } from 'vitest'
import { knowledgeContext, knowledgeDirection, knowledgeDue, knowledgeHighlightParts, knowledgeIsExplanation, knowledgeNoteStorageKey, knowledgePracticeQuestionIndex, knowledgeReadableFeedback, knowledgeSafeLink, knowledgeSearchExcerpt } from './knowledgeWorkspace'

describe('knowledge workspace', () => {
  it('highlights the exact cited text and keeps all surrounding content', () => {
    const text = '索引能减少扫描。写入也有维护成本。'
    const parts = knowledgeHighlightParts(text, 0, 8, '索引能减少扫描。')
    expect(parts.map(part => part.text).join('')).toBe(text)
    expect(parts.find(part => part.highlighted)?.text).toBe('索引能减少扫描。')
  })
  it('does not highlight invalid historical offsets or inject markup', () => {
    const text = '<script>示例</script>安全文本'
    expect(knowledgeHighlightParts(text, 500, 700, '不存在')).toEqual([{ text, highlighted: false }])
    expect(knowledgeHighlightParts(text, 500, 700, '安全文本').find(part => part.highlighted)?.text).toBe('安全文本')
  })
  it('keeps only authorized context identifiers in navigation', () => {
    expect(knowledgeContext({ q: 'Redis', jobId: 'J1', secret: 'private', planId: ['P1'], ai: '0' })).toEqual({ q: 'Redis', jobId: 'J1', ai: '0' })
  })
  it('separates note drafts by account and document', () => {
    expect(knowledgeNoteStorageKey('s1', 'd1')).not.toBe(knowledgeNoteStorageKey('s2', 'd1'))
  })
  it('recognizes due reminders and rejects unsafe source links', () => {
    expect(knowledgeDue('2026-10-06T10:00:00Z', Date.parse('2026-10-06T11:00:00Z'))).toBe(true)
    expect(knowledgeDue('invalid')).toBe(false)
    expect(knowledgeSafeLink('javascript:alert(1)')).toBe('')
    expect(knowledgeSafeLink('https://docs.oracle.com')).toBe('https://docs.oracle.com/')
  })
  it('maps target roles without treating unknown roles as Java', () => {
    expect(knowledgeDirection('内容运营')).toBe('OPERATIONS')
    expect(knowledgeDirection('Vue 前端')).toBe('FRONTEND')
    expect(knowledgeDirection('Java 后端')).toBe('JAVA')
    expect(knowledgeDirection('设计')).toBe('')
  })
  it('restores a pending reanswer before the next unanswered question', () => {
    const questions = [{ questionId: 'q1' }, { questionId: 'q2' }]
    expect(knowledgePracticeQuestionIndex(questions, [{ questionId: 'q1' }], { q1: '新回答', 'retry:q1': '1' })).toBe(0)
    expect(knowledgePracticeQuestionIndex(questions, [{ questionId: 'q1' }], { q1: '旧残留' })).toBe(1)
  })
  it('recognizes grounded explanation modes when returning to the answer', () => {
    expect(knowledgeIsExplanation('AI_VERIFIED')).toBe(true)
    expect(knowledgeIsExplanation('GROUNDED_EXPLANATION')).toBe(true)
    expect(knowledgeIsExplanation('RETRIEVAL_ONLY')).toBe(false)
  })
  it('shows substantive Markdown excerpts without repeating title lines', () => {
    expect(knowledgeSearchExcerpt('# SQL\n\n## 事务\n\n**原子性**表示全部成功或全部撤销，见[资料](https://example.com)。')).toBe('原子性表示全部成功或全部撤销，见资料。')
    expect(knowledgeSearchExcerpt('# 只有标题')).toBe('')
    expect(knowledgeSearchExcerpt('#')).toBe('')
    expect(knowledgeSearchExcerpt('正常、异常和边界用例都要验证。')).toBe('正常、异常和边界用例都要验证。')
  })
  it('replaces only authorized chunk identifiers in visible feedback', () => {
    expect(knowledgeReadableFeedback('见 KB-TOPIC-01-CH-003，其他 KB-UNKNOWN 保留。', ['KB-TOPIC-01-CH-003'])).toBe('见 引用资料，其他 KB-UNKNOWN 保留。')
    expect(knowledgeReadableFeedback('既有文字不应改变。', [])).toBe('既有文字不应改变。')
  })
})
