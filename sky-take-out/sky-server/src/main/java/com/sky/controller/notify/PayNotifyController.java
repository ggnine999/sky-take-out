package com.sky.controller.notify;

import com.alibaba.fastjson.JSONObject;
import com.sky.service.OrderService;
import com.sky.service.PaymentNotificationVerifier;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.util.StreamUtils;

import javax.servlet.http.HttpServletRequest;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Map;

@RestController
@RequestMapping("/notify")
public class PayNotifyController {
    private final OrderService orderService;
    private final PaymentNotificationVerifier verifier;

    public PayNotifyController(OrderService orderService, PaymentNotificationVerifier verifier) {
        this.orderService = orderService;
        this.verifier = verifier;
    }

    @PostMapping(value = "/paySuccess", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> paySuccessNotify(HttpServletRequest request) throws Exception {
        // Signature verification must use the exact received bytes, including trailing newlines.
        String body = StreamUtils.copyToString(request.getInputStream(), StandardCharsets.UTF_8);
        JSONObject transaction = verifier.verify(request, body);
        BigDecimal paid = transaction.getJSONObject("amount").getBigDecimal("total").movePointLeft(2);
        orderService.paySuccess(transaction.getString("out_trade_no"), paid);
        return Collections.singletonMap("code", "SUCCESS");
    }
}
