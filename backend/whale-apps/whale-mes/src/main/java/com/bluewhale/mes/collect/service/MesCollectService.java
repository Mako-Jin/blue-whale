package com.bluewhale.mes.collect.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.collect.dto.MesArchivePolicyQueryRequest;
import com.bluewhale.mes.collect.dto.MesArchivePolicySaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectBoardSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectDeviceSaveRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointQueryRequest;
import com.bluewhale.mes.collect.dto.MesCollectPointSaveRequest;
import com.bluewhale.mes.collect.dto.MesMetricIngestRequest;
import com.bluewhale.mes.collect.dto.MesMetricSampleQueryRequest;
import com.bluewhale.mes.collect.vo.MesArchivePolicyVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardDataVO;
import com.bluewhale.mes.collect.vo.MesCollectBoardVO;
import com.bluewhale.mes.collect.vo.MesCollectDeviceVO;
import com.bluewhale.mes.collect.vo.MesCollectPointVO;
import com.bluewhale.mes.collect.vo.MesMetricIngestResultVO;
import com.bluewhale.mes.collect.vo.MesMetricSampleVO;

/**
 * MES 数据采集应用服务
 *
 * <p>管理采集设备/采集点/看板/归档策略，并接收网关样本推送。
 * 不变量：编码唯一；样本按 {@code (point, sampledAt, seq)} 幂等；归档执行可重跑。</p>
 *
 * @author BlueWhale
 */
public interface MesCollectService {

    /**
     * 保存采集设备
     *
     * @param request 保存请求
     * @return 采集设备视图
     */
    MesCollectDeviceVO saveDevice(MesCollectDeviceSaveRequest request);

    /**
     * 分页查询采集设备
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesCollectDeviceVO> pageDevices(MesCollectDeviceQueryRequest request);

    /**
     * 采集设备心跳（置在线并刷新时间）
     *
     * @param deviceCode 采集设备编码
     * @return 采集设备视图
     */
    MesCollectDeviceVO heartbeat(String deviceCode);

    /**
     * 保存采集点
     *
     * @param request 保存请求
     * @return 采集点视图
     */
    MesCollectPointVO savePoint(MesCollectPointSaveRequest request);

    /**
     * 分页查询采集点
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesCollectPointVO> pagePoints(MesCollectPointQueryRequest request);

    /**
     * 接收网关样本（幂等）
     *
     * @param pointCode 采集点编码
     * @param request   样本批次
     * @return 上报结果
     */
    MesMetricIngestResultVO ingest(String pointCode, MesMetricIngestRequest request);

    /**
     * 分页查询样本
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesMetricSampleVO> pageSamples(MesMetricSampleQueryRequest request);

    /**
     * 保存看板配置
     *
     * @param request 保存请求
     * @return 看板视图
     */
    MesCollectBoardVO saveBoard(MesCollectBoardSaveRequest request);

    /**
     * 分页查询看板配置
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesCollectBoardVO> pageBoards(MesCollectBoardQueryRequest request);

    /**
     * 查询看板数据（配置 + 各采集点最新样本）
     *
     * @param boardCode 看板编码
     * @return 看板数据
     */
    MesCollectBoardDataVO boardData(String boardCode);

    /**
     * 保存归档策略
     *
     * @param request 保存请求
     * @return 归档策略视图
     */
    MesArchivePolicyVO saveArchivePolicy(MesArchivePolicySaveRequest request);

    /**
     * 分页查询归档策略
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesArchivePolicyVO> pageArchivePolicies(MesArchivePolicyQueryRequest request);

    /**
     * 执行归档（候选计数，可重复执行无副作用）
     *
     * @param id 归档策略 ID
     * @return 归档策略视图
     */
    MesArchivePolicyVO runArchive(Long id);
}
