package com.bluewhale.forum.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.forum.common.ForumPermissions;
import com.bluewhale.forum.common.enums.AuditMode;
import com.bluewhale.forum.common.enums.BoardVisibility;
import com.bluewhale.forum.common.enums.ModerationAction;
import com.bluewhale.forum.common.enums.ReactionType;
import com.bluewhale.forum.common.enums.ReportStatus;
import com.bluewhale.forum.common.enums.ThreadStatus;
import com.bluewhale.forum.common.enums.ThreadType;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ForumMetaVO;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;

/**
 * 论坛门面接口（M0）：枚举与上限一次性下发。
 *
 * <p>该接口同时是起服冒烟探针：返回 {@code code=0} 说明服务、网关路由、
 * 权限码头与配置装载四件事都通了。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/forum/v{version}/meta", version = "1")
@RequiredArgsConstructor
public class ForumMetaController {

    /** 版本标识 */
    private static final String VERSION = "M0";

    private final ForumProperties properties;

    /**
     * 门面信息。
     *
     * @return 枚举与上限
     */
    @GetMapping
    @RequirePermission(ForumPermissions.BROWSE)
    public Result<ForumMetaVO> meta() {
        ForumMetaVO view = new ForumMetaVO();
        view.setService("whale-forum");
        view.setVersion(VERSION);
        view.setThreadTypes(Arrays.stream(ThreadType.values()).map(Enum::name).toList());
        view.setThreadStatuses(Arrays.stream(ThreadStatus.values()).map(Enum::name).toList());
        view.setBoardVisibilities(Arrays.stream(BoardVisibility.values()).map(Enum::name).toList());
        view.setAuditModes(Arrays.stream(AuditMode.values()).map(Enum::name).toList());
        view.setReportStatuses(Arrays.stream(ReportStatus.values()).map(Enum::name).toList());
        view.setReactionTypes(Arrays.stream(ReactionType.values()).map(Enum::name).toList());
        view.setModerationActions(Arrays.stream(ModerationAction.values()).map(Enum::name).toList());
        // fail-closed：密钥未配置时匿名入口直接关闭，避免编号可预测导致封禁可绕过
        view.setAnonymousEnabled(properties.getAnonymousSecret() != null
                && !properties.getAnonymousSecret().isBlank());
        view.setLimits(limits());
        return Result.ok(view);
    }

    /**
     * 把配置项收敛为前端可见的限额视图，避免把 properties 对象整体下发。
     *
     * @return 论坛限额视图
     */
    private ForumMetaVO.Limits limits() {
        ForumMetaVO.Limits limits = new ForumMetaVO.Limits();
        limits.setMaxTitleLength(properties.getMaxTitleLength());
        limits.setMaxContentLength(properties.getMaxContentLength());
        limits.setMaxPostsPerThread(properties.getMaxPostsPerThread());
        limits.setPageSizeDefault(properties.getPageSizeDefault());
        limits.setPageSizeMax(properties.getPageSizeMax());
        limits.setMaxPageNumber(properties.getMaxPageNumber());
        limits.setMaxMentionsPerPost(properties.getMaxMentionsPerPost());
        limits.setPostIntervalSeconds(properties.getPostIntervalSeconds());
        limits.setReplyIntervalSeconds(properties.getReplyIntervalSeconds());
        limits.setHotRefreshMinutes(properties.getHotRefreshMinutes());
        return limits;
    }
}
