# Production-Style Validation Report

- Generated: 2026-06-12 01:12 +08:00
- Revision deployed: `a383152 feat: persist ai planning and dashboard analytics`
- VM1: `192.168.6.130`
- VM2: `192.168.6.141`
- VM3: `192.168.6.142`
- SSH user: `namettr`

## Deployment

The latest committed revision was deployed to the three Ubuntu VMs through `scripts/deploy-three-vm.ps1` with a temporary deployment env generated from the example env plus the local root `.env`. Secret values were not printed.

The first full deploy run started all containers and produced a passing three-VM health report, then the distributed AI flow failed during the final task polling step with a transient remote connection error. A direct rerun of the distributed AI flow passed, and later validation after restarting `ai-service` also passed.

## Final Smoke Reports

- `reports/deploy/three-vm-smoke-20260612-011315.md`: `PASS=40, FAIL=0, SKIPPED=0`
- `reports/deploy/distributed-ai-flow-20260612-011118.md`: `PASS=5, FAIL=0`

Final distributed AI result:

- Gateway login: passed.
- DashScope status: `configured=true`, provider `dashscope`, model `qwen-plus`.
- Real candidate screening: `mocked=false`, score `82`.
- Delivery event: created `D6c52cf10`.
- RocketMQ delivery-to-screening flow: task `AST-af919598`, status `COMPLETED`, idempotent count `1`.

## Frontend VM E2E

Command:

```powershell
$env:E2E_BASE_URL='http://192.168.6.130/'
npm run test:e2e
```

Result: passed.

Fix applied during validation:

- `frontend/scripts/e2e-smoke.cjs` now trims trailing slashes from `E2E_BASE_URL`.
- This prevents paths such as `http://192.168.6.130//student/plan`, which Vue Router treats as an unmatched route and leaves the main view empty.

Screenshots were regenerated under `frontend/.e2e-artifacts` and manually checked for the student planning page, student delivery page, admin dashboard, and company screening page.

## AI Planning Persistence Restart Check

Validation record:

- Record ID: `AIP-3e18573f-120c-4407-85f9-d260aafe3299`
- Student: `S001`
- Operation: `career-plan`
- Target role: `Java Backend Engineer - persistence restart validation`
- AI result: `mocked=false`, readiness score `78`

Steps:

1. Logged in through VM1 Gateway as `student`.
2. Called `POST /api/ai/career/plan`.
3. Confirmed the record appeared in `GET /api/ai/career/history?studentId=S001&limit=10`.
4. Restarted VM3 `ai-service`.
5. Waited for `http://192.168.6.142:8106/actuator/health`.
6. Queried the same history endpoint again through Gateway and found the same record.
7. Queried VM3 MySQL directly:

```text
AIP-3e18573f-120c-4407-85f9-d260aafe3299    S001    career-plan    0
```

The direct MySQL result confirms the planning record is persisted in `ai_planning_record`; `mocked=0` means the generated planning response used the real AI provider.

## Dashboard Real-Data Check

Gateway query:

- Endpoint: `GET /api/admin/dashboard`
- Auth: real `admin` login token through VM1 Gateway

Returned dashboard values:

```text
studentCount=5
companyCount=2
jobCount=1
deliveryCount=12
pendingDeliveryCount=8
averageMatchScore=87
trendPoints=2
riskAlerts=3
```

VM3 MySQL comparison:

```text
students_union      5
companies_union    2
job_record         1
delivery_record    12
delivery_submitted 8
match_avg          87
```

The Gateway dashboard values match VM3 MySQL, so the admin dashboard is reading real persisted data instead of the frontend fallback dataset.
