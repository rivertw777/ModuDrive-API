package com.moduDrive.common.infrastructure.messaging;

/** The broker rejected this message itself (destination missing, payload too large, bad key): the
 * outbox parks the row instead of retrying it forever and blocking everything behind it. */
public class PermanentPublishException extends RuntimeException {

    public PermanentPublishException(Throwable cause) {
        super(cause);
    }
}
