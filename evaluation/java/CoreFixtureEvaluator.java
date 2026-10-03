import com.aicampus.common.dto.*;
import com.aicampus.common.evidence.*;
import com.aicampus.match.service.EvidenceMatchRules;
import com.aicampus.ai.service.AiCoachService;
import com.aicampus.ai.service.DashScopeClient;
import com.fasterxml.jackson.databind.*;
import java.nio.file.*;
import java.util.*;

/** Runs the compiled production classes. This runner contains assertions, not replacement algorithms. */
public class CoreFixtureEvaluator {
    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static List<String> strings(JsonNode values) {
        List<String> result = new ArrayList<>();
        if (values.isArray()) values.forEach(item -> result.add(item.asText()));
        return result;
    }
    private static void check(boolean condition, String name, List<String> failures) { if (!condition) failures.add(name); }
    private static String env(String name, String fallback) {
        String value = System.getenv(name); return value == null || value.isBlank() ? fallback : value;
    }
    public static void main(String[] args) throws Exception {
        String mode = args[0]; Path source = Path.of(args[1]), output = Path.of(args[2]);
        boolean liveAi = args.length > 3 && "--ai".equals(args[3]);
        JsonNode dataset = JSON.readTree(source.toFile());
        List<Map<String,Object>> rows = new ArrayList<>();
        AiCoachService coach = null;
        if ("interview".equals(mode)) {
            String key = liveAi ? env("DASHSCOPE_API_KEY", "") : "";
            if (liveAi && key.isBlank()) throw new IllegalArgumentException("Live interview evaluation needs provider credentials in the environment");
            coach = new AiCoachService(new DashScopeClient(key,env("DASHSCOPE_MODEL","qwen-plus"),
                    env("DASHSCOPE_BASE_URL","https://dashscope.aliyuncs.com/compatible-mode/v1")));
        }
        for (JsonNode fixture : dataset.path("cases")) {
            List<String> failures = new ArrayList<>(), labelDifferences = new ArrayList<>();
            Map<String,Object> row = new LinkedHashMap<>();
            row.put("id",fixture.path("id").asText()); row.put("split",fixture.path("split").asText());
            row.put("track",fixture.path("track").asText()); row.put("scenario",fixture.path("scenario").asText());
            try {
                if ("resume".equals(mode)) {
                    JsonNode p = fixture.path("profile"), j = fixture.path("job"), expected = fixture.path("expected");
                    ResumeProfileSnapshot profile = new ResumeProfileSnapshot(p.path("education").asText(),strings(p.path("skills")),
                            strings(p.path("projects")),p.path("resumeText").asText());
                    JobSummary job = new JobSummary("job-"+fixture.path("id").asText(),"synthetic-company","匿名合成单位",j.path("title").asText(),
                            j.path("city").asText(),"合成样例，不提供真实薪资",strings(j.path("requiredSkills")),j.path("description").asText(),"","OPEN");
                    StructuredResumeDiagnosis input = new StructuredResumeDiagnosis(null,0,0,0,List.of(),List.of(),job,profile,false);
                    ResumeSummary resume = new ResumeSummary("resume-"+fixture.path("id").asText(),"synthetic-student","synthetic.txt",profile.education(),
                            profile.skills(),profile.projects(),"",0,null,"NONE","NOT_STORED","TXT","PARSED",profile.resumeText().length(),input);
                    MatchDetails details = EvidenceMatchRules.details(resume,job);
                    Set<String> declared = new LinkedHashSet<>(strings(expected.path("declaredSkills")));
                    Set<String> supported = new LinkedHashSet<>(strings(expected.path("supportedSkills")));
                    for (MatchRequirement item : details.requirements()) {
                        check(item.declared()==declared.contains(item.skill()),"declared:"+item.skill(),failures);
                        check(item.supported()==supported.contains(item.skill()),"supported:"+item.skill(),failures);
                        if (item.supported()) check(ResumeEvidenceRules.locate(profile,item.evidence().quote())!=null,"quote:"+item.skill(),failures);
                    }
                    check(details.skillsCoverage()==ResumeEvidenceRules.percent(declared.size(),details.requirements().size()),"declaration coverage",failures);
                    check(details.evidenceCoverage()==ResumeEvidenceRules.percent(supported.size(),details.requirements().size()),"evidence coverage",failures);
                    for (MatchCondition condition : details.conditions())
                        check(condition.status().equals(expected.path("conditions").path(condition.type()).asText()),"condition:"+condition.type(),failures);
                    row.put("declaredCoverage",details.skillsCoverage()); row.put("evidenceCoverage",details.evidenceCoverage());
                    row.put("algorithmVersion",details.metadata().algorithmVersion());
                } else {
                    String answer = fixture.path("answer").asText();
                    InterviewFeedback feedback = coach.evaluateSavedAnswer(new InterviewFeedbackRequest("synthetic-eval",fixture.path("id").asText(),
                            fixture.path("question").asText(),answer,fixture.path("targetRole").asText()),strings(fixture.path("referencePoints")));
                    check(feedback.dimensions()!=null && feedback.dimensions().size()==4,"four rubric dimensions",failures);
                    List<String> dimensions = feedback.dimensions().stream().map(InterviewDimensionScore::dimension).toList();
                    check(dimensions.equals(strings(fixture.path("expected").path("dimensions"))),"rubric order",failures);
                    check(feedback.evidence()!=null && !feedback.evidence().isEmpty(),"cited feedback",failures);
                    for (InterviewEvidenceNote note : feedback.evidence()) check(note.quote()!=null && answer.contains(note.quote()),"answer quote",failures);
                    check(feedback.mocked()!=liveAi,"requested evaluation mode",failures);
                    boolean follow = feedback.followUpQuestion()!=null && !feedback.followUpQuestion().isBlank();
                    if (follow!=fixture.path("expected").path("shouldFollowUp").asBoolean()) labelDifferences.add("follow-up differs from proposed semantic label");
                    String expectedType = fixture.path("expected").path("requiredEvidenceType").asText();
                    if (liveAi && feedback.evidence().stream().noneMatch(note -> expectedType.equals(note.type())))
                        labelDifferences.add("evidence type differs from proposed semantic label");
                    row.put("mocked",feedback.mocked()); row.put("followUp",follow); row.put("score",feedback.score());
                    row.put("answerCharacters",answer.length()); row.put("quoteCount",feedback.evidence().size());
                }
            } catch (Exception ex) { failures.add("evaluation failed: "+ex.getClass().getSimpleName()); }
            row.put("invariantPassed",failures.isEmpty()); row.put("failures",failures);
            row.put("proposedLabelDifferences",labelDifferences); rows.add(row);
        }
        Map<String,Object> report = new LinkedHashMap<>();
        report.put("mode",mode); report.put("evaluatedAt",java.time.Instant.now().toString());
        report.put("implementation","compiled production Java classes"); report.put("reviewStatus","OWNER_REVIEW_PENDING");
        report.put("total",rows.size()); report.put("invariantFailures",rows.stream().filter(row -> !(boolean)row.get("invariantPassed")).count());
        report.put("proposedLabelDisagreements",rows.stream().filter(row -> !((List<?>)row.get("proposedLabelDifferences")).isEmpty()).count());
        report.put("rows",rows); Files.createDirectories(output.toAbsolutePath().getParent());
        JSON.writerWithDefaultPrettyPrinter().writeValue(output.toFile(),report);
        // Only structural/rule assertions gate the run. Unreviewed semantic interview labels remain advisory.
        if ((long)report.get("invariantFailures")>0) System.exit(1);
    }
}
