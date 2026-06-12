package com.aicampus.delivery.service;

import com.aicampus.common.dto.DeliveryEvent;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.enums.DeliveryStatus;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.message.Message;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DeliveryEventPublisher implements DisposableBean {
    private static final int MAX_RECENT_EVENTS = 50;

    private final boolean enabled;
    private final String nameServer;
    private final String producerGroup;
    private final String topic;
    private final ObjectMapper objectMapper;
    private final List<DeliveryEvent> recentEvents = new CopyOnWriteArrayList<>();
    private volatile DefaultMQProducer producer;

    public DeliveryEventPublisher(
            @Value("${delivery.events.rocketmq.enabled:false}") boolean enabled,
            @Value("${delivery.events.rocketmq.name-server:127.0.0.1:9876}") String nameServer,
            @Value("${delivery.events.rocketmq.producer-group:delivery-service-producer}") String producerGroup,
            @Value("${delivery.events.rocketmq.topic:delivery-events}") String topic,
            ObjectMapper objectMapper) {
        this.enabled = enabled;
        this.nameServer = nameServer;
        this.producerGroup = producerGroup;
        this.topic = topic;
        this.objectMapper = objectMapper;
    }

    public DeliveryEvent publish(String eventType, DeliveryRecord record) {
        DeliveryEvent event = newEvent(eventType, record, "PENDING");
        DeliveryEvent result = enabled ? send(event) : withStatus(event, "DISABLED");
        remember(result);
        return result;
    }

    public DeliveryEvent publishLifecycleEvent(
            String eventType,
            String deliveryId,
            String studentId,
            String resumeId,
            String jobId,
            String companyId,
            DeliveryStatus deliveryStatus) {
        DeliveryRecord record = new DeliveryRecord(
                valueOr(deliveryId, "UNKNOWN"),
                valueOr(studentId, ""),
                valueOr(resumeId, ""),
                valueOr(jobId, ""),
                valueOr(companyId, ""),
                deliveryStatus == null ? DeliveryStatus.SUBMITTED : deliveryStatus,
                LocalDateTime.now());
        return publish(eventType, record);
    }

    public List<DeliveryEvent> recentEvents() {
        return List.copyOf(recentEvents);
    }

    private DeliveryEvent send(DeliveryEvent event) {
        try {
            byte[] body = objectMapper.writeValueAsString(event).getBytes(StandardCharsets.UTF_8);
            Message message = new Message(topic, event.eventType(), event.deliveryId(), body);
            SendResult sendResult = producer().send(message, 1500);
            return withStatus(event, sendResult.getSendStatus().name());
        } catch (Exception ex) {
            return withStatus(event, "FAILED");
        }
    }

    private DefaultMQProducer producer() throws Exception {
        DefaultMQProducer current = producer;
        if (current == null) {
            synchronized (this) {
                current = producer;
                if (current == null) {
                    current = new DefaultMQProducer(producerGroup);
                    current.setNamesrvAddr(nameServer);
                    current.start();
                    producer = current;
                }
            }
        }
        return current;
    }

    private void remember(DeliveryEvent event) {
        recentEvents.add(0, event);
        while (recentEvents.size() > MAX_RECENT_EVENTS) {
            recentEvents.remove(recentEvents.size() - 1);
        }
    }

    private static DeliveryEvent newEvent(String eventType, DeliveryRecord record, String publishStatus) {
        return new DeliveryEvent(
                "DE-" + UUID.randomUUID().toString().substring(0, 8),
                eventType,
                record.deliveryId(),
                record.studentId(),
                record.resumeId(),
                record.jobId(),
                record.companyId(),
                record.resumeSourceFormat(),
                record.resumeParseStatus(),
                record.resumeParsedTextLength(),
                record.status(),
                publishStatus,
                LocalDateTime.now());
    }

    private static DeliveryEvent withStatus(DeliveryEvent event, String publishStatus) {
        return new DeliveryEvent(
                event.eventId(),
                event.eventType(),
                event.deliveryId(),
                event.studentId(),
                event.resumeId(),
                event.jobId(),
                event.companyId(),
                event.resumeSourceFormat(),
                event.resumeParseStatus(),
                event.resumeParsedTextLength(),
                event.deliveryStatus(),
                publishStatus,
                event.createdAt());
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    @Override
    public void destroy() {
        DefaultMQProducer current = producer;
        if (current != null) {
            current.shutdown();
        }
    }
}
