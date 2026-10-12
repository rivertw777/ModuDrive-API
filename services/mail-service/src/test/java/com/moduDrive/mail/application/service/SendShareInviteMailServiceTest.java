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

import java.util.Arrays;
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

    private static boolean hasFileIcon(Map<String, byte[]> images, SendShareInviteMailService.FileIcon expected) {
        return Arrays.equals(images.get("fileIcon"), expected.png);
    }

    @Nested
    @DisplayName("등록된 회원에게 공유 초대 메일 발송을 요청받았을 때")
    class WhenRequestedForAMember {

        @Test
        void sendsHtmlMailContainingGranterAndFileName() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"),
                    eq("[ModuDrive] \"report.pdf\" 항목이 나와 공유되었습니다"),
                    argThat(html -> html.contains("report.pdf") && html.contains("홍길동")
                            && html.contains("owner@modudrive.com")
                            && html.contains("cid:logo") // logo attached inline, referenced by cid
                            && html.contains("cid:fileIcon")
                            && !html.contains("로그인하지 않아도") // member invite: no guest warning
                            && !html.contains("{{")), // no leftover placeholder tokens
                    eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> images.containsKey("logo") && images.containsKey("fileIcon")));
        }

        @Test
        void linksToTheFileItselfWithoutAKey() {
            UUID fileId = UUID.randomUUID();
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", fileId,
                    "홍길동", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    argThat(html -> html.contains("href=\"" + CLIENT_URL + "/files/" + fileId + "\"")
                            && !html.contains("?key=")),
                    eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> !images.containsKey("warning")));
        }

        @Test
        void picksTheFileIconByCategoryFromFileService() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> hasFileIcon(images, SendShareInviteMailService.FileIcon.DOCUMENT)));
        }

        @Test
        void fallsBackToTheOtherIconForAnUnrecognizedCategory() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "archive.zip", false, "ARCHIVE", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> hasFileIcon(images, SendShareInviteMailService.FileIcon.OTHER)));
        }

        @Test
        void picksTheFolderIconForADirectoryShareRegardlessOfCategory() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "team-photos", true, "OTHER", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> hasFileIcon(images, SendShareInviteMailService.FileIcon.FOLDER)));
        }

        @Test
        void escapesAUserSuppliedMessageIntoTheHtmlBody() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", UUID.randomUUID(),
                    "홍길동", "owner@modudrive.com", "<script>alert(1)</script>", null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    argThat(html -> html.contains("&lt;script&gt;") && !html.contains("<script>")),
                    eq("홍길동 (ModuDrive에서 공유)"), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void stillSendsWhenGranterNameAndEmailAreNull() {
            // file-service sends (null, null) when it can't resolve the granter.
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", UUID.randomUUID(),
                    null, null, null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    org.mockito.ArgumentMatchers.any(), contains("ModuDrive 사용자"),
                    org.mockito.ArgumentMatchers.any());
        }

        @Test
        void stripsAnEmbeddedAddressFromTheGranterNameInTheFromDisplayName() {
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", UUID.randomUUID(),
                    "ModuDrive Security <security@modudrive.com>", "owner@modudrive.com", null, null, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
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
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", fileId, "홍길동",
                    "owner@modudrive.com", null, inviteToken, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"),
                    eq("grantee@modudrive.com"), contains("공유"),
                    contains(CLIENT_URL + "/files/" + fileId + "?key=" + inviteToken),
                    eq("홍길동 (ModuDrive에서 공유)"), org.mockito.ArgumentMatchers.any());
        }

        @Test
        void includesTheGuestOnlyNoLoginWarning() {
            UUID fileId = UUID.randomUUID();
            UUID inviteToken = UUID.randomUUID();
            SendShareInviteMailCommand command = new SendShareInviteMailCommand(
                    "grantee@modudrive.com", "report.pdf", false, "DOCUMENT", fileId, "홍길동",
                    "owner@modudrive.com", null, inviteToken, "d1");

            sendShareInviteMailService.sendShareInviteMail(command);

            then(sendMailPort).should().sendHtml(eq("d1"), eq("grantee@modudrive.com"), contains("공유"),
                    contains("로그인하지 않아도"), eq("홍길동 (ModuDrive에서 공유)"),
                    argThat(images -> images.containsKey("warning")));
        }
    }
}
