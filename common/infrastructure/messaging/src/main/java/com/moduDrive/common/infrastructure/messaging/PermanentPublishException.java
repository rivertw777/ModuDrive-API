package com.moduDrive.common.infrastructure.messaging;

/** The broker rejected this message itself (queue missing, payload too large, bad key): the
 * outbox parks the row instead of retrying it forever and blocking everything behind it. */
public class PermanentPublishException extends RuntimeException {

    public PermanentPublishException(Throwable cause) {
        super(cause);
    }
}
