package com.aicampus.common.dto;
public record MatchRequirement(String skill, boolean declared, boolean supported, String status, SkillEvidence evidence, String suggestion) {}
