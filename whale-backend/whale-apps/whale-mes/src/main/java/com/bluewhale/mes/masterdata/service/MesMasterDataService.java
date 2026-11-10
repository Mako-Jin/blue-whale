package com.bluewhale.mes.masterdata.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mes.masterdata.dto.MesMasterDataQueryRequest;
import com.bluewhale.mes.masterdata.dto.MesMasterDataSaveRequest;
import com.bluewhale.mes.masterdata.enums.MasterDataKind;
import com.bluewhale.mes.masterdata.vo.MesMasterDataVO;

import java.util.List;

/**
 * MES 车间主数据应用服务
 *
 * @author BlueWhale
 */
public interface MesMasterDataService {

    /**
     * 分页查询主数据
     *
     * @param kind 主数据类型
     * @param request 查询条件
     * @return 分页结果
     */
    PageResult<MesMasterDataVO> page(MasterDataKind kind, MesMasterDataQueryRequest request);

    /**
     * 查询主数据详情
     *
     * @param kind 主数据类型
     * @param id 主键
     * @return 主数据视图
     */
    MesMasterDataVO detail(MasterDataKind kind, Long id);

    /**
     * 新增主数据
     *
     * @param kind 主数据类型
     * @param request 保存请求
     * @return 主数据视图
     */
    MesMasterDataVO create(MasterDataKind kind, MesMasterDataSaveRequest request);

    /**
     * 编辑主数据
     *
     * @param kind 主数据类型
     * @param id 主键
     * @param request 保存请求
     * @return 主数据视图
     */
    MesMasterDataVO update(MasterDataKind kind, Long id, MesMasterDataSaveRequest request);

    /**
     * 删除主数据
     *
     * @param kind 主数据类型
     * @param id 主键
     */
    void delete(MasterDataKind kind, Long id);

    /**
     * 查询启用状态的主数据选项
     *
     * @param kind 主数据类型
     * @return 主数据选项
     */
    List<MesMasterDataVO> options(MasterDataKind kind);
}
