package com.bluewhale.pdm.collab.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.collab.dto.PdmClbReviewCloseRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewQueryRequest;
import com.bluewhale.pdm.collab.dto.PdmClbReviewSaveRequest;
import com.bluewhale.pdm.collab.service.PdmClbReviewService;
import com.bluewhale.pdm.collab.support.PdmClbRequestContext;
import com.bluewhale.pdm.collab.vo.PdmClbReviewVO;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * PDM 研发评审接口
 *
 * <p>草稿可改；关闭写入结论并冻结。审批流本体在 OA，本接口只保存实例 ID 快照。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/pdm/v{version}/collab/review", version = "1")
@RequiredArgsConstructor
public class PdmClbReviewController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_clb_review";

    /** 审计模块 */
    private static final String MODULE = "PDM 协同办公";

    private final PdmClbReviewService reviewService;

    /**
     * 分页查询评审
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:collab:review:list")
    public Result<PageResult<PdmClbReviewVO>> page(PdmClbReviewQueryRequest request) {
        return Result.ok(reviewService.page(request));
    }

    /**
     * 查询评审详情
     *
     * @param id 评审 ID
     * @return 评审视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:collab:review:list")
    public Result<PdmClbReviewVO> detail(@PathVariable Long id) {
        return Result.ok(reviewService.detail(id));
    }

    /**
     * 新建评审
     *
     * @param request        保存请求
     * @param servletRequest 当前请求
     * @return 评审视图
     */
    @PostMapping
    @RequirePermission("sys:pdm:collab:review:add")
    @AuditLog(module = MODULE, type = OperateType.CREATE, operation = "新建研发评审", targetType = TARGET_TYPE)
    public Result<PdmClbReviewVO> create(@Valid @RequestBody PdmClbReviewSaveRequest request,
                                         HttpServletRequest servletRequest) {
        return Result.ok(reviewService.create(request, PdmClbRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 更新草稿评审
     *
     * @param id             评审 ID
     * @param request        保存请求
     * @param servletRequest 当前请求
     * @return 评审视图
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:collab:review:edit")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "更新研发评审", targetType = TARGET_TYPE)
    public Result<PdmClbReviewVO> update(@PathVariable Long id,
                                         @Valid @RequestBody PdmClbReviewSaveRequest request,
                                         HttpServletRequest servletRequest) {
        return Result.ok(reviewService.update(id, request,
                PdmClbRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 关闭评审并写入结论
     *
     * @param id             评审 ID
     * @param request        关闭请求
     * @param servletRequest 当前请求
     * @return 评审视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:pdm:collab:review:close")
    @AuditLog(module = MODULE, type = OperateType.UPDATE, operation = "关闭研发评审", targetType = TARGET_TYPE)
    public Result<PdmClbReviewVO> close(@PathVariable Long id,
                                        @Valid @RequestBody PdmClbReviewCloseRequest request,
                                        HttpServletRequest servletRequest) {
        return Result.ok(reviewService.close(id, request,
                PdmClbRequestContext.requireUserId(servletRequest)));
    }

    /**
     * 删除草稿评审
     *
     * @param id 评审 ID
     * @return 空结果
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:collab:review:edit")
    @AuditLog(module = MODULE, type = OperateType.DELETE, operation = "删除研发评审", targetType = TARGET_TYPE)
    public Result<Void> delete(@PathVariable Long id) {
        reviewService.delete(id);
        return Result.ok();
    }
}
