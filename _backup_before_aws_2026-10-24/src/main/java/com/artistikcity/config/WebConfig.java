package com.artistikcity.config;

import com.artistikcity.http.GuardInterceptors;
import com.artistikcity.http.SessionInterceptor;
import com.artistikcity.support.Storage;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Middleware and static file configuration (Laravel's Http/Kernel.php + "storage:link").
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final SessionInterceptor sessionInterceptor;
    private final GuardInterceptors guards;
    private final Storage storage;

    public WebConfig(SessionInterceptor sessionInterceptor, GuardInterceptors guards, Storage storage) {
        this.sessionInterceptor = sessionInterceptor;
        this.guards = guards;
        this.storage = storage;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // "web" group: session, flash, CSRF
        registry.addInterceptor(sessionInterceptor).addPathPatterns("/**");

        // "auth" middleware
        registry.addInterceptor(guards.authenticated())
                .addPathPatterns("/user/**", "/dashboard", "/dashboard/**", "/logout", "/verify-email", "/verify-email/**",
                        "/email/verification-notification", "/confirm-password")
                // public page read by Facebook/Twitter/LinkedIn crawlers
                .excludePathPatterns("/user/certificate/social_share");

        // "guest" middleware
        registry.addInterceptor(guards.guest())
                .addPathPatterns("/login", "/register", "/join", "/forgot-password", "/reset-password", "/reset-password/**");

        // AdminMiddleware
        registry.addInterceptor(guards.admin())
                .addPathPatterns("/admin/**")
                .excludePathPatterns("/admin/login", "/admin/password/**");
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // uploaded files: /storage/uploads/... and /storage/certificates/...
        String location = storage.root().toUri().toString();
        registry.addResourceHandler("/storage/**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/");
        // the student area uses <base href="/user/">, so relative "assets/images/x" requests arrive as /user/assets/...
        registry.addResourceHandler("/user/assets/**")
                .addResourceLocations("classpath:/static/assets/user/", "classpath:/static/assets/");
    }
}
