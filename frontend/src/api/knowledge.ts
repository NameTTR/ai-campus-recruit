import { authenticatedFileRequest, authenticatedRequest, answerKnowledgeBase, type KnowledgeAnswerResponse } from './client'

export interface KnowledgeTopic {
  id: string; role: string; skill: string; title: string; summary: string; content: string; example?: string
  practicePrompt?: string; practiceType?: string; difficulty: string; estimatedMinutes: number
  prerequisites: string[]; source: string; sourceUrl?: string; applicableVersion?: string; checkedAt?: string
  documentId?: string; version: number; status: string; headings: string[]; createdAt?: string; updatedAt?: string
}
export interface KnowledgePage { pageNumber: number; startOffset: number; endOffset: number }
export interface KnowledgeLocation { chunkId: string; chunkIndex?: number; heading?: string; startOffset?: number; endOffset?: number; pageNumber?: number; snippet: string }
export interface KnowledgeLibraryDocument { documentId: string; title: string; content: string; source: string; category: string; tags: string[]; version: number; status: string; originalAvailable: boolean; pages: KnowledgePage[]; locations: KnowledgeLocation[] }
export interface KnowledgeRecommendation { recommendationId: string; topicId: string; title: string; role: string; skill: string; reason: string; gapType: string; priority: number; estimatedMinutes: number; prerequisites: string[] }
export type KnowledgeStudyStatus = 'TO_LEARN' | 'LEARNING' | 'SELF_MASTERED' | 'TO_REVIEW'
export interface KnowledgeItem { itemId: string; studentId: string; topicId: string; kind: 'BOOKMARK' | 'NOTE' | 'STUDY'; status: KnowledgeStudyStatus; note: string; scheduledAt?: string; lastReviewedAt?: string; nextReviewAt?: string; intervalDays: number[]; revision: number; createdAt?: string; updatedAt?: string }
export interface KnowledgeHistory { historyId: string; studentId: string; query: string; role: string; jobId?: string; matchId?: string; answerSnapshot: KnowledgeAnswerResponse; permissionVersion?: string; createdAt: string }
export interface KnowledgePracticeQuestion { questionId: string; order: number; type: string; prompt: string; referenceChunkIds: string[]; rubric: string[] }
export interface KnowledgePractice { practiceId: string; topicId: string; title: string; questions: KnowledgePracticeQuestion[]; estimatedMinutes: number; sourceLocations: KnowledgeLocation[]; createdAt: string }
export interface KnowledgeEvaluation { status: string; score: number; judgement: string; quote?: string; feedback: string; nextAction: string; referenceChunkIds: string[]; createdAt?: string }
export interface KnowledgeAttempt { attemptId: string; studentId: string; practiceId: string; questionId: string; attemptNo: number; answer: string; status: string; evaluationSnapshot?: KnowledgeEvaluation; inputFingerprint: string; selected: boolean; createdAt: string; evaluatedAt?: string }
export interface KnowledgeActionPreview { previewId: string; studentId: string; type: string; topicId: string; title: string; reason: string; estimatedMinutes: number; impact: string; status: string; payload: Record<string, unknown>; createdAt: string }
export interface KnowledgeActionRequest { type: string; topicId?: string; planId?: string; resumeId?: string; jobId?: string; matchId?: string; targetRole?: string; practiceId?: string; weeklyHours?: number; durationWeeks?: number; dailyMinutesCap?: number; startDate?: string; studyDays?: string[] }

const json = <T>(path: string, init: RequestInit = {}) => authenticatedRequest<T>(path, { ...init, headers: { 'Content-Type': 'application/json', ...(init.headers || {}) } })

