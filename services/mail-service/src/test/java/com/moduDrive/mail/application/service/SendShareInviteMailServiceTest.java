package com.moduDrive.mail.application.service;

import com.moduDrive.mail.application.port.in.command.SendShareInviteMailCommand;
import com.moduDrive.mail.application.port.out.SendMailPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class SendShareInviteMailServiceTest {

    private static final String CLIENT_URL = "http://localhost:3000";

    @Mock
    private SendMailPort sendMailPort;
    private SendShareInviteMailService sendShareInviteMailService;

    @BeforeEach
    void setUp() {
        sendShareInviteMailService = new SendShareInviteMailService(sendMailPort, CLIENT_URL);
    }

    private static boolean fileIconContains(Map<String, String> inlineSvgImages, String snippet) {
        String fileIcon = inlineSvgImages.get("fileIcon");
        return fileIcon != null && fileIcon.contains(snippet);
    }

    @Nested
    @DisplayName("등록된 회원에게 공유 초대 메일 발송을 요청받았을 때")
    class WhenRequestedForAMember {

        @Test
        void sendsHtmlMailContainingGranterAndFileNameButNoRole() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"),
                    eq("[ModuDrive] \"report.pdf\" 항목이 나와 공유되었습니다"),
                    argThat(html -> html.contains("report.pdf") && html.contains("홍길동")
                            && html.contains("owner@modudrive.com")
                            && html.contains("cid:logo") // logo attached inline, referenced by cid
                            && html.contains("cid:fileIcon")
                            && !html.contains("VIEWER") // role display removed
                            && !html.contains("로그인하지 않아도") // member invite: no guest warning
                            && !html.contains("{{")), // no leftover placeholder tokens
                    eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> images.containsKey("logo") && images.containsKey("fileIcon")));
        }

        @Test
        void picksTheFileIconColorByCategoryFromFileService() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            // DOCUMENT -> #3b82f6, matching entry-icon.tsx's category color. The category comes
            // straight from file-service (FileCategory.of), not from mail-service guessing the
            // extension itself.
            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> fileIconContains(images, "#3b82f6")));
        }

        @Test
        void fallsBackToTheOtherIconForAnUnrecognizedCategory() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "archive.zip", false, "ARCHIVE", "VIEWER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            // "ARCHIVE" isn't a category this enum knows (e.g. file-service added it first) —
            // must fall back to OTHER's color rather than blow up the whole mail.
            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> fileIconContains(images, "#94a3b8")));
        }

        @Test
        void picksTheFolderIconForADirectoryShareRegardlessOfCategory() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "team-photos", true, "OTHER", "VIEWER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> fileIconContains(images, "#336fa3")));
        }

        @Test
        void escapesAUserSuppliedMessageIntoTheHtmlBody() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", "<script>alert(1)</script>", null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    argThat(html -> html.contains("&lt;script&gt;") && !html.contains("<script>")),
                    eq("홍길동 (ModuDrive에서 공유)"), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void stillSendsWhenGranterNameAndEmailAreNull() {
            // FindMemberByIdPort#findMemberByIdOrUnknown degrades to (null, null) when
            // member-service can't resolve the granter — the mail must still go out, not crash
            // and get lost to the DLT.
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", UUID.randomUUID(),
                    null, null, null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), contains("ModuDrive 사용자"),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        void stripsAnEmbeddedAddressFromTheGranterNameInTheFromDisplayName() {
            // A signup name is free text with no restriction beyond @NotBlank — without
            // sanitizing it, a name like this would make the From display name look exactly like
            // it's spoofing a different sender address than the mailbox actually used.
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", UUID.randomUUID(),
                    "ModuDrive Security <security@modudrive.com>", "owner@modudrive.com", null, null);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(),
                    argThat(fromDisplayName -> !fromDisplayName.contains("<")
                            && !fromDisplayName.contains("@")
                            && !fromDisplayName.contains(">")),
                    org.mockito.ArgumentMatchers.any());
        }
    }

    @Nested
    @DisplayName("회원이 아닌 이메일로 게스트 공유 초대 메일 발송을 요청받았을 때")
    class WhenRequestedForAGuest {

        @Test
        void sendsMailContainingTheNoLoginLink() {
            UUID fileId = UUID.randomUUID();
            UUID inviteToken = UUID.randomUUID();
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", fileId, "홍길동",
                    "owner@modudrive.com", null, inviteToken);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(
                    eq("grantee@modudrive.com"), contains("공유"),
                    contains(CLIENT_URL + "/files/" + fileId + "?key=" + inviteToken),
                    eq("홍길동 (ModuDrive에서 공유)"), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void includesTheGuestOnlyNoLoginWarning() {
            UUID fileId = UUID.randomUUID();
            UUID inviteToken = UUID.randomUUID();
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", "VIEWER", fileId, "홍길동",
                    "owner@modudrive.com", null, inviteToken);

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("grantee@modudrive.com"), contains("공유"),
                    contains("로그인하지 않아도"), eq("홍길동 (ModuDrive에서 공유)"),
                    org.mockito.ArgumentMatchers.any());
        }
    }
}
