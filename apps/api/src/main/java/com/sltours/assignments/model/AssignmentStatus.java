package com.sltours.assignments.model;
/**
 * SCHEDULED → (collection) → AWAITING_RELEASE (approval required) or RELEASED → (return) → RETURNED → (review) → CLOSED.
 * CANCELLED is reachable only before the vehicle is released.
 */
public enum AssignmentStatus { SCHEDULED, AWAITING_RELEASE, RELEASED, RETURNED, CLOSED, CANCELLED }
