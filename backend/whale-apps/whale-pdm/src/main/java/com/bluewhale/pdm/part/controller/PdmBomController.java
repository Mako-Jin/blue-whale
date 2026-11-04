package com.bluewhale.pdm.part.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomQueryRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.support.PdmBomRequestContext;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.part.vo.PdmBomVO;
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

import java.util.List;

/**
 * PDM BOM 接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/pdm/bom")
@RequiredArgsConstructor
public class PdmBomController {

    /** 审计对象类型 */
    private static final String TARGET_TYPE = "pdm_bom";

    private final PdmBomService bomService;

    /**
     * 分页查询 BOM
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:pdm:bom:list")
    public Result<PageResult<PdmBomVO>> page(PdmBomQueryRequest request) {
        return Result.ok(bomService.page(request));
    }

    /**
     * BOM 详情（头 + 行 + 树）
     *
     * @param id BOM ID
     * @return BOM 详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:pdm:bom:list")
    public Result<PdmBomVO> detail(@PathVariable Long id) {
        return Result.ok(bomService.detail(id));
    }

    /**
     * 查询 BOM 发布记录
     *
     * @param bomNo BOM 编号
     * @return 发布记录
     */
    @GetMapping("/{bomNo}/releases")
    @RequirePermission("sys:pdm:bom:list")
    public Result<List<PdmBomReleaseVO>> releases(@PathVariable String bomNo) {
        return Result.ok(bomService.releases(bomNo));
    }

    /**
     * 新建 BOM 版本
     *
     * @param request 新建请求
     * @return BOM 详情
     */
    @PostMapping
    @RequirePermission("sys:pdm:bom:add")
    @AuditLog(operation = "新建 BOM", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<PdmBomVO> create(@Valid @RequestBody PdmBomSaveRequest request) {
        return Result.ok(bomService.create(request));
    }

    /**
     * 删除草稿 BOM
     *
     * @param id BOM ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:pdm:bom:delete")
    @AuditLog(operation = "删除 BOM", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable Long id) {
        bomService.delete(id);
        return Result.ok();
    }

    /**
     * 新增 BOM 行
     *
     * @param id      BOM ID
     * @param request 行请求
     * @return BOM 详情
     */
    @PostMapping("/{id}/lines")
    @RequirePermission("sys:pdm:bom:edit")
    @AuditLog(operation = "新增 BOM 行", type = OperateType.CREATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmBomVO> addLine(@PathVariable Long id,
                                    @Valid @RequestBody PdmBomLineSaveRequest request) {
        return Result.ok(bomService.addLine(id, request));
    }

    /**
     * 更新 BOM 行
     *
     * @param id      BOM ID
     * @param lineId  行ID
     * @param request 行请求
     * @return BOM 详情
     */
    @PutMapping("/{id}/lines/{lineId}")
    @RequirePermission("sys:pdm:bom:edit")
    @AuditLog(operation = "更新 BOM 行", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmBomVO> updateLine(@PathVariable Long id, @PathVariable Long lineId,
                                       @Valid @RequestBody PdmBomLineSaveRequest request) {
        return Result.ok(bomService.updateLine(id, lineId, request));
    }

    /**
     * 删除 BOM 行
     *
     * @param id     BOM ID
     * @param lineId 行ID
     * @return BOM 详情
     */
    @DeleteMapping("/{id}/lines/{lineId}")
    @RequirePermission("sys:pdm:bom:edit")
    @AuditLog(operation = "删除 BOM 行", type = OperateType.DELETE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmBomVO> deleteLine(@PathVariable Long id, @PathVariable Long lineId) {
        return Result.ok(bomService.deleteLine(id, lineId));
    }

    /**
     * 发布 BOM（重复发布返回既有快照）
     *
     * @param id      BOM ID
     * @param request 当前请求
     * @return 发布记录
     */
    @PostMapping("/{id}/release")
    @RequirePermission("sys:pdm:bom:release")
    @AuditLog(operation = "发布 BOM", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmBomReleaseVO> release(@PathVariable Long id, HttpServletRequest request) {
        return Result.ok(bomService.release(id, PdmBomRequestContext.requireUserId(request)));
    }
}
