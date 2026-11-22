package com.bluewhale.mes.masterdata.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.masterdata.dto.MesMasterDataQueryRequest;
import com.bluewhale.mes.masterdata.dto.MesMasterDataSaveRequest;
import com.bluewhale.mes.masterdata.enums.MasterDataKind;
import com.bluewhale.mes.masterdata.service.MesMasterDataService;
import com.bluewhale.mes.masterdata.vo.MesMasterDataVO;
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
 * MES 车间主数据接口
 *
 * <p>统一路径 {@code /mes/master-data/{kind}/**}，其中 {@code kind} 取值为
 * {@code workshop / line / work-center / station / team / shift / operation /
 * defect-code / reason-code}。各类型共用请求与视图，服务层按类型做字段裁剪与引用校验。</p>
 *
 * <p>全部接口受 {@code sys:mes:master:*} 权限保护，写操作均留审计日志。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/mes/v{version}/master-data/{kind}", version = "1")
@RequiredArgsConstructor
public class MesMasterDataController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_master_data";

    private final MesMasterDataService mesMasterDataService;

    /**
     * 分页查询车间主数据
     *
     * @param kind    主数据类型路径标识
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/page")
    @RequirePermission("sys:mes:master:list")
    public Result<PageResult<MesMasterDataVO>> page(@PathVariable String kind, MesMasterDataQueryRequest request) {
        return Result.ok(mesMasterDataService.page(MasterDataKind.fromPath(kind), request));
    }

    /**
     * 查询启用状态的主数据选项
     *
     * @param kind 主数据类型路径标识
     * @return 主数据选项列表
     */
    @GetMapping("/options")
    @RequirePermission("sys:mes:master:list")
    public Result<List<MesMasterDataVO>> options(@PathVariable String kind) {
        return Result.ok(mesMasterDataService.options(MasterDataKind.fromPath(kind)));
    }

    /**
     * 车间主数据详情
     *
     * @param kind 主数据类型路径标识
     * @param id   主键 ID
     * @return 主数据详情
     */
    @GetMapping("/{id}")
    @RequirePermission("sys:mes:master:list")
    public Result<MesMasterDataVO> detail(@PathVariable String kind, @PathVariable Long id) {
        return Result.ok(mesMasterDataService.detail(MasterDataKind.fromPath(kind), id));
    }

    /**
     * 新增车间主数据
     *
     * @param kind    主数据类型路径标识
     * @param request 新增请求
     * @return 新增后的主数据
     */
    @PostMapping
    @RequirePermission("sys:mes:master:add")
    @AuditLog(operation = "新增 MES 基础主数据", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<MesMasterDataVO> create(@PathVariable String kind,
                                          @Valid @RequestBody MesMasterDataSaveRequest request) {
        return Result.ok(mesMasterDataService.create(MasterDataKind.fromPath(kind), request));
    }

    /**
     * 更新车间主数据
     *
     * @param kind    主数据类型路径标识
     * @param id      主键 ID
     * @param request 更新请求
     * @return 更新后的主数据
     */
    @PutMapping("/{id}")
    @RequirePermission("sys:mes:master:edit")
    @AuditLog(operation = "更新 MES 基础主数据", type = OperateType.UPDATE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesMasterDataVO> update(@PathVariable String kind, @PathVariable Long id,
                                          @Valid @RequestBody MesMasterDataSaveRequest request) {
        return Result.ok(mesMasterDataService.update(MasterDataKind.fromPath(kind), id, request));
    }

    /**
     * 删除车间主数据（逻辑删除）
     *
     * @param kind 主数据类型路径标识
     * @param id   主键 ID
     * @return 空响应体
     */
    @DeleteMapping("/{id}")
    @RequirePermission("sys:mes:master:delete")
    @AuditLog(operation = "删除 MES 基础主数据", type = OperateType.DELETE,
            targetType = TARGET_TYPE, targetId = "#id")
    public Result<Void> delete(@PathVariable String kind, @PathVariable Long id) {
        mesMasterDataService.delete(MasterDataKind.fromPath(kind), id);
        return Result.ok();
    }
}
