package com.bluewhale.oa.hr.interfaces.controller;

import com.bluewhale.common.core.result.Result;
import com.bluewhale.oa.hr.application.view.LeaveConflictView;
import com.bluewhale.oa.hr.domain.model.Leave;
import com.bluewhale.oa.hr.domain.repository.LeaveRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

/**
 * 请假内网只读契约接口。
 *
 * <p>供 whale-lms 在培训班排期时提示「哪些学员当天已批准请假」。网关已将
 * {@code /oa/internal/**} 加入 internal-deny，外部访问一律 403。</p>
 *
 * <p>只读、只查已批准的请假：待审批的还没定，拿它做冲突提示只会制造噪声。</p>
 *
 * @author BlueWhale
 */
@RestController
@RequestMapping("/oa/internal/leave")
@RequiredArgsConstructor
public class OaInternalLeaveController {

    /** 单次查询的申请人数上限 */
    private static final int MAX_USER_IDS = 200;

    /** 单次返回的请假条数上限 */
    private static final int MAX_ROWS = 500;

    private final LeaveRepository leaveRepository;

    /**
     * 查询指定人员在区间内已批准的请假。
     *
     * @param userIds 申请人用户 ID（逗号分隔）
     * @param from 区间起（含）
     * @param to 区间止（含）
     * @return 请假列表
     */
    @GetMapping("/approved")
    public Result<List<LeaveConflictView>> approved(
            @RequestParam("userIds") String userIds,
            @RequestParam("from") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam("to") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        List<Long> ids = parseIds(userIds);
        if (ids.isEmpty() || from == null || to == null || to.isBefore(from)) {
            return Result.ok(List.of());
        }
        List<LeaveConflictView> views = leaveRepository
                .listApprovedOverlapping(ids, from, to, MAX_ROWS)
                .stream().map(this::narrow).toList();
        return Result.ok(views);
    }

    /**
     * 解析逗号分隔的用户 ID（非法片段忽略，不因一个脏值整批失败）。
     *
     * @param raw 原始串
     * @return 用户 ID 列表
     */
    private List<Long> parseIds(String raw) {
        if (raw == null || raw.isBlank()) {
            return List.of();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(text -> !text.isEmpty())
                .limit(MAX_USER_IDS)
                .map(this::toLong)
                .filter(id -> id != null && id > 0)
                .distinct()
                .toList();
    }

    /**
     * 安全转长整型。
     *
     * @param text 文本
     * @return 数值；非数字返回 {@code null}
     */
    private Long toLong(String text) {
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException exception) {
            return null;
        }
    }

    /**
     * 裁成内网契约字段集。
     *
     * @param leave 请假
     * @return 视图
     */
    private LeaveConflictView narrow(Leave leave) {
        LeaveConflictView view = new LeaveConflictView();
        view.setCode(leave.getCode());
        view.setApplicantId(leave.getApplicantId());
        view.setApplicant(leave.getApplicant());
        view.setLeaveType(leave.getLeaveType());
        view.setStartDate(leave.getStartDate());
        view.setEndDate(leave.getEndDate());
        view.setStatus(leave.getStatus() == null ? null : leave.getStatus().name());
        return view;
    }
}
