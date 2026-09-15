package com.sky.websocket;

import com.sky.constant.JwtClaimsConstant;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import com.alibaba.fastjson.JSONObject;
import org.junit.jupiter.api.*;
import org.springframework.web.socket.*;
import java.util.Collections;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class WebSocketServerTest {
    WebSocketServer server;
    EmployeeMapper employees;
    WebSocketSession session;
    JwtProperties properties;

    @BeforeEach void setup() throws Exception {
        properties = new JwtProperties();
        properties.setAdminSecretKey("test-admin-key-at-least-thirty-two-bytes");
        employees = mock(EmployeeMapper.class); session = mock(WebSocketSession.class);
        when(session.getId()).thenReturn("session-1"); when(session.isOpen()).thenReturn(true);
        server = new WebSocketServer(properties, employees);
        server.afterConnectionEstablished(session);
    }

    @Test void anonymousConnectionReceivesNoOrders() throws Exception {
        server.sendToAllClient("private-order");
        verify(session, never()).sendMessage(any());
    }

    @Test void onlyAuthenticatedEnabledEmployeeReceivesOrders() throws Exception {
        when(employees.getById(1L)).thenReturn(Employee.builder().id(1L).status(1).build());
        String token = JwtUtil.createJWT(properties.getAdminSecretKey(), 60000,
                Collections.singletonMap(JwtClaimsConstant.EMP_ID, 1L));
        JSONObject authentication = new JSONObject(); authentication.put("type", "authenticate"); authentication.put("token", token);
        server.handleMessage(session, new TextMessage(authentication.toJSONString()));
        server.sendToAllClient("private-order");
        verify(session).sendMessage(argThat(message -> "private-order".equals(message.getPayload())));
        when(employees.getById(1L)).thenReturn(Employee.builder().id(1L).status(0).build());
        server.sendToAllClient("second-order");
        verify(session, never()).sendMessage(argThat(message -> "second-order".equals(message.getPayload())));
        verify(session).close(CloseStatus.POLICY_VIOLATION);
    }
}
