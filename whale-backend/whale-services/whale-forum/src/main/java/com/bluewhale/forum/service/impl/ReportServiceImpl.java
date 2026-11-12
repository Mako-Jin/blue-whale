package com.bluewhale.forum.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.forum.common.ForumActor;
import com.bluewhale.forum.common.ForumErrorCodes;
import com.bluewhale.forum.config.ForumProperties;
import com.bluewhale.forum.dto.ReportCreateRequest;
import com.bluewhale.forum.dto.ReportHandleRequest;
import com.bluewhale.forum.dto.ReportVO;
import com.bluewhale.forum.entity.ForumPost;
import com.bluewhale.forum.entity.ForumReport;
import com.bluewhale.forum.entity.ForumThread;
import com.bluewhale.forum.mapper.ForumPostMapper;
import com.bluewhale.forum.mapper.ForumReportMapper;
import com.bluewhale.forum.mapper.ForumThreadMapper;
import com.bluewhale.forum.service.ForumNotifyService;
import com.bluewhale.forum.service.ModerationService;
import com.bluewhale.forum.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 举报工单实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** 待受理 */
    private static final String STATUS_PENDING = "PENDING";

    /** 处理中 */
    private static final String STATUS_HANDLING = "HANDLING";

    /** 已处置 */
    private static final String STATUS_RESOLVED = "RESOLVED";

    /** 举报不成立 */
    private static final String STATUS_REJECTED = "REJECTED";

    /** 对象类型：帖子 */
    private static final String TYPE_THREAD = "THREAD";

    /** 对象类型：楼层 */
    private static final String TYPE_POST = "POST";

    /** 对象类型：用户 */
    private static final String TYPE_USER = "USER";

    /** 无处置动作 */
    private static final String ACTION_NONE = "NONE";

    private final ForumReportMapper reportMapper;

    private final ForumThreadMapper threadMapper;

    private final ForumPostMapper postMapper;

    private final ModerationService moderationService;

    private final ForumNotifyService notifyService;

    private final ForumProperties properties;

    @Override
    public Long submit(ReportCreateRequest request, ForumActor actor) {
        Long tenantId = TenantContext.requireId();
        if (request.getTargetId() == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "举报对象ID不能为空");
        }
        Long authorId = resolveAuthorId(request.getTargetType(), request.getTargetId());
        if (authorId != null && authorId.equals(actor.userId())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "不能举报自己的内容");
        }
        LocalDateTime since = LocalDateTime.now().minusHours(properties.getReportDuplicateHours());
        ForumReport existing = reportMapper.selectOne(new QueryWrapper<ForumReport>()
                .eq("reporter_id", actor.userId())
                .eq("target_type", request.getTargetType())
                .eq("target_id", request.getTargetId())
                .in("status", STATUS_PENDING, STATUS_HANDLING)
                .ge("create_time", since)
                .last("LIMIT 1"));
        if (existing != null) {
            // 同人同目标窗口内重复举报：返回既有工单，不重复建单也不报错
            return existing.getId();
        }
        ForumReport report = new ForumReport();
        report.setTenantId(tenantId);
        report.setTargetType(request.getTargetType());
        report.setTargetId(request.getTargetId());
        report.setReporterId(actor.userId());
        report.setReportType(request.getReportType());
        report.setDescription(request.getDescription());
        report.setStatus(STATUS_PENDING);
        reportMapper.insert(report);
        return report.getId();
    }

    @Override
    public PageResult<ReportVO> page(String status, Long current, Long size, ForumActor actor) {
        TenantContext.requireId();
        long pageNo = current == null || current < 1 ? 1 : Math.min(current, properties.getMaxPageNumber());
        long pageSize = size == null || size < 1
                ? properties.getPageSizeDefault()
                : Math.min(size, properties.getPageSizeMax());
        QueryWrapper<ForumReport> query = new QueryWrapper<>();
        if (status != null && !status.isBlank()) {
            query.eq("status", status.trim().toUpperCase());
        }
        query.orderByDesc("create_time");
        IPage<ForumReport> page = reportMapper.selectPage(new Page<>(pageNo, pageSize), query);
        Map<Long, String> titles = resolveTitles(page.getRecords());
        List<ReportVO> records = new ArrayList<>(page.getRecords().size());
        for (ForumReport report : page.getRecords()) {
            records.add(toVO(report, titles));
        }
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void handle(Long id, ReportHandleRequest request, ForumActor actor) {
        TenantContext.requireId();
        ForumReport report = id == null ? null : reportMapper.selectById(id);
        if (report == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "工单不存在");
        }
        if (!STATUS_PENDING.equals(report.getStatus()) && !STATUS_HANDLING.equals(report.getStatus())) {
            throw new BizException(ForumErrorCodes.THREAD_STATE_ILLEGAL,
                    "工单当前状态为 " + report.getStatus() + "，不可重复处理");
        }
        String action = request.getAction() == null ? ACTION_NONE : request.getAction();
        String remark = request.getRemark();
        if (STATUS_REJECTED.equals(request.getStatus()) && (remark == null || remark.isBlank())) {
            throw new BizException(ForumErrorCodes.AUDIT_REMARK_REQUIRED, "驳回举报必须填写理由");
        }
        if (STATUS_RESOLVED.equals(request.getStatus()) && !ACTION_NONE.equals(action)) {
            applyAction(report, action, request.getMuteDays(), remark, actor);
        }
        report.setStatus(request.getStatus());
        report.setHandlerId(actor.userId());
        report.setHandleRemark(remark);
        report.setHandleTime(LocalDateTime.now());
        reportMapper.updateById(report);

        boolean resolved = STATUS_RESOLVED.equals(request.getStatus());
        String title = resolved ? "你的举报已处理" : "你的举报已结案";
        String content = "工单 #" + report.getId() + "（" + typeLabel(report.getTargetType()) + "）："
                + (remark == null || remark.isBlank() ? (resolved ? "已处置" : "举报不成立") : remark);
        // 只通知举报人；被处置人由 ModerationService 另行通知，不被举报时不会收到任何消息
        notifyService.enqueue(report.getReporterId(), "MODERATION", report.getId(),
                "forum:report:" + report.getId() + ":reporter", title, content);
    }

    /**
     * 执行工单附带处置。
     *
     * @param report 工单
     * @param action REMOVE / MUTE / BAN
     * @param muteDays 天数
     * @param reason 原因
     * @param actor 操作者
     */
    private void applyAction(ForumReport report, String action, Integer muteDays, String reason, ForumActor actor) {
        if ("REMOVE".equals(action)) {
            if (TYPE_THREAD.equals(report.getTargetType())) {
                moderationService.removeThread(report.getTargetId(), reason, actor);
            } else if (TYPE_POST.equals(report.getTargetType())) {
                moderationService.removePost(report.getTargetId(), reason, actor);
            } else {
                throw new BizException(ErrorCode.PARAM_ERROR, "用户类举报不支持删除动作，请使用禁言/封号");
            }
            return;
        }
        if ("MUTE".equals(action) || "BAN".equals(action)) {
            Long userId = TYPE_USER.equals(report.getTargetType())
                    ? report.getTargetId()
                    : resolveAuthorId(report.getTargetType(), report.getTargetId());
            if (userId == null) {
                throw new BizException(ErrorCode.PARAM_ERROR, "无法定位被举报用户");
            }
            int days = muteDays == null ? 0 : muteDays;
            moderationService.ban(userId, action, days, reason, actor, report.getId());
        }
    }

    /**
     * 取对象作者。
     *
     * @param targetType 类型
     * @param targetId 对象ID
     * @return 作者ID（USER 类型直接返回 targetId；对象不存在抛 30001）
     */
    private Long resolveAuthorId(String targetType, Long targetId) {
        if (TYPE_USER.equals(targetType)) {
            return targetId;
        }
        if (TYPE_THREAD.equals(targetType)) {
            ForumThread thread = threadMapper.selectById(targetId);
            if (thread == null) {
                throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "举报的帖子不存在或已删除");
            }
            return thread.getAuthorId();
        }
        if (TYPE_POST.equals(targetType)) {
            ForumPost post = postMapper.selectById(targetId);
            if (post == null) {
                throw new BizException(ForumErrorCodes.THREAD_NOT_FOUND, "举报的楼层不存在或已删除");
            }
            return post.getAuthorId();
        }
        throw new BizException(ErrorCode.PARAM_ERROR, "举报对象类型不合法");
    }

    /**
     * 批量解析举报对象的标题（避免逐条查询）。
     *
     * @param reports 工单列表
     * @return targetType:targetId → 标题
     */
    private Map<Long, String> resolveTitles(List<ForumReport> reports) {
        List<Long> threadIds = reports.stream()
                .filter(r -> TYPE_THREAD.equals(r.getTargetType()))
                .map(ForumReport::getTargetId)
                .distinct()
                .toList();
        Map<Long, String> result = new HashMap<>();
        if (threadIds.isEmpty()) {
            return result;
        }
        for (ForumThread thread : threadMapper.selectByIds(threadIds)) {
            result.put(thread.getId(), thread.getTitle());
        }
        return result;
    }

    /**
     * 工单转视图。
     *
     * @param report 工单
     * @param titles 帖子标题缓存
     * @return 视图
     */
    private ReportVO toVO(ForumReport report, Map<Long, String> titles) {
        ReportVO vo = new ReportVO();
        vo.setId(report.getId());
        vo.setTargetType(report.getTargetType());
        vo.setTargetId(report.getTargetId());
        vo.setTargetTitle(TYPE_THREAD.equals(report.getTargetType())
                ? titles.getOrDefault(report.getTargetId(), "（已删除）")
                : typeLabel(report.getTargetType()) + " #" + report.getTargetId());
        vo.setReporterId(report.getReporterId());
        vo.setReportType(report.getReportType());
        vo.setDescription(report.getDescription());
        vo.setStatus(report.getStatus());
        vo.setHandlerId(report.getHandlerId());
        vo.setHandleRemark(report.getHandleRemark());
        vo.setHandleTime(report.getHandleTime());
        vo.setCreateTime(report.getCreateTime());
        return vo;
    }

    /**
     * 类型中文名。
     *
     * @param targetType 类型
     * @return 中文名
     */
    private String typeLabel(String targetType) {
        return switch (targetType == null ? "" : targetType) {
            case TYPE_POST -> "回复";
            case TYPE_USER -> "用户";
            default -> "帖子";
        };
    }
}
