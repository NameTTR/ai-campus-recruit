package com.aicampus.common.dto;

import java.time.Instant;

public record InterviewTimer(Instant startedAt, Instant pausedAt, long accumulatedSeconds,
        Integer timerMinutes, boolean timeoutReached, long pausedSeconds, Instant runningSince) {}
