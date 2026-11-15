package com.bluewhale.form.event;

import com.bluewhale.form.po.FormDataPo;

import java.time.LocalDateTime;

/**
 * 表单数据变更领域事件（提交 / 修改 / 删除 / 审批状态推进后发布）。
 *
 * <p>用于弱化「表单数据变更 → 档案落库 / 统计汇总 / 消息通知」之间的同步耦合：
 * 订阅方通过 {@code @TransactionalEventListener(phase = AFTER_COMMIT)} 接收，
 * 只在数据真正提交后触发，避免主流程回滚而下游已消费的情况。</p>
 *
 * <p>事件只携带定位信息，不携带完整 {@code dataJson}：下游如需明细应回查
 * {@code GET /form/v1/internal/data/{id}}，避免事件体随表单大小膨胀。</p>
 *
 * @param dataId 表单数据ID
 * @param definitionId 表单定义ID
 * @param definitionCode 表单定义编码
 * @param tenantId 租户ID
 * @param changeType 变更类型（{@code CREATE} / {@code UPDATE} / {@code DELETE} / {@code APPROVAL}）
 * @param bizKey 业务键（可空）
 * @param operator 操作人（可空，取决于调用方是否透传）
 * @param occurredAt 事件发生时间
 * @author BlueWhale
 */
public record FormDataChangedEvent(
        Long dataId,
        Long definitionId,
        String definitionCode,
        Long tenantId,
        String changeType,
        String bizKey,
        String operator,
        LocalDateTime occurredAt) {

    /** 变更类型：提交 */
    public static final String CREATE = "CREATE";

    /** 变更类型：修改 */
    public static final String UPDATE = "UPDATE";

    /** 变更类型：删除 */
    public static final String DELETE = "DELETE";

    /** 变更类型：审批状态推进 */
    public static final String APPROVAL = "APPROVAL";

    /**
     * 按实体与变更类型构造事件
     *
     * @param data 表单数据实体
     * @param changeType 变更类型
     * @param operator 操作人（可空）
     * @return 表单数据变更事件
     */
    public static FormDataChangedEvent of(FormDataPo data, String changeType, String operator) {
        return new FormDataChangedEvent(data.getId(), data.getDefinitionId(), data.getDefinitionCode(),
                data.getTenantId(), changeType, data.getBizKey(), operator, LocalDateTime.now());
    }
}
