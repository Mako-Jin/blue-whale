package com.bluewhale.oa.collab.interfaces.controller;

import com.bluewhale.auth.sdk.annotation.RequirePermission;
import com.bluewhale.common.audit.annotation.AuditLog;
import com.bluewhale.common.audit.enums.OperateType;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.MeetingAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * OA 会议与会议室接口。
 *
 * <p>服务内路径 {@code /oa/meeting/**}，受 {@code sys:oa:meeting:*} 权限保护。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/meeting")
@RequiredArgsConstructor
public class MeetingController {

    private static final String TARGET_TYPE = "oa_meeting";

    private final MeetingAppService meetingAppService;

    @GetMapping("/rooms/page")
    @RequirePermission("sys:oa:meeting:room:list")
    public Result<PageResult<CollabViews.RoomView>> pageRooms(CollabQuery query) {
        return Result.ok(meetingAppService.pageRooms(query));
    }

    @GetMapping("/rooms/all")
    @RequirePermission("sys:oa:meeting:room:list")
    public Result<List<CollabViews.RoomView>> listRooms(CollabQuery query) {
        return Result.ok(meetingAppService.listRooms(query));
    }

    @PostMapping("/rooms")
    @RequirePermission("sys:oa:meeting:room:manage")
    @AuditLog(operation = "新增会议室", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.RoomView> createRoom(@Valid @RequestBody CollabCommands.RoomSaveCommand command) {
        return Result.ok(meetingAppService.createRoom(command));
    }

    @PutMapping("/rooms/{id}")
    @RequirePermission("sys:oa:meeting:room:manage")
    @AuditLog(operation = "编辑会议室", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.RoomView> updateRoom(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.RoomSaveCommand command) {
        return Result.ok(meetingAppService.updateRoom(id, command));
    }

    @DeleteMapping("/rooms/{id}")
    @RequirePermission("sys:oa:meeting:room:manage")
    @AuditLog(operation = "删除会议室", type = OperateType.DELETE, targetType = TARGET_TYPE)
    public Result<Void> deleteRoom(@PathVariable Long id) {
        meetingAppService.deleteRoom(id);
        return Result.ok();
    }

    @GetMapping("/page")
    @RequirePermission("sys:oa:meeting:list")
    public Result<PageResult<CollabViews.MeetingView>> page(CollabQuery query) {
        return Result.ok(meetingAppService.pageMeetings(query));
    }

    @GetMapping("/{id}")
    @RequirePermission("sys:oa:meeting:list")
    public Result<CollabViews.MeetingDetailView> detail(@PathVariable Long id) {
        return Result.ok(meetingAppService.meetingDetail(id));
    }

    @PostMapping
    @RequirePermission("sys:oa:meeting:manage")
    @AuditLog(operation = "创建会议", type = OperateType.CREATE, targetType = TARGET_TYPE)
    public Result<CollabViews.MeetingDetailView> create(
            @Valid @RequestBody CollabCommands.MeetingSaveCommand command) {
        return Result.ok(meetingAppService.createMeeting(command));
    }

    @PutMapping("/{id}")
    @RequirePermission("sys:oa:meeting:manage")
    @AuditLog(operation = "编辑会议", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.MeetingDetailView> update(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.MeetingSaveCommand command) {
        return Result.ok(meetingAppService.updateMeeting(id, command));
    }

    @PutMapping("/{id}/status")
    @RequirePermission("sys:oa:meeting:manage")
    @AuditLog(operation = "变更会议状态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<Boolean> changeStatus(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.MeetingStatusCommand command) {
        return Result.ok(meetingAppService.changeMeetingStatus(id, command.getStatus()));
    }

    @PutMapping("/{id}/participants/{userId}/status")
    @RequirePermission("sys:oa:meeting:manage")
    @AuditLog(operation = "登记参会状态", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.ParticipantView> changeParticipantStatus(
            @PathVariable Long id, @PathVariable Long userId,
            @Valid @RequestBody CollabCommands.ParticipantStatusCommand command) {
        return Result.ok(meetingAppService.changeParticipantStatus(id, userId, command.getStatus(), command.getRemark()));
    }

    @PutMapping("/{id}/minute")
    @RequirePermission("sys:oa:meeting:manage")
    @AuditLog(operation = "保存会议纪要", type = OperateType.UPDATE, targetType = TARGET_TYPE)
    public Result<CollabViews.MeetingDetailView> saveMinute(
            @PathVariable Long id, @Valid @RequestBody CollabCommands.MinuteSaveCommand command) {
        return Result.ok(meetingAppService.saveMinute(id, command));
    }
}
