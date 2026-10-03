package com.aicampus.ai.service.screening;

import com.aicampus.ai.service.core.RecruitmentContextClient;
import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.dto.JobSummary;
import com.aicampus.common.dto.ResumeSummary;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(prefix = "ai.screening.rocketmq", name = "enabled", havingValue = "true")
public class CandidateScreenEventConsumer implements InitializingBean, DisposableBean {
    private static final Logger log = LoggerFactory.getLogger(CandidateScreenEventConsumer.class);

    private final String nameServer;
    private final String consumerGroup;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final CandidateScreenTaskService taskService;
    private final RecruitmentContextClient recruitmentContextClient;
    private DefaultMQPushConsumer consumer;

    public CandidateScreenEventConsumer(
            @Value("${ai.screening.rocketmq.name-server:127.0.0.1:9876}") String nameServer,
            @Value("${ai.screening.rocketmq.consumer-group:ai-screening-consumer}") String consumerGroup,
            @Value("${ai.screening.rocketmq.topic:delivery-events}") String topic,
            ObjectMapper objectMapper,
            CandidateScreenTaskService taskService,
            RecruitmentContextClient recruitmentContextClient) {
        this.nameServer = nameServer;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
        this.recruitmentContextClient = recruitmentContextClient;
    }

    @Override
    public void afterPropertiesSet() {
        DefaultMQPushConsumer mqConsumer = new DefaultMQPushConsumer(consumerGroup);
        mqConsumer.setNamesrvAddr(nameServer);
        try {
            mqConsumer.subscribe(topic, "*");
            mqConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                return consume(messages);
            });
            mqConsumer.start();
            consumer = mqConsumer;
            log.info("AI screening RocketMQ consumer started, topic={}, nameServer={}", topic, nameServer);
        } catch (Exception ex) {
            mqConsumer.shutdown();
            log.warn("AI screening RocketMQ consumer disabled because startup failed, topic={}, nameServer={}",
                    topic, nameServer, ex);
        }
    }

    private ConsumeConcurrentlyStatus consume(List<MessageExt> messages) {
        boolean retryRequired = false;
        for (MessageExt message : messages) {
            DeliveryEvent event;
            try {
                event = objectMapper.readValue(message.getBody(), DeliveryEvent.class);
            } catch (Exception ex) {
                log.warn("Discarding malformed delivery event for AI screening, messageId={}", message.getMsgId());
                continue;
            }
            if (event == null || !"DELIVERY_CREATED".equals(event.eventType())) {
                continue;
            }
            if (!hasText(event.deliveryId()) || !hasText(event.studentId())
                    || !hasText(event.resumeId()) || !hasText(event.jobId()) || !hasText(event.companyId())) {
                log.warn("Discarding delivery event with missing screening identifiers, messageId={}", message.getMsgId());
                continue;
            }
            try {
                taskService.submitOnce(
                        toCandidateScreenRequest(event),
                        CandidateScreenTaskSource.ROCKETMQ,
                        dedupKey(event));
            } catch (RuntimeException ex) {
                retryRequired = true;
                log.warn("Unable to persist screening task; delivery event will be redelivered, deliveryId={}",
                        event.deliveryId(), ex);
            }
        }
        // Successfully persisted tasks use the delivery dedup key when a batch is redelivered.
        return retryRequired ? ConsumeConcurrentlyStatus.RECONSUME_LATER : ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }

    private CandidateScreenRequest toCandidateScreenRequest(DeliveryEvent event) {
        RecruitmentContextClient.ValidatedContext context = recruitmentContextClient.validate(
                event.studentId(), event.resumeId(), event.jobId(), null, "ADMIN");
        ResumeSummary resume = context.resume();
        JobSummary job = context.job();
        if (resume == null || job == null) {
            throw new IllegalStateException("Candidate screening requires a resume and job snapshot");
        }
        return new CandidateScreenRequest(
                event.deliveryId(),
                event.companyId(),
                event.studentId(),
                event.resumeId(),
                event.jobId(),
                resume.sourceFormat(),
                resume.parseStatus(),
                resume.parsedTextLength(),
                valueOr(job.title(), event.jobId()),
                safeList(context.resumeSkills()),
                safeList(resume.projects()),
                safeList(context.requiredSkills()),
                "Education: " + valueOr(resume.education(), "Unavailable")
                        + "\nSkills: " + String.join(", ", safeList(context.resumeSkills()))
                        + "\nProjects: " + String.join("; ", safeList(resume.projects()))
                        + "\nProfile: " + valueOr(resume.diagnosis(), "Unavailable"),
                valueOr(job.description(), "Job description unavailable"));
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
    private static List<String> safeList(List<String> values) {
        return values == null ? List.of() : values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    private static String dedupKey(DeliveryEvent event) {
        String deliveryId = event.deliveryId();
        if (deliveryId != null && !deliveryId.isBlank()) {
            return "delivery-created:" + deliveryId.trim();
        }
        return event.eventId() == null || event.eventId().isBlank()
                ? null
                : "event:" + event.eventId().trim();
    }

    @Override
    public void destroy() {
        if (consumer != null) {
            consumer.shutdown();
        }
    }
}
