package com.bluewhale.pdm.change.support;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;

/**
 * OA 审批状态常量。
 *
 * <p>对外契约使用明文状态；工作流数字状态 0/1/2/3 在映射时转换：
 * 0 运行中、1 通过、2 驳回、3 撤销。</p>
 *
 * @author BlueWhale
 */
public final class PdmChgApprovalStatus {

    /** 运行中 */
    public static final String RUNNING = "RUNNING";

    /** 审批通过 */
    public static final String APPROVED = "APPROVED";

    /** 审批驳回 */
    public static final String REJECTED = "REJECTED";

    /** 已撤销 */
    public static final String WITHDRAWN = "WITHDRAWN";

    /**
     * 工具类禁止实例化
     */
    private PdmChgApprovalStatus() {
    }

    /**
     * 工作流数字状态转明文状态。
     *
     * @param workflowStatus 工作流状态：0 运行中 / 1 通过 / 2 驳回 / 3 撤销
     * @return 明文状态
     */
    public static String fromWorkflow(Integer workflowStatus) {
        if (workflowStatus == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.approval-status-empty", "审批状态不能为空");
        }
        return switch (workflowStatus) {
            case 0 -> RUNNING;
            case 1 -> APPROVED;
            case 2 -> REJECTED;
            case 3 -> WITHDRAWN;
            default -> throw new BizException(ErrorCode.PARAM_ERROR, "biz.pdm.change.approval-status-supports-0-1-2", "审批状态仅支持 0/1/2/3");
        };
    }

    /**
     * 是否为终态。
     *
     * @param status 明文状态
     * @return true 表示已结束
     */
    public static boolean isFinal(String status) {
        return APPROVED.equals(status) || REJECTED.equals(status) || WITHDRAWN.equals(status);
    }
}
