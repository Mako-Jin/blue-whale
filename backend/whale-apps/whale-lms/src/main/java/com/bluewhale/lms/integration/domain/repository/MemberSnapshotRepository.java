package com.bluewhale.lms.integration.domain.repository;

import com.bluewhale.lms.integration.domain.model.MemberSnapshot;
import com.bluewhale.lms.integration.domain.model.MemberSnapshotQuery;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * 员工只读快照仓储。
 *
 * <p>快照由 ERP 单向同步写入，LMS 业务侧只做读；因此没有「按业务改快照」的方法，
 * 唯一写入口是 {@link #save(MemberSnapshot)}（同步时的 upsert）。</p>
 *
 * @author BlueWhale
 */
public interface MemberSnapshotRepository {

    /**
     * 分页查询快照。
     *
     * @param query 查询条件
     * @param offset 偏移量
     * @param size 条数
     * @return 快照列表
     */
    List<MemberSnapshot> page(MemberSnapshotQuery query, long offset, int size);

    /**
     * 统计快照总数。
     *
     * @param query 查询条件
     * @return 总数
     */
    long count(MemberSnapshotQuery query);

    /**
     * 按员工编号查询。
     *
     * @param employeeNo 员工编号
     * @return 快照
     */
    Optional<MemberSnapshot> findByEmployeeNo(String employeeNo);

    /**
     * 按平台账号查询。
     *
     * @param userId 用户 ID
     * @return 快照
     */
    Optional<MemberSnapshot> findByUserId(Long userId);

    /**
     * 批量按平台账号查询（催办时批量判定在职状态，避免逐条回表）。
     *
     * @param userIds 用户 ID 集合
     * @return 快照列表
     */
    List<MemberSnapshot> listByUserIds(Collection<Long> userIds);

    /**
     * 保存快照：按员工编号存在则覆盖，不存在则插入。
     *
     * @param snapshot 快照
     * @return 主键
     */
    Long save(MemberSnapshot snapshot);

    /**
     * 统计快照总数（同步结果汇报用）。
     *
     * @return 总数
     */
    long countAll();
}
