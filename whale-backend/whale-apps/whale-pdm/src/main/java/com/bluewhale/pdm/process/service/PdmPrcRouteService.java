package com.bluewhale.pdm.process.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.pdm.process.dto.PdmPrcRouteQueryRequest;
import com.bluewhale.pdm.process.dto.PdmPrcRouteSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcSopSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcStepSaveRequest;
import com.bluewhale.pdm.process.dto.PdmPrcToolingSaveRequest;
import com.bluewhale.pdm.process.vo.PdmPrcRouteSnapshotVO;
import com.bluewhale.pdm.process.vo.PdmPrcRouteVO;

/**
 * 工艺路线服务
 *
 * <p>路线发布后不可编辑；MES 下发失败不回滚发布结果，可通过重试端点补发。</p>
 *
 * @author BlueWhale
 */
public interface PdmPrcRouteService {

    /**
     * 分页查询工艺路线
     *
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<PdmPrcRouteVO> page(PdmPrcRouteQueryRequest request);

    /**
     * 查询路线详情（含工序、工装、SOP 与下发状态）
     *
     * @param id 路线ID
     * @return 路线视图
     */
    PdmPrcRouteVO detail(Long id);

    /**
     * 新建路线版本
     *
     * @param request 新建请求
     * @param userId  当前用户
     * @return 路线视图
     */
    PdmPrcRouteVO create(PdmPrcRouteSaveRequest request, Long userId);

    /**
     * 更新草稿路线
     *
     * @param id      路线ID
     * @param request 更新请求
     * @return 路线视图
     */
    PdmPrcRouteVO update(Long id, PdmPrcRouteSaveRequest request);

    /**
     * 删除草稿路线
     *
     * @param id 路线ID
     */
    void delete(Long id);

    /**
     * 新增工序
     *
     * @param id      路线ID
     * @param request 工序请求
     * @return 路线视图
     */
    PdmPrcRouteVO addStep(Long id, PdmPrcStepSaveRequest request);

    /**
     * 更新工序
     *
     * @param id      路线ID
     * @param stepId  工序ID
     * @param request 工序请求
     * @return 路线视图
     */
    PdmPrcRouteVO updateStep(Long id, Long stepId, PdmPrcStepSaveRequest request);

    /**
     * 删除工序
     *
     * @param id     路线ID
     * @param stepId 工序ID
     * @return 路线视图
     */
    PdmPrcRouteVO deleteStep(Long id, Long stepId);

    /**
     * 新增工装
     *
     * @param id      路线ID
     * @param request 工装请求
     * @return 路线视图
     */
    PdmPrcRouteVO addTooling(Long id, PdmPrcToolingSaveRequest request);

    /**
     * 删除工装
     *
     * @param id        路线ID
     * @param toolingId 工装ID
     * @return 路线视图
     */
    PdmPrcRouteVO deleteTooling(Long id, Long toolingId);

    /**
     * 保存工序 SOP（仅允许已发布文档版本）
     *
     * @param id      路线ID
     * @param request SOP 请求
     * @return 路线视图
     */
    PdmPrcRouteVO saveSop(Long id, PdmPrcSopSaveRequest request);

    /**
     * 删除工序 SOP
     *
     * @param id    路线ID
     * @param sopId SOP 记录ID
     * @return 路线视图
     */
    PdmPrcRouteVO deleteSop(Long id, Long sopId);

    /**
     * 发布路线并下发 MES
     *
     * @param id     路线ID
     * @param userId 当前用户
     * @return 路线视图
     */
    PdmPrcRouteVO release(Long id, Long userId);

    /**
     * 重试 MES 下发
     *
     * @param id 路线ID
     * @return 路线视图
     */
    PdmPrcRouteVO retryPublish(Long id);

    /**
     * 撤回路线（作废并向 MES 撤回）
     *
     * @param id 路线ID
     * @return 路线视图
     */
    PdmPrcRouteVO withdraw(Long id);

    /**
     * 读取已发布路线快照（按编号 + 版本）。
     *
     * @param routeNo   路线编号
     * @param versionNo 版本号
     * @return 路线快照
     */
    PdmPrcRouteSnapshotVO snapshot(String routeNo, String versionNo);

    /**
     * 读取零件最新已发布路线快照。
     *
     * @param partCode 零件编码
     * @return 路线快照
     */
    PdmPrcRouteSnapshotVO latestReleased(String partCode);
}
