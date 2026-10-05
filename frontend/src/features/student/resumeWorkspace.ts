import type {
  LearningPlan,
  ResumeDraft,
  ResumeDraftData,
  ResumeDraftSuggestion,
  ResumeWorkspaceExperience,
  ResumeWorkspaceProfileData,
  ResumeWorkspaceSourceRef
} from '../../api/client'

/** Copy every profile item so editing one field cannot discard unrelated records. */
export function copyResumeProfile(data?: ResumeWorkspaceProfileData): ResumeWorkspaceProfileData {
  const copied = data ? JSON.parse(JSON.stringify(data)) as ResumeWorkspaceProfileData : undefined
  return {
    ...copied,
    basics: { name: '', phone: '', email: '', city: '', portfolioUrl: '', ...copied?.basics },
    education: (copied?.education || []).map(item => ({ ...item, courses: [...(item.courses || [])] })),
    skills: copied?.skills || [],
    experiences: (copied?.experiences || []).map(item => ({ ...item, skills: [...(item.skills || [])], links: [...(item.links || [])] })),
    credentials: copied?.credentials || [],
    availability: { cities: [], earliestStartDate: '', graduationDate: '', ...copied?.availability }
  }
}

export function studentSource(quote = ''): ResumeWorkspaceSourceRef {
  return { kind: 'STUDENT', sourceId: 'workspace', quote, confirmed: false, assessment: '学生填写，等待确认' }
}

export function confirmedResumeProfile(data: ResumeWorkspaceProfileData): ResumeWorkspaceProfileData {
  const copied = copyResumeProfile(data)
  const source = (existing: ResumeWorkspaceSourceRef | undefined, quote: string) => ({
    ...(existing || studentSource(quote)), confirmed: true
  })
  copied.education = copied.education.map(item => ({ ...item, courses: item.courses.map(value => value.trim()).filter(Boolean), source: source(item.source, `${item.school} ${item.major}`.trim()) }))
  copied.skills = copied.skills.map(item => ({ ...item, source: source(item.source, item.name) }))
  copied.experiences = copied.experiences.map(item => ({ ...item, skills: item.skills.map(value => value.trim()).filter(Boolean), links: item.links.map(value => value.trim()).filter(Boolean), confirmed: true, source: source(item.source, item.title) }))
  copied.credentials = copied.credentials.map(item => ({ ...item, source: source(item.source, item.title) }))
  copied.availability.cities = copied.availability.cities.map(value => value.trim()).filter(Boolean)
  return copied
}

/** Imported values remain candidates until the separate save-and-confirm operation. */
export function mergeResumeCandidate(current: ResumeWorkspaceProfileData, candidate: ResumeWorkspaceProfileData): ResumeWorkspaceProfileData {
  const result = copyResumeProfile(current)
  const imported = copyResumeProfile(candidate)
  for (const key of ['name', 'phone', 'email', 'city', 'portfolioUrl', 'photoObjectKey'] as const) {
    if (!result.basics[key] && imported.basics[key]) result.basics[key] = imported.basics[key]
  }
  const mergeItems = <T extends { id: string }>(items: T[], candidates: T[]) => {
    const existing = new Set(items.map(item => item.id))
    return [...items, ...candidates.filter(item => !existing.has(item.id))]
  }
  result.education = mergeItems(result.education, imported.education)
  result.skills = mergeItems(result.skills, imported.skills)
  result.experiences = mergeItems(result.experiences, imported.experiences)
  result.credentials = mergeItems(result.credentials, imported.credentials)
  if (!result.availability.cities.length) result.availability.cities = imported.availability.cities
  if (!result.availability.earliestStartDate) result.availability.earliestStartDate = imported.availability.earliestStartDate
  if (!result.availability.graduationDate) result.availability.graduationDate = imported.availability.graduationDate
  if (result.availability.daysPerWeek == null) result.availability.daysPerWeek = imported.availability.daysPerWeek
  if (result.availability.continuousMonths == null) result.availability.continuousMonths = imported.availability.continuousMonths
  return result
}

/** Keep the final separator while typing so textarea and multi-value inputs remain editable. */
export function editableResumeLines(value: string): string[] { return value.split(/\r?\n/) }
export function editableResumeValues(value: string): string[] { return value.split(/[,\uFF0C\u3001\n]/) }

