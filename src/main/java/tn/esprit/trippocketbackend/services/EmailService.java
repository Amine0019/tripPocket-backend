package tn.esprit.trippocketbackend.services;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username:}")
    private String fromEmail;

    @Async
    public void sendPasswordResetEmail(String to, String firstName, String resetLink, int expirationMinutes) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            if (fromEmail != null && !fromEmail.isBlank()) {
                helper.setFrom(fromEmail.trim(), "TripPocket");
            }

            helper.setTo(to);
            helper.setSubject("TripPocket - Password Reset");
            helper.setText(buildPasswordResetHtml(firstName, resetLink, expirationMinutes), true);

            log.info("Dispatching reset email from [{}] to [{}]...", fromEmail, to);
            mailSender.send(message);
            log.info("Password reset email successfully accepted by SMTP server for recipient: {}", to);
        } catch (Exception e) {
            log.error("Failed to send password reset email to: {}. Error: {}", to, e.getMessage());
            log.info("Development fallback - Password reset link generated: {}", resetLink);
        }
    }

    private String buildPasswordResetHtml(String firstName, String resetLink, int expirationMinutes) {
        return """
                <!DOCTYPE html>
                <html lang="en">
                <head><meta charset="UTF-8"/></head>
                <body style="margin:0;padding:0;background:#f3f7f6;font-family:'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
                <table width="100%%" cellpadding="0" cellspacing="0" style="background:#f3f7f6;padding:40px 0;">
                  <tr><td align="center">
                    <table width="520" cellpadding="0" cellspacing="0" style="background:#ffffff;border-radius:18px;overflow:hidden;box-shadow:0 10px 30px rgba(18,44,52,0.06);">
                
                      <!-- Header -->
                      <tr><td style="background:#0c1f26;padding:32px 40px;text-align:center;">
                        <span style="display:inline-block;width:42px;height:42px;line-height:42px;background:#ef7d62;border-radius:12px;color:#fff;font-weight:800;font-size:13px;letter-spacing:0.04em;">TP</span>
                        <p style="margin:10px 0 0;color:#ffffff;font-size:18px;font-weight:700;">tripPocket</p>
                      </td></tr>
                
                      <!-- Body -->
                      <tr><td style="padding:36px 40px 28px;">
                        <h1 style="margin:0 0 12px;font-size:24px;color:#1c2c34;">Password Reset</h1>
                        <p style="margin:0 0 20px;color:#6d7c83;font-size:15px;line-height:1.6;">
                          Hi <strong>%s</strong>,<br/><br/>
                          We received a request to reset the password for your TripPocket account.
                          Click the button below to set a new password:
                        </p>
                
                        <div style="text-align:center;margin:28px 0;">
                          <a href="%s"
                             style="display:inline-block;padding:14px 36px;background:#1b7c6e;color:#ffffff;font-size:15px;font-weight:700;border-radius:999px;text-decoration:none;box-shadow:0 10px 24px rgba(27,124,110,0.28);">
                            Reset Password
                          </a>
                        </div>
                
                        <p style="margin:0 0 8px;color:#6d7c83;font-size:13px;line-height:1.5;">
                          If the button doesn't work, copy and paste this link into your browser:
                        </p>
                        <p style="margin:0 0 20px;word-break:break-all;color:#1b7c6e;font-size:13px;">%s</p>
                
                        <p style="margin:0;padding:14px 16px;background:#fef9ea;border:1px solid #f0d87e;border-radius:10px;color:#7c6a14;font-size:13px;line-height:1.5;">
                          ⏱ This link will expire in <strong>%d minutes</strong>.<br/>
                          🔒 If you did not request a password reset, you can safely ignore this email.
                        </p>
                      </td></tr>
                
                      <!-- Footer -->
                      <tr><td style="padding:20px 40px 32px;border-top:1px solid #edf2f1;text-align:center;">
                        <p style="margin:0;color:#a8bfba;font-size:11px;">
                          &copy; TripPocket &mdash; Your travel companion
                        </p>
                      </td></tr>
                
                    </table>
                  </td></tr>
                </table>
                </body>
                </html>
                """.formatted(firstName, resetLink, resetLink, expirationMinutes);
    }

    private String maskEmail(String email) {
        int at = email.indexOf('@');
        if (at <= 2) return "***" + email.substring(at);
        return email.substring(0, 2) + "***" + email.substring(at);
    }
}
