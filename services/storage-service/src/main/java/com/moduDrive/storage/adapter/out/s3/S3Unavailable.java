package com.moduDrive.storage.adapter.out.s3;

import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.model.S3Exception;

import java.util.function.Predicate;

/** What counts against {@code s3CircuitBreaker} (spec 006 2-4-3): S3 not answering — a timeout or
 * connect failure — or answering 5xx. A 404 or a refused conditional delete is S3 working. */
public class S3Unavailable implements Predicate<Throwable> {

    @Override
    public boolean test(Throwable e) {
        return e instanceof SdkClientException
                || (e instanceof S3Exception s3 && s3.statusCode() >= 500);
    }
}