export function splitResumeLines(value: string): string[] {
  return value.split(/\r?\n/).map(item => item.trim()).filter(Boolean)
}
export function splitResumeValues(value: string): string[] {
  return value.split(/[,，、\n]/).map(item => item.trim()).filter(Boolean)
}
export function moveResumeItem<T>(items: T[], index: number, delta: number): void {
  const target = index + delta
  if (index < 0 || index >= items.length || target < 0 || target >= items.length) return
  const [item] = items.splice(index, 1)
  items.splice(target, 0, item!)
}
export function resumeDraftFingerprint(draft: Pick<ResumeDraft, 'data' | 'templateId'>): string {
  return JSON.stringify({ templateId: draft.templateId, data: draft.data })
}
export function resumeDraftCanAutoPreview(draft: Pick<ResumeDraft, 'data'>): boolean {
  const entries = draft.data.blocks.filter(block => block.visible).flatMap(block => block.entries.filter(entry => entry.visible))
  return entries.length > 0 && entries.every(entry => entry.confirmed)
    && entries.some(entry => entry.title.trim() || entry.bullets.some(line => line.trim()))
}
export function isResumeRevisionConflict(error: unknown): boolean {
  return error instanceof Error && /conflict|revision|版本|冲突|409/i.test(error.message)
}
export function suggestionNeedsProfile(item: ResumeDraftSuggestion): boolean {
  return !item.suggestedText?.trim() || item.suggestedText.trim() === item.originalQuote.trim()
}
export function suggestionMatchesDraft(data: ResumeDraftData, item: ResumeDraftSuggestion): boolean {
  if (item.status === 'APPLIED' || suggestionNeedsProfile(item)) return false
  const entry = data.blocks.find(block => block.id === item.blockId)?.entries.find(entry => entry.id === item.entryId)
  return Boolean(entry && item.originalQuote && entry.bullets.some(text => text === item.originalQuote))
}
export function resumeSourceLabel(source?: ResumeWorkspaceSourceRef): string {
  if (!source) return '学生填写'
  if (/interview/i.test(source.kind)) return '面试回答（学生确认）'
  if (/learn|practice/i.test(source.kind)) return '学习成果（学生选择）'
  if (/import|resume/i.test(source.kind)) return '导入原文'
  if (/student|manual/i.test(source.kind)) return '学生填写'
  return '已保存资料'
}
export interface ResumeLearningCandidate {
  id: string
  title: string
  description: string
  links: string[]
  evaluated: boolean
  evaluation: string
  experience: ResumeWorkspaceExperience
}
export function resumeLearningCandidates(plans: LearningPlan[]): ResumeLearningCandidate[] {
  const seen = new Set<string>()
  return plans.flatMap(plan => plan.tasks.flatMap(task => (task.evidence || []).flatMap(evidence => {
    if (seen.has(evidence.evidenceId) || !evidence.description.trim()) return []
    if (!evidence.confirmed || !evidence.evaluation || evidence.evaluation.mocked
      || evidence.evaluation.score < 70 || !['SUCCEEDED', 'CONFIRMED', 'RESUME_CANDIDATE'].includes(evidence.status)) return []
    seen.add(evidence.evidenceId)
    const evaluation = evidence.evaluation?.conclusion || ''
    return [{
      id: evidence.evidenceId, title: task.title, description: evidence.description,
      links: [...evidence.links], evaluated: Boolean(evidence.evaluation), evaluation,
      experience: {
        id: `learning-${evidence.evidenceId}`, type: 'PROJECT', title: task.title,
        organization: '', startDate: '', endDate: '', role: '', actions: evidence.description,
        methods: '', results: '', skills: [], links: [...evidence.links], confirmed: false,
        source: { kind: 'LEARNING_EVIDENCE', sourceId: evidence.evidenceId,
          quote: evidence.description, confirmed: false, assessment: evaluation || '已提交成果，尚无评价' }
      }
    }]
  })))
}

export function resumeInterviewCandidateRequest(query: Record<string, unknown>) {
  const fields = ['interviewSessionId', 'questionId', 'attemptId'] as const
  const values = fields.map(field => query[field])
  if (!values.every(value => typeof value === 'string' && /^[A-Za-z0-9._:-]{1,200}$/.test(value))) return undefined
  return { sessionId: values[0] as string, questionId: values[1] as string, attemptId: values[2] as string }
}

export function resumeExportStatusLabel(status: string): string {
  return ({ QUEUED: '等待转换', RUNNING: '正在转换', SUCCEEDED: '文件已生成', NEEDS_EDIT: '需要调整排版', FAILED: '转换失败' } as Record<string, string>)[status] || '正在处理'
}
export function resumeWorkspaceMessage(error: unknown, fallback: string): string {
  if (!(error instanceof Error)) return fallback
  if (isResumeRevisionConflict(error)) return '保存版本已变化。你的输入仍保留，请先查看服务器版本，再选择如何保存。'
  const message = error.message
  if (/not found|does not exist/i.test(message)) return '记录不存在或当前账号无权访问，请刷新后重试。'
  if (/confirm.*draft|draft.*confirm/i.test(message)) return '请先确认保存草稿，再生成文件。'
  if (/profile.*required|profile.*confirmed/i.test(message)) return '请填写并确认主资料，再生成简历。'
  if (/quote|suggestion.*stale/i.test(message)) return '建议对应的原文已变化，请重新诊断后再采纳。'
  return /[\u4e00-\u9fff]/.test(message) ? message : fallback
}
