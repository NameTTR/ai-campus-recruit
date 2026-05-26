# API 文档

所有接口返回：

```json
{
  "code": 0,
  "message": "ok",
  "data": {}
}
```

## Auth

- `POST /api/auth/login`：登录。
- `POST /api/auth/logout`：登出。
- `GET /api/auth/me`：获取当前用户。

## Resume

- `POST /api/resumes/upload`：上传简历。
- `GET /api/resumes/{id}`：查看简历摘要。
- `POST /api/resumes/{id}/analyze`：触发 AI 简历诊断。

## Job

- `POST /api/jobs`：发布岗位。
- `GET /api/jobs`：岗位列表。
- `GET /api/jobs/{id}`：岗位详情。
- `POST /api/jobs/{id}/analyze`：触发 AI 岗位分析。

## Match

- `POST /api/matches/resume-job`：生成简历和岗位匹配结果。
- `GET /api/matches/student/{studentId}`：学生匹配结果。
- `GET /api/matches/job/{jobId}`：岗位候选人匹配结果。

## Delivery

- `POST /api/deliveries`：投递岗位。
  - 请求体：`studentId`、`resumeId`、`jobId`。
  - 返回：`DeliveryRecord`，包含 `deliveryId`、`studentId`、`resumeId`、`jobId`、`companyId`、`status`、`createdAt`。
- `GET /api/deliveries/my`：我的投递。
- `GET /api/deliveries/company?companyId=C001`：企业查看本企业投递列表。
- `GET /api/deliveries/statistics`：投递状态统计。
  - 返回：`totalCount`、`statusCounts`、`pendingCount`。
- `PUT /api/deliveries/{id}/status?status=INTERVIEW`：更新投递状态。
  - 支持状态：`SUBMITTED`、`VIEWED`、`INTERVIEW`、`OFFER`、`REJECTED`。

## Admin

- `GET /api/admin/dashboard`：学校端统计看板。
  - 返回：`studentCount`、`companyCount`、`jobCount`、`deliveryCount`、`averageMatchScore`、`deliveryStatusCounts`、`pendingDeliveryCount`。
