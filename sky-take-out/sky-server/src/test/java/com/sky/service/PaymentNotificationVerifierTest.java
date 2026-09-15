package com.sky.service;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.properties.WeChatProperties;
import com.wechat.pay.contrib.apache.httpclient.auth.CertificatesVerifier;
import com.wechat.pay.contrib.apache.httpclient.notification.NotificationHandler;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.server.ResponseStatusException;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PaymentNotificationVerifierTest {
    private static final byte[] API_KEY = "0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8);
    PaymentNotificationVerifier verifier;
    KeyPair keyPair;
    MockHttpServletRequest request;
    String body;

    @BeforeEach void setup() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA"); generator.initialize(2048);
        keyPair = generator.generateKeyPair();
        X509Certificate certificate = mock(X509Certificate.class);
        when(certificate.getSerialNumber()).thenReturn(BigInteger.ONE);
        when(certificate.getPublicKey()).thenReturn(keyPair.getPublic());
        when(certificate.getNotAfter()).thenReturn(Date.from(Instant.now().plusSeconds(3600)));
        WeChatProperties properties = new WeChatProperties(); properties.setAppid("test-app"); properties.setMchid("test-merchant");
        verifier = spy(new PaymentNotificationVerifier(properties));
        doReturn(new NotificationHandler(new CertificatesVerifier(Collections.singletonList(certificate)), API_KEY))
                .when(verifier).createHandler();
        fixture("test-app", Instant.now().getEpochSecond());
    }

    private void fixture(String appId, long timestamp) throws Exception {
        String nonce = "test-nonce12";
        JSONObject transaction = JSON.parseObject("{\"trade_state\":\"SUCCESS\",\"appid\":\"test-app\",\"mchid\":\"test-merchant\","
                + "\"out_trade_no\":\"order-10\",\"transaction_id\":\"wx-10\",\"amount\":{\"currency\":\"CNY\",\"total\":4800}}");
        transaction.put("appid", appId);
        Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
        aes.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(API_KEY, "AES"), new GCMParameterSpec(128, nonce.getBytes(StandardCharsets.UTF_8)));
        aes.updateAAD("transaction".getBytes(StandardCharsets.UTF_8));
        JSONObject resource = new JSONObject();
        resource.put("algorithm", "AEAD_AES_256_GCM"); resource.put("nonce", nonce);
        resource.put("original_type", "transaction");
        resource.put("associated_data", "transaction");
        resource.put("ciphertext", Base64.getEncoder().encodeToString(aes.doFinal(transaction.toJSONString().getBytes(StandardCharsets.UTF_8))));
        JSONObject notification = new JSONObject();
        notification.put("id", "notification-1"); notification.put("create_time", Instant.now().toString());
        notification.put("summary", "payment successful");
        notification.put("event_type", "TRANSACTION.SUCCESS"); notification.put("resource_type", "encrypt-resource");
        notification.put("resource", resource); body = notification.toJSONString() + "\n";
        Signature signer = Signature.getInstance("SHA256withRSA"); signer.initSign(keyPair.getPrivate());
        signer.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
        request = new MockHttpServletRequest();
        request.addHeader("Wechatpay-Timestamp", String.valueOf(timestamp));
        request.addHeader("Wechatpay-Nonce", nonce); request.addHeader("Wechatpay-Serial", "1");
        request.addHeader("Wechatpay-Signature", Base64.getEncoder().encodeToString(signer.sign()));
    }

    @Test void verifiesRsaSignatureAndDecryptsActualAesPayload() throws Exception {
        assertEquals("order-10", verifier.verify(request, body).getString("out_trade_no"));
    }
    @Test void rejectsChangedBytesBeforeAcceptingPayment() {
        assertThrows(ResponseStatusException.class, () -> verifier.verify(request, body.trim()));
    }
    @Test void rejectsWrongMerchantApplicationEvenWithValidSignature() throws Exception {
        fixture("another-app", Instant.now().getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> verifier.verify(request, body));
    }
    @Test void rejectsExpiredSignedNotification() throws Exception {
        fixture("test-app", Instant.now().minusSeconds(600).getEpochSecond());
        assertThrows(ResponseStatusException.class, () -> verifier.verify(request, body));
    }
}
