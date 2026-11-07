package com.bluewhale.erp.mfg.domain.repository;

import com.bluewhale.erp.mfg.domain.model.MfgQuery;
import com.bluewhale.erp.mfg.domain.model.WorkCenter;

import java.util.List;
import java.util.Optional;

/**
 * 工作中心仓储。
 *
 * @author BlueWhale
 */
public interface WorkCenterRepository {

    /**
     * 分页查询工作中心。
     *
     * @param query 查询条件
     * @return 工作中心列表
     */
    List<WorkCenter> page(MfgQuery query);

    /**
     * 统计工作中心数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(MfgQuery query);

    /**
     * 查询全部工作中心。
     *
     * @return 工作中心列表
     */
    List<WorkCenter> listAll();

    /**
     * 按主键查询。
     *
     * @param id 主键
     * @return 工作中心
     */
    Optional<WorkCenter> findById(Long id);

    /**
     * 按编码查询。
     *
     * @param code 编码
     * @return 工作中心
     */
    Optional<WorkCenter> findByCode(String code);

    /**
     * 新增工作中心。
     *
     * @param workCenter 工作中心
     */
    void insert(WorkCenter workCenter);

    /**
     * 更新工作中心。
     *
     * @param workCenter 工作中心
     */
    void update(WorkCenter workCenter);

    /**
     * 删除工作中心。
     *
     * @param id 主键
     */
    void delete(Long id);
}
