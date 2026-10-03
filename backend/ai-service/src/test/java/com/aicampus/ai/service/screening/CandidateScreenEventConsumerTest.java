package com.aicampus.ai.service.screening;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.aicampus.ai.service.core.RecruitmentContextClient;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.aicampus.common.enums.DeliveryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.common.message.MessageExt;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

class CandidateScreenEventConsumerTest {
    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    private final CandidateScreenTaskService tasks = mock(CandidateScreenTaskService.class);
    private final RecruitmentContextClient context = mock(RecruitmentContextClient.class);
    private final CandidateScreenEventConsumer consumer = new CandidateScreenEventConsumer(
            "localhost:9876", "test", "events", mapper, tasks, context);

    @Test
    void screeningUsesCandidateEvidenceRatherThanInventingRequiredSkills() throws Exception {
        ResumeSummary resume = new ResumeSummary("R1", "S1", "resume.pdf", "Bachelor", List.of("Java"),
                List.of("Student project"), "Extracted profile", 60, "key", "minio", "STORED", "PDF", "TEXT_EXTRACTED", 200);
        JobSummary job = new JobSummary("J1", "C1", "Company", "Backend Engineer", "City", "Salary",
                List.of("Java", "Redis"), "Actual job description", "Summary");
        when(context.validate("S1", "R1", "J1", null, "ADMIN")).thenReturn(
                new RecruitmentContextClient.ValidatedContext(resume, job, null, List.of("Java"),
                        List.of("Java", "Redis"), List.of("Redis")));

        assertThat(consume(message(event()))).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);

        ArgumentCaptor<CandidateScreenRequest> request = ArgumentCaptor.forClass(CandidateScreenRequest.class);
        verify(tasks).submitOnce(request.capture(), eq(CandidateScreenTaskSource.ROCKETMQ), eq("delivery-created:D1"));
        assertThat(request.getValue().skills()).containsExactly("Java");
        assertThat(request.getValue().jobRequirements()).containsExactly("Java", "Redis");
        assertThat(request.getValue().projects()).containsExactly("Student project");
        assertThat(request.getValue().targetRole()).isEqualTo("Backend Engineer");
        assertThat(request.getValue().resumeSummary()).contains("Bachelor", "Extracted profile");
    }

    @Test
    void transientContextFailureAsksBrokerToRedeliver() throws Exception {
        when(context.validate("S1", "R1", "J1", null, "ADMIN")).thenThrow(new IllegalArgumentException("service unavailable"));
        when(context.loadJobForInternal("J1")).thenThrow(new IllegalArgumentException("service unavailable"));
        assertThat(consume(message(event()))).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
        verifyNoInteractions(tasks);
    }

    @Test
    void taskPersistenceFailureAsksBrokerToRedeliver() throws Exception {
        ResumeSummary resume = new ResumeSummary("R1", "S1", "resume.pdf", "Bachelor", List.of(), List.of(),
                "profile", 40, "key", "minio", "STORED", "PDF", "TEXT_EXTRACTED", 200);
        JobSummary job = new JobSummary("J1", "C1", "Company", "Role", "City", "Salary", List.of(), "Description", "Summary");
        when(context.validate("S1", "R1", "J1", null, "ADMIN")).thenReturn(
                new RecruitmentContextClient.ValidatedContext(resume, job, null, List.of(), List.of(), List.of()));
        when(tasks.submitOnce(any(), any(), any())).thenThrow(new IllegalStateException("database unavailable"));
        assertThat(consume(message(event()))).isEqualTo(ConsumeConcurrentlyStatus.RECONSUME_LATER);
    }

    @Test
    void malformedPayloadDoesNotBlockValidEventsForever() {
        MessageExt message = new MessageExt();
        message.setBody("invalid json".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(consume(message)).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verifyNoInteractions(context, tasks);
    }

    @Test
    void unrelatedEventIsAcknowledgedWithoutSubmittingScreening() throws Exception {
        DeliveryEvent event = new DeliveryEvent("E2", "DELIVERY_VIEWED", "D1", "S1", "R1", "J1", "C1",
                DeliveryStatus.VIEWED, "PUBLISHED", null);
        assertThat(consume(message(event))).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verifyNoInteractions(context, tasks);
    }

    @Test
    void missingIdentifiersAreDiscardedWithoutSubmittingUnscopedTask() throws Exception {
        DeliveryEvent event = new DeliveryEvent("E3", "DELIVERY_CREATED", "D1", null, null, null, null,
                DeliveryStatus.SUBMITTED, "PUBLISHED", null);
        assertThat(consume(message(event))).isEqualTo(ConsumeConcurrentlyStatus.CONSUME_SUCCESS);
        verifyNoInteractions(context, tasks);
    }

    private Object consume(MessageExt message) {
        return ReflectionTestUtils.invokeMethod(consumer, "consume", List.of(message));
    }

    private MessageExt message(DeliveryEvent event) throws Exception {
        MessageExt message = new MessageExt();
        message.setBody(mapper.writeValueAsBytes(event));
        return message;
    }

    private DeliveryEvent event() {
        return new DeliveryEvent("E1", "DELIVERY_CREATED", "D1", "S1", "R1", "J1", "C1",
                "PDF", "TEXT_EXTRACTED", 200, DeliveryStatus.SUBMITTED, "PUBLISHED", null);
    }
}
