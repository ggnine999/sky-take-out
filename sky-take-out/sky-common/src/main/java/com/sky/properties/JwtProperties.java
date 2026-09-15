package com.sky.properties;

import lombok.Data;
import org.springframework.beans.factory.InitializingBean;
import java.nio.charset.StandardCharsets;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "sky.jwt")
@Data
public class JwtProperties implements InitializingBean {

    /**
     * 管理端员工生成jwt令牌相关配置
     */
    private String adminSecretKey;
    private long adminTtl;
    private String adminTokenName;

    /**
     * 用户端微信用户生成jwt令牌相关配置
     */
    private String userSecretKey;
    private long userTtl;
    private String userTokenName;

    @Override
    public void afterPropertiesSet() {
        if (adminSecretKey == null || userSecretKey == null
                || adminSecretKey.getBytes(StandardCharsets.UTF_8).length < 32
                || userSecretKey.getBytes(StandardCharsets.UTF_8).length < 32
                || adminSecretKey.equals(userSecretKey)) {
            throw new IllegalStateException("JWT secrets must be distinct random values of at least 32 bytes; configure SKY_JWT_ADMIN_SECRET and SKY_JWT_USER_SECRET");
        }
    }

}
