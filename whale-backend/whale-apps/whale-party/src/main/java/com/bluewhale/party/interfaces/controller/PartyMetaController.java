package com.bluewhale.party.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.party.common.PartyPermissions;
import com.bluewhale.party.config.PartyProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 党政工会门禁状态接口。
 *
 * <p>门禁通过时返回已启用的里程碑；门禁关闭时只返回规划状态与后续里程碑，
 * 不暴露任何业务数据；该接口不读写党工业务表。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/party/v{version}", version = "1")
@RequiredArgsConstructor
public class PartyMetaController {

    private final PartyProperties partyProperties;

    /**
     * 查询党政工会门禁状态。
     *
     * @return 门禁状态与后续里程碑
     */
    @GetMapping("/meta")
    @RequirePermission(PartyPermissions.BROWSE)
    public Result<PartyMetaView> meta() {
        PartyMetaView view = new PartyMetaView();
        view.setDemandGatePassed(partyProperties.isDemandGatePassed());
        view.setMessage(partyProperties.isDemandGatePassed()
                ? "需求门禁已通过：M-P0 底座已启用，党建与工会业务接口随波次开放"
                : "需求门禁未通过：仅保留规划与权限登记，业务接口未启用");
        view.setMilestones(List.of(
                "M-P0 底座", "M-P1 党建台账", "M-P2 党建内容与先审后发",
                "M-P3 工会", "M-P4 看板与收口"));
        view.setBoundaries(List.of(
                "员工与组织主数据只读引用 ERP（含 orgCode），本服务不回写人事数据",
                "附件字节归 whale-file，本服务只保存引用，不转码",
                "通知投递走 whale-message，慰问与福利审批走 whale-workflow",
                "不读 bluewhale_oa 任何表；党建内容与 OA 公告各自独立，不互相同步"));
        return Result.ok(view);
    }

    /**
     * 党政工会门禁状态视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class PartyMetaView {

        /** 需求门禁是否通过 */
        private boolean demandGatePassed;

        /** 门禁说明 */
        private String message;

        /** 里程碑列表 */
        private List<String> milestones;

        /** 领域边界说明 */
        private List<String> boundaries;
    }
}
