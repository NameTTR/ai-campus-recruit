package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryMatchRecordStore implements MatchRecordStore {
    private final ConcurrentMap<String, MatchResult> matches = new ConcurrentHashMap<>();

    @Override
    public void save(MatchResult match) {
        matches.put(match.matchId(), match);
    }

    @Override
    public List<MatchResult> listAll() {
        return sorted(matches.values().stream().toList());
    }

    @Override
    public List<MatchResult> listByStudent(String studentId) {
        return sorted(matches.values().stream()
                .filter(match -> match.studentId().equals(studentId))
                .toList());
    }

    @Override
    public List<MatchResult> listByJob(String jobId) {
        return sorted(matches.values().stream()
                .filter(match -> match.jobId().equals(jobId))
                .toList());
    }

    private static List<MatchResult> sorted(List<MatchResult> values) {
        return new ArrayList<>(values).stream()
                .sorted(Comparator.comparing(MatchResult::matchId))
                .toList();
    }
}
