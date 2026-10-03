package com.aicampus.common.dto;

import java.util.List;

/** A factual excerpt whose text and source quote have both been verified against readable material. */
public record KnowledgeAnswerClaim(String text, List<String> citationIds, String supportQuote) {}
