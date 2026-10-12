package com.moduDrive.mail.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Loads the mail templates under {@code resources/templates} and the images they reference. */
final class MailTemplates {

    /** {@code cid:logo}: the web MarketingHeader icon + wordmark (Quicksand font) captured at 3x. Mail
     * clients can't load the web font, so re-capture it if the brand changes. */
    static final byte[] LOGO_PNG = image("logo.png");
    /** Referenced from the templates' warning boxes as {@code cid:warning} (icons.tsx's AlertCircleIcon). */
    static final byte[] WARNING_PNG = image("warning.png");

    private MailTemplates() {
    }

    static String load(String path) {
        try (InputStream in = MailTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing mail template: " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A PNG under {@code resources/mail-images}, rendered at 3x from the web icons for high-DPI screens. */
    static byte[] image(String name) {
        String path = "/mail-images/" + name;
        try (InputStream in = MailTemplates.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing mail image: " + path);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
