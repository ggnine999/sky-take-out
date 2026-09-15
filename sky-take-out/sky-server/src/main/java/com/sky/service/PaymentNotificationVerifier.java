package com.sky.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.notification.Notification;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationRequest;
import com.wechat.pay.contrib.apache.httpclient.util.PemUtil;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import javax.servlet.http.HttpServletRequest;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.Objects;

@Component
public class PaymentNotificationVerifier {
    private final WeChatProperties properties;

    public PaymentNotificationVerifier(WeChatProperties properties) {
        this.properties = properties;
    }

    public JSONObject verify(HttpServletRequest request, String body) throws Exception {
        NotificationHandler handler = createHandler();
        try {
            String timestamp = request.getHeader("Wechatpay-Timestamp");
            long seconds = Long.parseLong(timestamp);
            long now = Instant.now().getEpochSecond();
            if (seconds < now - 300 || seconds > now + 300) {
                throw new IllegalArgumentException("Expired notification");
            }
            NotificationRequest signed = new NotificationRequest.Builder()
                    .withSerialNumber(request.getHeader("Wechatpay-Serial"))
                    .withNonce(request.getHeader("Wechatpay-Nonce"))
                    .withTimestamp(timestamp)
                    .withSignature(request.getHeader("Wechatpay-Signature"))
                    .withBody(body).build();
            Notification notification = handler.parse(signed);
            JSONObject transaction = JSON.parseObject(notification.getDecryptData());
            if (!"TRANSACTION.SUCCESS".equals(notification.getEventType())
                    || !"SUCCESS".equals(transaction.getString("trade_state"))
                    || !Objects.equals(properties.getAppid(), transaction.getString("appid"))
                    || !Objects.equals(properties.getMchid(), transaction.getString("mchid"))
                    || transaction.getString("out_trade_no") == null
                    || transaction.getString("transaction_id") == null
                    || transaction.getJSONObject("amount") == null
                    || !"CNY".equals(transaction.getJSONObject("amount").getString("currency"))) {
                throw new IllegalArgumentException("Unexpected payment notification");
            }
            int cents = transaction.getJSONObject("amount").getBigDecimal("total").intValueExact();
            if (cents <= 0) {
                throw new IllegalArgumentException("Invalid amount");
            }
            return transaction;
        } catch (Exception ex) {
            // Never acknowledge an unauthenticated callback or expose payment plaintext in logs.
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid payment notification", ex);
        }
    }

    NotificationHandler createHandler() throws Exception {
        // Reload on each callback so replacing the platform certificate does not require a restart.
        try (FileInputStream input = new FileInputStream(properties.getWeChatPayCertFilePath())) {
            X509Certificate certificate = PemUtil.loadCertificate(input);
            certificate.checkValidity();
            return new NotificationHandler(new CertificatesVerifier(Collections.singletonList(certificate)),
                    properties.getApiV3Key().getBytes(StandardCharsets.UTF_8));
        }
    }
}
