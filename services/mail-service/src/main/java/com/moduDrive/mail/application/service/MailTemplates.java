package com.moduDrive.mail.application.service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/** Mail bodies live as one HTML file per mail under {@code resources/templates}; this reads them
 * and the images they reference. */
final class MailTemplates {

    /** Referenced from the templates as {@code cid:logo}: the web home page header's icon +
     * "ModuDrive" wordmark (MarketingHeader, Quicksand font), captured at 3x — re-capture it if the
     * brand changes, since mail clients can't load the web font to draw the text themselves. */
    static final byte[] LOGO_PNG = image("logo.png");
    /** Referenced from the templates' warning boxes as {@code cid:warning} (icons.tsx's AlertCircleIcon). */
    static final byte[] WARNING_PNG = image("warning.png");

    private MailTemplates() {
    }

    /** Called once per service at construction — the files never change at runtime. */
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

    /** A PNG under {@code resources/mail-images}, rendered at 3x its display size from the
     * frontend's icons (icons.tsx) so it stays sharp on high-DPI screens. */
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
