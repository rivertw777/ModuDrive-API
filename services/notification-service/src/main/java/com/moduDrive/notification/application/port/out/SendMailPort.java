package com.moduDrive.notification.application.port.out;

public interface SendMailPort {
    void send(String to, String subject, String body);
}
