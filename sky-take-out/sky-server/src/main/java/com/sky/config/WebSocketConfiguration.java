package com.sky.config;

import com.sky.websocket.WebSocketServer;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfiguration implements WebSocketConfigurer {
    private final WebSocketServer server;

    public WebSocketConfiguration(WebSocketServer server) {
        this.server = server;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // Keep the default same-origin policy; clients connect via the HTTP reverse proxy.
        registry.addHandler(server, "/ws/*");
    }
}
