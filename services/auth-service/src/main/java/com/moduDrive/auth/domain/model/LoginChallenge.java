package com.moduDrive.auth.domain.model;

import com.moduDrive.auth.domain.vo.MemberEmail;

/** A login whose password matched on an unknown device, waiting for its emailed code (spec 004 2-2).
 * Keeps the email the member typed so its attempt count can be cleared once a session is issued. */
public record LoginChallenge(MemberAuthData memberAuthData, MemberEmail memberEmail) {
}
