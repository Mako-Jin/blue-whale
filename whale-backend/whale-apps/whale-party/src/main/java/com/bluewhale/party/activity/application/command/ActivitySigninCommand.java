package com.bluewhale.party.activity.application.command;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;

import java.util.List;

/**
 * 组织生活签到命令（整场覆盖提交）。
 *
 * <p>按「一次提交整场名单」而不是「逐人点击签到」：党务同志实际是在会后拿签到表批量录入，
 * 逐人调用会让一张 30 人的签到表发 30 个请求，中途失败还会留下半场签到。</p>
 *
 * @author BlueWhale
 */
@Data
public class ActivitySigninCommand {

    /** 签到明细 */
    @NotEmpty(message = "签到明细不能为空")
    @Valid
    private List<SigninItem> items;

    /**
     * 单条签到明细。
     *
     * @author BlueWhale
     */
    @Data
    public static class SigninItem {

        /** 党员档案 ID */
        private Long memberId;

        /** 是否到会 */
        private Boolean present;

        /** 缺席原因 */
        private String absentReason;
    }
}
