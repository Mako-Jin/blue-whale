package com.bluewhale.crm.customer.application.view;

import com.bluewhale.crm.customer.domain.model.CustomerOwnerLog;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 客户负责人变更留痕视图。
 *
 * @author BlueWhale
 */
@Data
public class OwnerLogView {

    /** 主键 */
    private Long id;

    /** 客户 ID */
    private Long customerId;

    /** 动作 */
    private String action;

    /** 原负责人 */
    private Long fromOwnerId;

    /** 新负责人 */
    private Long toOwnerId;

    /** 操作人 */
    private Long operatorId;

    /** 备注 */
    private String remark;

    /** 创建时间 */
    private LocalDateTime createTime;

    /**
     * 由领域模型构建视图。
     *
     * @param log 留痕
     * @return 留痕视图
     */
    public static OwnerLogView from(CustomerOwnerLog log) {
        OwnerLogView view = new OwnerLogView();
        view.setId(log.getId());
        view.setCustomerId(log.getCustomerId());
        view.setAction(log.getAction());
        view.setFromOwnerId(log.getFromOwnerId());
        view.setToOwnerId(log.getToOwnerId());
        view.setOperatorId(log.getOperatorId());
        view.setRemark(log.getRemark());
        view.setCreateTime(log.getCreateTime());
        return view;
    }
}
