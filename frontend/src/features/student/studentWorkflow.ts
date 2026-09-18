export const MAX_RESUME_FILE_SIZE = 10 * 1024 * 1024

export interface ResumeFileLike {
  name: string
  size: number
}

export interface JobLike {
  title: string
  companyName: string
  city: string
  requiredSkills: string[]
}

export interface JobFilters {
  keyword: string
  city: string
  skill: string
}

export interface MatchLike {
  resumeId: string
  jobId: string
  resumeSkillsSnapshot?: string[]
  requiredSkillsSnapshot?: string[]
}

export interface ResumeProfileLike {
  education: string
  skills: string[]
  projects: string[]
}

export interface ResumeDraft {
  education: string
  skills: string
  projects: string
}

export interface QuestionLike {
  questionId: string
}

export interface AnswerLike {
  questionId: string
  answer: string
}

export function splitProfileLines(value: string) {
  return value.split(/[\n,;\uFF0C\uFF1B]/).map((item) => item.trim()).filter(Boolean)
}

export function validateResumeFile(file: ResumeFileLike) {
  const extension = file.name.trim().toLowerCase().match(/\.(pdf|doc|docx)$/)?.[1]
  if (!extension) {
    return { valid: false, message: '仅支持 PDF、DOC 和 DOCX 格式的简历文件。' }
  }
  if (file.size <= 0) {
    return { valid: false, message: '简历文件不能为空。' }
  }
  if (file.size > MAX_RESUME_FILE_SIZE) {
    return { valid: false, message: '简历文件不能超过 10 MB。' }
  }
  return { valid: true, extension }
}

export function filterJobs<T extends JobLike>(jobs: T[], filters: JobFilters) {
  const keyword = filters.keyword.trim().toLowerCase()
  const city = filters.city.trim().toLowerCase()
  const skill = filters.skill.trim().toLowerCase()
  return jobs.filter((job) => {
    const matchesKeyword = !keyword || [job.title, job.companyName, job.city, ...job.requiredSkills]
      .some((value) => value.toLowerCase().includes(keyword))
    const matchesCity = !city || job.city.toLowerCase() === city
    const matchesSkill = !skill || job.requiredSkills.some((value) => value.toLowerCase() === skill)
    return matchesKeyword && matchesCity && matchesSkill
  })
}

export function latestMatchForPair<T extends MatchLike>(matches: T[], resumeId?: string, jobId?: string) {
  if (!resumeId || !jobId) {
    return undefined
  }
  return matches.find((match) => match.resumeId === resumeId && match.jobId === jobId)
}

export function matchUsesCurrentSkills(match: MatchLike, resumeSkills: string[], requiredSkills: string[]) {
  const normalize = (skills: string[]) => [...new Set(skills.map((skill) => skill.trim().toLowerCase()).filter(Boolean))].sort().join('\n')
  return Array.isArray(match.resumeSkillsSnapshot) && Array.isArray(match.requiredSkillsSnapshot)
    && normalize(match.resumeSkillsSnapshot) === normalize(resumeSkills)
    && normalize(match.requiredSkillsSnapshot) === normalize(requiredSkills)
}

type DraftStorage = Pick<Storage, 'getItem' | 'setItem' | 'removeItem'>

function studentDraftKey(userId: string, kind: string, recordId: string) {
  return `aicampus.draft.${encodeURIComponent(userId)}.${kind}.${encodeURIComponent(recordId)}`
}

export function readStudentDraft(storage: DraftStorage, userId: string, kind: string, recordId: string): Record<string, string> {
  if (!userId || !recordId) return {}
  try {
    const value: unknown = JSON.parse(storage.getItem(studentDraftKey(userId, kind, recordId)) || '{}')
    if (!value || typeof value !== 'object' || Array.isArray(value)) return {}
    return Object.fromEntries(Object.entries(value).filter((entry): entry is [string, string] => typeof entry[1] === 'string'))
  } catch {
    return {}
  }
}

export function writeStudentDraft(storage: DraftStorage, userId: string, kind: string, recordId: string, draft: Record<string, string>) {
  if (!userId || !recordId) return
  try {
    const key = studentDraftKey(userId, kind, recordId)
    if (Object.keys(draft).length) storage.setItem(key, JSON.stringify(draft))
    else storage.removeItem(key)
  } catch {
    // Storage restrictions must not prevent answering or saving to the server.
  }
}

function normalizedValues(values: string[]) {
  return values.map((value) => value.trim()).filter(Boolean).join('\n')
}

export function resumeProfileIsDirty(resume: ResumeProfileLike | undefined, draft: ResumeDraft) {
  if (!resume) {
    return false
  }
  return resume.education.trim() !== draft.education.trim()
    || normalizedValues(resume.skills) !== normalizedValues(splitProfileLines(draft.skills))
    || normalizedValues(resume.projects) !== normalizedValues(splitProfileLines(draft.projects))
}

export function resumeSummaryFromProfile(draft: ResumeDraft) {
  return [
    draft.education.trim() && `Education: ${draft.education.trim()}`,
    splitProfileLines(draft.skills).length && `Skills: ${splitProfileLines(draft.skills).join(', ')}`,
    splitProfileLines(draft.projects).length && `Projects: ${splitProfileLines(draft.projects).join('; ')}`
  ].filter(Boolean).join('\n')
}

export function firstUnansweredQuestionIndex(questions: QuestionLike[], answers: AnswerLike[]) {
  const answeredQuestionIds = new Set(answers.filter((answer) => answer.answer.trim()).map((answer) => answer.questionId))
  const index = questions.findIndex((question) => !answeredQuestionIds.has(question.questionId))
  return index === -1 ? questions.length : index
}

export function isQuestionLocked(index: number, questions: QuestionLike[], answers: AnswerLike[], status?: string) {
  return status !== 'IN_PROGRESS' || index !== firstUnansweredQuestionIndex(questions, answers)
}

export function mergeAnswerDrafts(existing: Record<string, string>, persistedAnswers: AnswerLike[]) {
  const persisted = Object.fromEntries(persistedAnswers.map((answer) => [answer.questionId, answer.answer]))
  return { ...existing, ...persisted }
}

export function unfinishedQuestionCount(questions: QuestionLike[], answers: AnswerLike[]) {
  return Math.max(0, questions.length - answers.filter((answer) => answer.answer.trim()).length)
}

export function appendRecentQuery(history: string[], query: string, limit = 6) {
  const normalized = query.trim()
  if (!normalized) {
    return history
  }
  return [normalized, ...history.filter((item) => item.toLocaleLowerCase() !== normalized.toLocaleLowerCase())].slice(0, limit)
}

export function planStatusLabel(status?: string) {
  const labels: Record<string, string> = {
    ACTIVE: '进行中',
    COMPLETED: '已完成',
    SUPERSEDED: '已替代'
  }
  return labels[status || ''] || status || '未知状态'
}
