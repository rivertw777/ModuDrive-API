package com.moduDrive.auth.application.port.out;

import com.moduDrive.auth.domain.model.MemberAuthData;
import com.moduDrive.auth.domain.model.MemberAuthData.MemberId;

public interface FetchMemberStatusPort {
    MemberAuthData fetchMemberStatus(MemberId memberId);
}
