# 数据库设计

## Core Tables

- `sys_user`：用户基础信息。
- `student_profile`：学生资料、专业、技能、求职意向。
- `company_profile`：企业资料。
- `resume`：简历文件、摘要、诊断状态。
- `job`：岗位 JD、要求、薪资和状态。
- `match_result`：匹配分数、优势、短板、建议。
- `delivery`：投递记录、流程状态和投递时的简历解析质量快照。
- `ai_task`：AI 调用任务、输入摘要、输出、状态。
- `ai_candidate_screen_record`：AI 候选人初筛历史，包含筛选编号、企业编号、投递编号、学生编号、岗位编号、简历解析格式、解析状态、抽取正文长度、分数、推荐结论、优势、风险、面试追问、下一步动作、mock 标记和创建时间。

MVP 阶段使用内存仓储保证演示闭环；接入 MySQL 时按以上表结构落库。

## v0.8 AI Screening Table

```sql
CREATE TABLE IF NOT EXISTS ai_candidate_screen_record (
    screening_id VARCHAR(64) NOT NULL PRIMARY KEY,
    company_id VARCHAR(64) NOT NULL,
    delivery_id VARCHAR(64) NOT NULL,
    student_id VARCHAR(64) NOT NULL,
    job_id VARCHAR(64) NOT NULL,
    resume_source_format VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parse_status VARCHAR(32) NOT NULL DEFAULT 'UNKNOWN',
    resume_parsed_text_length INT NOT NULL DEFAULT 0,
    score INT NOT NULL,
    recommendation TEXT NOT NULL,
    strengths TEXT NOT NULL,
    risks TEXT NOT NULL,
    interview_questions TEXT NOT NULL,
    next_actions TEXT NOT NULL,
    mocked TINYINT(1) NOT NULL DEFAULT 0,
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    KEY idx_ai_candidate_screen_record_company_delivery_created (company_id, delivery_id, created_at),
    KEY idx_ai_candidate_screen_record_delivery_created (delivery_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

`strengths`、`risks`、`interview_questions`、`next_actions` 使用 JSON 字符串保存，服务层通过 MyBatis-Plus 类型处理器转换为字符串数组。
