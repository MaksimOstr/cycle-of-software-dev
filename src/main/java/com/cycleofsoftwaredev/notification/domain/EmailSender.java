package com.cycleofsoftwaredev.notification.domain;

/** Port for sending emails; the SMTP adapter is work item SHOP-43. */
public interface EmailSender {

    void send(EmailMessage message);
}
