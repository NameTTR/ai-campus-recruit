package com.aicampus.resume.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.minio.BucketExistsArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

class ResumeObjectStorageServiceTest {
    @Test
    void successfulUploadClosesSourceStream() throws Exception {
        verifyStreamClosed(false);
    }

    @Test
    void failedUploadClosesSourceStream() throws Exception {
        verifyStreamClosed(true);
    }

    private void verifyStreamClosed(boolean failUpload) throws Exception {
        MinioClient minio = mock(MinioClient.class);
        when(minio.bucketExists(any(BucketExistsArgs.class))).thenReturn(true);
        if (failUpload) {
            when(minio.putObject(any(PutObjectArgs.class))).thenThrow(new IllegalStateException("storage unavailable"));
        }
        ResumeObjectStorageService storage = new ResumeObjectStorageService(true, "http://minio.test", "test-access", "test-secret", "resumes");
        ReflectionTestUtils.setField(storage, "client", minio);
        TrackedStream stream = new TrackedStream();
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn("resume.pdf");
        when(file.getSize()).thenReturn(1L);
        when(file.getInputStream()).thenReturn(stream);
        assertThat(storage.store("R1", file).storageStatus()).isEqualTo(failUpload ? "FAILED" : "STORED");
        assertThat(stream.closed).isTrue();
    }

    private static class TrackedStream extends ByteArrayInputStream {
        private boolean closed;
        TrackedStream() {
            super(new byte[] {1});
        }
        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }
}