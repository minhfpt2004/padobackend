package com.oneforall.padobackend.service;

import com.oneforall.padobackend.exception.EmailDeliveryException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
public class RegistrationEmailService {
    private static final Logger log = LoggerFactory.getLogger(RegistrationEmailService.class);

    private final String provider;
    private final String resendApiKey;
    private final String from;
    private final RestClient resendClient;

    public RegistrationEmailService(
            @Value("${app.email.provider:log}") String provider,
            @Value("${app.email.resend.api-key:}") String resendApiKey,
            @Value("${app.email.from:PADO <onboarding@resend.dev>}") String from,
            @Value("${app.email.resend.url:https://api.resend.com/emails}") String resendUrl
    ) {
        this.provider = provider.trim().toLowerCase(Locale.ROOT);
        this.resendApiKey = resendApiKey.trim();
        this.from = from.trim();
        this.resendClient = RestClient.create(resendUrl);
    }

    public String sendRegistrationOtp(String email, String otp, long expiresInMinutes) {
        if ("log".equals(provider)) {
            log.warn("LOCAL OTP delivery for {}: {} (expires in {} minutes)", email, otp, expiresInMinutes);
            return "LOCAL_LOG";
        }
        if (!"resend".equals(provider)) {
            throw new EmailDeliveryException("Unsupported email provider: " + provider);
        }
        if (resendApiKey.isBlank()) {
            throw new EmailDeliveryException("RESEND_API_KEY is required when EMAIL_PROVIDER=resend");
        }

        Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(email),
                "subject", "[PADO] Mã xác minh đăng ký",
                "html", html(otp, expiresInMinutes)
        );
        try {
            resendClient.post()
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + resendApiKey)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            return "EMAIL";
        } catch (RestClientException exception) {
            log.error("Could not send registration OTP to {} through Resend", email, exception);
            throw new EmailDeliveryException("Could not send the verification email. Please try again later.", exception);
        }
    }

    private String html(String otp, long expiresInMinutes) {
        return """
                <div style="font-family:Arial,sans-serif;max-width:480px;margin:auto;padding:24px;border:1px solid #e2e8f0;border-radius:16px">
                  <h1 style="color:#0369a1;text-align:center">PADO</h1>
                  <p>Bạn vừa yêu cầu tạo tài khoản PADO. Mã xác minh của bạn là:</p>
                  <div style="font-size:32px;font-weight:800;letter-spacing:8px;text-align:center;color:#0284c7;padding:18px;background:#f0f9ff;border-radius:12px">%s</div>
                  <p>Mã có hiệu lực trong %d phút. Không chia sẻ mã này với bất kỳ ai.</p>
                  <p style="color:#64748b;font-size:12px">Nếu bạn không thực hiện yêu cầu này, hãy bỏ qua email.</p>
                </div>
                """.formatted(otp, expiresInMinutes);
    }
}
