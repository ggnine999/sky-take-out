package com.sky.service;

import com.sky.exception.OrderBusinessException;
import org.junit.jupiter.api.*;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class DeliveryRangeServiceTest {
    DeliveryRangeService service;
    MockRestServiceServer server;
    @BeforeEach void setup() {
        service = new DeliveryRangeService(new RestTemplateBuilder());
        ReflectionTestUtils.setField(service, "ak", "test-ak");
        ReflectionTestUtils.setField(service, "shopAddress", "武汉店铺");
        server = MockRestServiceServer.bindTo((RestTemplate) ReflectionTestUtils.getField(service, "client")).build();
    }

    private void coordinates() {
        server.expect(queryParam("address", "%E6%AD%A6%E6%B1%89%E5%BA%97%E9%93%BA")).andRespond(withSuccess(
                "{\"status\":0,\"result\":{\"location\":{\"lat\":30,\"lng\":114}}}", MediaType.APPLICATION_JSON));
        server.expect(queryParam("address", "%E7%94%A8%E6%88%B7%E5%9C%B0%E5%9D%80")).andRespond(withSuccess(
                "{\"status\":0,\"result\":{\"location\":{\"lat\":31,\"lng\":115}}}", MediaType.APPLICATION_JSON));
    }

    @Test void usesUserResponseAndCorrectOriginParameter() {
        coordinates();
        server.expect(queryParam("origin", "30,114")).andExpect(queryParam("destination", "31,115"))
                .andRespond(withSuccess("{\"status\":0,\"result\":{\"routes\":[{\"distance\":5000}]}}", MediaType.APPLICATION_JSON));
        assertDoesNotThrow(() -> service.check("用户地址"));
        server.verify();
    }

    @Test void rejectsBeyondDeliveryBoundary() {
        coordinates();
        server.expect(anything()).andRespond(withSuccess(
                "{\"status\":0,\"result\":{\"routes\":[{\"distance\":5001}]}}", MediaType.APPLICATION_JSON));
        assertThrows(OrderBusinessException.class, () -> service.check("用户地址"));
        server.verify();
    }

    @Test void missingApiKeyFailsBeforeCallingRemoteService() {
        ReflectionTestUtils.setField(service, "ak", "");
        assertThrows(OrderBusinessException.class, () -> service.check("用户地址"));
        server.verify();
    }
}
