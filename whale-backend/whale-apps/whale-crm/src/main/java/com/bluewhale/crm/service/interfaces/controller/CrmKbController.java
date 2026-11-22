package com.bluewhale.crm.service.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.common.CrmPermissions;
import com.bluewhale.crm.interfaces.support.CrmActorContext;
import com.bluewhale.crm.interfaces.support.PageParams;
import com.bluewhale.crm.service.application.command.KbRefCommand;
import com.bluewhale.crm.service.application.service.KbAppService;
import com.bluewhale.crm.service.application.view.KbRefView;
import com.bluewhale.crm.service.domain.model.KbRefQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 知识引用接口（正文归 whale-doc，CRM 只维护元数据与命中）。
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/crm/v{version}/kb", version = "1")
@RequiredArgsConstructor
public class CrmKbController {

    private final KbAppService kbAppService;

    /**
     * 分页查询知识引用。
     *
     * @param keyword 关键字
     * @param category 分类
     * @param visibleScope 可见范围
     * @param status 状态
     * @param current 当前页
     * @param pageNum 当前页（兼容参数）
     * @param size 每页条数
     * @param pageSize 每页条数（兼容参数）
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(CrmPermissions.KB_LIST)
    public Result<PageResult<KbRefView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "visibleScope", required = false) String visibleScope,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        KbRefQuery query = new KbRefQuery();
        query.setKeyword(keyword);
        query.setCategory(category);
        query.setVisibleScope(visibleScope);
        query.setStatus(status);
        query.setCurrent((int) PageParams.current(current, pageNum));
        query.setSize((int) PageParams.size(size, pageSize));
        return Result.ok(kbAppService.page(query, CrmActorContext.from(request)));
    }

    /**
     * 标题 / 标签检索启用中的知识引用。
     *
     * @param keyword 关键字
     * @param request 当前请求
     * @return 视图列表
     */
    @GetMapping("/search")
    @RequirePermission(CrmPermissions.KB_LIST)
    public Result<List<KbRefView>> search(@RequestParam(value = "keyword", required = false) String keyword,
                                          HttpServletRequest request) {
        return Result.ok(kbAppService.search(keyword, CrmActorContext.from(request)));
    }

    /**
     * 查看知识引用详情。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(CrmPermissions.KB_LIST)
    public Result<KbRefView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(kbAppService.detail(id, CrmActorContext.from(request)));
    }

    /**
     * 新建知识引用。
     *
     * @param command 命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(CrmPermissions.KB_MANAGE)
    @AuditLog(operation = "新建知识引用", type = OperateType.CREATE, targetType = "crm_kb_ref")
    public Result<KbRefView> create(@Valid @RequestBody KbRefCommand command, HttpServletRequest request) {
        return Result.ok(kbAppService.create(command, CrmActorContext.from(request)));
    }

    /**
     * 更新知识引用。
     *
     * @param id 主键
     * @param command 命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(CrmPermissions.KB_MANAGE)
    @AuditLog(operation = "更新知识引用", type = OperateType.UPDATE, targetType = "crm_kb_ref", targetId = "#id")
    public Result<KbRefView> update(@PathVariable("id") Long id,
                                    @Valid @RequestBody KbRefCommand command,
                                    HttpServletRequest request) {
        return Result.ok(kbAppService.update(id, command, CrmActorContext.from(request)));
    }

    /**
     * 逻辑删除知识引用。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission(CrmPermissions.KB_MANAGE)
    @AuditLog(operation = "删除知识引用", type = OperateType.DELETE, targetType = "crm_kb_ref", targetId = "#id")
    public Result<Void> delete(@PathVariable("id") Long id, HttpServletRequest request) {
        kbAppService.delete(id, CrmActorContext.from(request));
        return Result.ok();
    }

    /**
     * 记录引用命中。
     *
     * @param id 主键
     * @param request 当前请求
     * @return 命中后的视图
     */
    @PostMapping("/{id}/hit")
    @RequirePermission(CrmPermissions.KB_MANAGE)
    @AuditLog(operation = "记录知识引用命中", type = OperateType.UPDATE, targetType = "crm_kb_ref", targetId = "#id")
    public Result<KbRefView> hit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(kbAppService.hit(id, CrmActorContext.from(request)));
    }
}
