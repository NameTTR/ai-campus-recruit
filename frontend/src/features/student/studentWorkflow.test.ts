import { describe, expect, it } from 'vitest'
import {
  MAX_RESUME_FILE_SIZE,
  appendRecentQuery,
  filterJobs,
  firstUnansweredQuestionIndex,
  isQuestionLocked,
  latestMatchForPair,
  matchUsesCurrentSkills,
  mergeAnswerDrafts,
  readStudentDraft,
  resumeProfileIsDirty,
  unfinishedQuestionCount,
  validateResumeFile,
  writeStudentDraft
} from './studentWorkflow'

describe('student workflow helpers', () => {
  it('validates the client resume file contract', () => {
    expect(validateResumeFile({ name: 'candidate.docx', size: MAX_RESUME_FILE_SIZE }).valid).toBe(true)
    expect(validateResumeFile({ name: 'candidate.txt', size: 10 }).valid).toBe(false)
    expect(validateResumeFile({ name: 'candidate.pdf', size: 0 }).valid).toBe(false)
    expect(validateResumeFile({ name: 'candidate.pdf', size: MAX_RESUME_FILE_SIZE + 1 }).valid).toBe(false)
  })

  it('filters jobs by keyword, city, and required skill', () => {
    const jobs = [
      { title: 'Java Engineer', companyName: 'A', city: 'Shanghai', requiredSkills: ['Java', 'Redis'] },
      { title: 'Frontend Engineer', companyName: 'B', city: 'Beijing', requiredSkills: ['Vue'] }
    ]
    expect(filterJobs(jobs, { keyword: 'engineer', city: 'Shanghai', skill: 'Redis' })).toEqual([jobs[0]])
    expect(filterJobs(jobs, { keyword: '', city: '', skill: 'Vue' })).toEqual([jobs[1]])
  })

  it('restores the newest matching record from ordered history', () => {
    const newest = { resumeId: 'r1', jobId: 'j1', id: 'new' }
    expect(latestMatchForPair([newest, { resumeId: 'r1', jobId: 'j1', id: 'old' }], 'r1', 'j1')).toBe(newest)
  })

  it('detects unsaved structured resume fields', () => {
    const resume = { education: 'CS', skills: ['Java'], projects: ['API'] }
    expect(resumeProfileIsDirty(resume, { education: 'CS', skills: 'Java', projects: 'API' })).toBe(false)
    expect(resumeProfileIsDirty(resume, { education: 'CS', skills: 'Java, Redis', projects: 'API' })).toBe(true)
  })

  it('rejects stale skill evidence while tolerating order and case changes', () => {
    const match = { resumeId: 'r1', jobId: 'j1', resumeSkillsSnapshot: ['Java', 'Redis'], requiredSkillsSnapshot: ['SQL'] }
    expect(matchUsesCurrentSkills(match, ['redis', 'JAVA'], ['SQL'])).toBe(true)
    expect(matchUsesCurrentSkills(match, ['Java'], ['SQL'])).toBe(false)
    expect(matchUsesCurrentSkills(match, ['Java', 'Redis'], ['SQL', 'Vue'])).toBe(false)
    expect(matchUsesCurrentSkills({ resumeId: 'r1', jobId: 'j1' }, [], [])).toBe(false)
  })

  it('restores drafts only for the same user, module, and record', () => {
    const values = new Map<string, string>()
    const storage = {
      getItem: (key: string) => values.get(key) ?? null,
      setItem: (key: string, value: string) => { values.set(key, value) },
      removeItem: (key: string) => { values.delete(key) }
    }
    writeStudentDraft(storage, 'u1', 'interview', 's1', { q1: 'unfinished answer' })
    expect(readStudentDraft(storage, 'u1', 'interview', 's1')).toEqual({ q1: 'unfinished answer' })
    expect(readStudentDraft(storage, 'u2', 'interview', 's1')).toEqual({})
    expect(readStudentDraft(storage, 'u1', 'interview', 's2')).toEqual({})
    expect(readStudentDraft(storage, 'u1', 'resume', 's1')).toEqual({})
    writeStudentDraft(storage, 'u1', 'interview', 's1', {})
    expect(readStudentDraft(storage, 'u1', 'interview', 's1')).toEqual({})
  })

  it('tolerates malformed or unavailable browser storage', () => {
    const storage = { getItem: () => '{broken', setItem: () => { throw new Error('denied') }, removeItem: () => {} }
    expect(readStudentDraft(storage, 'u1', 'interview', 's1')).toEqual({})
    expect(() => writeStudentDraft(storage, 'u1', 'interview', 's1', { q1: 'draft' })).not.toThrow()
  })

  it('enforces sequential interview answers and preserves another draft', () => {
    const questions = [{ questionId: 'q1' }, { questionId: 'q2' }]
    const answers = [{ questionId: 'q1', answer: 'saved' }]
    expect(firstUnansweredQuestionIndex(questions, answers)).toBe(1)
    expect(isQuestionLocked(0, questions, answers, 'IN_PROGRESS')).toBe(true)
    expect(isQuestionLocked(1, questions, answers, 'IN_PROGRESS')).toBe(false)
    expect(isQuestionLocked(1, questions, answers, 'ACTIVE')).toBe(true)
    expect(isQuestionLocked(1, questions, answers, 'COMPLETED')).toBe(true)
    expect(mergeAnswerDrafts({ q1: 'stale', q2: 'draft' }, answers)).toEqual({ q1: 'saved', q2: 'draft' })
    expect(unfinishedQuestionCount(questions, answers)).toBe(1)
  })

  it('keeps bounded, de-duplicated query history', () => {
    expect(appendRecentQuery(['Redis', 'Java'], 'redis', 2)).toEqual(['redis', 'Java'])
  })
})
