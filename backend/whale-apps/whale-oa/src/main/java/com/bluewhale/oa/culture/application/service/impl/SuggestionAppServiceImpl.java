package com.bluewhale.oa.culture.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.SuggestionAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import com.bluewhale.oa.culture.domain.model.Suggestion;
import com.bluewhale.oa.culture.domain.model.SuggestionCategory;
import com.bluewhale.oa.culture.domain.model.SuggestionReply;
import com.bluewhale.oa.culture.domain.model.SuggestionStatus;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 建议箱应用服务实现。
 *
 * <p>匿名建议库内保留提交人用于审计，仅在视图层隐藏。</p>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class SuggestionAppServiceImpl implements SuggestionAppService {

    private final CultureRepository repository;

    @Override
    public PageResult<CultureViews.Suggestion> page(CultureQuery query, Long userId) {
        Long submitterId = Boolean.TRUE.equals(query.getMine()) ? userId : null;
        PageResult<Suggestion> source = repository.pageSuggestions(query, submitterId);
        List<CultureViews.Suggestion> records = source.getRecords().stream()
                .map(SuggestionAppServiceImpl::toView).toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public CultureViews.SuggestionDetail detail(Long id) {
        Suggestion suggestion = requireSuggestion(id);
        CultureViews.SuggestionDetail detail = new CultureViews.SuggestionDetail();
        fillView(detail, suggestion);
        detail.setReplies(repository.listReplies(id).stream()
                .map(SuggestionAppServiceImpl::toReplyView).toList());
        return detail;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Suggestion create(CultureCommands.SuggestionSave command, Long submitterId,
                                          String submitterName) {
        if (submitterId == null) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_INVALID, "提交人不能为空");
        }
        if (!StringUtils.hasText(command.getTitle()) || !StringUtils.hasText(command.getContent())) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_INVALID, "建议标题与内容不能为空");
        }
        Suggestion suggestion = new Suggestion();
        suggestion.setTitle(command.getTitle().trim());
        suggestion.setCategory(StringUtils.hasText(command.getCategory())
                ? SuggestionCategory.of(command.getCategory()).name() : SuggestionCategory.SUGGESTION.name());
        suggestion.setContent(command.getContent());
        suggestion.setAnonymous(Boolean.TRUE.equals(command.getAnonymous()) ? 1 : 0);
        suggestion.setSubmitterId(submitterId);
        suggestion.setSubmitterName(submitterName);
        suggestion.setDeptId(command.getDeptId());
        suggestion.setStatus(SuggestionStatus.PENDING.name());
        repository.saveSuggestion(suggestion);
        return toView(suggestion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Suggestion reply(Long id, CultureCommands.SuggestionReplySave command, Long handlerId,
                                         String handlerName) {
        Suggestion suggestion = requireSuggestion(id);
        if (!StringUtils.hasText(command.getContent())) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_INVALID, "回复内容不能为空");
        }
        if (StringUtils.hasText(command.getStatus())) {
            SuggestionStatus.of(command.getStatus());
        }
        suggestion.reply(handlerId, handlerName, LocalDateTime.now());
        repository.updateSuggestion(suggestion);
        SuggestionReply reply = new SuggestionReply();
        reply.setSuggestionId(id);
        reply.setReplierId(handlerId);
        reply.setReplierName(handlerName);
        reply.setContent(command.getContent().trim());
        repository.saveReply(reply);
        return toView(suggestion);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void close(Long id) {
        Suggestion suggestion = requireSuggestion(id);
        suggestion.close();
        repository.updateSuggestion(suggestion);
    }

    /**
     * 按ID查询建议，不存在时抛出业务异常。
     *
     * @param id 建议ID
     * @return 建议实体
     */
    private Suggestion requireSuggestion(Long id) {
        Suggestion suggestion = repository.findSuggestion(id);
        if (suggestion == null) {
            throw OaErrorCode.exception(OaErrorCode.SUGGESTION_NOT_FOUND);
        }
        return suggestion;
    }

    /**
     * 将建议实体转换为视图对象。
     *
     * @param suggestion 建议实体
     * @return 建议视图
     */
    private static CultureViews.Suggestion toView(Suggestion suggestion) {
        CultureViews.Suggestion view = new CultureViews.Suggestion();
        fillView(view, suggestion);
        return view;
    }

    /**
     * 将建议实体的字段复制到视图，匿名提交时隐藏提交人信息。
     *
     * @param view       建议视图
     * @param suggestion 建议实体
     */
    private static void fillView(CultureViews.Suggestion view, Suggestion suggestion) {
        view.setId(suggestion.getId());
        view.setTitle(suggestion.getTitle());
        view.setCategory(suggestion.getCategory());
        view.setContent(suggestion.getContent());
        view.setAnonymous(suggestion.isAnonymous());
        view.setSubmitterId(suggestion.isAnonymous() ? null : suggestion.getSubmitterId());
        view.setSubmitterName(suggestion.isAnonymous() ? null : suggestion.getSubmitterName());
        view.setDeptId(suggestion.getDeptId());
        view.setStatus(suggestion.getStatus());
        view.setHandlerId(suggestion.getHandlerId());
        view.setHandlerName(suggestion.getHandlerName());
        view.setHandledTime(suggestion.getHandledTime());
        view.setCreateTime(suggestion.getCreateTime());
    }

    /**
     * 将建议回复实体转换为回复视图对象。
     *
     * @param reply 建议回复实体
     * @return 建议回复视图
     */
    private static CultureViews.SuggestionReply toReplyView(SuggestionReply reply) {
        CultureViews.SuggestionReply view = new CultureViews.SuggestionReply();
        view.setId(reply.getId());
        view.setSuggestionId(reply.getSuggestionId());
        view.setReplierId(reply.getReplierId());
        view.setReplierName(reply.getReplierName());
        view.setContent(reply.getContent());
        view.setCreateTime(reply.getCreateTime());
        return view;
    }
}
