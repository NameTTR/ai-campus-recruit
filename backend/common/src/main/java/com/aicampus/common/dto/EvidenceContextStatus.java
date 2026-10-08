package com.aicampus.common.dto;

/**
 * Describes whether an analysis can still be trusted against the current
 * recruitment workspace.  The values are deliberately stable wire values so
 * older clients can safely ignore the field.
 */
public enum EvidenceContextStatus {
    CURRENT,
    STALE,
    INCOMPLETE,
    SOURCE_UNAVAILABLE
}
