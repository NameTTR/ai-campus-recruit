# Current Codex Worker Task: v0.3 AI Interview Coach

## Role

You are the implementation Codex worker.
The primary Codex session is the caller, planner, reviewer, release owner, committer, and pusher.
Do not run `git commit` or `git push`.
Do not revert user edits or unrelated changes.

## Goal

Implement v0.3: an AI interview coach loop for the campus recruitment platform.
After a student has matched or delivered a job application, the student can generate mock interview questions and submit an answer for structured AI feedback.

This version should stay small and demo-ready:
- Backend exposes AI interview question and answer feedback APIs.
- Frontend student page has a usable mock interview panel.
- Frontend keeps offline fallback data so the demo works when backend is unavailable.
- Docs and tests are updated.

## Write Scope

Only edit these paths:

- `backend/common/src/main/java/com/aicampus/common/dto/**`
- `backend/ai-service/src/main/java/**`
- `backend/ai-service/src/test/java/**`
- `frontend/src/api/client.ts`
- `frontend/src/api/client.test.ts`
- `frontend/src/views/StudentView.vue`
- `docs/api.md`
- `docs/requirements.md`
- `docs/releases/v0.3.md`

## Backend Requirements

1. Add DTOs in `common`, using Java records:
   - `InterviewQuestionRequest`
     - `studentId`
     - `resumeId`
     - `jobId`
     - `targetRole`
     - `skills`
   - `InterviewQuestion`
     - `questionId`
     - `category`
     - `difficulty`
     - `question`
     - `referencePoints`
   - `InterviewFeedbackRequest`
     - `studentId`
     - `questionId`
     - `question`
     - `answer`
     - `targetRole`
   - `InterviewFeedback`
     - `score`
     - `strengths`
     - `gaps`
     - `suggestions`
     - `summary`
     - `mocked`

2. Add APIs in `ai-service`:
   - `POST /api/ai/interview/questions`
     - returns `ApiResponse<List<InterviewQuestion>>`
   - `POST /api/ai/interview/feedback`
     - returns `ApiResponse<InterviewFeedback>`

3. Extend `DashScopeClient` or add a small service class using the existing pattern:
   - If `dashscope.api-key` is empty, return deterministic mock data.
   - If calling DashScope fails, fall back to deterministic mock data.
   - Never hardcode secrets.
   - Keep public responses wrapped in `ApiResponse<T>`.

4. Add or update ai-service tests:
   - question generation returns multiple questions.
   - feedback returns score, suggestions, and `mocked=true` when no key is configured.
   - existing `/api/ai/analyze` test still passes.

## Frontend Requirements

1. Update `frontend/src/api/client.ts`:
   - Add TypeScript interfaces matching backend DTOs.
   - Add `generateInterviewQuestions(...)`.
   - Add `submitInterviewFeedback(...)`.
   - Add deterministic fallback data.

2. Update `frontend/src/views/StudentView.vue`:
   - Add an "AI mock interview" panel in the current student workflow.
   - User can generate interview questions.
   - User can select a question, type an answer, submit it, and see score/strengths/gaps/suggestions.
   - Keep layout responsive and consistent with existing page style.

3. Update `frontend/src/api/client.test.ts`:
   - Cover fallback question generation.
   - Cover fallback feedback.

## Documentation Requirements

1. Add `docs/releases/v0.3.md`:
   - version goal
   - feature list
   - new APIs
   - acceptance scenarios

2. Update:
   - `docs/api.md`
   - `docs/requirements.md`

## Verification Commands

Run these before reporting done:

```powershell
cd D:\Study\homework\fenbushixitong\exfinal1\backend
mvn -s settings.xml.example test

cd D:\Study\homework\fenbushixitong\exfinal1\frontend
npm run test:unit
npm run build
```

## Final Report

When complete, report in the worker window:

- changed files
- verification commands
- results
- remaining risks

Again: do not run `git commit` or `git push`.
