package com.sky.websocket;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.sky.constant.JwtClaimsConstant;
import com.sky.entity.Employee;
import com.sky.mapper.EmployeeMapper;
import com.sky.properties.JwtProperties;
import com.sky.utils.JwtUtil;
import io.jsonwebtoken.Claims;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import java.util.Date;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketServer extends TextWebSocketHandler {
    private final JwtProperties jwt;
    private final EmployeeMapper employees;
    private final Map<String, Connection> connections = new ConcurrentHashMap<>();

    public WebSocketServer(JwtProperties jwt, EmployeeMapper employees) {
        this.jwt = jwt;
        this.employees = employees;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        connections.put(session.getId(), new Connection(
                new ConcurrentWebSocketSessionDecorator(session, 5000, 65536)));
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        Connection connection = connections.get(session.getId());
        if (connection == null) {
            session.close(CloseStatus.POLICY_VIOLATION);
            return;
        }
        try {
            if (message.getPayloadLength() > 8192 || System.currentTimeMillis() - connection.opened > 5000) {
                throw new IllegalArgumentException("Authentication timeout");
            }
            JSONObject authentication = JSON.parseObject(message.getPayload());
            if (!"authenticate".equals(authentication.getString("type"))) {
                throw new IllegalArgumentException("Authentication required");
            }
            Claims claims = JwtUtil.parseJWT(jwt.getAdminSecretKey(), authentication.getString("token"));
            Long employeeId = Long.valueOf(claims.get(JwtClaimsConstant.EMP_ID).toString());
            Employee employee = employees.getById(employeeId);
            Date expires = claims.getExpiration();
            if (employee == null || !Integer.valueOf(1).equals(employee.getStatus())
                    || expires == null || !expires.after(new Date())) {
                throw new IllegalArgumentException("Invalid employee");
            }
            connection.employeeId = employeeId;
            connection.expires = expires;
            connection.session.sendMessage(new TextMessage("{\"type\":\"authenticated\"}"));
        } catch (Exception ex) {
            close(connection);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        connections.remove(session.getId());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
        Connection connection = connections.get(session.getId());
        if (connection != null) {
            close(connection);
        }
    }

    @Scheduled(fixedDelay = 5000)
    public void expireConnections() {
        long now = System.currentTimeMillis();
        for (Connection connection : connections.values()) {
            if ((connection.expires == null && now - connection.opened > 5000)
                    || (connection.expires != null && connection.expires.getTime() <= now)) {
                close(connection);
            }
        }
    }

    public void sendToAllClient(String message) {
        for (Connection connection : connections.values()) {
            if (connection.expires == null) {
                continue;
            }
            try {
                Employee employee = employees.getById(connection.employeeId);
                if (!connection.expires.after(new Date()) || employee == null
                        || !Integer.valueOf(1).equals(employee.getStatus())) {
                    close(connection);
                    continue;
                }
                connection.session.sendMessage(new TextMessage(message));
            } catch (Exception ex) {
                close(connection);
            }
        }
    }

    private void close(Connection connection) {
        connections.remove(connection.session.getId());
        try {
            connection.session.close(CloseStatus.POLICY_VIOLATION);
        } catch (Exception ignored) {
            // The peer may already have disconnected.
        }
    }

    private static class Connection {
        final WebSocketSession session;
        final long opened = System.currentTimeMillis();
        volatile Long employeeId;
        volatile Date expires;

        Connection(WebSocketSession session) {
            this.session = session;
        }
    }
}
