package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.config.LmsProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 学习培训考试门禁状态接口。
 *
 * <p>门禁通过时返回已启用的里程碑；门禁关闭时只返回规划状态与后续里程碑，
 * 不暴露任何业务数据；该接口不读写学习表。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}", version = "1")
@RequiredArgsConstructor
public class LmsMetaController {

    private final LmsProperties lmsProperties;

    /**
     * 查询 LMS 门禁状态。
     *
     * @return 门禁状态与后续里程碑
     */
    @GetMapping("/meta")
    @RequirePermission("sys:lms:browse")
    public Result<LmsMetaView> meta() {
        LmsMetaView view = new LmsMetaView();
        view.setDemandGatePassed(lmsProperties.isDemandGatePassed());
        view.setMessage(lmsProperties.isDemandGatePassed()
                ? "需求门禁已通过：M0 底座与 M1 课程与学习接口已启用"
                : "需求门禁未通过：仅保留规划与权限登记，业务接口未启用");
        view.setMilestones(List.of(
                "M0 底座", "M1 课程与学习", "M2 培训计划", "M3 题库与考试",
                "M4 证书与学分", "M5 集成收口", "M6 看板与试点"));
        view.setBoundaries(List.of(
                "课件字节归 whale-file，LMS 只保存引用，不点播不转码",
                "通知投递走 whale-message，员工主数据只读引用 ERP",
                "党政工会归 whale-oa 承载，不在本服务建表"));
        return Result.ok(view);
    }

    /**
     * LMS 门禁状态视图。
     *
     * @author BlueWhale
     */
    @Data
    public static class LmsMetaView {

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
