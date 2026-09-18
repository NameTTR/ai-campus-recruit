package com.aicampus.common.dto;

import java.time.Instant;

public record InterviewSessionAnswer(String questionId, String answer, Instant answeredAt) {
}
