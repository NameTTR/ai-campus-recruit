package com.aicampus.ai.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class DashScopeClientReliabilityTest {
    private static final String URL = "http://provider.test";
    private static final String RESPONSE = "{\"choices\":[{\"message\":{\"content\":\"success\"}}]}";

    @Test
    void lateSuccessDoesNotCloseCircuitOpenedByAnotherConcurrentRequest() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        CountDownLatch started = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.expect(requestTo(URL + "/chat/completions")).andRespond(request -> {
            started.countDown();
            try {
                if (!release.await(3, TimeUnit.SECONDS)) {
                    throw new AssertionError("Timed out waiting for concurrent provider failure");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new AssertionError(ex);
            }
            return withSuccess(RESPONSE, MediaType.APPLICATION_JSON).createResponse(request);
        });
        server.expect(requestTo(URL + "/chat/completions")).andRespond(withServerError());
        DashScopeClient client = client(builder.build(), 2);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<String> inFlight = executor.submit(() -> client.complete("system", "user", false));
            assertThat(started.await(3, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> client.complete("system", "user", false)).isInstanceOf(RuntimeException.class);
            release.countDown();
            assertThat(inFlight.get(3, TimeUnit.SECONDS)).isEqualTo("success");
            assertThatThrownBy(() -> client.complete("system", "user", false))
                    .isInstanceOf(IllegalStateException.class).hasMessageContaining("circuit is open");
            server.verify();
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void queuedRequestRechecksCircuitAfterAcquiringConcurrencyPermit() throws Exception {
        RestClient.Builder builder = RestClient.builder().baseUrl(URL);
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DashScopeClient client = client(builder.build(), 1);
        Semaphore limiter = (Semaphore) ReflectionTestUtils.getField(client, "concurrencyLimiter");
        assertThat(limiter).isNotNull();
        limiter.acquire();
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<Throwable> queued = executor.submit(() -> {
                try {
                    client.complete("system", "user", false);
                    return null;
                } catch (Throwable ex) {
                    return ex;
                }
            });
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(2);
            while (!limiter.hasQueuedThreads() && System.nanoTime() < deadline) {
                Thread.yield();
            }
            assertThat(limiter.hasQueuedThreads()).isTrue();
            ReflectionTestUtils.setField(client, "circuitOpenUntil", System.currentTimeMillis() + 30000);
            limiter.release();
            assertThat(queued.get(3, TimeUnit.SECONDS)).isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("circuit is open");
            assertThat(limiter.availablePermits()).isEqualTo(1);
            server.verify();
        } finally {
            executor.shutdownNow();
        }
    }

    private DashScopeClient client(RestClient restClient, int concurrency) {
        return new DashScopeClient("test-key", "model", URL, 0.2, 100,
                Duration.ofSeconds(1), Duration.ofSeconds(1), concurrency,
                Duration.ofSeconds(3), 1, Duration.ofSeconds(30), restClient);
    }
}