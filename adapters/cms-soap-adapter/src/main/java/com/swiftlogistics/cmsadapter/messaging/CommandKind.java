package com.swiftlogistics.cmsadapter.messaging;

/** Which direction the saga is moving, so the reply lands on the right queue. */
public enum CommandKind {

    /** Doing the work. */
    FORWARD,

    /** Undoing work that was already done. */
    COMPENSATION
}
