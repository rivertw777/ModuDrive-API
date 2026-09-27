package com.moduDrive.auth.domain.vo;

/** Ties the emailed code to the login that asked for it; travels only in its cookie (spec 004 2-1). */
public record LoginChallengeId(String value) {

    @Override
    public String toString() {
        return "LoginChallengeId[***]";
    }
}