export function getKnowledgeRecommendations(params: Record<string, string | undefined> = {}) {
  const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value) as [string, string][])
  return json<KnowledgeRecommendation[]>(`/api/ai/knowledge/recommendations${query.size ? `?${query}` : ''}`)
}
export function getKnowledgeTopics(params: Record<string, string | undefined> = {}) {
  const query = new URLSearchParams(Object.entries(params).filter(([, value]) => value) as [string, string][])
  return json<KnowledgeTopic[]>(`/api/ai/knowledge/topics${query.size ? `?${query}` : ''}`)
}
export function getKnowledgeTopic(id: string) { return json<KnowledgeTopic>(`/api/ai/knowledge/topics/${encodeURIComponent(id)}`) }
export function getKnowledgeLibrary(id: string) { return json<KnowledgeLibraryDocument>(`/api/ai/knowledge/library/${encodeURIComponent(id)}`) }
export function getKnowledgeOriginal(id: string) { return authenticatedFileRequest(`/api/ai/knowledge/library/${encodeURIComponent(id)}/original`) }
export function searchKnowledge(payload: Record<string, unknown>) { return json<KnowledgeAnswerResponse>('/api/ai/knowledge/workspace/search', { method: 'POST', body: JSON.stringify(payload) }) }
export function getKnowledgeItems(params: Record<string, string | undefined> = {}) { const q = new URLSearchParams(Object.entries(params).filter(([, v]) => v) as [string, string][]); return json<KnowledgeItem[]>(`/api/ai/knowledge/me/items${q.size ? `?${q}` : ''}`) }
export function saveKnowledgeItem(payload: { topicId: string; kind: string; status: string; note?: string; intervalDays?: number[]; reviewEnabled?: boolean; expectedRevision?: number }) { return json<KnowledgeItem>('/api/ai/knowledge/me/items', { method: 'POST', body: JSON.stringify(payload) }) }
export function updateKnowledgeItem(id: string, payload: Partial<Parameters<typeof saveKnowledgeItem>[0]>) { return json<KnowledgeItem>(`/api/ai/knowledge/me/items/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(payload) }) }
export function deleteKnowledgeItem(id: string) { return json<void>(`/api/ai/knowledge/me/items/${encodeURIComponent(id)}`, { method: 'DELETE' }) }
export function reviewKnowledgeItem(id: string, passed: boolean) { return json<KnowledgeItem>(`/api/ai/knowledge/me/items/${encodeURIComponent(id)}/review`, { method: 'POST', body: JSON.stringify({ passed }) }) }
export function getKnowledgeHistory(limit = 30) { return json<KnowledgeHistory[]>(`/api/ai/knowledge/me/history?limit=${Math.max(1, Math.min(100, limit))}`) }
export function getKnowledgeHistoryEntry(id: string) { return json<KnowledgeHistory>(`/api/ai/knowledge/me/history/${encodeURIComponent(id)}`) }
export function deleteKnowledgeHistory(id: string) { return json<void>(`/api/ai/knowledge/me/history/${encodeURIComponent(id)}`, { method: 'DELETE' }) }
export function createKnowledgePractice(topicId: string) { return json<KnowledgePractice>('/api/ai/knowledge/practices', { method: 'POST', body: JSON.stringify({ topicId }) }) }
export function getKnowledgePractice(id: string) { return json<KnowledgePractice>(`/api/ai/knowledge/practices/${encodeURIComponent(id)}`) }
export function getKnowledgeAttempts(id: string) { return json<KnowledgeAttempt[]>(`/api/ai/knowledge/practices/${encodeURIComponent(id)}/attempts`) }
export function saveKnowledgeAnswer(id: string, questionId: string, answer: string) { return json<KnowledgeAttempt>(`/api/ai/knowledge/practices/${encodeURIComponent(id)}/answers`, { method: 'POST', body: JSON.stringify({ questionId, answer }) }) }
export function retryKnowledgeAttempt(id: string, attemptId: string) { return json<KnowledgeAttempt>(`/api/ai/knowledge/practices/${encodeURIComponent(id)}/attempts/${encodeURIComponent(attemptId)}/evaluate`, { method: 'POST' }) }
export function previewKnowledgeAction(payload: KnowledgeActionRequest) { return json<KnowledgeActionPreview>('/api/ai/knowledge/actions/preview', { method: 'POST', body: JSON.stringify(payload) }) }
export function confirmKnowledgeAction(previewId: string) { return json<KnowledgeActionPreview>('/api/ai/knowledge/actions/confirm', { method: 'POST', body: JSON.stringify({ previewId }) }) }

export interface KnowledgePublication { documentId: string; title: string; content: string; category: string; source: string; tags: string[]; roles: string[]; version?: number; revision?: number; status?: string; publicationId?: string; createdAt?: string; updatedAt?: string }
export function listKnowledgePublications() { return json<KnowledgePublication[]>('/api/ai/knowledge/publications') }
export function createKnowledgePublication(payload: { title: string; content: string; category: string; source: string; tags: string[]; roles: string[]; expectedRevision?: number }) { return json<KnowledgePublication>('/api/ai/knowledge/publications', { method: 'POST', body: JSON.stringify(payload) }) }
export function updateKnowledgePublication(id: string, payload: Parameters<typeof createKnowledgePublication>[0]) { return json<KnowledgePublication>(`/api/ai/knowledge/publications/${encodeURIComponent(id)}`, { method: 'PUT', body: JSON.stringify(payload) }) }
export function publishKnowledgePublication(id: string) { return json<KnowledgePublication>(`/api/ai/knowledge/publications/${encodeURIComponent(id)}/publish`, { method: 'POST' }) }
export function unpublishKnowledgePublication(id: string) { return json<KnowledgePublication>(`/api/ai/knowledge/publications/${encodeURIComponent(id)}/unpublish`, { method: 'POST' }) }
export function reparseKnowledgePublication(id: string) { return json<KnowledgePublication>(`/api/ai/knowledge/publications/${encodeURIComponent(id)}/reparse`, { method: 'POST' }) }

// Retained as an explicit opt-in fallback for older deployments during rollout.
export { answerKnowledgeBase }
