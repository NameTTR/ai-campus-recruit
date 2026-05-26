# 数据库设计

## Core Tables

- `sys_user`：用户基础信息。
- `student_profile`：学生资料、专业、技能、求职意向。
- `company_profile`：企业资料。
- `resume`：简历文件、摘要、诊断状态。
- `job`：岗位 JD、要求、薪资和状态。
- `match_result`：匹配分数、优势、短板、建议。
- `delivery`：投递记录和流程状态。
- `ai_task`：AI 调用任务、输入摘要、输出、状态。

MVP 阶段使用内存仓储保证演示闭环；接入 MySQL 时按以上表结构落库。

