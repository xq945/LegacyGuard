package com.legacyguard.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI 文档配置。
 *
 * <p>访问地址（开发环境）：
 * <ul>
 *   <li>Swagger UI：{@code http://localhost:8080/swagger-ui.html}</li>
 *   <li>JSON：{@code http://localhost:8080/v3/api-docs}</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    private static final String SECURITY_SCHEME = "bearerAuth";

    @Bean
    public OpenAPI legacyGuardOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("LegacyGuard API")
                        .version("v1")
                        .description("""
                                数字遗产与账号托管平台接口文档。

                                认证方式：请求头 `Authorization: Bearer <accessToken>`。

                                错误响应统一结构 `{code, message, data, requestId}`，
                                业务码与 HTTP 状态码绑定 —— 401 未登录、403 无权限、
                                409 并发冲突、422 业务规则不满足。
                                """))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME, new SecurityScheme()
                                .name(SECURITY_SCHEME)
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")
                                .description("登录后获得的访问令牌")))
                .addSecurityItem(new SecurityRequirement().addList(SECURITY_SCHEME));
    }
}
