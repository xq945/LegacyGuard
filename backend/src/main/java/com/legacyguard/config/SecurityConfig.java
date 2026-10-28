package com.legacyguard.config;

import com.legacyguard.common.web.CorsProperties;
import com.legacyguard.common.web.RequestContext;
import com.legacyguard.security.JwtAuthenticationFilter;
import com.legacyguard.security.RestAccessDeniedHandler;
import com.legacyguard.security.RestAuthenticationEntryPoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 配置。
 *
 * <p>关键决策：
 * <ul>
 *   <li><b>无状态</b>：不使用 Session，身份完全由 JWT 承载</li>
 *   <li><b>关闭 CSRF</b>：没有 Cookie 会话，CSRF 攻击面不存在</li>
 *   <li><b>默认拒绝</b>：{@code anyRequest().denyAll()}，新增接口必须显式放行，
 *       避免"忘了配就自动公开"这类反向失误</li>
 *   <li><b>方法级安全</b>：{@code @EnableMethodSecurity} 供业务层用
 *       {@code @PreAuthorize} 做粗粒度控制；资源级权限仍在 Service 里查库判断</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    /** 无需认证的路径 */
    private static final String[] PUBLIC_PATHS = {
            "/api/auth/**",
            "/actuator/health/**",
            "/actuator/info",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html"
    };

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final RestAuthenticationEntryPoint authenticationEntryPoint;
    private final RestAccessDeniedHandler accessDeniedHandler;
    private final CorsProperties corsProperties;

    public SecurityConfig(JwtAuthenticationFilter jwtAuthenticationFilter,
                          RestAuthenticationEntryPoint authenticationEntryPoint,
                          RestAccessDeniedHandler accessDeniedHandler,
                          CorsProperties corsProperties) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
        this.corsProperties = corsProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource(corsProperties)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(handling -> handling
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler))
                .authorizeHttpRequests(auth -> auth
                        // 预检请求必须放行，否则跨域直接失败
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        // 管理端：角色粗筛。真正的资源级校验在 Service 层
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/**").authenticated()
                        // 兜底：未显式声明的路径一律拒绝
                        .anyRequest().denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * 密码哈希。
     *
     * <p>strength 10 —— 默认值。提高到 12 会让每次登录多耗约 200ms，
     * 对内部系统不值得，且 BCrypt 有 72 字节输入上限（超出部分被截断，需在注册时校验长度）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder(10);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration configuration = new CorsConfiguration();

        // 开发环境放行 Vite 开发服务器；生产环境走同源反向代理，列表为空即可
        List<String> origins = corsProperties.allowedOrigins().isEmpty()
                ? List.of("http://localhost:5173")
                : corsProperties.allowedOrigins();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        // 让前端能读到 requestId，报障时附上
        configuration.setExposedHeaders(List.of(RequestContext.HEADER_NAME));
        // 使用 Bearer Token 而非 Cookie，因此不需要携带凭证
        configuration.setAllowCredentials(false);
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
