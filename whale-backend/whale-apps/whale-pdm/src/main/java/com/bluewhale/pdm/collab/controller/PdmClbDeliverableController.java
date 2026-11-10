package com.bluewhale.pdm.collab.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbDeliverableSaveRequest;
import com.bluewhale.pdm.collab.service.PdmClbDeliverableService;
import com.bluewhale.pdm.collab.support.PdmClbRequestContext;
import com.bluewhale.pdm.collab.vo.PdmClbDeliverableVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM 项目交付物接口
 *
 * <p>只归档已发布对象版本；项目本体归 {@code whale-pm}，本接口只保存引用。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/collab/deliverable")
@RequiredArgsConstructor
public class PdmClbDeliverableController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_clb_deliverable";

    /** 审计模块 */
    private static final String MODULE = "PDM 协同办公";

    private final PdmClbDeliverableService deliverableService;

    /**
     * 分页查询交付物
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:collab:deliverable:list")
    public Result<PageResult<PdmClbDeliverableVO>> page(PdmClbDeliverableQueryRequest request) {
        return Result.ok(deliverableService.page(request));
    }

    /**
     * 归档项目交付物
     *
     * @param request         归档请求
     * @param servletRequest  当前请求
     * @return 交付物视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:collab:deliverable:add")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "归档项目交付物", targetType = TARGET_TYPE)
    public Result<PdmClbDeliverableVO> archive(@Valid @RequestBody PdmClbDeliverableSaveRequest request,
                                              HttpServletRequest servletRequest) {
        return Result.ok(deliverableService.archive(request,
                PdmClbRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 取消归档
     *
     * @param id 交付物 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:collab:deliverable:delete")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "取消交付物归档", targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        deliverableService.delete(id);
        return Result.ok();
    }
}
