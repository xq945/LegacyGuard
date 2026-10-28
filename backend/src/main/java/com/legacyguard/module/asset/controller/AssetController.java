package com.legacyguard.module.asset.controller;

import com.legacyguard.common.api.ApiResponse;
import com.legacyguard.common.api.PageResult;
import com.legacyguard.common.web.ClientIpResolver;
import com.legacyguard.module.asset.dto.AssetContentResponse;
import com.legacyguard.module.asset.dto.AssetResponse;
import com.legacyguard.module.asset.dto.CreateAssetRequest;
import com.legacyguard.module.asset.dto.UpdateAssetRequest;
import com.legacyguard.module.asset.service.AssetService;
import com.legacyguard.security.CurrentUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 数字资产接口。
 *
 * <p><b>所有方法都没有 userId 参数。</b>身份一律来自 {@link CurrentUser}，
 * 这是"不信任前端提交的用户身份"这条规则在资产模块的体现。
 *
 * <p>元数据与明文的接口是分开的：
 * <ul>
 *   <li>{@code GET /{id}} —— 只返回元数据</li>
 *   <li>{@code GET /{id}/content} —— 返回解密后的明文，每次访问都会经过守卫并留痕</li>
 * </ul>
 * 拆开的好处是权限判断集中在唯一一个返回明文的地方，不会出现
 * "列表接口也带出了内容"这类疏漏。
 */
@RestController
@RequestMapping("/api/assets")
@Validated
@Tag(name = "数字资产", description = "资产的增删改查与授权访问")
public class AssetController {

    private final AssetService assetService;
    private final ClientIpResolver clientIpResolver;

    public AssetController(AssetService assetService, ClientIpResolver clientIpResolver) {
        this.assetService = assetService;
        this.clientIpResolver = clientIpResolver;
    }

    @PostMapping
    @Operation(summary = "创建资产", description = "内容将以信封加密方式存储，明文不落库。")
    public ResponseEntity<ApiResponse<AssetResponse>> create(@Valid @RequestBody CreateAssetRequest request) {
        AssetResponse created = assetService.create(CurrentUser.get(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(created));
    }

    @GetMapping
    @Operation(summary = "我的资产列表", description = "只返回元数据，不包含任何密文或明文。")
    public ApiResponse<PageResult<AssetResponse>> list(
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "页码从 1 开始") long page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "每页至少 1 条")
            @Max(value = 100, message = "每页最多 100 条") long size) {
        return ApiResponse.success(assetService.list(CurrentUser.get(), page, size));
    }

    @GetMapping("/{id}")
    @Operation(summary = "资产元数据", description = "所有者或已授权联系人可见。")
    public ApiResponse<AssetResponse> meta(@PathVariable Long id) {
        return ApiResponse.success(assetService.getMeta(CurrentUser.get(), id));
    }

    @GetMapping("/{id}/content")
    @Operation(summary = "读取资产内容", description = """
            返回解密后的明文。系统里唯一返回明文的接口。

            每次调用都会重新校验权限并写入访问流水 —— 授权被撤销后，
            下一次请求立即返回 403，不存在"缓存未过期还能再看一会"的窗口。
            """)
    public ApiResponse<AssetContentResponse> content(@PathVariable Long id, HttpServletRequest httpRequest) {
        String clientIp = clientIpResolver.resolve(httpRequest);
        return ApiResponse.success(assetService.getContent(CurrentUser.get(), id, clientIp));
    }

    @PutMapping("/{id}")
    @Operation(summary = "修改资产", description = "仅所有者可调用；被授权的联系人只能读不能改。")
    public ApiResponse<AssetResponse> update(@PathVariable Long id,
                                             @Valid @RequestBody UpdateAssetRequest request) {
        return ApiResponse.success(assetService.update(CurrentUser.get(), id, request));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除资产", description = "软删除。存在未终结的授权案件时拒绝删除。")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        assetService.delete(CurrentUser.get(), id);
        return ApiResponse.success();
    }
}
