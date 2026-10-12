package com.bluewhale.sts.audit.auditlog;

import lombok.Getter;
import lombok.Setter;

/**
 * 按动作聚合的一行。
 *
 * <p>把**失败条数**一起算出来，而不是只给总数：审计最常被问的两个问题是
 * "哪个动作被调用得最多"（判断哪些管理操作是日常）与"哪些操作在反复失败"
 * （越权尝试、客户端写错了参数、权限配错）。后者只有把失败数单列出来才看得见 ——
 * 一个总数为 3、失败数为 3 的动作，和一个总数为 300、失败数为 3 的动作，
 * 意义完全不同。
 */
@Getter
@Setter
public class AuditActionCount {

    /** 动作标识 */
    private String action;

    /** 时间窗内该动作的总条数 */
    private Long actionCount;

    /** 其中结果为 FAILURE 的条数 */
    private Long failureCount;

}
