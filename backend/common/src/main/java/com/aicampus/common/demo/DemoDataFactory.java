package com.aicampus.common.demo;

import com.aicampus.common.dto.AiCallRecord;
import com.aicampus.common.dto.AiPlanningRecord;
import com.aicampus.common.dto.CandidateScreenRecord;
import com.aicampus.common.dto.CandidateScreenResult;
import com.aicampus.common.dto.CandidateScreenTask;
import com.aicampus.common.dto.CareerPlanResponse;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.InterviewRecord;
import com.aicampus.common.dto.InterviewSchedule;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.KnowledgeDocument;
import com.aicampus.common.dto.MatchResult;
import com.aicampus.common.dto.NotificationMessage;
import com.aicampus.common.dto.ResumeRewriteResponse;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.dto.UserProfile;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.CandidateScreenTaskStatus;
import com.aicampus.common.enums.DeliveryStatus;
import com.aicampus.common.enums.InterviewScheduleStatus;
import com.aicampus.common.enums.Role;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

public final class DemoDataFactory {
    public static final int DEFAULT_SIZE = 120;
    private static final LocalDateTime BASE_DATE_TIME = LocalDateTime.of(2026, 6, 12, 9, 0);
    private static final Instant BASE_INSTANT = BASE_DATE_TIME.toInstant(ZoneOffset.ofHours(8));

    private static final String[] FAMILY_NAMES = {
            "Chen", "Li", "Wang", "Zhang", "Liu", "Yang", "Huang", "Zhao", "Wu", "Zhou",
            "Xu", "Sun", "Ma", "Zhu", "Hu", "Guo", "He", "Gao", "Lin", "Luo"
    };
    private static final String[] GIVEN_NAMES = {
            "Yichen", "Zihan", "Mingyu", "Haoran", "Yutong", "Jingyi", "Xinyu", "Bowen", "Siyuan", "Qianwen",
            "Jiahao", "Ruoxi", "Yuxuan", "Tianqi", "Anqi", "Zixuan", "Yiming", "Shuyi", "Junhao", "Yiran"
    };
    private static final String[] SCHOOLS = {
            "Zhejiang University", "Nanjing University", "Wuhan University", "Xidian University",
            "Beijing University of Posts and Telecommunications", "South China University of Technology",
            "Sichuan University", "Shandong University", "Hunan University", "Shanghai University"
    };
    private static final String[] MAJORS = {
            "Software Engineering", "Computer Science", "Data Science", "Information Security",
            "Artificial Intelligence", "Network Engineering", "Internet of Things", "Automation"
    };
    private static final String[] COMPANIES = {
            "ByteSpring Technology", "Aliyun Campus Lab", "Tencent Cloud Recruit", "Meituan Infrastructure",
            "JD Logistics Tech", "Baidu AI Cloud", "NetEase Games Platform", "Huawei Cloud BU",
            "Bilibili Data Platform", "Xiaomi IoT Platform", "Didi Travel Tech", "Kingsoft Office"
    };
    private static final String[] CITIES = {
            "Hangzhou", "Shanghai", "Beijing", "Shenzhen", "Guangzhou", "Nanjing", "Chengdu", "Wuhan", "Suzhou", "Xi'an"
    };
    private static final String[] TITLES = {
            "Java Backend Intern", "Frontend Engineer Intern", "AI Application Intern", "Data Development Intern",
            "Test Development Intern", "SRE Intern", "Product Data Analyst Intern", "Algorithm Engineer Intern",
            "Cloud Native Development Intern", "Security Development Intern"
    };
    private static final String[][] SKILL_POOLS = {
            {"Java", "Spring Boot", "MyBatis Plus", "MySQL", "Redis"},
            {"Vue", "TypeScript", "Element Plus", "Vite", "ECharts"},
            {"Python", "Prompt Engineering", "RAG", "Milvus", "DashScope"},
            {"SQL", "Flink", "Hive", "Kafka", "Data Modeling"},
            {"Java", "JUnit", "Postman", "Selenium", "JMeter"},
            {"Linux", "Docker", "Prometheus", "Nacos", "Shell"},
            {"SQL", "Excel", "Python", "A/B Testing", "Dashboard"},
            {"Python", "PyTorch", "Machine Learning", "Vector Search", "Feature Engineering"},
            {"Spring Cloud Alibaba", "Docker", "Gateway", "Nacos", "RocketMQ"},
            {"Java", "JWT", "RBAC", "Gateway", "Security Testing"}
    };
    private static final DeliveryStatus[] DELIVERY_STATUSES = {
            DeliveryStatus.SUBMITTED, DeliveryStatus.VIEWED, DeliveryStatus.INTERVIEW, DeliveryStatus.OFFER, DeliveryStatus.REJECTED
    };
    private static final InterviewScheduleStatus[] SCHEDULE_STATUSES = {
            InterviewScheduleStatus.PROPOSED, InterviewScheduleStatus.CONFIRMED, InterviewScheduleStatus.DECLINED,
            InterviewScheduleStatus.COMPLETED, InterviewScheduleStatus.CANCELLED
    };

