package com.moduDrive.mail.adapter.out.mail;

import com.moduDrive.mail.application.port.out.MailRejectedException;
import java.io.IOException;
import java.net.ServerSocket;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.MailSendException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SmtpMailAdapterTest {

    @Test
    @DisplayName("SMTP 서버에 닿지 못하면 재시도할 수 있는 예외를 그대로 던진다")
    void unreachableServerIsRetryable() throws IOException {
        // given — a port nothing listens on
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        SmtpMailAdapter adapter = new SmtpMailAdapter("localhost", port, "user", "pw", "noreply@modudrive.com");

        // when & then
        assertThatThrownBy(() -> adapter.sendHtml("q_1", "river@modudrive.com", "subject", "<p>body</p>", null, Map.of()))
                .isInstanceOf(MailSendException.class)
                .isNotInstanceOf(MailRejectedException.class);
    }

    @Test
    @DisplayName("주소가 잘못된 메일은 재시도 없이 거절한다")
    void malformedAddressIsRejected() {
        // given
        SmtpMailAdapter adapter = new SmtpMailAdapter("localhost", 25, "user", "pw", "noreply@modudrive.com");

        // when & then
        assertThatThrownBy(() -> adapter.sendHtml("q_1", "not an address", "subject", "<p>body</p>", null, Map.of()))
                .isInstanceOf(MailRejectedException.class);
    }
}
