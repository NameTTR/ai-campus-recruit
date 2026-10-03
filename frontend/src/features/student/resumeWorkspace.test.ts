import { describe, expect, it } from 'vitest'
import type { LearningPlan, ResumeDraftData, ResumeWorkspaceProfileData } from '../../api/client'
import { confirmedResumeProfile, copyResumeProfile, isResumeRevisionConflict, mergeResumeCandidate, moveResumeItem, resumeLearningCandidates, suggestionMatchesDraft, suggestionNeedsProfile, editableResumeLines, editableResumeValues } from './resumeWorkspace'

function profile(): ResumeWorkspaceProfileData {
  return {
    basics: { name: '李同学', phone: '', email: '', city: '杭州', portfolioUrl: 'https://example.test', photoObjectKey: 'owned-photo' },
    education: [1, 2].map(i => ({ id: `edu-${i}`, school: `大学${i}`, major: '计算机', degree: '本科', startDate: '2022-09', endDate: '2026-06', graduationDate: '2026-06-30', courses: ['课程项目'], notes: '真实备注' })),
    skills: [{ id: 'skill-1', name: 'Java', source: { kind: 'IMPORT', sourceId: 'R1', quote: 'Java', confirmed: false } }],
    experiences: [1, 2].map(i => ({ id: `exp-${i}`, type: i === 1 ? 'PROJECT' : 'CAMPUS', title: `经历${i}`, organization: '校园', role: '成员', startDate: '', endDate: '', actions: '实际行动', methods: '方法', results: '定性结果', skills: ['Java'], links: ['https://example.test/work'], confirmed: false })),
    credentials: [{ id: 'credential-1', title: '竞赛', date: '2025-05', description: '参与' }],
    availability: { cities: ['杭州', '上海'], earliestStartDate: '2026-07-01', daysPerWeek: 4, continuousMonths: 3, graduationDate: '2026-06-30' }
  }
}
describe('resume workspace data preservation', () => {
  it('preserves trailing separators and newlines while the student types', () => {
    expect(editableResumeLines('第一项\n')).toEqual(['第一项', ''])
    expect(editableResumeValues('Java，')).toEqual(['Java', ''])
  })
  it('editing and confirming preserves every education, experience, credential, photo and availability', () => {
    const original = profile()
    const editing = copyResumeProfile(original)
    editing.basics.name = '李学生'
    const confirmed = confirmedResumeProfile(editing)
    expect(confirmed.education).toHaveLength(2)
    expect(confirmed.experiences).toHaveLength(2)
    expect(confirmed.credentials).toEqual(original.credentials.map(item => ({ ...item, source: expect.objectContaining({ confirmed: true }) })))
    expect(confirmed.basics.photoObjectKey).toBe('owned-photo')
    expect(confirmed.availability).toEqual(original.availability)
    expect(confirmed.skills[0]?.source).toEqual({ ...original.skills[0]?.source, confirmed: true })
    expect(original.basics.name).toBe('李同学')
    expect(original.experiences[0]?.confirmed).toBe(false)
  })
  it('merges candidate facts without replacing current edits, dropping records or confirming imports', () => {
    const current = profile()
    const candidate = copyResumeProfile(current)
    candidate.basics.name = '导入姓名'
    candidate.basics.email = 'student@example.test'
    candidate.experiences.push({ ...candidate.experiences[0]!, id: 'import-exp', title: '新项目' })
    const merged = mergeResumeCandidate(current, candidate)
    expect(merged.basics.name).toBe('李同学')
    expect(merged.basics.email).toBe('student@example.test')
    expect(merged.experiences).toHaveLength(3)
    expect(merged.experiences[2]?.confirmed).toBe(false)
    expect(mergeResumeCandidate(merged, candidate).experiences).toHaveLength(3)
  })
  it('moves whole entries without losing facts and ignores invalid boundaries', () => {
    const entries = profile().experiences
    moveResumeItem(entries, 1, -1)
    expect(entries.map(item => item.id)).toEqual(['exp-2', 'exp-1'])
    expect(entries[1]?.skills).toEqual(['Java'])
    moveResumeItem(entries, 0, -1)
    expect(entries.map(item => item.id)).toEqual(['exp-2', 'exp-1'])
  })
  it('recognizes version conflicts while the local data remains unchanged', () => {
    const editing = profile()
    const before = JSON.stringify(editing)
    expect(isResumeRevisionConflict(new Error('Draft revision conflict'))).toBe(true)
    expect(isResumeRevisionConflict(new Error('版本冲突'))).toBe(true)
    expect(JSON.stringify(editing)).toBe(before)
  })
})
describe('resume source and suggestion checks', () => {
  it('requires the current exact bullet and forbids applying an already applied suggestion', () => {
    const data: ResumeDraftData = { blocks: [{ id: 'b', type: 'PROJECT', title: '项目', visible: true, entries: [{ id: 'e', title: '课程项目', subtitle: '', bullets: ['实现了真实功能'], links: [], factIds: ['fact'], visible: true, confirmed: true }] }], questions: [], suggestions: [], warnings: [], generationSource: 'RULE_FALLBACK' }
    const suggestion = { id: 's', blockId: 'b', entryId: 'e', originalQuote: '实现了真实功能', suggestedText: '清晰表达', problem: '', basis: '', factIds: ['fact'], status: 'PENDING' }
    expect(suggestionMatchesDraft(data, suggestion)).toBe(true)
    expect(suggestionNeedsProfile({ ...suggestion, suggestedText: suggestion.originalQuote })).toBe(true)
    expect(suggestionMatchesDraft(data, { ...suggestion, suggestedText: suggestion.originalQuote })).toBe(false)
    expect(suggestionNeedsProfile({ ...suggestion, suggestedText: '' })).toBe(true)
    expect(suggestionMatchesDraft(data, { ...suggestion, status: 'APPLIED' })).toBe(false)
    expect(suggestionMatchesDraft(data, { ...suggestion, originalQuote: '真实功能' })).toBe(false)
    expect(suggestionMatchesDraft(data, { ...suggestion, originalQuote: '课程项目' })).toBe(false)
    data.blocks[0]!.entries[0]!.bullets = ['学生已经修改']
    expect(suggestionMatchesDraft(data, suggestion)).toBe(false)
  })
  it('uses submitted learning evidence only and never infers skills from task completion', () => {
    const plans: LearningPlan[] = [{
      planId: 'plan', studentId: 'student', targetRole: 'Java', weeklyHours: 6,
      durationWeeks: 2, status: 'ACTIVE', version: 1, createdAt: '', updatedAt: '',
      tasks: [
        { taskId: 'completed', title: 'Redis', week: 1, description: '', estimatedHours: 3,
          status: 'COMPLETED', updatedAt: '', evidence: [] },
        { taskId: 'evidence', title: '缓存练习', week: 2, description: '', estimatedHours: 3,
          status: 'COMPLETED', updatedAt: '', evidence: [{
            evidenceId: 'e1', planId: 'plan', taskId: 'evidence', studentId: 'student',
            description: '提交测试说明', links: ['https://example.test/tests'], status: 'EVALUATED', submittedAt: '',
            evaluation: { score: 70, conclusion: '测试材料已提供', strengths: [], gaps: [], suggestions: [], evidence: [], mocked: true }
          }] }
      ]
    }]
    const candidates = resumeLearningCandidates(plans)
    expect(candidates).toHaveLength(1)
    expect(candidates[0]?.experience.skills).toEqual([])
    expect(candidates[0]?.experience.confirmed).toBe(false)
    expect(candidates[0]?.experience.source?.assessment).toBe('测试材料已提供')
    expect(candidates[0]?.experience.source?.sourceId).toBe('e1')
  })
})
