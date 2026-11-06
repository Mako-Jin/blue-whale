package com.bluewhale.lms.integration.application.service;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.integration.application.view.MemberSnapshotView;
import com.bluewhale.lms.integration.application.view.MemberSyncResultView;
import com.bluewhale.lms.integration.domain.model.MemberSnapshotQuery;

/**
 * 员工只读快照应用服务。
 *
 * @author BlueWhale
 */
public interface MemberSnapshotAppService {

    /**
     * 从 ERP 全量同步员工快照（仅管理员）。
     *
     * @param actor 操作人
     * @return 同步结果
     */
    MemberSyncResultView sync(LmsActor actor);

    /**
     * 分页查询快照（仅管理员）。
     *
     * @param query 查询条件
     * @param actor 操作人
     * @return 分页结果
     */
    PageResult<MemberSnapshotView> page(MemberSnapshotQuery query, LmsActor actor);

    /**
     * 按平台账号查询快照。
     *
     * @param userId 用户 ID
     * @return 快照；未收录返回 {@code null}
     */
    MemberSnapshotView byUserId(Long userId);

    /**
     * 判断某个账号当前是否可被触达（在职且已收录）。
     *
     * <p>未收录的账号按「不在职」处理：快照尚未同步时宁可不发，
     * 也不要给已离职的人发催办。</p>
     *
     * @param userId 用户 ID
     * @return 可触达返回 {@code true}
     */
    boolean isReachable(Long userId);
}
