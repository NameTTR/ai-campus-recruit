package com.aicampus.common.dto;

public record InterviewDimensionScore(
        String dimension, String label, int score, String explanation) {}
