package com.bluewhale.crm.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.crm.config.CrmProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 客户关系门禁状态接口。
 *
 * <p>门禁未通过时只返回规划状态与后续里程碑，不暴露任何业务数据；
 * 该接口也不读写客户表，避免在门禁前产生脏数据。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/crm")
@RequiredArgsConstructor
public class CrmMetaController {

    private final CrmProperties crmProperties;

    /**
     * 查询 CRM 门禁状态。
     *
     * @return 门禁状态与后续里程碑
     */
    @GetMapping("/meta")
    @RequirePermission("sys:crm:browse")
    public Result<CrmMetaView> meta() {
        CrmMetaView view = new CrmMetaView();
        view.setDemandGatePassed(crmProperties.isDemandGatePassed());
        view.setMessage(crmProperties.isDemandGatePassed()
                ? "需求门禁已通过，可进入 M4 客户与联系人"
                : "需求门禁未通过：仅保留规划与权限登记，业务接口未启用");
        view.setMilestones(List.of("M4 客户与联系人", "M5 商机与合同联动"));
        view.setBoundaries(List.of(
                "客户主数据归 CRM，供应商归 ERP/SRM，二者不合并",
                "合同只保存 crmCustomerId 快照，不复制客户主数据",
                "审批走 whale-workflow，消息投递走 whale-message，附件走 whale-file"));
        return Result.ok(view);
    }

    /**
     * CRM 门禁状态视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class CrmMetaView {

        /** 需求门禁是否通过 */
        private boolean demandGatePassed;

        /** 状态说明 */
        private String message;

        /** 后续里程碑 */
        private List<String> milestones;

        /** 边界约束 */
        private List<String> boundaries;
    }
}
