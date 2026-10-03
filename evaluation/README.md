# Core deepening evaluation fixtures

## Provenance

All records are `SYNTHETIC_ANONYMOUS`. They do not contain real student, company or hiring data. Labels are proposed by the fixture author from the supplied text and expected behavior. `OWNER_REVIEW_PENDING` is intentionally retained until the project owner reviews the calibration labels.

## Files

- `fixtures/resume-job.json`: 90 Java, frontend and operations resume-job pairs.
- `fixtures/interview-answers.json`: 45 saved-answer interview cases with four rubric dimensions.
- `fixtures/knowledge-queries.json`: 60 knowledge queries and 51 isolated synthetic documents.
- `manifest.json`: counts, split policy, hashes and target metrics.
- `java/CoreFixtureEvaluator.java`: calls compiled production rules and the existing rule interview evaluator. It asserts invariants; it does not reimplement scoring.

Each dataset has `calibration` and `test` rows. Calibration rows can suggest a rerank threshold. Test rows are evaluated once after the threshold decision and never change the configuration automatically.

## Commands

```powershell
node scripts/evaluate-core-deepening.cjs --mode validate
node scripts/evaluate-core-deepening.cjs --mode resume
node scripts/evaluate-core-deepening.cjs --mode interview
node scripts/evaluate-core-deepening.cjs --mode rag --env-file .env --output evaluation/reports/rag-acceptance.json
```

The RAG command creates documents with a run prefix, searches through the authenticated API, validates `AiSearchResult.citation.documentId` and original offsets, checks answer claims and deletes only documents created by that run. Use `--keep-documents` only for a deliberate manual investigation.

The report never writes API credentials, JWTs, request headers, provider response bodies or passwords. Reports are ignored by `evaluation/.gitignore`.

Use `--output` to keep independent reports for each mode. The default report path is shared and a later run replaces it.
