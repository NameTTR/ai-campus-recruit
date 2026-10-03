package com.aicampus.resume.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.resume.client.AiAnalyzeClient;
import com.aicampus.resume.service.ResumeObjectStorageService;
import com.aicampus.resume.service.ResumeObjectStorageService.StoredResumeObject;
import com.aicampus.resume.service.ResumeTextExtractionService;
import com.aicampus.resume.service.store.ResumeRecord;
import com.aicampus.resume.service.store.ResumeRecordStore;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

class ResumeConsistencyTest {
    private final ResumeObjectStorageService storage = mock(ResumeObjectStorageService.class);
    private final ResumeTextExtractionService extraction = mock(ResumeTextExtractionService.class);
    private final ResumeRecordStore records = mock(ResumeRecordStore.class);
    private final ResumeController controller = new ResumeController(mock(AiAnalyzeClient.class), storage, extraction, records, false);

    @Test
    void databaseFailureCompensatesUploadedObjectAndPreservesOriginalFailure() {
        MockMultipartFile file = file();
        when(extraction.extract(file)).thenReturn("Bachelor degree\nSkills: Java\nProject: Campus platform");
        when(storage.store(anyString(), eq(file))).thenReturn(new StoredResumeObject("uploaded-key", "minio", "STORED"));
        IllegalStateException failure = new IllegalStateException("database unavailable");
        doThrow(failure).when(records).save(any());
        assertThatThrownBy(() -> controller.upload(file, "S1", "STUDENT")).isSameAs(failure);
        verify(storage).delete("uploaded-key");
    }

    @Test
    void failedObjectUploadDoesNotPersistResumeMetadata() {
        MockMultipartFile file = file();
        when(extraction.extract(file)).thenReturn("Skills: Java");
        when(storage.store(anyString(), eq(file))).thenReturn(new StoredResumeObject("key", "minio", "FAILED"));
        assertThatThrownBy(() -> controller.upload(file, "S1", "STUDENT"))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("was not saved");
        verify(records, never()).save(any());
    }

    @Test
    void unsuccessfulDatabaseDeleteDoesNotRemoveObject() {
        when(records.findById("R1")).thenReturn(Optional.of(record()));
        when(records.delete("R1")).thenReturn(false);
        assertThat(controller.delete("R1", "S1", "STUDENT").code()).isNotZero();
        verify(storage, never()).delete(anyString());
    }

    @Test
    void successfulMetadataDeleteCleansStoredObject() {
        when(records.findById("R1")).thenReturn(Optional.of(record()));
        when(records.delete("R1")).thenReturn(true);
        assertThat(controller.delete("R1", "S1", "STUDENT").data()).isTrue();
        verify(storage).delete("uploaded-key");
    }

    private ResumeRecord record() {
        ResumeSummary summary = new ResumeSummary("R1", "S1", "resume.pdf", "Bachelor", List.of(), List.of(), "profile", 40,
                "uploaded-key", "minio", "STORED", "PDF", "TEXT_EXTRACTED", 100);
        return new ResumeRecord(summary, "Skills: Java");
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "resume.pdf", "application/pdf", new byte[] {1});
    }
}