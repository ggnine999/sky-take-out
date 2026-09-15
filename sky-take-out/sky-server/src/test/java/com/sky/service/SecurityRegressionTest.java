package com.sky.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.context.BaseContext;
import com.sky.entity.Employee;
import com.sky.interceptor.JwtTokenUserInterceptor;
import com.sky.properties.JwtProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.method.HandlerMethod;
import static org.junit.jupiter.api.Assertions.*;

class SecurityRegressionTest {
    @Test void employeeJsonNeverIncludesPasswordHash() throws Exception {
        String json = new ObjectMapper().writeValueAsString(Employee.builder().id(1L).password("secret-hash").build());
        assertFalse(json.contains("password")); assertFalse(json.contains("secret-hash"));
    }
    @Test void rejectsWeakOrSharedJwtSecrets() {
        JwtProperties props = new JwtProperties(); props.setAdminSecretKey("short"); props.setUserSecretKey("short");
        assertThrows(IllegalStateException.class,props::afterPropertiesSet);
        props.setAdminSecretKey("12345678901234567890123456789012"); props.setUserSecretKey(props.getAdminSecretKey());
        assertThrows(IllegalStateException.class,props::afterPropertiesSet);
        props.setUserSecretKey("different-secret-with-at-least-32-bytes");
        assertDoesNotThrow(props::afterPropertiesSet);
    }
    @Test void failedAuthenticationClearsReusedThreadAndCompletionClearsIdentity() throws Exception {
        JwtTokenUserInterceptor interceptor = new JwtTokenUserInterceptor();
        JwtProperties props = new JwtProperties(); props.setUserTokenName("authentication"); props.setUserSecretKey("invalid-unused-key");
        ReflectionTestUtils.setField(interceptor,"jwtProperties",props);
        BaseContext.setCurrentId(99L);
        HandlerMethod handler = new HandlerMethod(this,getClass().getDeclaredMethod("endpoint"));
        MockHttpServletResponse response = new MockHttpServletResponse();
        assertFalse(interceptor.preHandle(new MockHttpServletRequest(),response,handler));
        assertEquals(401,response.getStatus()); assertNull(BaseContext.getCurrentId());
        BaseContext.setCurrentId(1L); interceptor.afterCompletion(null,null,null,null);
        assertNull(BaseContext.getCurrentId());
    }
    public void endpoint() {}
}
