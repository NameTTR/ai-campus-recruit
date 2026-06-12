package com.aicampus.delivery.service;

import com.aicampus.common.demo.DemoDataFactory;
import com.aicampus.common.dto.DeliveryRecord;
import com.aicampus.common.dto.InterviewSchedule;
import com.aicampus.common.dto.InterviewScheduleRequest;
import com.aicampus.common.enums.InterviewScheduleStatus;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

@Service
public class InterviewScheduleService {
    private final ConcurrentMap<String, InterviewSchedule> schedules = new ConcurrentHashMap<>();
    private final NotificationCenterService notificationCenterService;
    private final DeliveryEventPublisher eventPublisher;

    public InterviewScheduleService(
            NotificationCenterService notificationCenterService,
            DeliveryEventPublisher eventPublisher) {
        this.notificationCenterService = notificationCenterService;
        this.eventPublisher = eventPublisher;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedDefaultSchedules() {
        DemoDataFactory.interviewSchedules().forEach(this::seed);
        seed(new InterviewSchedule(
                "IS-DEMO-001",
                "D003",
                "C001",
                "S003",
                "J002",
                "Java backend technical interview",
                LocalDateTime.now().plusDays(2).withSecond(0).withNano(0),
                45,
                "Online",
                "https://meet.example.com/demo-java-backend",
                "Prepare one backend project and one MySQL troubleshooting case.",
                InterviewScheduleStatus.PROPOSED,
                LocalDateTime.now().minusHours(2),
                LocalDateTime.now().minusHours(2)));
    }

    public InterviewSchedule schedule(InterviewScheduleRequest request, DeliveryRecord delivery, String companyId) {
        LocalDateTime now = LocalDateTime.now();
        InterviewSchedule schedule = new InterviewSchedule(
                "IS-" + UUID.randomUUID().toString().substring(0, 8),
                delivery.deliveryId(),
                companyId,
                delivery.studentId(),
                delivery.jobId(),
                valueOr(request == null ? null : request.title(), "Interview invitation"),
                request == null || request.startTime() == null ? now.plusDays(1).withSecond(0).withNano(0) : request.startTime(),
                request == null || request.durationMinutes() == null ? 30 : request.durationMinutes(),
                valueOr(request == null ? null : request.location(), "Online"),
                valueOr(request == null ? null : request.meetingUrl(), ""),
                valueOr(request == null ? null : request.note(), ""),
                InterviewScheduleStatus.PROPOSED,
                now,
                now);
        schedules.put(schedule.scheduleId(), schedule);
        notificationCenterService.create(
                "STUDENT",
                schedule.studentId(),
                "Interview invitation",
                schedule.companyId() + " scheduled an interview for " + schedule.startTime() + ".",
                "INTERVIEW",
                schedule.scheduleId());
        eventPublisher.publishLifecycleEvent(
                "INTERVIEW_SCHEDULED",
                schedule.deliveryId(),
                schedule.studentId(),
                delivery.resumeId(),
                schedule.jobId(),
                schedule.companyId(),
                delivery.status());
        return schedule;
    }

    public List<InterviewSchedule> listByStudent(String studentId) {
        String filter = valueOr(studentId, "");
        if (filter.isBlank()) {
            return List.of();
        }
        return schedules.values().stream()
                .filter(schedule -> filter.equals(schedule.studentId()))
                .sorted(Comparator.comparing(InterviewSchedule::startTime).reversed())
                .toList();
    }

    public List<InterviewSchedule> listByCompany(String companyId) {
        String filter = valueOr(companyId, "");
        if (filter.isBlank()) {
            return List.of();
        }
        return schedules.values().stream()
                .filter(schedule -> filter.equals(schedule.companyId()))
                .sorted(Comparator.comparing(InterviewSchedule::startTime).reversed())
                .toList();
    }

    public List<InterviewSchedule> listAll() {
        return schedules.values().stream()
                .sorted(Comparator.comparing(InterviewSchedule::startTime).reversed())
                .toList();
    }

    public InterviewSchedule findById(String scheduleId) {
        return schedules.get(scheduleId);
    }

    public InterviewSchedule updateStatus(InterviewSchedule current, InterviewScheduleStatus status) {
        InterviewSchedule updated = new InterviewSchedule(
                current.scheduleId(),
                current.deliveryId(),
                current.companyId(),
                current.studentId(),
                current.jobId(),
                current.title(),
                current.startTime(),
                current.durationMinutes(),
                current.location(),
                current.meetingUrl(),
                current.note(),
                status,
                current.createdAt(),
                LocalDateTime.now());
        schedules.put(updated.scheduleId(), updated);
        notificationCenterService.create(
                "COMPANY",
                updated.companyId(),
                "Interview status changed",
                updated.studentId() + " interview schedule is now " + updated.status() + ".",
                "INTERVIEW",
                updated.scheduleId());
        notificationCenterService.create(
                "STUDENT",
                updated.studentId(),
                "Interview status changed",
                "Your interview schedule is now " + updated.status() + ".",
                "INTERVIEW",
                updated.scheduleId());
        eventPublisher.publishLifecycleEvent(
                "INTERVIEW_STATUS_CHANGED",
                updated.deliveryId(),
                updated.studentId(),
                "",
                updated.jobId(),
                updated.companyId(),
                null);
        return updated;
    }

    private void seed(InterviewSchedule schedule) {
        schedules.putIfAbsent(schedule.scheduleId(), schedule);
    }

    private static String valueOr(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
