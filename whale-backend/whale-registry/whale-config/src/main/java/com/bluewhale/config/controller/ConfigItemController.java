package com.bluewhale.config.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.config.dto.ConfigItemCreateRequest;
import com.bluewhale.config.dto.ConfigItemQueryRequest;
import com.bluewhale.config.dto.ConfigItemRollbackRequest;
import com.bluewhale.config.dto.ConfigItemUpdateRequest;
import com.bluewhale.config.dto.OperatorInfo;
import com.bluewhale.config.service.ConfigItemService;
import com.bluewhale.config.vo.ConfigItemHistoryVO;
import com.bluewhale.config.vo.ConfigItemVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 业务配置项管理接口
 *
 * <p>服务内路径 {@code /config/item/**}；经网关按 {@code Path=/config/**} 规则转发，
 * 外部路径与本文档一致。全部接口返回统一响应体 {@link Result}，受
 * {@code @RequirePermission("sys:config:*")} 保护（由网关权限码头
 * {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定），
 * 写操作均标注 {@link AuditLog} 留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/config/item")
@RequiredArgsConstructor
public class ConfigItemController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "config_item";

    /** 网关注入的用户ID头 */
    private static final String USER_ID_HEADER = "X-User-Id";

    /** 网关注入的用户名头 */
    private static final String USERNAME_HEADER = "X-Username";

    private final ConfigItemService configItemService;

    /**
     * 分页查询配置项
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:config:list")
    public Result<PageResult<ConfigItemVO>> page(ConfigItemQueryRequest request) {
        return Result.ok(configItemService.page(request));
    }

    /**
     * 配置项详情
     *
     * @param id 配置项ID
     * @return 配置项视图对象
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:config:list")
    public Result<ConfigItemVO> detail(@PathVariable Long id) {
        return Result.ok(configItemService.getDetail(id));
    }

    /**
     * 创建配置项
     *
     * @param request   创建请求
     * @param userId    网关注入的用户ID
     * @param username  网关注入的用户名
     * @return 创建后的配置项视图对象
     */
    @PostMapping
    @RequirePermission("sys:config:add")
    @AuditLog(operation = "创建配置项", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<ConfigItemVO> create(@Valid @RequestBody ConfigItemCreateRequest request,
                                       @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
                                       @RequestHeader(value = USERNAME_HEADER, required = false) String username) {
        return Result.ok(configItemService.create(request, OperatorInfo.from(userId, username)));
    }

    /**
     * 更新配置项（值变更版本号 +1）
     *
     * @param id        配置项ID
     * @param request   更新请求
     * @param userId    网关注入的用户ID
     * @param username  网关注入的用户名
     * @return 更新后的配置项视图对象
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:config:edit")
    @AuditLog(operation = "更新配置项", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ConfigItemVO> update(@PathVariable Long id,
                                       @Valid @RequestBody ConfigItemUpdateRequest request,
                                       @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
                                       @RequestHeader(value = USERNAME_HEADER, required = false) String username) {
        return Result.ok(configItemService.update(id, request, OperatorInfo.from(userId, username)));
    }

    /**
     * 删除配置项（逻辑删除，落 DELETE 历史）
     *
     * @param id       配置项ID
     * @param userId   网关注入的用户ID
     * @param username 网关注入的用户名
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:config:delete")
    @AuditLog(operation = "删除配置项", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
                               @RequestHeader(value = USERNAME_HEADER, required = false) String username) {
        configItemService.delete(id, OperatorInfo.from(userId, username));
        return Result.ok();
    }

    /**
     * 配置项变更历史（按版本号倒序）
     *
     * @param id 配置项ID
     * @return 历史列表
     */
    @GetMapping("/{id}/history")
    @RequirePermission("sys:config:list")
    public Result<List<ConfigItemHistoryVO>> history(@PathVariable Long id) {
        return Result.ok(configItemService.listHistory(id));
    }

    /**
     * 回滚配置值到目标版本（产生新版本）
     *
     * @param id        配置项ID
     * @param request   回滚请求
     * @param userId    网关注入的用户ID
     * @param username  网关注入的用户名
     * @return 回滚后的配置项视图对象
     */
    @PostMapping("/{id}/rollback")
    @RequirePermission("sys:config:rollback")
    @AuditLog(operation = "回滚配置项", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<ConfigItemVO> rollback(@PathVariable Long id,
                                         @Valid @RequestBody ConfigItemRollbackRequest request,
                                         @RequestHeader(value = USER_ID_HEADER, required = false) String userId,
                                         @RequestHeader(value = USERNAME_HEADER, required = false) String username) {
        return Result.ok(configItemService.rollback(id, request, OperatorInfo.from(userId, username)));
    }
}
