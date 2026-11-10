package com.bluewhale.oa.collab.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.collab.application.command.CollabCommands;
import com.bluewhale.oa.collab.application.service.NoticeAppService;
import com.bluewhale.oa.collab.application.view.CollabViews;
import com.bluewhale.oa.collab.domain.gateway.CollabUserDirectoryGateway;
import com.bluewhale.oa.collab.domain.model.CollabQuery;
import com.bluewhale.oa.collab.domain.model.Notice;
import com.bluewhale.oa.collab.domain.model.NoticeRecipient;
import com.bluewhale.oa.collab.domain.model.NoticeScopeType;
import com.bluewhale.oa.collab.domain.model.NoticeStatus;
import com.bluewhale.oa.collab.domain.model.UserCandidate;
import com.bluewhale.oa.collab.domain.repository.CollabRepository;
import com.bluewhale.oa.common.OaErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 公告应用服务实现。
 *
 * <p>发布时若未显式指定接收人：全员公告从 Auth 内网通讯录解析，部门/指定用户公告要求显式选择；
 * 接收人落库为快照，已读状态按快照统计。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class NoticeAppServiceImpl implements NoticeAppService {

    private static final int MAX_RESOLVE_RECIPIENTS = 2000;

    private static final int MAX_CANDIDATE_LIMIT = 200;

    private final CollabRepository repository;

    private final CollabUserDirectoryGateway userDirectoryGateway;

    @Override
    public PageResult<CollabViews.NoticeView> pageNotices(CollabQuery query) {
        PageResult<Notice> page = repository.pageNotices(query);
        List<CollabViews.NoticeView> records = page.getRecords().stream()
                .map(NoticeAppServiceImpl::toNoticeView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public PageResult<CollabViews.NoticeView> pageMyNotices(CollabQuery query) {
        PageResult<Notice> page = repository.pageMyNotices(query);
        List<CollabViews.NoticeView> records = page.getRecords().stream()
                .map(NoticeAppServiceImpl::toNoticeView).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    public CollabViews.NoticeDetailView noticeDetail(Long id) {
        return toNoticeDetail(requireNotice(id));
    }

    @Override
    public List<CollabViews.CandidateView> recipientCandidates(String keyword, Long deptId, int limit) {
        int safeLimit = Math.min(Math.max(limit, 1), MAX_CANDIDATE_LIMIT);
        return userDirectoryGateway.listUsers(keyword, deptId, safeLimit).stream()
                .map(NoticeAppServiceImpl::toCandidateView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.NoticeView createNotice(CollabCommands.NoticeSaveCommand command) {
        Notice notice = new Notice();
        fillNotice(notice, command);
        notice.setStatus(NoticeStatus.DRAFT.name());
        repository.saveNotice(notice);
        if (command.getRecipients() != null && !command.getRecipients().isEmpty()) {
            repository.saveRecipients(buildRecipients(notice.getId(), command.getRecipients()));
        }
        return toNoticeView(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.NoticeView updateNotice(Long id, CollabCommands.NoticeSaveCommand command) {
        Notice notice = requireNotice(id);
        notice.assertEditable();
        fillNotice(notice, command);
        repository.updateNotice(notice);
        if (command.getRecipients() != null) {
            repository.deleteRecipients(id);
            if (!command.getRecipients().isEmpty()) {
                repository.saveRecipients(buildRecipients(id, command.getRecipients()));
            }
        }
        return toNoticeView(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.NoticeView publishNotice(Long id, Long operatorId, String operator) {
        Notice notice = requireNotice(id);
        notice.assertEditable();
        if (repository.listRecipients(id).isEmpty()) {
            List<NoticeRecipient> resolved = resolveRecipients(notice);
            if (resolved.isEmpty()) {
                throw OaErrorCode.exception(OaErrorCode.NOTICE_RECIPIENT_EMPTY);
            }
            repository.saveRecipients(resolved);
        }
        String publisher = StringUtils.hasText(operator) ? operator : String.valueOf(operatorId);
        notice.publish(publisher, operatorId, LocalDateTime.now());
        repository.updateNotice(notice);
        return toNoticeView(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CollabViews.NoticeView withdrawNotice(Long id) {
        Notice notice = requireNotice(id);
        notice.withdraw();
        repository.updateNotice(notice);
        return toNoticeView(notice);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void markRead(Long id, Long userId) {
        Notice notice = requireNotice(id);
        if (NoticeStatus.of(notice.getStatus()) != NoticeStatus.PUBLISHED) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_STATUS_CONFLICT, "仅已发布的公告可标记已读");
        }
        NoticeRecipient recipient = repository.findRecipient(id, userId);
        if (recipient == null) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_RECIPIENT_NOT_FOUND);
        }
        if (recipient.getReadFlag() != null && recipient.getReadFlag() == 1) {
            return;
        }
        recipient.setReadFlag(1);
        recipient.setReadTime(LocalDateTime.now());
        repository.updateRecipient(recipient);
    }

    @Override
    public CollabViews.NoticeDetailView readStats(Long id) {
        return toNoticeDetail(requireNotice(id));
    }

    // ---------------------------------------------------------------- 私有方法

    /**
     * 将公告命令字段填充到公告模型，并校验部门公告必须指定部门。
     *
     * @param notice 目标公告模型
     * @param command 公告保存命令
     */
    private void fillNotice(Notice notice, CollabCommands.NoticeSaveCommand command) {
        String scope = StringUtils.hasText(command.getScopeType())
                ? NoticeScopeType.of(command.getScopeType()).name() : NoticeScopeType.ALL.name();
        if (NoticeScopeType.of(scope) == NoticeScopeType.DEPT && !StringUtils.hasText(command.getScopeValue())) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_INVALID, "部门公告需指定部门");
        }
        notice.setTitle(command.getTitle().trim());
        notice.setContent(command.getContent());
        notice.setScopeType(scope);
        notice.setScopeValue(command.getScopeValue());
        notice.setRemark(command.getRemark());
    }

    /**
     * 构建公告接收人快照列表，校验用户ID有效且不重复。
     *
     * @param noticeId 公告ID
     * @param commands 接收人命令列表
     * @return 接收人快照列表
     */
    private List<NoticeRecipient> buildRecipients(Long noticeId, List<CollabCommands.RecipientCommand> commands) {
        Map<Long, NoticeRecipient> unique = new LinkedHashMap<>();
        for (CollabCommands.RecipientCommand item : commands) {
            if (item.getUserId() == null || unique.containsKey(item.getUserId())) {
                throw OaErrorCode.exception(OaErrorCode.NOTICE_INVALID, "接收人重复或用户ID为空");
            }
            NoticeRecipient recipient = new NoticeRecipient();
            recipient.setNoticeId(noticeId);
            recipient.setUserId(item.getUserId());
            recipient.setUserName(item.getUserName());
            recipient.setReadFlag(0);
            unique.put(item.getUserId(), recipient);
        }
        return List.copyOf(unique.values());
    }

    /**
     * 按公告范围解析接收人：仅全员公告从通讯录拉取，其它范围返回空列表。
     *
     * @param notice 公告模型
     * @return 解析得到的接收人快照列表
     */
    private List<NoticeRecipient> resolveRecipients(Notice notice) {
        if (NoticeScopeType.of(notice.getScopeType()) != NoticeScopeType.ALL) {
            return List.of();
        }
        List<UserCandidate> candidates = userDirectoryGateway.listUsers(null, null, MAX_RESOLVE_RECIPIENTS);
        return candidates.stream().map(candidate -> {
            NoticeRecipient recipient = new NoticeRecipient();
            recipient.setNoticeId(notice.getId());
            recipient.setUserId(candidate.getUserId());
            recipient.setUserName(StringUtils.hasText(candidate.getNickname())
                    ? candidate.getNickname() : candidate.getUsername());
            recipient.setReadFlag(0);
            return recipient;
        }).toList();
    }

    /**
     * 查询公告，不存在时抛出业务异常。
     *
     * @param id 公告ID
     * @return 公告模型
     */
    private Notice requireNotice(Long id) {
        Notice notice = repository.findNotice(id);
        if (notice == null) {
            throw OaErrorCode.exception(OaErrorCode.NOTICE_NOT_FOUND);
        }
        return notice;
    }

    /**
     * 将公告模型转换为详情视图，补充接收人列表、接收数与已读数。
     *
     * @param notice 公告模型
     * @return 公告详情视图
     */
    private CollabViews.NoticeDetailView toNoticeDetail(Notice notice) {
        List<NoticeRecipient> recipients = repository.listRecipients(notice.getId());
        CollabViews.NoticeDetailView view = new CollabViews.NoticeDetailView();
        CollabViews.NoticeView base = toNoticeView(notice);
        view.setId(base.getId());
        view.setTitle(base.getTitle());
        view.setContent(base.getContent());
        view.setScopeType(base.getScopeType());
        view.setScopeValue(base.getScopeValue());
        view.setStatus(base.getStatus());
        view.setPublisher(base.getPublisher());
        view.setPublisherId(base.getPublisherId());
        view.setPublishTime(base.getPublishTime());
        view.setRemark(base.getRemark());
        view.setCreateTime(base.getCreateTime());
        view.setMyReadFlag(base.getMyReadFlag());
        view.setMyReadTime(base.getMyReadTime());
        view.setRecipientCount((long) recipients.size());
        view.setReadCount(recipients.stream()
                .filter(item -> item.getReadFlag() != null && item.getReadFlag() == 1).count());
        view.setRecipients(recipients.stream().map(NoticeAppServiceImpl::toRecipientView).toList());
        return view;
    }

    /**
     * 将公告模型转换为公告视图。
     *
     * @param notice 公告模型
     * @return 公告视图
     */
    private static CollabViews.NoticeView toNoticeView(Notice notice) {
        CollabViews.NoticeView view = new CollabViews.NoticeView();
        view.setId(notice.getId());
        view.setTitle(notice.getTitle());
        view.setContent(notice.getContent());
        view.setScopeType(notice.getScopeType());
        view.setScopeValue(notice.getScopeValue());
        view.setStatus(notice.getStatus());
        view.setPublisher(notice.getPublisher());
        view.setPublisherId(notice.getPublisherId());
        view.setPublishTime(notice.getPublishTime());
        view.setRemark(notice.getRemark());
        view.setCreateTime(notice.getCreateTime());
        view.setMyReadFlag(notice.getMyReadFlag());
        view.setMyReadTime(notice.getMyReadTime());
        return view;
    }

    /**
     * 将公告接收人模型转换为接收人视图。
     *
     * @param recipient 公告接收人模型
     * @return 接收人视图
     */
    private static CollabViews.RecipientView toRecipientView(NoticeRecipient recipient) {
        CollabViews.RecipientView view = new CollabViews.RecipientView();
        view.setId(recipient.getId());
        view.setUserId(recipient.getUserId());
        view.setUserName(recipient.getUserName());
        view.setReadFlag(recipient.getReadFlag());
        view.setReadTime(recipient.getReadTime());
        return view;
    }

    /**
     * 将通讯录用户候选模型转换为候选视图。
     *
     * @param candidate 通讯录用户候选模型
     * @return 候选视图
     */
    private static CollabViews.CandidateView toCandidateView(UserCandidate candidate) {
        CollabViews.CandidateView view = new CollabViews.CandidateView();
        view.setUserId(candidate.getUserId());
        view.setUsername(candidate.getUsername());
        view.setNickname(candidate.getNickname());
        view.setDeptId(candidate.getDeptId());
        return view;
    }
}
