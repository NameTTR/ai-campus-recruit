package com.aicampus.resume.controller;

import com.aicampus.common.api.ApiResponse;
import com.aicampus.common.resume.ResumeWorkspaceModels.*;
import com.aicampus.resume.workspace.WorkspaceException;
import com.aicampus.resume.workspace.WorkspaceService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@RestController
@RequestMapping("/api/resumes")
@CrossOrigin
public class ResumeWorkspaceController {
    private final WorkspaceService service;
    public ResumeWorkspaceController(WorkspaceService service) { this.service = service; }
    private String student(String uid, String role) {
        if (uid == null || uid.isBlank() || role == null || !"STUDENT".equalsIgnoreCase(role.trim()))
            throw new WorkspaceException(org.springframework.http.HttpStatus.UNAUTHORIZED, "Student authentication is required");
        return uid.trim();
    }
    @Operation(summary="Read master profile")
    @GetMapping("/master-profile")
    public ApiResponse<MasterProfile> profile(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role){return ApiResponse.ok(service.getProfile(student(uid,role)));}
    @Operation(summary="Save confirmed master profile")
    @PutMapping("/master-profile")
    public ApiResponse<MasterProfile> save(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@RequestBody ProfileSaveRequest req){return ApiResponse.ok(service.saveProfile(student(uid,role),req));}
    @Operation(summary="Import resume as unconfirmed candidate facts")
    @PostMapping("/master-profile/import")
    public ApiResponse<ImportCandidate> importCandidate(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@RequestBody ImportRequest req){return ApiResponse.ok(service.importCandidate(student(uid,role),req));}
    @Operation(summary="Upload owned profile photo")
    @PostMapping(value="/master-profile/photo",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<PhotoAsset> photo(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@RequestPart("file") MultipartFile file){return ApiResponse.ok(service.savePhoto(student(uid,role),file));}
    @Operation(summary="List verified templates")
    @GetMapping("/templates")
    public ApiResponse<List<TemplateInfo>> templates(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role){student(uid,role);return ApiResponse.ok(service.templates());}
    @Operation(summary="Create role-specific resume draft")
    @PostMapping("/drafts")
    public ApiResponse<ResumeDraft> create(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@RequestBody DraftCreateRequest req){return ApiResponse.ok(service.createDraft(student(uid,role),req));}
    @Operation(summary="List owned drafts")
    @GetMapping("/drafts")
    public ApiResponse<List<ResumeDraft>> list(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role){return ApiResponse.ok(service.listDrafts(student(uid,role)));}
    @Operation(summary="Read owned draft")
    @GetMapping("/drafts/{id}")
    public ApiResponse<ResumeDraft> get(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id){return ApiResponse.ok(service.getDraft(student(uid,role),id));}
    @Operation(summary="Update draft with optimistic revision")
    @PatchMapping("/drafts/{id}")
    public ApiResponse<ResumeDraft> update(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id,@RequestBody DraftUpdateRequest req){return ApiResponse.ok(service.updateDraft(student(uid,role),id,req));}
    @Operation(summary="Generate evidence-linked diagnosis suggestions")
    @PostMapping("/drafts/{id}/diagnose")
    public ApiResponse<ResumeDraft> diagnose(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id){return ApiResponse.ok(service.diagnoseDraft(student(uid,role),id));}
    @Operation(summary="Read immutable draft revisions")
    @GetMapping("/drafts/{id}/revisions")
    public ApiResponse<List<DraftRevision>> revisions(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id){return ApiResponse.ok(service.revisions(student(uid,role),id));}
    @Operation(summary="Apply exact-quote suggestion")
    @PostMapping("/drafts/{id}/suggestions/apply")
    public ApiResponse<ResumeDraft> apply(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id,@RequestBody ApplySuggestionRequest req){return ApiResponse.ok(service.applySuggestion(student(uid,role),id,req));}
    @Operation(summary="Restore a draft revision")
    @PostMapping("/drafts/{id}/restore")
    public ApiResponse<ResumeDraft> restore(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id,@RequestBody RestoreDraftRequest req){return ApiResponse.ok(service.restore(student(uid,role),id,req));}
    @Operation(summary="Create or retry confirmed draft export")
    @PostMapping("/drafts/{id}/exports")
    public ApiResponse<ExportStatus> export(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id,@RequestBody ExportRequest req){return ApiResponse.ok(service.createExport(student(uid,role),id,req));}
    @Operation(summary="Get owned export status and refreshed URL")
    @GetMapping("/exports/{id}")
    public ApiResponse<ExportStatus> exportStatus(@RequestHeader("X-User-Id") String uid,@RequestHeader("X-User-Role") String role,@PathVariable String id){return ApiResponse.ok(service.export(student(uid,role),id));}
    @ExceptionHandler(WorkspaceException.class)
    public ResponseEntity<ApiResponse<Void>> workspaceError(WorkspaceException e){return ResponseEntity.status(e.status()).body(ApiResponse.fail(e.getMessage()));}
}
