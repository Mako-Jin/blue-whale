package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.PermissionStrategySaveRequest;
import com.bluewhale.auth.interfaces.dto.response.PermissionStrategyResponse;
import com.bluewhale.common.core.query.PageQuery;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 权限策略 API 契约（权限中心扩展 M3）：GRANT / DENY 策略按角色 × 资源维度下发附加条件。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/perm-strategy/v{version}", version = "1")
public interface PermissionStrategyApi {

    /**
     * 分页查询权限策略
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字（模糊匹配编码/名称，可空）
     * @param strategyType 类型过滤（GRANT / DENY，可空）
     * @return 策略分页结果
     */
    @GetMapping("/page")
    Result<PageResult<PermissionStrategyResponse>> page(PageQuery pageQuery,
                                                        @RequestParam(required = false) String keyword,
                                                        @RequestParam(required = false) String strategyType);

    /**
     * 新增权限策略
     *
     * @param request 保存请求
     * @return 新建策略
     */
    @PostMapping
    Result<PermissionStrategyResponse> create(@Valid @RequestBody PermissionStrategySaveRequest request);

    /**
     * 编辑权限策略（策略编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的策略
     */
    @PutMapping("/{id}")
    Result<PermissionStrategyResponse> update(@PathVariable Long id,
                                              @Valid @RequestBody PermissionStrategySaveRequest request);

    /**
     * 删除权限策略
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);

    /**
     * 设置为默认策略（同租户仅一条，其余自动取消）
     *
     * @param id 主键
     * @return 设置后的策略
     */
    @PutMapping("/{id}/default")
    Result<PermissionStrategyResponse> setDefault(@PathVariable Long id);
}
