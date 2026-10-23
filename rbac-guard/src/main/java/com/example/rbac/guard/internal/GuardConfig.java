package com.example.rbac.guard.internal;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** 注册 PermissionInterceptor。JwtAuthFilter 通过 @Component 自动生效。 */
@Configuration
public class GuardConfig implements WebMvcConfigurer {

    private final PermissionInterceptor interceptor;

    public GuardConfig(PermissionInterceptor interceptor) {
        this.interceptor = interceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(interceptor)
                .addPathPatterns("/**")
                .excludePathPatterns("/actuator/**", "/error");
    }
}
