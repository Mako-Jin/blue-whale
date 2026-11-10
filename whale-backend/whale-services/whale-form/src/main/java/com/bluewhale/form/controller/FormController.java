package com.bluewhale.form.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.form.dto.FormDefinitionCopyRequest;
import com.bluewhale.form.dto.FormDefinitionSaveRequest;
import com.bluewhale.form.dto.FormQueryRequest;
import com.bluewhale.form.service.FormDefinitionService;
import com.bluewhale.form.service.FormPublishService;
import com.bluewhale.form.vo.FormDefinitionVO;
import com.bluewhale.form.vo.FormDefinitionVersionVO;
import com.bluewhale.form.vo.FormTableRegistryVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 表单定义管理接口。
 *
 * <p>服务内路径前缀 {@code /form}；经网关按 {@code Path=/form/**} 规则转发，外部路径与本文档一致。
 * 全部接口受 {@code @RequirePermission("sys:form:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/form")
@RequiredArgsConstructor
public class FormController {

    private final FormDefinitionService formDefinitionService;

    private final FormPublishService formPublishService;

    /**
     * 新增表单定义。
     *
     * @param request 表单定义请求参数：code / name 必填
     * @return 表单定义视图对象
     */
    @PostMapping("/definition")
    @RequirePermission("sys:form:add")
    public Result<FormDefinitionVO> create(@Valid @RequestBody FormDefinitionSaveRequest request) {
        return Result.ok(formDefinitionService.create(request));
    }

    /**
     * 更新表单定义。
     *
     * @param id 表单定义ID
     * @param request 表单定义请求参数：code / name 必填
     * @return 表单定义视图对象
     */
    @PutMapping("/definition/{id}")
    @RequirePermission("sys:form:edit")
    public Result<FormDefinitionVO> update(@PathVariable Long id,
                                           @Valid @RequestBody FormDefinitionSaveRequest request) {
        return Result.ok(formDefinitionService.update(id, request));
    }

    /**
     * 删除表单定义（逻辑删除）。
     *
     * @param id 表单定义ID
     * @return 空结果
     */
    @DeleteMapping("/definition/{id}")
    @RequirePermission("sys:form:delete")
    public Result<Void> delete(@PathVariable Long id) {
        formDefinitionService.delete(id);
        return Result.ok();
    }

    /**
     * 表单定义详情。
     *
     * @param id 表单定义ID
     * @return 表单定义视图对象
     */
    @GetMapping("/definition/{id}")
    @RequirePermission("sys:form:list")
    public Result<FormDefinitionVO> detail(@PathVariable Long id) {
        return Result.ok(formDefinitionService.getDetail(id));
    }

    /**
     * 分页查询表单定义。
     *
     * @param request 查询条件：name 模糊、code 等值、status 等值
     * @return 表单定义分页结果
     */
    @GetMapping("/definition/page")
    @RequirePermission("sys:form:list")
    public Result<PageResult<FormDefinitionVO>> page(FormQueryRequest request) {
        return Result.ok(formDefinitionService.page(request));
    }

    /**
     * 发布表单定义：解析 schema 建物理表并登记版本映射。
     *
     * @param id 表单定义ID
     * @return 物理表登记视图对象
     */
    @PostMapping("/definition/{id}/publish")
    @RequirePermission("sys:form:publish")
    public Result<FormTableRegistryVO> publish(@PathVariable Long id) {
        return Result.ok(formPublishService.publish(id));
    }

    /**
     * 查询表单定义已发布的物理表登记列表。
     *
     * @param id 表单定义ID
     * @return 登记视图对象列表
     */
    @GetMapping("/definition/{id}/tables")
    @RequirePermission("sys:form:list")
    public Result<List<FormTableRegistryVO>> tables(@PathVariable Long id) {
        return Result.ok(formPublishService.listTables(id));
    }

    /**
     * 复制表单定义（含字段配置与选项）。
     *
     * @param id 源表单定义ID
     * @param request 复制请求（可为空，编码/名称由服务端生成）
     * @return 新表单定义视图对象
     */
    @PostMapping("/definition/{id}/copy")
    @RequirePermission("sys:form:add")
    public Result<FormDefinitionVO> copy(@PathVariable Long id,
                                         @Valid @RequestBody(required = false) FormDefinitionCopyRequest request) {
        return Result.ok(formDefinitionService.copy(id, request));
    }

    /**
     * 查询表单定义的版本快照列表（按版本号降序）。
     *
     * @param id 表单定义ID
     * @return 版本快照视图对象列表
     */
    @GetMapping("/definition/{id}/versions")
    @RequirePermission("sys:form:list")
    public Result<List<FormDefinitionVersionVO>> versions(@PathVariable Long id) {
        return Result.ok(formDefinitionService.listVersions(id));
    }

    /**
     * 回滚表单定义到指定版本（回滚本身产生新版本）。
     *
     * @param id 表单定义ID
     * @param version 目标版本号
     * @return 回滚后的表单定义视图对象
     */
    @PostMapping("/definition/{id}/rollback/{version}")
    @RequirePermission("sys:form:edit")
    public Result<FormDefinitionVO> rollback(@PathVariable Long id, @PathVariable Integer version) {
        return Result.ok(formDefinitionService.rollback(id, version));
    }
}
