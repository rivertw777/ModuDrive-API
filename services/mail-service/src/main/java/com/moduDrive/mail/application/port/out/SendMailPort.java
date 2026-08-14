package com.moduDrive.mail.application.port.out;

public interface SendMailPort {
    void send(String to, String subject, String body);
}
