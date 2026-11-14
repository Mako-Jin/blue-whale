package com.bluewhale.form.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.form.model.request.FormQueryRequest;
import com.bluewhale.form.model.request.FormTableDefinitionSaveRequest;
import com.bluewhale.form.service.FormPublishService;
import com.bluewhale.form.service.FormTableDefinitionService;
import com.bluewhale.form.model.vo.FormTableDefinitionVO;
import com.bluewhale.form.model.vo.FormTableRegistryVO;
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
 * 表定义管理接口（三层模型的表定义层：表 + 列聚合）。
 *
 * <p>服务内路径前缀 {@code /form/table-definition}；经网关按 {@code Path=/form/**} 规则转发，外部路径与本文档一致。
 * 全部接口受 {@code @RequirePermission("sys:form:table:*")} 保护
 * （AUTH-06：由网关权限码头 {@code X-Permission-Codes} + starter {@code RequirePermissionInterceptor} 判定）。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/form/v{version}/table-definition", version = "1")
@RequiredArgsConstructor
public class FormTableDefinitionController {

    private final FormTableDefinitionService formTableDefinitionService;

    private final FormPublishService formPublishService;

    /**
     * 新增表定义（含列集合）。
     *
     * @param request 表定义请求参数：tableCode / tableName 必填，body 内嵌 {@code columns[]}
     * @return 表定义视图对象（含列）
     */
    @PostMapping
    @RequirePermission("sys:form:table:add")
    public Result<FormTableDefinitionVO> create(@Valid @RequestBody FormTableDefinitionSaveRequest request) {
        return Result.ok(formTableDefinitionService.create(request));
    }

    /**
     * 更新表定义（整体替换列集合）。
     *
     * @param id 表定义ID
     * @param request 表定义请求参数
     * @return 表定义视图对象（含列）
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:form:table:edit")
    public Result<FormTableDefinitionVO> update(@PathVariable Long id,
                                                @Valid @RequestBody FormTableDefinitionSaveRequest request) {
        return Result.ok(formTableDefinitionService.update(id, request));
    }

    /**
     * 删除表定义（逻辑删除表与其列）。
     *
     * @param id 表定义ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:form:table:delete")
    public Result<Void> delete(@PathVariable Long id) {
        formTableDefinitionService.delete(id);
        return Result.ok();
    }

    /**
     * 表定义详情（含列）。
     *
     * @param id 表定义ID
     * @return 表定义视图对象（含列）
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:form:table:list")
    public Result<FormTableDefinitionVO> detail(@PathVariable Long id) {
        return Result.ok(formTableDefinitionService.getDetail(id));
    }

    /**
     * 分页查询表定义。
     *
     * @param request 查询条件：name 模糊（表名）、code 等值（表编码）、status 等值
     * @return 表定义分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:form:table:list")
    public Result<PageResult<FormTableDefinitionVO>> page(@Valid FormQueryRequest request) {
        return Result.ok(formTableDefinitionService.page(request));
    }

    /**
     * 发布表定义：由列元数据建物理表并登记版本映射。
     *
     * @param id 表定义ID
     * @return 物理表登记视图对象
     */
    @PostMapping("/{id}/publish")
    @RequirePermission("sys:form:table:publish")
    public Result<FormTableRegistryVO> publish(@PathVariable Long id) {
        return Result.ok(formPublishService.publishTableDefinition(id));
    }

    /**
     * 查询表定义已发布的物理表登记列表。
     *
     * @param id 表定义ID
     * @return 登记视图对象列表
     */
    @GetMapping("/{id}/tables")
    @RequirePermission("sys:form:table:list")
    public Result<List<FormTableRegistryVO>> tables(@PathVariable Long id) {
        return Result.ok(formPublishService.listTablesByTableDefinition(id));
    }
}
