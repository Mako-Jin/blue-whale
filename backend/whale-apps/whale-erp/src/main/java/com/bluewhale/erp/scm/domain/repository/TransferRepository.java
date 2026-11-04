package com.bluewhale.erp.scm.domain.repository;

import com.bluewhale.erp.scm.domain.model.ScmQuery;
import com.bluewhale.erp.scm.domain.model.Transfer;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * 库存调拨单仓储。
 *
 * @author BlueWhale
 */
public interface TransferRepository {

    /**
     * 分页查询调拨单。
     *
     * @param query 查询条件
     * @return 调拨单列表
     */
    List<Transfer> page(ScmQuery query);

    /**
     * 统计调拨单数量。
     *
     * @param query 查询条件
     * @return 总条数
     */
    long count(ScmQuery query);

    /**
     * 按主键查询调拨单（含分录）。
     *
     * @param id 主键
     * @return 调拨单
     */
    Optional<Transfer> findById(Long id);

    /**
     * 生成调拨单号。
     *
     * @param transferDate 调拨日期
     * @return 单号
     */
    String nextDocNo(LocalDate transferDate);

    /**
     * 新增调拨单。
     *
     * @param transfer 调拨单
     */
    void insert(Transfer transfer);

    /**
     * 更新调拨单头与分录。
     *
     * @param transfer 调拨单
     */
    void update(Transfer transfer);
}
