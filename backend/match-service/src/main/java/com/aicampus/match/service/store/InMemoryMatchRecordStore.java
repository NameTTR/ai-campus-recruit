package com.aicampus.match.service.store;

import com.aicampus.common.dto.MatchResult;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class InMemoryMatchRecordStore implements MatchRecordStore {
    private final ConcurrentMap<String, MatchResult> matches = new ConcurrentHashMap<>();
    private final ConcurrentLinkedDeque<String> matchIds = new ConcurrentLinkedDeque<>();

    @Override
    public void save(MatchResult match) {
        MatchResult existing = matches.put(match.matchId(), match);
        if (existing == null) {
            matchIds.addFirst(match.matchId());
        }
    }

    @Override
    public List<MatchResult> listAll() {
        return orderedMatches();
    }

    @Override
    public List<MatchResult> listByStudent(String studentId) {
        return orderedMatches().stream()
                .filter(match -> match.studentId().equals(studentId))
                .toList();
    }

    @Override
    public List<MatchResult> listByJob(String jobId) {
        return orderedMatches().stream()
                .filter(match -> match.jobId().equals(jobId))
                .toList();
    }

    private List<MatchResult> orderedMatches() {
        return matchIds.stream()
                .map(matches::get)
                .filter(java.util.Objects::nonNull)
                .toList();
    }
}
