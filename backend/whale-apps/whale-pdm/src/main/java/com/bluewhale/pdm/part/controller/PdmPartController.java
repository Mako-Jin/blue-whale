package com.bluewhale.pdm.part.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.dto.PdmProductQueryRequest;
import com.bluewhale.pdm.dto.PdmProductSaveRequest;
import com.bluewhale.pdm.part.dto.PdmPartMergeRequest;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.service.PdmPartDedupService;
import com.bluewhale.pdm.part.vo.PdmPartDedupGroupVO;
import com.bluewhale.pdm.part.vo.PdmPartMergeResultVO;
import com.bluewhale.pdm.part.vo.PdmPartWhereUsedVO;
import com.bluewhale.pdm.service.PdmProductService;
import com.bluewhale.pdm.vo.PdmProductVO;
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
 * PDM 零件主档接口
 *
 * <p>复用 {@link PdmProductService} 的零件主档能力，新增 where-used 查询。
 * 旧 {@code /pdm/product/**} 接口保持不变。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/part")
@RequiredArgsConstructor
public class PdmPartController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_part";

    private final PdmProductService productService;

    private final PdmBomService bomService;

    private final PdmPartDedupService dedupService;

    /**
     * 分页查询零件
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:part:list")
    public Result<PageResult<PdmProductVO>> page(PdmProductQueryRequest request) {
        return Result.ok(productService.page(request));
    }

    /**
     * 零件详情
     *
     * @param id 零件ID
     * @return 零件详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:part:list")
    public Result<PdmProductVO> detail(@PathVariable Long id) {
        return Result.ok(productService.getDetail(id));
    }

    /**
     * 零件使用处（where-used）
     *
     * @param id 零件ID
     * @return 使用该零件的 BOM 列表
     */
    @GetMapping("/{id}/where-used")
    @RequirePermission("sys:pdm:part:list")
    public Result<List<PdmPartWhereUsedVO>> whereUsed(@PathVariable Long id) {
        return Result.ok(bomService.whereUsed(id));
    }

    /**
     * 零件去重候选
     *
     * @return 归一化名称/材质/规格相同的候选组
     */
    @GetMapping("/dedup/candidates")
    @RequirePermission("sys:pdm:part:dedup:list")
    public Result<List<PdmPartDedupGroupVO>> dedupCandidates() {
        return Result.ok(dedupService.listCandidates());
    }

    /**
     * 人工合并零件（改写草稿 BOM 引用并停用源件）
     *
     * @param request 合并请求
     * @return 合并结果
     */
    @PostMapping("/merge")
    @RequirePermission("sys:pdm:part:merge")
    @AuditLog(operation = "合并零件", type = OperateType.UPDATE, targetType = TARGET_TYPE,
            targetId = "#request.sourceId")
    public Result<PdmPartMergeResultVO> merge(@Valid @RequestBody PdmPartMergeRequest request) {
        return Result.ok(dedupService.merge(request));
    }

    /**
     * 新增零件
     *
     * @param request 新增请求
     * @return 新增后的零件
     */
    @PostMapping
    @RequirePermission("sys:pdm:part:add")
    @AuditLog(operation = "新增零件", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmProductVO> create(@Valid @RequestBody PdmProductSaveRequest request) {
        return Result.ok(productService.create(request));
    }

    /**
     * 更新零件
     *
     * @param id      零件ID
     * @param request 更新请求
     * @return 更新后的零件
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:pdm:part:edit")
    @AuditLog(operation = "更新零件", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmProductVO> update(@PathVariable Long id,
                                       @Valid @RequestBody PdmProductSaveRequest request) {
        return Result.ok(productService.update(id, request));
    }

    /**
     * 删除零件（逻辑删除，被 BOM 引用时拒绝）
     *
     * @param id 零件ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:part:delete")
    @AuditLog(operation = "删除零件", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return Result.ok();
    }
}
