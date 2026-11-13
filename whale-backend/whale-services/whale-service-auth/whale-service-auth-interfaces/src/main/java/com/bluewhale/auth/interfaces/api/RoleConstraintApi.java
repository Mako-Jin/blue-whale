package com.bluewhale.auth.interfaces.api;

import com.bluewhale.auth.interfaces.dto.request.RoleConstraintSaveRequest;
import com.bluewhale.auth.interfaces.dto.response.RoleConstraintResponse;
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
 * 角色约束 API 契约（权限中心扩展 M3）：MUTEX 互斥约束 / CARDINALITY 基数约束。
 *
 * @author BlueWhale
 */
@RequestMapping(path = "/role-constraint/v{version}", version = "1")
public interface RoleConstraintApi {

    /**
     * 分页查询角色约束
     *
     * @param pageQuery 分页参数
     * @param keyword 关键字（模糊匹配编码/名称，可空）
     * @param constraintType 类型过滤（MUTEX / CARDINALITY，可空）
     * @return 约束分页结果
     */
    @GetMapping("/page")
    Result<PageResult<RoleConstraintResponse>> page(PageQuery pageQuery,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) String constraintType);

    /**
     * 新增角色约束
     *
     * @param request 保存请求
     * @return 新建约束
     */
    @PostMapping
    Result<RoleConstraintResponse> create(@Valid @RequestBody RoleConstraintSaveRequest request);

    /**
     * 编辑角色约束（约束编码不可变）
     *
     * @param id 主键
     * @param request 保存请求
     * @return 更新后的约束
     */
    @PutMapping("/{id}")
    Result<RoleConstraintResponse> update(@PathVariable Long id,
                                          @Valid @RequestBody RoleConstraintSaveRequest request);

    /**
     * 删除角色约束
     *
     * @param id 主键
     * @return 操作结果
     */
    @DeleteMapping("/{id}")
    Result<Void> delete(@PathVariable Long id);
}
