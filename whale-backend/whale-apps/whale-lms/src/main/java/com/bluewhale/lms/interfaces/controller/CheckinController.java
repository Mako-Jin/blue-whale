package com.bluewhale.lms.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsPermissions;
import com.bluewhale.lms.interfaces.support.LmsActorContext;
import com.bluewhale.lms.training.application.command.CheckinSupplementCommand;
import com.bluewhale.lms.training.application.service.CheckinAppService;
import com.bluewhale.lms.training.application.view.CheckinView;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 面授签到接口（M2）。
 *
 * <p>自助签到受场次时间窗限制（结束后有宽限）；补签需管理权限且必须填写原因，
 * 服务端把操作人与备注写入签到记录留痕。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping(path = "/lms/v{version}/training", version = "1")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinAppService checkinAppService;

    /**
     * 自助签到（学员本人）。
     *
     * @param sessionId 场次 ID
     * @param request 当前请求
     * @return 签到记录
     */
    @PostMapping("/session/{sessionId}/checkin")
    @RequirePermission(LmsPermissions.TRAINING_CHECKIN)
    @AuditLog(operation = "培训签到", type = OperateType.CREATE,
            targetType = "lr_checkin")
    public Result<CheckinView> checkin(@PathVariable Long sessionId, HttpServletRequest request) {
        return Result.ok(checkinAppService.checkin(sessionId, actor(request)));
    }

    /**
     * 补签（管理员，需留原因）。
     *
     * @param sessionId 场次 ID
     * @param command 补签命令
     * @param request 当前请求
     * @return 签到记录
     */
    @PostMapping("/session/{sessionId}/checkin/supplement")
    @RequirePermission(LmsPermissions.TRAINING_CHECKIN_MANAGE)
    @AuditLog(operation = "培训补签", type = OperateType.CREATE,
            targetType = "lr_checkin")
    public Result<CheckinView> supplement(@PathVariable Long sessionId,
                                          @Valid @RequestBody CheckinSupplementCommand command,
                                          HttpServletRequest request) {
        return Result.ok(checkinAppService.supplement(sessionId, command, actor(request)));
    }

    /**
     * 签到明细（可按场次过滤）。
     *
     * @param id 培训班 ID
     * @param sessionId 场次 ID，可空
     * @param request 当前请求
     * @return 签到记录列表
     */
    @GetMapping("/{id}/checkin")
    @RequirePermission(LmsPermissions.TRAINING_CHECKIN_MANAGE)
    public Result<List<CheckinView>> list(@PathVariable Long id,
                                          @RequestParam(value = "sessionId", required = false) Long sessionId,
                                          HttpServletRequest request) {
        return Result.ok(checkinAppService.listByClass(id, sessionId, actor(request)));
    }

    /**
     * 解析操作人。
     *
     * @param request 当前请求
     * @return 操作人
     */
    private LmsActor actor(HttpServletRequest request) {
        return LmsActorContext.from(request);
    }
}
