package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyActor;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.interfaces.support.PageParams;
import com.bluewhale.party.interfaces.support.PartyActorContext;
import com.bluewhale.party.union.application.command.ReliefPayCommand;
import com.bluewhale.party.union.application.command.ReliefSaveCommand;
import com.bluewhale.party.union.application.service.UnionReliefAppService;
import com.bluewhale.party.union.application.view.UnionReliefSubmitView;
import com.bluewhale.party.union.application.view.UnionReliefView;
import com.bluewhale.party.union.domain.model.UnionReliefQuery;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 慰问帮扶接口（M-P3）。
 *
 * <p>提交、同步、发放是三个独立动作：提交会触发上限校验与审批发起，同步只读工作流状态，
 * 发放是资金动作。拆开才能让「审批不可用」的降级只影响提交，而不会牵连已保存的申请。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}/union/relief", version = "1")
@RequiredArgsConstructor
public class PartyUnionReliefController {

    private final UnionReliefAppService unionReliefAppService;

    /**
     * 申请列表分页。
     *
     * @param keyword 关键字（申请编号 / 事由）
     * @param status 状态
     * @param reliefType 慰问类型
     * @param year 申请年度
     * @param current 后端风格页码
     * @param pageNum 前端风格页码
     * @param size 后端风格条数
     * @param pageSize 前端风格条数
     * @param request 当前请求
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission(PartyPermissions.UNION_RELIEF_LIST)
    public Result<PageResult<UnionReliefView>> page(
            @RequestParam(value = "keyword", required = false) String keyword,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "reliefType", required = false) String reliefType,
            @RequestParam(value = "year", required = false) Integer year,
            @RequestParam(value = "current", required = false) Long current,
            @RequestParam(value = "pageNum", required = false) Long pageNum,
            @RequestParam(value = "size", required = false) Long size,
            @RequestParam(value = "pageSize", required = false) Long pageSize,
            HttpServletRequest request) {
        UnionReliefQuery query = new UnionReliefQuery();
        query.setKeyword(keyword);
        query.setStatus(status);
        query.setReliefType(reliefType);
        query.setYear(year);
        return Result.ok(unionReliefAppService.page(query,
                PageParams.current(current, pageNum),
                PageParams.size(size, pageSize),
                actor(request)));
    }

    /**
     * 申请详情。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 视图
     */
    @GetMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_RELIEF_LIST)
    public Result<UnionReliefView> detail(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionReliefAppService.detail(id, actor(request)));
    }

    /**
     * 新建申请（草稿）。
     *
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping
    @RequirePermission(PartyPermissions.UNION_RELIEF_MANAGE)
    @AuditLog(operation = "新建慰问帮扶申请", type = OperateType.CREATE, targetType = "pty_union_relief")
    public Result<UnionReliefView> create(@Valid @RequestBody ReliefSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionReliefAppService.create(command, actor(request)));
    }

    /**
     * 编辑申请（仅草稿）。
     *
     * @param id 申请 ID
     * @param command 保存命令
     * @param request 当前请求
     * @return 视图
     */
    @PutMapping("/{id}")
    @RequirePermission(PartyPermissions.UNION_RELIEF_MANAGE)
    @AuditLog(operation = "编辑慰问帮扶申请", type = OperateType.UPDATE, targetType = "pty_union_relief")
    public Result<UnionReliefView> update(@PathVariable("id") Long id,
                                          @Valid @RequestBody ReliefSaveCommand command,
                                          HttpServletRequest request) {
        return Result.ok(unionReliefAppService.update(id, command, actor(request)));
    }

    /**
     * 提交审批；审批不可用时返回草稿与降级提示。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 提交结果
     */
    @PostMapping("/{id}/submit")
    @RequirePermission(PartyPermissions.UNION_RELIEF_MANAGE)
    @AuditLog(operation = "提交慰问帮扶审批", type = OperateType.UPDATE, targetType = "pty_union_relief")
    public Result<UnionReliefSubmitView> submit(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionReliefAppService.submit(id, actor(request)));
    }

    /**
     * 同步审批结果。
     *
     * @param id 申请 ID
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/sync")
    @RequirePermission(PartyPermissions.UNION_RELIEF_MANAGE)
    @AuditLog(operation = "同步慰问帮扶审批结果", type = OperateType.UPDATE, targetType = "pty_union_relief")
    public Result<UnionReliefView> sync(@PathVariable("id") Long id, HttpServletRequest request) {
        return Result.ok(unionReliefAppService.sync(id, actor(request)));
    }

    /**
     * 登记发放（资金动作，仅工会管理员）。
     *
     * @param id 申请 ID
     * @param command 发放命令（可空，默认当天）
     * @param request 当前请求
     * @return 视图
     */
    @PostMapping("/{id}/pay")
    @RequirePermission(PartyPermissions.UNION_RELIEF_MANAGE)
    @AuditLog(operation = "登记慰问帮扶发放", type = OperateType.UPDATE, targetType = "pty_union_relief")
    public Result<UnionReliefView> pay(@PathVariable("id") Long id,
                                       @RequestBody(required = false) ReliefPayCommand command,
                                       HttpServletRequest request) {
        return Result.ok(unionReliefAppService.pay(id, command, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private PartyActor actor(HttpServletRequest request) {
        return PartyActorContext.from(request);
    }
}
