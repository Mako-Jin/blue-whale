package com.bluewhale.pdm.part.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.pdm.part.dto.PdmBomDeriveRequest;
import com.bluewhale.pdm.part.dto.PdmBomLineSaveRequest;
import com.bluewhale.pdm.part.dto.PdmBomQueryRequest;
import com.bluewhale.pdm.part.dto.PdmBomSaveRequest;
import com.bluewhale.pdm.part.service.PdmBomService;
import com.bluewhale.pdm.part.support.PdmBomExcelSupport;
import com.bluewhale.pdm.part.support.PdmBomRequestContext;
import com.bluewhale.pdm.part.vo.PdmBomDiffTaskVO;
import com.bluewhale.pdm.part.vo.PdmBomDiffVO;
import com.bluewhale.pdm.part.vo.PdmBomExportRowVO;
import com.bluewhale.pdm.part.vo.PdmBomReleaseVO;
import com.bluewhale.pdm.part.vo.PdmBomVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
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
     * 对比两个 BOM（同一产品）
     *
     * @param baseId   基线 BOM ID
     * @param targetId 目标 BOM ID
     * @return 对比结果
     */
    @GetMapping("/diff")
    @RequirePermission("sys:pdm:bom:list")
    public Result<PdmBomDiffVO> diff(@RequestParam Long baseId, @RequestParam Long targetId) {
        return Result.ok(bomService.diff(baseId, targetId));
    }

    /**
     * 创建 BOM 异步对比任务（不限行数）
     *
     * @param baseId   基线 BOM ID
     * @param targetId 目标 BOM ID
     * @param request  当前请求
     * @return 任务视图
     */
    @PostMapping("/diff/tasks")
    @RequirePermission("sys:pdm:bom:list")
    public Result<PdmBomDiffTaskVO> createDiffTask(@RequestParam Long baseId,
                                                   @RequestParam Long targetId,
                                                   HttpServletRequest request) {
        return Result.ok(bomService.createDiffTask(baseId, targetId,
                PdmBomRequestContext.requireUserId(request)));
    }

    /**
     * 查询 BOM 异步对比任务
     *
     * @param id 任务ID
     * @return 任务视图（成功时含对比结果）
     */
    @GetMapping("/diff/tasks/{id}")
    @RequirePermission("sys:pdm:bom:list")
    public Result<PdmBomDiffTaskVO> getDiffTask(@PathVariable Long id) {
        return Result.ok(bomService.getDiffTask(id));
    }

    /**
     * 从 EBOM 派生 PBOM/MBOM
     *
     * @param id      BOM ID
     * @param request 派生请求
     * @return 派生 BOM 详情
     */
    @PostMapping("/{id}/derive")
    @RequirePermission("sys:pdm:bom:add")
    @AuditLog(operation = "派生 BOM 视图", type = OperateType.CREATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<PdmBomVO> derive(@PathVariable Long id,
                                   @Valid @RequestBody PdmBomDeriveRequest request) {
        return Result.ok(bomService.derive(id, request));
    }

    /**
     * 导出 BOM Excel
     *
     * @param id       BOM ID
     * @param response HTTP 响应
     * @throws IOException 写出失败
     */
    @GetMapping("/{id}/export")
    @RequirePermission("sys:pdm:bom:export")
    public void export(@PathVariable Long id, HttpServletResponse response) throws IOException {
        PdmBomVO bom = bomService.detail(id);
        List<PdmBomExportRowVO> rows = bomService.exportRows(id);
        response.setContentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        String filename = URLEncoder.encode(bom.getBomNo() + ".xlsx", StandardCharsets.UTF_8)
                .replace("+", "%20");
        response.setHeader("Content-Disposition", "attachment; filename=\"" + filename + "\"");
        response.getOutputStream().write(PdmBomExcelSupport.toXlsx(bom.getBomNo(), rows));
        response.flushBuffer();
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