    private DemoDataFactory() {
    }

    public static List<UserProfile> studentProfiles() {
        List<UserProfile> profiles = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            profiles.add(new UserProfile(
                    studentId(i),
                    studentName(i),
                    Role.STUDENT,
                    SCHOOLS[(i - 1) % SCHOOLS.length],
                    MAJORS[(i + 1) % MAJORS.length],
                    skills(i),
                    TITLES[(i - 1) % TITLES.length]));
        }
        return profiles;
    }

    public static List<ResumeSummary> resumes() {
        List<ResumeSummary> resumes = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                resumes.add(new ResumeSummary(
                        "R001",
                        "S001",
                        "demo-resume.pdf",
                        "Demo University / Software Engineering / 2026 Bachelor",
                        List.of("Java", "Spring Boot", "MySQL", "Redis"),
                        List.of("Campus second-hand trading system", "Online exam platform"),
                        "Resume structure is complete; add quantified impact and internship evidence.",
                        82,
                        "resumes/R001/demo-resume.pdf",
                        "local-demo",
                        "SEEDED",
                        "PDF",
                        "SEEDED",
                        62));
                continue;
            }
            String format = i % 7 == 0 ? "DOCX" : i % 5 == 0 ? "TXT" : "PDF";
            List<String> skills = skills(i);
            resumes.add(new ResumeSummary(
                    resumeId(i),
                    studentId(i),
                    "resume-" + studentId(i).toLowerCase() + "." + format.toLowerCase(),
                    SCHOOLS[(i - 1) % SCHOOLS.length] + " / " + MAJORS[(i + 1) % MAJORS.length] + " / 2026 Bachelor",
                    skills,
                    projects(i),
                    "Resume has clear project ownership, " + skills.get(0) + " evidence, and " + (78 + i % 18) + " readiness score.",
                    68 + (i % 29),
                    "resumes/" + resumeId(i) + "/resume-" + studentId(i).toLowerCase() + "." + format.toLowerCase(),
                    "seed-demo",
                    "SEEDED",
                    format,
                    i % 11 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    850 + i * 17));
        }
        return resumes;
    }

    public static List<String> resumeTexts() {
        List<String> texts = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                texts.add("Demo University software engineering bachelor. Skills: Java, Spring Boot, MySQL, Redis. Projects: campus second-hand trading system and online exam platform.");
                continue;
            }
            List<String> skills = skills(i);
            texts.add(studentName(i) + " from " + SCHOOLS[(i - 1) % SCHOOLS.length]
                    + ". Target role: " + TITLES[(i - 1) % TITLES.length]
                    + ". Skills: " + String.join(", ", skills)
                    + ". Projects: " + String.join("; ", projects(i))
                    + ". Internship evidence includes API design, database modeling, deployment logs, and performance review.");
        }
        return texts;
    }

    public static List<JobSummary> jobs() {
        List<JobSummary> jobs = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            List<String> skills = skills(i);
            jobs.add(new JobSummary(
                    jobId(i),
                    companyId(i),
                    companyName(i),
                    TITLES[(i - 1) % TITLES.length],
                    CITIES[(i + 2) % CITIES.length],
                    salary(i),
                    skills,
                    "Join " + companyName(i) + " to work on " + businessDomain(i)
                            + ". Daily work includes " + skills.get(0) + " development, code review, testing, and production issue analysis.",
                    "Best fit for candidates with " + String.join(", ", skills.subList(0, Math.min(3, skills.size())))
                            + " and evidence from campus or internship projects."));
        }
        return jobs;
    }

    public static List<MatchResult> matches() {
        List<MatchResult> matches = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            List<String> skills = skills(i);
            matches.add(new MatchResult(
                    matchId(i),
                    resumeId(i),
                    jobId(((i + 11) % DEFAULT_SIZE) + 1),
                    studentId(i),
                    58 + (i * 7 % 41),
                    List.of(
                            "Resume skills overlap with " + skills.get(0) + " and " + skills.get(1),
                            "Project evidence mentions API design, data model, and deployment workflow",
                            "Career target is aligned with the job title"),
                    List.of(
                            "Needs more quantified results for latency, traffic, or conversion",
                            "Distributed troubleshooting experience should be explained in more detail"),
                    List.of(
                            "Add one metric for request latency or data volume",
                            "Prepare a STAR story for " + skills.get(0) + " project ownership",
                            "Review MySQL index and Redis cache consistency scenarios")));
        }
        return matches;
    }

    public static List<DeliveryRecord> deliveries() {
        List<DeliveryRecord> deliveries = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                deliveries.add(new DeliveryRecord(
                        "D001",
                        "S001",
                        "R001",
                        "J001",
                        "C001",
                        "PDF",
                        "SEEDED",
                        62,
                        DeliveryStatus.SUBMITTED,
                        BASE_DATE_TIME.minusDays(1)));
                continue;
            }
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            deliveries.add(new DeliveryRecord(
                    deliveryId(i),
                    studentId(i),
                    resumeId(i),
                    jobId(jobIndex),
                    companyId(jobIndex),
                    i % 7 == 0 ? "DOCX" : "PDF",
                    i % 13 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    820 + i * 16,
                    DELIVERY_STATUSES[(i - 1) % DELIVERY_STATUSES.length],
                    BASE_DATE_TIME.minusHours(i * 3L)));
        }
        return deliveries;
    }

    public static List<NotificationMessage> notifications() {
        List<NotificationMessage> messages = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean studentTarget = i % 2 == 1;
            String sourceId = studentTarget ? deliveryId(i) : deliveryId(((i + 17) % DEFAULT_SIZE) + 1);
            messages.add(new NotificationMessage(
                    "N-DEMO-" + "%03d".formatted(i),
                    studentTarget ? "STUDENT" : "COMPANY",
                    studentTarget ? studentId(i) : companyId(i),
                    studentTarget ? "Application progress updated" : "New candidate needs review",
                    studentTarget
                            ? "Your application " + sourceId + " has a new status in the campus recruitment workflow."
                            : "Candidate " + studentId(i) + " submitted a resume for " + jobId(i) + ". Please finish screening within 48 hours.",
                    studentTarget ? "DELIVERY_STATUS" : "DELIVERY",
                    sourceId,
                    i % 4 == 0,
                    BASE_DATE_TIME.minusMinutes(i * 37L)));
        }
        return messages;
    }

    public static List<InterviewSchedule> interviewSchedules() {
        List<InterviewSchedule> schedules = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            if (i == 1) {
                schedules.add(new InterviewSchedule(
                        "IS-DEMO-001",
                        "D003",
                        "C001",
                        "S003",
                        "J002",
                        "Java backend technical interview",
                        BASE_DATE_TIME.plusDays(2).withSecond(0).withNano(0),
                        45,
                        "Online",
                        "https://meet.example.com/demo-java-backend",
                        "Prepare one backend project and one MySQL troubleshooting case.",
                        InterviewScheduleStatus.PROPOSED,
                        BASE_DATE_TIME.minusHours(2),
                        BASE_DATE_TIME.minusHours(2)));
                continue;
            }
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            LocalDateTime createdAt = BASE_DATE_TIME.minusHours(i);
            schedules.add(new InterviewSchedule(
                    "IS-DEMO-" + "%03d".formatted(i),
                    deliveryId(i),
                    companyId(jobIndex),
                    studentId(i),
                    jobId(jobIndex),
                    interviewTitle(i),
                    BASE_DATE_TIME.plusDays(1 + i % 14).withHour(9 + i % 8).withMinute((i % 2) * 30),
                    30 + (i % 3) * 15,
                    i % 4 == 0 ? CITIES[i % CITIES.length] + " R&D Center" : "Online Tencent Meeting",
                    "https://meet.example.com/campus/" + "%03d".formatted(i),
                    "Prepare one project deep dive, one SQL troubleshooting case, and questions for the interviewer.",
                    SCHEDULE_STATUSES[(i - 1) % SCHEDULE_STATUSES.length],
                    createdAt,
                    createdAt.plusMinutes(15 + i)));
        }
        return schedules;
    }

    public static List<CandidateScreenRecord> candidateScreenRecords() {
        List<CandidateScreenRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            int jobIndex = ((i + 11) % DEFAULT_SIZE) + 1;
            List<String> skills = skills(i);
            int score = 60 + (i * 5 % 39);
            records.add(new CandidateScreenRecord(
                    "CS-DEMO-" + "%03d".formatted(i),
                    companyId(jobIndex),
                    deliveryId(i),
                    studentId(i),
                    jobId(jobIndex),
                    i % 7 == 0 ? "DOCX" : "PDF",
                    i % 13 == 0 ? "UNPARSED" : "TEXT_EXTRACTED",
                    820 + i * 16,
                    score,
                    score >= 85 ? "Strong recommend for technical interview" : score >= 72 ? "Recommend with focused follow-up" : "Keep in talent pool",
                    List.of("Core skill " + skills.get(0) + " matches the role", "Project work covers API, data, and deployment evidence"),
                    List.of("Quantified impact is still limited", "Needs deeper explanation of failures and tradeoffs"),
                    List.of(
                            "Explain one " + skills.get(0) + " project with personal ownership",
                            "How would you debug a slow API from logs to SQL plan?",
                            "What tradeoff did you make in database or cache design?"),
                    List.of("Invite to first interview", "Ask for project metrics", "Check internship availability"),
                    true,
                    BASE_INSTANT.minusSeconds(i * 1800L)));
        }
        return records;
    }

    public static List<CandidateScreenTask> candidateScreenTasks() {
        List<CandidateScreenTask> tasks = new ArrayList<>();
        List<CandidateScreenRecord> records = candidateScreenRecords();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            CandidateScreenRecord record = records.get(i - 1);
            CandidateScreenResult result = new CandidateScreenResult(
                    record.deliveryId(),
                    record.studentId(),
                    record.jobId(),
                    record.resumeSourceFormat(),
                    record.resumeParseStatus(),
                    record.resumeParsedTextLength(),
                    record.score(),
                    record.recommendation(),
                    record.strengths(),
                    record.risks(),
                    record.interviewQuestions(),
                    record.nextActions(),
                    true);
            CandidateScreenTaskStatus status = i % 9 == 0 ? CandidateScreenTaskStatus.FAILED : CandidateScreenTaskStatus.COMPLETED;
            Instant createdAt = BASE_INSTANT.minusSeconds(i * 1700L);
            tasks.add(new CandidateScreenTask(
                    "AST-DEMO-" + "%03d".formatted(i),
                    record.deliveryId(),
                    record.companyId(),
                    record.studentId(),
                    resumeId(i),
                    record.jobId(),
                    status,
                    i % 3 == 0 ? CandidateScreenTaskSource.ROCKETMQ : CandidateScreenTaskSource.DEMO,
                    status == CandidateScreenTaskStatus.FAILED ? "Demo task failed for retry validation" : "Demo screening completed",
                    status == CandidateScreenTaskStatus.COMPLETED ? result : null,
                    createdAt,
                    createdAt.plusSeconds(40 + i)));
        }
        return tasks;
    }

    public static List<AiPlanningRecord> planningRecords() {
        List<AiPlanningRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean rewrite = i % 2 == 0;
            String studentId = studentId(((i - 1) % 20) + 1);
            String targetRole = TITLES[(i - 1) % TITLES.length];
            records.add(new AiPlanningRecord(
                    "PLAN-DEMO-" + "%03d".formatted(i),
                    studentId,
                    rewrite ? "resume-rewrite" : "career-plan",
                    resumeId(((i - 1) % DEFAULT_SIZE) + 1),
                    targetRole,
                    rewrite ? resumeRewrite(studentId, targetRole, i) : null,
                    rewrite ? null : careerPlan(studentId, targetRole, i),
                    true,
                    BASE_INSTANT.minusSeconds(i * 2100L)));
        }
        return records;
    }

    public static List<AiCallRecord> aiCallRecords() {
        List<AiCallRecord> records = new ArrayList<>();
        String[] operations = {"analyze", "resume-rewrite", "career-plan", "candidate-screening", "interview-feedback", "semantic-search"};
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            boolean success = i % 17 != 0;
            boolean mocked = i % 5 == 0;
            records.add(new AiCallRecord(
                    "AI-CALL-DEMO-" + "%03d".formatted(i),
                    operations[(i - 1) % operations.length],
                    mocked ? "mock-dashscope" : "dashscope",
                    i % 4 == 0 ? "qwen-max" : "qwen-plus",
                    success,
                    mocked,
                    180 + (i * 37L % 1200),
                    320 + i * 9,
                    180 + i * 7,
                    success ? null : "Simulated provider timeout for observability drill",
                    BASE_INSTANT.minusSeconds(i * 900L)));
        }
        return records;
    }

    public static List<InterviewRecord> interviewRecords() {
        List<InterviewRecord> records = new ArrayList<>();
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            List<String> skills = skills(i);
            records.add(new InterviewRecord(
                    "IR-DEMO-" + "%03d".formatted(i),
                    studentId(((i - 1) % 20) + 1),
                    TITLES[(i - 1) % TITLES.length],
                    "IQ-DEMO-" + "%03d".formatted(i),
                    "How did you use " + skills.get(0) + " in a real project, and what metric improved after your change?",
                    "I owned the module design, added tests, reviewed logs, and compared before/after latency and error rate.",
                    62 + (i * 3 % 36),
                    "Answer includes project context and technical action; add more numbers for stronger evidence.",
                    List.of("Add traffic or latency data", "Explain a failure case", "Clarify team collaboration boundary"),
                    true,
                    BASE_INSTANT.minusSeconds(i * 1600L)));
        }
        return records;
    }

    public static List<KnowledgeDocument> knowledgeDocuments() {
        List<KnowledgeDocument> documents = new ArrayList<>();
        String[] categories = {"resume", "interview", "delivery", "microservice", "ai", "operations", "security", "company"};
        for (int i = 1; i <= DEFAULT_SIZE; i++) {
            String category = categories[(i - 1) % categories.length];
            List<String> skillSet = skills(i);
            documents.add(new KnowledgeDocument(
                    "KB-DEMO-BULK-" + "%03d".formatted(i),
                    knowledgeTitle(category, i),
                    knowledgeContent(category, skillSet, i),
                    category,
                    "seed-demo-bulk",
                    List.of(category, skillSet.get(0), "campus-recruit", "v3.12-demo-data"),
                    i % 5 == 0 ? List.of("ADMIN") : List.of("STUDENT", "COMPANY", "ADMIN"),
                    "demo-seeder",
                    BASE_DATE_TIME.minusMinutes(i * 11L)));
        }
        return documents;
    }

    public static String companyId(int index) {
        return "C" + "%03d".formatted(((index - 1) % 24) + 1);
    }

    public static String studentId(int index) {
        return "S" + "%03d".formatted(index);
    }

    public static String resumeId(int index) {
        return "R" + "%03d".formatted(index);
    }

    public static String jobId(int index) {
        return "J" + "%03d".formatted(index);
    }

    public static String deliveryId(int index) {
        return "D" + "%03d".formatted(index);
    }

    private static String matchId(int index) {
        return "M" + "%03d".formatted(index);
    }

    private static String studentName(int index) {
        return FAMILY_NAMES[(index - 1) % FAMILY_NAMES.length] + " " + GIVEN_NAMES[(index + 3) % GIVEN_NAMES.length];
    }

    private static String companyName(int index) {
        return COMPANIES[(index - 1) % COMPANIES.length];
    }

    private static List<String> skills(int index) {
        return List.of(SKILL_POOLS[(index - 1) % SKILL_POOLS.length]);
    }

    private static List<String> projects(int index) {
        List<String> skills = skills(index);
        return List.of(
                "Campus recruitment workflow with " + skills.get(0) + " and " + skills.get(1),
                "Resume diagnosis and delivery analytics dashboard",
                "Three-VM microservice deployment and smoke testing");
    }

    private static String salary(int index) {
        int low = 150 + (index % 8) * 20;
        return low + "-" + (low + 80) + "/day";
    }

    private static String businessDomain(int index) {
        String[] domains = {
                "resume parsing and job matching", "AI interview feedback", "delivery conversion analytics",
                "distributed deployment monitoring", "candidate screening automation", "enterprise campus recruitment"
        };
        return domains[(index - 1) % domains.length];
    }

    private static String interviewTitle(int index) {
        String[] rounds = {"HR screening", "Technical first round", "Project deep-dive", "Final manager interview"};
        return TITLES[(index - 1) % TITLES.length] + " - " + rounds[(index - 1) % rounds.length];
    }

    private static ResumeRewriteResponse resumeRewrite(String studentId, String targetRole, int index) {
        List<String> skillSet = skills(index);
        return new ResumeRewriteResponse(
                studentId,
                resumeId(((index - 1) % DEFAULT_SIZE) + 1),
                targetRole,
                "Candidate highlights " + skillSet.get(0) + ", " + skillSet.get(1)
                        + ", and a campus recruitment project with measurable delivery workflow impact.",
                List.of(
                        "Owned " + skillSet.get(0) + " module design and implemented API, validation, and tests.",
                        "Improved dashboard loading by caching core statistics and reducing repeated service calls."),
                List.of(skillSet.get(0), skillSet.get(1), skillSet.get(2), "project metrics", "deployment evidence"),
                List.of("Add exact QPS or latency numbers", "Clarify personal contribution in team project"),
                List.of("Rewrite first project with STAR", "Add one production-like debugging case", "Prepare a two-minute project pitch"),
                true);
    }

    private static CareerPlanResponse careerPlan(String studentId, String targetRole, int index) {
        List<String> skillSet = skills(index);
        return new CareerPlanResponse(
                studentId,
                targetRole,
                60 + (index * 3 % 38),
                "Focus on " + skillSet.get(0) + " fundamentals, measurable project proof, and interview drills for " + targetRole + ".",
                List.of(
                        new CareerPlanResponse.Milestone("Evidence polish", "Week 1-2", List.of("Quantify project result", "Prepare architecture diagram")),
                        new CareerPlanResponse.Milestone("Technical depth", "Week 3-5", List.of("Review " + skillSet.get(0), "Practice troubleshooting")),
                        new CareerPlanResponse.Milestone("Interview sprint", "Week 6-8", List.of("Mock interview", "Target company delivery"))),
                List.of(skillSet.get(2) + " depth", "System design tradeoffs", "Production troubleshooting"),
                List.of("Review one technical topic", "Update resume evidence", "Submit to three matching roles"),
                List.of("Architecture diagram", "API test report", "Deployment screenshot"),
                List.of("Project deep dive", "SQL and cache troubleshooting", "Behavioral STAR answer"),
                true);
    }

    private static String knowledgeTitle(String category, int index) {
        return switch (category) {
            case "resume" -> "Resume evidence checklist " + index;
            case "interview" -> "Interview follow-up guide " + index;
            case "delivery" -> "Delivery conversion playbook " + index;
            case "microservice" -> "Microservice deployment note " + index;
            case "ai" -> "AI screening prompt policy " + index;
            case "operations" -> "Operations smoke test runbook " + index;
            case "security" -> "Recruitment RBAC audit note " + index;
            default -> "Company campus hiring FAQ " + index;
        };
    }

    private static String knowledgeContent(String category, List<String> skillSet, int index) {
        return "Scenario " + index + " for " + category + ": use " + skillSet.get(0) + ", " + skillSet.get(1)
                + ", and " + skillSet.get(2)
                + " evidence to answer campus recruitment questions. Include owner, timeline, measurable result, risk, and next action. "
                + "This document is seeded for RAG retrieval, citation testing, admin upload comparison, and vector-index fallback validation.";
    }
}
