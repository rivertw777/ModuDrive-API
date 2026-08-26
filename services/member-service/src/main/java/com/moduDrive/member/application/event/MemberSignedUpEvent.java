package com.moduDrive.member.application.event;

import java.util.UUID;

public record MemberSignedUpEvent(UUID memberId, String email) {}
