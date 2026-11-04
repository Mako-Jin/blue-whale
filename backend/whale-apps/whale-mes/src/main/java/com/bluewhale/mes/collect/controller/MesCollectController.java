package com.bluewhale.mes.collect.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.mes.collect.dto.MesArchivePolicyQueryRequest;
import com.bluewhale.mes.collect.dto.MesArchivePolicySaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointSaveRequest;
import com.bluewhale.mes.collect.dto.MesMetricSampleQueryRequest;
import com.bluewhale.mes.collect.service.MesCollectService;
import com.bluewhale.mes.collect.vo.MesArchivePolicyVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardDataVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardVO;
import com.bluewhale.mes.collect.vo.MesCollectDeviceVO;
import com.bluewhale.mes.collect.vo.MesCollectPointVO;
import com.bluewhale.mes.collect.vo.MesMetricSampleVO;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * MES 数据采集管理接口
 *
 * <p>管理采集设备、采集点、样本查询、看板与归档策略；网关样本推送走 {@code /mes/internal/collect}。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/mes/collect")
@RequiredArgsConstructor
public class MesCollectController {

    /** 审计操作对象类型 */
    private static final String TARGET_TYPE = "mes_collect";

    private final MesCollectService collectService;

    /**
     * 保存采集设备
     *
     * @param request 保存请求
     * @return 采集设备视图
     */
    @PostMapping("/device")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 采集设备保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesCollectDeviceVO> saveDevice(@Valid @RequestBody MesCollectDeviceSaveRequest request) {
        return Result.ok(collectService.saveDevice(request));
    }

    /**
     * 分页查询采集设备
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/device/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesCollectDeviceVO>> pageDevices(MesCollectDeviceQueryRequest request) {
        return Result.ok(collectService.pageDevices(request));
    }

    /**
     * 采集设备心跳
     *
     * @param deviceCode 采集设备编码
     * @return 采集设备视图
     */
    @PostMapping("/device/{deviceCode}/heartbeat")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 采集设备心跳", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesCollectDeviceVO> heartbeat(@PathVariable String deviceCode) {
        return Result.ok(collectService.heartbeat(deviceCode));
    }

    /**
     * 保存采集点
     *
     * @param request 保存请求
     * @return 采集点视图
     */
    @PostMapping("/point")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 采集点保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesCollectPointVO> savePoint(@Valid @RequestBody MesCollectPointSaveRequest request) {
        return Result.ok(collectService.savePoint(request));
    }

    /**
     * 分页查询采集点
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/point/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesCollectPointVO>> pagePoints(MesCollectPointQueryRequest request) {
        return Result.ok(collectService.pagePoints(request));
    }

    /**
     * 分页查询样本
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/sample/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesMetricSampleVO>> pageSamples(MesMetricSampleQueryRequest request) {
        return Result.ok(collectService.pageSamples(request));
    }

    /**
     * 保存看板配置
     *
     * @param request 保存请求
     * @return 看板视图
     */
    @PostMapping("/board")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 看板配置保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesCollectBoardVO> saveBoard(@Valid @RequestBody MesCollectBoardSaveRequest request) {
        return Result.ok(collectService.saveBoard(request));
    }

    /**
     * 分页查询看板配置
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/board/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesCollectBoardVO>> pageBoards(MesCollectBoardQueryRequest request) {
        return Result.ok(collectService.pageBoards(request));
    }

    /**
     * 查询看板数据
     *
     * @param boardCode 看板编码
     * @return 看板数据
     */
    @GetMapping("/board/{boardCode}/data")
    @RequirePermission("sys:mes:equipment:list")
    public Result<MesCollectBoardDataVO> boardData(@PathVariable String boardCode) {
        return Result.ok(collectService.boardData(boardCode));
    }

    /**
     * 保存归档策略
     *
     * @param request 保存请求
     * @return 归档策略视图
     */
    @PostMapping("/archive")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 归档策略保存", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<MesArchivePolicyVO> saveArchivePolicy(@Valid @RequestBody MesArchivePolicySaveRequest request) {
        return Result.ok(collectService.saveArchivePolicy(request));
    }

    /**
     * 分页查询归档策略
     *
     * @param request 查询条件
     * @return 分页结果
     */
    @GetMapping("/archive/page")
    @RequirePermission("sys:mes:equipment:list")
    public Result<PageResult<MesArchivePolicyVO>> pageArchivePolicies(MesArchivePolicyQueryRequest request) {
        return Result.ok(collectService.pageArchivePolicies(request));
    }

    /**
     * 执行归档（重复执行无副作用）
     *
     * @param id 归档策略 ID
     * @return 归档策略视图
     */
    @PostMapping("/archive/{id}/run")
    @RequirePermission("sys:mes:collect:manage")
    @AuditLog(operation = "MES 归档执行", type = OperateType.UPDATE, targetType = TARGET_TYPE, targetId = "#id")
    public Result<MesArchivePolicyVO> runArchive(@PathVariable Long id) {
        return Result.ok(collectService.runArchive(id));
    }
}
