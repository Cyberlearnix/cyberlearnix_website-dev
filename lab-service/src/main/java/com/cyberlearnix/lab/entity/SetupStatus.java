package com.cyberlearnix.lab.entity;

/**
 * Lifecycle states for the pre-installation build pipeline.
 *
 * NOT_CONFIGURED  → script not yet saved
 * BUILDING        → async build in progress
 * STAGED          → build succeeded; staged image ready, not yet published
 * ACTIVE          → staged image promoted to active; students use this image
 * FAILED          → last build attempt failed; previous active image (if any) is untouched
 */
public enum SetupStatus {
    NOT_CONFIGURED,
    BUILDING,
    STAGED,
    ACTIVE,
    FAILED
}
