# 当前开发 Codex 任务：v0.2 投递审核与状态看板

## 角色

你是开发 Codex worker。主控 Codex 负责规划、验收、提交和推送；你负责实现本任务。

不要 revert 用户或主控 Codex 的无关改动。修改前先看现有代码模式。

## 目标

实现 v0.2：补齐学生、企业、学校三端的投递审核闭环。

学生投递岗位后：

- 企业端可以查看投递列表。
- 企业端可以推进投递状态。
- 学生端能看到中文状态。
- 学校端能看到状态分布和待处理数量。

## 写入范围

- `backend/common/src/main/java/com/aicampus/common/dto/**`
- `backend/delivery-service/src/main/java/**`
- `backend/delivery-service/src/test/java/**`
- `backend/user-service/src/main/java/**`
- `backend/user-service/src/test/java/**`
- `frontend/src/api/client.ts`
- `frontend/src/views/StudentView.vue`
- `frontend/src/views/CompanyView.vue`
- `frontend/src/views/AdminView.vue`
- `frontend/src/api/client.test.ts`
- `docs/api.md`
- `docs/requirements.md`
- `docs/releases/v0.2.md`

## 后端要求

1. `delivery-service` 增加企业查看投递接口：
   - `GET /api/deliveries/company?companyId=C001`
   - 返回投递记录列表。

2. `delivery-service` 增加投递状态统计接口：
   - `GET /api/deliveries/statistics`
   - 返回总数、各状态数量、待处理数。
   - 可在 common 新增 DTO，例如 `DeliveryStatistics`。

3. `PUT /api/deliveries/{id}/status?status=INTERVIEW` 继续可用。

4. `user-service` 的 `/api/admin/dashboard` 增强返回待处理投递数等看板字段。

## 前端要求

1. `CompanyView.vue` 增加“投递审核”区域：
   - 展示企业投递列表。
   - 支持状态更新为 `VIEWED` / `INTERVIEW` / `OFFER` / `REJECTED`。

2. `StudentView.vue` 投递记录状态显示中文：
   - `SUBMITTED`：已投递
   - `VIEWED`：已查看
   - `INTERVIEW`：面试中
   - `OFFER`：已录用
   - `REJECTED`：未通过

3. `AdminView.vue` 增加投递状态分布和待处理数展示。

4. `client.ts` 必须有 fallback，后端不启动时页面仍可演示。

## 文档要求

1. 新增 `docs/releases/v0.2.md`：
   - 版本目标
   - 功能清单
   - 新增接口
   - 验收场景

2. 更新：
   - `docs/api.md`
   - `docs/requirements.md`

## 测试要求

后端：

- `delivery-service` 测试覆盖：
  - 企业投递列表
  - 投递统计
  - 状态更新

- `user-service` 测试适配 dashboard 新字段。

前端：

- `client.test.ts` 覆盖企业投递列表或投递统计 fallback。

## 验证命令

```powershell
cd D:\Study\homework\fenbushixitong\exfinal1\backend
mvn -s settings.xml.example test

cd D:\Study\homework\fenbushixitong\exfinal1\frontend
npm run test:unit
npm run build
```

## 最终汇报

完成后在窗口里汇报：

- 修改文件
- 测试命令
- 测试结果
- 遗留风险

不要执行 `git commit` 或 `git push`，由主控 Codex 完成。

