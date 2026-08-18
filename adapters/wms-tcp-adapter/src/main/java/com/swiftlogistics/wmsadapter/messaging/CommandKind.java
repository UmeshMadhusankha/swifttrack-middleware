package com.swiftlogistics.wmsadapter.messaging;

/**
 * Which direction the saga is moving.
 *
 * The orchestrator listens on different queues for the two, so the reply has to
 * say which one it is answering.
 */
public enum CommandKind {

    /** Doing the work. */
    FORWARD,

    /** Undoing work that was already done. */
    COMPENSATION
}
