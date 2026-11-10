package com.bluewhale.mes.quality.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.quality.dto.MesNcrCloseRequest;
import com.bluewhale.mes.quality.dto.MesNcrCreateRequest;
import com.bluewhale.mes.quality.dto.MesNcrQueryRequest;
import com.bluewhale.mes.quality.service.MesNcrService;
import com.bluewhale.mes.quality.vo.MesNcrVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 不合格品记录（NCR）接口
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/quality/ncr")
@RequiredArgsConstructor
public class MesNcrController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_quality";

    private final MesNcrService ncrService;

    /**
     * 手工创建 NCR
     *
     * @param request 创建请求
     * @return NCR 视图
     */
    @PostMapping
    @RequirePermission("sys:mes:quality:ncr")
    @AuditLog(operation = "MES NCR 创建", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesNcrVO> create(@Valid @RequestBody MesNcrCreateRequest request) {
        return Result.ok(ncrService.create(request));
    }

    /**
     * 分页查询 NCR
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:quality:list")
    public Result<PageResult<MesNcrVO>> page(MesNcrQueryRequest request) {
        return Result.ok(ncrService.page(request));
    }

    /**
     * 查询 NCR 详情
     *
     * @param id NCR ID
     * @return NCR 视图
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mes:quality:list")
    public Result<MesNcrVO> detail(@PathVariable Long id) {
        return Result.ok(ncrService.get(id));
    }

    /**
     * 关闭 NCR（需已完成处置并填写结论）
     *
     * @param id      NCR ID
     * @param request 关闭请求
     * @return NCR 视图
     */
    @PostMapping("/{id}/close")
    @RequirePermission("sys:mes:quality:ncr")
    @AuditLog(operation = "MES NCR 关闭", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesNcrVO> close(@PathVariable Long id, @Valid @RequestBody MesNcrCloseRequest request) {
        return Result.ok(ncrService.close(id, request));
    }
}
