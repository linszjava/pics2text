package com.pics2text.api.config;

import cn.dev33.satoken.interceptor.SaInterceptor;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.pics2text.api.application.UserService;
import com.pics2text.identity.domain.Permission;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class SecurityConfiguration implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new SaInterceptor(handle -> StpUtil.checkLogin()))
                .addPathPatterns("/api/**")
                .excludePathPatterns("/api/v1/auth/login", "/api/v1/auth/bootstrap");
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(12);
    }

    @Bean
    StpInterface stpInterface(UserService userService) {
        return new StpInterface() {
            @Override
            public List<String> getPermissionList(Object loginId, String loginType) {
                return userService.findById(loginId.toString())
                        .filter(user -> user.active() && user.administrator())
                        .map(user -> Permission.adminCodes())
                        .orElseGet(List::of);
            }

            @Override
            public List<String> getRoleList(Object loginId, String loginType) {
                return userService.findById(loginId.toString())
                        .filter(user -> user.active())
                        .map(user -> List.of(user.globalRole().name()))
                        .orElseGet(List::of);
            }
        };
    }
}
