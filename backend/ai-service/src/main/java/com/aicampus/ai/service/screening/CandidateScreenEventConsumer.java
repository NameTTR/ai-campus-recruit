package com.aicampus.ai.service.screening;

import com.aicampus.common.dto.CandidateScreenRequest;
import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.enums.CandidateScreenTaskSource;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
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
    private DefaultMQPushConsumer consumer;

    public CandidateScreenEventConsumer(
            @Value("${ai.screening.rocketmq.name-server:127.0.0.1:9876}") String nameServer,
            @Value("${ai.screening.rocketmq.consumer-group:ai-screening-consumer}") String consumerGroup,
            @Value("${ai.screening.rocketmq.topic:delivery-events}") String topic,
            ObjectMapper objectMapper,
            CandidateScreenTaskService taskService) {
        this.nameServer = nameServer;
        this.consumerGroup = consumerGroup;
        this.topic = topic;
        this.objectMapper = objectMapper;
        this.taskService = taskService;
    }

    @Override
    public void afterPropertiesSet() {
        DefaultMQPushConsumer mqConsumer = new DefaultMQPushConsumer(consumerGroup);
        mqConsumer.setNamesrvAddr(nameServer);
        try {
            mqConsumer.subscribe(topic, "*");
            mqConsumer.registerMessageListener((MessageListenerConcurrently) (messages, context) -> {
                consume(messages);
                return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
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

    private void consume(List<MessageExt> messages) {
        for (MessageExt message : messages) {
            try {
                DeliveryEvent event = objectMapper.readValue(
                        new String(message.getBody(), StandardCharsets.UTF_8),
                        DeliveryEvent.class);
                if (!"DELIVERY_CREATED".equals(event.eventType())) {
                    continue;
                }
                taskService.submitOnce(
                        toCandidateScreenRequest(event),
                        CandidateScreenTaskSource.ROCKETMQ,
                        dedupKey(event));
            } catch (RuntimeException ex) {
                log.warn("Failed to consume delivery event for AI screening", ex);
            } catch (Exception ex) {
                log.warn("Failed to parse delivery event for AI screening", ex);
            }
        }
    }

    private static CandidateScreenRequest toCandidateScreenRequest(DeliveryEvent event) {
        return new CandidateScreenRequest(
                event.deliveryId(),
                event.companyId(),
                event.studentId(),
                event.resumeId(),
                event.jobId(),
                event.resumeSourceFormat(),
                event.resumeParseStatus(),
                event.resumeParsedTextLength(),
                "Java Backend Intern",
                List.of("Java", "Spring Boot", "MySQL", "Redis", "Docker"),
                List.of("Campus recruitment platform"),
                List.of("Java", "Spring Boot", "MySQL", "Redis"),
                "Candidate resume snapshot from delivery event",
                "Auto screening triggered by delivery event");
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
