package com.bluewhale.auth.starter.controller;

import com.bluewhale.auth.application.model.command.SaveBrandConfigCommand;
import com.bluewhale.auth.application.model.query.BrandPageQuery;
import com.bluewhale.auth.application.model.query.BrandResolveQuery;
import com.bluewhale.auth.application.service.BrandAppService;
import com.bluewhale.auth.interfaces.api.BrandApi;
import com.bluewhale.auth.interfaces.dto.request.BrandConfigSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.BrandConfigResponse;
import com.bluewhale.auth.interfaces.dto.response.BrandResolveResponse;
import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.auth.starter.assemble.BrandAssembler;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 品牌配置接口实现（Web 适配层）：协议适配 + DTO ↔ Command 转换，业务编排在 application 层。
 *
 * @author BlueWhale
 */
@RestController
@RequiredArgsConstructor
public class BrandController implements BrandApi {

    private final BrandAppService brandAppService;
    private final BrandAssembler brandAssembler;

    /**
     * 登录页品牌解析（免登录，SecurityConfig 白名单）
     *
     * @param targetType 作用域类型
     * @param targetId 作用域 ID
     * @param domain 请求域名
     * @return 品牌渲染信息
     */
    @Override
    public Result<BrandResolveResponse> resolve(@RequestParam(required = false) String targetType,
                                                @RequestParam(required = false) Long targetId,
                                                @RequestParam(required = false) String domain) {
        return Result.ok(brandAssembler.toResolveResponse(brandAppService.resolve(
                new BrandResolveQuery(targetType, targetId, domain))));
    }

    /**
     * 分页查询品牌配置
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字
     * @param targetType 作用域类型
     * @param status 状态
     * @return 品牌分页结果
     */
    @Override
    @RequirePermission("sys:brand:list")
    public Result<PageResult<BrandConfigResponse>> page(PageQuery pageQuery,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String targetType,
                                                        @RequestParam(required = false) Integer status) {
        return Result.ok(brandAssembler.toPage(brandAppService.page(new BrandPageQuery(
                pageQuery.getCurrent(), pageQuery.getSize(), keyword, targetType, status))));
    }

    /**
     * 新增品牌配置
     *
     * @param request 保存请求
     * @return 新建品牌
     */
    @Override
    @RequirePermission("sys:brand:add")
    @AuditLog(operation = "新增品牌配置", type = OperateType.CREATE, targetType = "sys_brand_config")
    public Result<BrandConfigResponse> create(@Valid @RequestBody BrandConfigSaveRequest request) {
        SaveBrandConfigCommand command = brandAssembler.toCommand(request);
        return Result.ok(brandAssembler.toResponse(brandAppService.create(command)));
    }

    /**
     * 编辑品牌配置
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的品牌
     */
    @Override
    @RequirePermission("sys:brand:edit")
    @AuditLog(operation = "编辑品牌配置", type = OperateType.UPDATE, targetType = "sys_brand_config",
            targetId = "#id")
    public Result<BrandConfigResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody BrandConfigSaveRequest request) {
        SaveBrandConfigCommand command = brandAssembler.toCommand(request);
        return Result.ok(brandAssembler.toResponse(brandAppService.update(id, command)));
    }

    /**
     * 删除品牌配置（逻辑删除）
     *
     * @param id 主键
     * @return 操作结果
     */
    @Override
    @RequirePermission("sys:brand:delete")
    @AuditLog(operation = "删除品牌配置", type = OperateType.DELETE, targetType = "sys_brand_config",
            targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        brandAppService.delete(id);
        return Result.ok();
    }
}
