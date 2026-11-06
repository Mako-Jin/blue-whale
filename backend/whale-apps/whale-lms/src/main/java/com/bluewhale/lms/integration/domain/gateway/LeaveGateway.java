package com.bluewhale.lms.integration.domain.gateway;

import com.bluewhale.lms.integration.domain.model.LeaveConflictEntry;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

/**
 * OA 请假只读网关。
 *
 * <p>只做「查已批准请假」这一件事。LMS 不写 OA 的请假单，也不判断请假是否合理——
 * 请假审批归 OA，LMS 只在排期时提示冲突。</p>
 *
 * @author BlueWhale
 */
public interface LeaveGateway {

    /**
     * 查询指定人员在日期区间内已批准的请假。
     *
     * @param userIds 用户 ID 集合
     * @param from 区间起（含）
     * @param to 区间止（含）
     * @return 请假列表；OA 不可达时返回空列表（提示类信息不阻断业务）
     */
    List<LeaveConflictEntry> listApproved(Collection<Long> userIds, LocalDate from, LocalDate to);
}
