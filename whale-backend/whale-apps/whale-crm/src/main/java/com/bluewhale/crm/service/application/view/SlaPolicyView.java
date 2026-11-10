package com.bluewhale.crm.service.application.view;

import com.bluewhale.crm.service.domain.model.SlaPolicy;
import lombok.Data;

/**
 * SLA 策略视图。
 *
 * @author BlueWhale
 */
@Data
public class SlaPolicyView {

    /** 优先级 */
    private String priority;

    /** 响应时限（工作小时） */
    private Integer responseHours;

    /** 解决时限（工作小时） */
    private Integer resolveHours;

    /** 是否启用 */
    private Boolean enabled;

    /**
     * 由领域对象构建视图。
     *
     * @param policy 策略
     * @return 视图
     */
    public static SlaPolicyView from(SlaPolicy policy) {
        SlaPolicyView view = new SlaPolicyView();
        view.setPriority(policy.getPriority() == null ? null : policy.getPriority().name());
        view.setResponseHours(policy.getResponseHours());
        view.setResolveHours(policy.getResolveHours());
        view.setEnabled(policy.getEnabled());
        return view;
    }
}
