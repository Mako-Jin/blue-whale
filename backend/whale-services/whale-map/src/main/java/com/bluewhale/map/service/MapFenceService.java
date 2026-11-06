package com.bluewhale.map.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.map.dto.FenceCheckRequest;
import com.bluewhale.map.dto.FenceQuery;
import com.bluewhale.map.dto.FenceSaveRequest;
import com.bluewhale.map.vo.FenceHitVO;
import com.bluewhale.map.vo.FenceVO;

import java.util.List;

/**
 * 围栏服务（打卡范围、销售区域、配送范围）。
 *
 * @author BlueWhale
 */
public interface MapFenceService {

    /**
     * 保存围栏（新建或按 id 覆盖更新）
     *
     * @param request 保存请求
     * @return 保存后的围栏（展示坐标系）
     */
    FenceVO save(FenceSaveRequest request);

    /**
     * 分页查询围栏
     *
     * @param query 查询条件
     * @return 分页结果（展示坐标系）
     */
    PageResult<FenceVO> page(FenceQuery query);

    /**
     * 围栏详情
     *
     * @param id 围栏ID
     * @return 围栏（展示坐标系）
     */
    FenceVO detail(Long id);

    /**
     * 删除围栏（逻辑删除，打卡记录仍可指回名称）
     *
     * @param id 围栏ID
     */
    void remove(Long id);

    /**
     * 判定某点是否在指定围栏内
     *
     * @param id      围栏ID
     * @param request 点位
     * @return 判定结果
     */
    FenceHitVO contains(Long id, FenceCheckRequest request);

    /**
     * 反查某点命中的全部围栏（打卡场景：我在哪些范围内）
     *
     * @param request 点位与业务范围
     * @return 命中的围栏（按到边界的距离降序，越靠中心越前）
     */
    List<FenceHitVO> match(FenceCheckRequest request);
}
