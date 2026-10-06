package com.aicampus.ai.service.knowledge.workspace;

import static com.aicampus.common.dto.KnowledgeWorkspaceModels.*;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.ai.service.KnowledgeBaseService;
import io.swagger.v3.oas.annotations.Operation;
import java.util.List;
import java.util.Map;
import java.util.Arrays;
import java.nio.charset.StandardCharsets;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRange;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.CrossOrigin;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@CrossOrigin
@RestController
@RequestMapping("/api/ai/knowledge")
public class KnowledgeWorkspaceController {
    private static final String USER = "X-User-Id";
    private static final String ROLE = "X-User-Role";
    private final KnowledgeWorkspaceService workspace;
    private final KnowledgeCatalogService catalog;
    private KnowledgeBaseService knowledge;

    public KnowledgeWorkspaceController(KnowledgeWorkspaceService workspace, KnowledgeCatalogService catalog) {
        this.workspace = workspace;
        this.catalog = catalog;
    }

    @org.springframework.beans.factory.annotation.Autowired
    public void setKnowledge(KnowledgeBaseService knowledge) { this.knowledge = knowledge; }

    @Operation(summary = "Search knowledge with an account-scoped history snapshot")
    @PostMapping({"/query", "/workspace/search"})
    public ApiResponse<com.aicampus.common.dto.KnowledgeAnswerResponse> query(@RequestBody KnowledgeQuery request,
            @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.query(request, user, role));
    }

    @Operation(summary = "List job-aware knowledge recommendations")
    @GetMapping("/recommendations")
    public ApiResponse<List<KnowledgeWorkspaceRecommendation>> recommendations(
            @RequestParam(required = false) String roleDirection, @RequestParam(required = false) String skill,
            @RequestParam(required = false) String difficulty, @RequestParam(required = false) String contentType,
            @RequestParam(required = false) String resumeId, @RequestParam(required = false) String jobId,
            @RequestParam(required = false) String matchId, @RequestParam(required = false) String planId,
            @RequestParam(required = false) String interviewSessionId, @RequestParam(required = false) String targetRole,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.recommendations(new KnowledgeQuery(null, roleDirection, skill, difficulty,
                contentType, false, resumeId, jobId, matchId, planId, interviewSessionId, targetRole), user, role));
    }

    @Operation(summary = "List published topics")
    @GetMapping("/topics")
    public ApiResponse<List<KnowledgeTopic>> topics(@RequestParam(required = false) String roleDirection,
            @RequestParam(required = false) String skill, @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) String contentType, @RequestHeader(value = ROLE, required = false) String role,
            @RequestHeader(value = USER, required = false) String user) {
        requireIdentity(user, role);
        return ApiResponse.ok(catalog.listTopics(roleDirection, skill, difficulty, contentType, role));
    }

    @Operation(summary = "Read one published topic")
    @GetMapping("/topics/{topicId}")
    public ApiResponse<KnowledgeTopic> topic(@PathVariable String topicId,
            @RequestHeader(value = ROLE, required = false) String role, @RequestHeader(value = USER, required = false) String user) {
        requireIdentity(user, role);
        return ApiResponse.ok(catalog.topic(topicId, role));
    }

    @Operation(summary = "Read a source document and citation locations")
    @GetMapping("/library/{documentId}")
    public ApiResponse<KnowledgeLibraryDocument> library(@PathVariable String documentId,
            @RequestHeader(value = ROLE, required = false) String role, @RequestHeader(value = USER, required = false) String user) {
        requireIdentity(user, role);
        return ApiResponse.ok(catalog.library(documentId, role));
    }

    @Operation(summary = "List the current student's knowledge items")
    @GetMapping("/me/items")
    public ApiResponse<List<KnowledgeWorkspaceItem>> items(@RequestParam(required = false) String kind,
            @RequestParam(required = false) String status, @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.items(user, role, kind, status));
    }

    @Operation(summary = "Save or update a bookmark, note or review item")
    @org.springframework.web.bind.annotation.RequestMapping(value = "/me/items", method = {
            org.springframework.web.bind.annotation.RequestMethod.POST, org.springframework.web.bind.annotation.RequestMethod.PUT})
    public ApiResponse<KnowledgeWorkspaceItem> saveItem(@RequestBody KnowledgeItemRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.saveItem(request, user, role));
    }

    @Operation(summary = "Update an existing personal item with a revision check")
    @PutMapping("/me/items/{itemId}")
    public ApiResponse<KnowledgeWorkspaceItem> updateItem(@PathVariable String itemId, @RequestBody KnowledgeItemRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        KnowledgeWorkspaceItem current = workspace.items(user, role, null, null).stream()
                .filter(item -> itemId.equals(item.itemId())).findFirst().orElseThrow(() -> new IllegalArgumentException("Learning item not found"));
        if (request == null || (request.topicId() != null && !current.topicId().equals(request.topicId()))
                || (request.kind() != null && !current.kind().equalsIgnoreCase(request.kind())))
            throw new IllegalArgumentException("Item identity cannot be changed");
        return ApiResponse.ok(workspace.saveItem(new KnowledgeItemRequest(current.topicId(), current.kind(), request.status(),
                request.note(), request.intervalDays(), request.reviewEnabled(), request.expectedRevision()), user, role));
    }

    @Operation(summary = "Delete a personal knowledge item")
    @DeleteMapping("/me/items/{itemId}")
    public ApiResponse<Boolean> deleteItem(@PathVariable String itemId, @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.deleteItem(itemId, user, role));
    }

    @Operation(summary = "Record a review result")
    @PostMapping("/me/items/{itemId}/review")
    public ApiResponse<KnowledgeWorkspaceItem> review(@PathVariable String itemId, @RequestBody KnowledgeReviewRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.reviewItem(itemId, request, user, role));
    }

    @Operation(summary = "List query history")
    @GetMapping("/me/history")
    public ApiResponse<List<KnowledgeWorkspaceHistory>> history(@RequestParam(defaultValue = "30") int limit,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.history(user, role, limit));
    }

    @Operation(summary = "Read a query history snapshot")
    @GetMapping("/me/history/{historyId}")
    public ApiResponse<KnowledgeWorkspaceHistory> historyItem(@PathVariable String historyId,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.historyItem(historyId, user, role));
    }

    @Operation(summary = "Delete query history")
    @DeleteMapping("/me/history/{historyId}")
    public ApiResponse<Boolean> deleteHistory(@PathVariable String historyId,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.deleteHistory(historyId, user, role));
    }

    @Operation(summary = "Create a source-grounded three-question practice")
    @PostMapping("/practices")
    public ApiResponse<KnowledgePractice> createPractice(@RequestParam(required = false) String topicId,
            @RequestBody(required = false) Map<String, String> request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.createPractice(topicId == null && request != null ? request.get("topicId") : topicId, user, role));
    }

    @Operation(summary = "Get a practice")
    @GetMapping("/practices/{practiceId}")
    public ApiResponse<KnowledgePractice> practice(@PathVariable String practiceId,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.practice(practiceId, user, role));
    }

    @Operation(summary = "Save an immutable practice answer before evaluation")
    @PostMapping("/practices/{practiceId}/answers")
    public ApiResponse<KnowledgePracticeAttempt> answer(@PathVariable String practiceId,
            @RequestBody KnowledgePracticeAnswerRequest request, @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.answer(practiceId, request, user, role));
    }

    @Operation(summary = "List immutable practice attempts")
    @GetMapping("/practices/{practiceId}/attempts")
    public ApiResponse<List<KnowledgePracticeAttempt>> attempts(@PathVariable String practiceId,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.attempts(practiceId, user, role));
    }

    @Operation(summary = "Retry a failed practice evaluation")
    @PostMapping({"/practices/{practiceId}/attempts/{attemptId}/retry", "/practices/{practiceId}/attempts/{attemptId}/evaluate"})
    public ApiResponse<KnowledgePracticeAttempt> retry(@PathVariable String practiceId, @PathVariable String attemptId,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.retry(practiceId, attemptId, user, role));
    }

    @Operation(summary = "Preview a learning plan or interview action")
    @PostMapping("/actions/preview")
    public ApiResponse<KnowledgeActionPreview> previewAction(@RequestBody KnowledgeActionRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        return ApiResponse.ok(workspace.previewAction(request, user, role));
    }

    @Operation(summary = "Confirm a previously previewed action")
    @PostMapping("/actions/confirm")
    public ApiResponse<KnowledgeActionPreview> confirmAction(@RequestBody KnowledgeActionConfirmRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        if (request == null || request.previewId() == null) return ApiResponse.fail("previewId is required");
        return ApiResponse.ok(workspace.confirmAction(request.previewId(), user, role));
    }

    @Operation(summary = "Download an authorized original knowledge file")
    @GetMapping("/library/{documentId}/original")
    public ResponseEntity<byte[]> original(@PathVariable String documentId,
            @RequestHeader(value = ROLE, required = false) String role,
            @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = HttpHeaders.RANGE, required = false) String range) {
        requireIdentity(user, role);
        KnowledgeCatalogService.OriginalFile file = catalog.readOriginal(documentId, role);
        byte[] bytes = file.bytes();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType(file.contentType()));
        headers.set(HttpHeaders.ACCEPT_RANGES, "bytes");
        headers.setCacheControl("private, no-store");
        headers.setContentDisposition(ContentDisposition.inline().filename(file.fileName(), StandardCharsets.UTF_8).build());
        if (range == null || range.isBlank()) {
            headers.setContentLength(bytes.length);
            return ResponseEntity.ok().headers(headers).body(bytes);
        }
        try {
            List<HttpRange> parsed = HttpRange.parseRanges(range);
            if (parsed.size() != 1 || bytes.length == 0) throw new IllegalArgumentException("One byte range is supported");
            long start = parsed.get(0).getRangeStart(bytes.length);
            long end = parsed.get(0).getRangeEnd(bytes.length);
            if (start >= bytes.length || end < start) throw new IllegalArgumentException("Range is outside file");
            byte[] part = Arrays.copyOfRange(bytes, (int) start, (int) end + 1);
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes " + start + "-" + end + "/" + bytes.length);
            headers.setContentLength(part.length);
            return ResponseEntity.status(206).headers(headers).body(part);
        } catch (IllegalArgumentException ex) {
            headers.set(HttpHeaders.CONTENT_RANGE, "bytes */" + bytes.length);
            return ResponseEntity.status(416).headers(headers).build();
        }
    }

    @Operation(summary = "Edit a knowledge publication as an administrator")
    @PutMapping({"/admin/publications/{documentId}", "/publications/{documentId}"})
    public ApiResponse<KnowledgeLibraryDocument> editPublication(@PathVariable String documentId,
            @RequestBody KnowledgePublicationRequest request, @RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        requireAdmin(user, role);
        return ApiResponse.ok(catalog.adminEdit(documentId, request, user));
    }

    @Operation(summary = "Publish or unpublish a knowledge topic")
    @PostMapping({"/admin/publications/{documentId}/publish", "/publications/{documentId}/publish"})
    public ApiResponse<KnowledgeLibraryDocument> publish(@PathVariable String documentId, @RequestParam(defaultValue = "true") boolean enabled,
            @RequestHeader(value = ROLE, required = false) String role, @RequestHeader(value = USER, required = false) String user) {
        requireAdmin(user, role);
        return ApiResponse.ok(catalog.publish(documentId, enabled));
    }

    @Operation(summary = "Withdraw a knowledge document")
    @PostMapping("/publications/{documentId}/unpublish")
    public ApiResponse<KnowledgeLibraryDocument> unpublish(@PathVariable String documentId,
            @RequestHeader(value = ROLE, required = false) String role, @RequestHeader(value = USER, required = false) String user) {
        requireAdmin(user, role);
        return ApiResponse.ok(catalog.publish(documentId, false));
    }

    @Operation(summary = "Reparse a knowledge source")
    @PostMapping({"/admin/publications/{documentId}/reparse", "/publications/{documentId}/reparse"})
    public ApiResponse<KnowledgeLibraryDocument> reparse(@PathVariable String documentId,
            @RequestHeader(value = ROLE, required = false) String role, @RequestHeader(value = USER, required = false) String user) {
        requireAdmin(user, role);
        return ApiResponse.ok(catalog.reparse(documentId));
    }

    @Operation(summary = "Create an unpublished knowledge document")
    @PostMapping("/publications")
    public ApiResponse<KnowledgeLibraryDocument> createPublication(@RequestBody KnowledgePublicationRequest request,
            @RequestHeader(value = USER, required = false) String user, @RequestHeader(value = ROLE, required = false) String role) {
        requireAdmin(user, role);
        return ApiResponse.ok(catalog.adminEdit("new", request, user));
    }

    @Operation(summary = "List administrator publications including draft versions")
    @GetMapping("/publications")
    public ApiResponse<List<Map<String, Object>>> publications(@RequestHeader(value = USER, required = false) String user,
            @RequestHeader(value = ROLE, required = false) String role) {
        requireAdmin(user, role);
        if (knowledge == null) throw new IllegalStateException("Publication listing is unavailable");
        return ApiResponse.ok(knowledge.list(null, role, 100).stream().map(document -> {
            KnowledgeLibraryDocument library = catalog.library(document.documentId(), role);
            Map<String, Object> value = new java.util.LinkedHashMap<>();
            value.put("documentId", document.documentId()); value.put("title", library.title());
            value.put("content", library.content()); value.put("category", library.category());
            value.put("source", library.source()); value.put("tags", library.tags());
            value.put("roles", document.roles()); value.put("version", library.version()); value.put("revision", library.version());
            value.put("status", library.status()); value.put("createdAt", document.createdAt());
            return value;
        }).toList());
    }

    private static void requireIdentity(String user, String role) {
        if (user == null || user.isBlank() || role == null || !List.of("STUDENT", "ADMIN", "COMPANY").contains(role.toUpperCase()))
            throw new IllegalArgumentException("An authenticated identity is required");
    }
    private static void requireAdmin(String user, String role) {
        requireIdentity(user, role);
        if (!"ADMIN".equalsIgnoreCase(role)) throw new IllegalArgumentException("Administrator role is required");
    }
}
