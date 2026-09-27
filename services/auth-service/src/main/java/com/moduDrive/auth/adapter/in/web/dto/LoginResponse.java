package com.moduDrive.auth.adapter.in.web.dto;

/** {@code verificationRequired}: no session yet — the emailed code for this new device comes next (spec 004 2-1). */
public record LoginResponse(boolean verificationRequired) {
}
