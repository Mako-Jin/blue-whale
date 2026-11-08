package com.bluewhale.oa.culture.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.culture.application.command.CultureCommands;
import com.bluewhale.oa.culture.application.service.CultureEventAppService;
import com.bluewhale.oa.culture.application.view.CultureViews;
import com.bluewhale.oa.culture.domain.model.CultureEvent;
import com.bluewhale.oa.culture.domain.model.CultureEventStatus;
import com.bluewhale.oa.culture.domain.model.CultureEventType;
import com.bluewhale.oa.culture.domain.model.CultureQuery;
import com.bluewhale.oa.culture.domain.repository.CultureRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 文化动态应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CultureEventAppServiceImpl implements CultureEventAppService {

    private final CultureRepository repository;

    @Override
    public PageResult<CultureViews.Event> page(CultureQuery query) {
        PageResult<CultureEvent> source = repository.pageEvents(query);
        List<CultureViews.Event> records = source.getRecords().stream()
                .map(CultureEventAppServiceImpl::toView).toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public CultureViews.Event detail(Long id) {
        return toView(requireEvent(id));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Event create(CultureCommands.EventSave command) {
        CultureEvent event = new CultureEvent();
        fill(event, command);
        event.setStatus(CultureEventStatus.DRAFT.name());
        repository.saveEvent(event);
        return toView(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Event update(Long id, CultureCommands.EventSave command) {
        CultureEvent event = requireEvent(id);
        event.assertEditable();
        fill(event, command);
        repository.updateEvent(event);
        return toView(event);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        CultureEvent event = requireEvent(id);
        event.assertEditable();
        repository.deleteEvent(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CultureViews.Event publish(Long id, Long operatorId, String operator) {
        CultureEvent event = requireEvent(id);
        if (CultureEventStatus.of(event.getStatus()) == CultureEventStatus.PUBLISHED) {
            return toView(event);
        }
        event.publish(operatorId, operator, LocalDateTime.now());
        repository.updateEvent(event);
        return toView(event);
    }

    /**
     * 按ID查询文化动态，不存在时抛出业务异常。
     *
     * @param id 文化动态ID
     * @return 文化动态实体
     */
    private CultureEvent requireEvent(Long id) {
        CultureEvent event = repository.findEvent(id);
        if (event == null) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_EVENT_NOT_FOUND);
        }
        return event;
    }

    /**
     * 将保存命令中的字段写入文化动态实体，并校验标题非空。
     *
     * @param event 文化动态实体
     * @param command 保存命令
     */
    private void fill(CultureEvent event, CultureCommands.EventSave command) {
        String type = StringUtils.hasText(command.getEventType())
                ? CultureEventType.of(command.getEventType()).name() : CultureEventType.ACTIVITY.name();
        if (!StringUtils.hasText(command.getTitle())) {
            throw OaErrorCode.exception(OaErrorCode.CULTURE_INVALID, "文化动态标题不能为空");
        }
        event.setEventType(type);
        event.setTitle(command.getTitle().trim());
        event.setContent(command.getContent());
        event.setEventDate(command.getEventDate());
        event.setRemark(command.getRemark());
    }

    /**
     * 将文化动态实体转换为视图对象。
     *
     * @param event 文化动态实体
     * @return 文化动态视图
     */
    private static CultureViews.Event toView(CultureEvent event) {
        CultureViews.Event view = new CultureViews.Event();
        view.setId(event.getId());
        view.setEventType(event.getEventType());
        view.setTitle(event.getTitle());
        view.setContent(event.getContent());
        view.setEventDate(event.getEventDate());
        view.setCoverFileId(event.getCoverFileId());
        view.setStatus(event.getStatus());
        view.setPublisherId(event.getPublisherId());
        view.setPublisherName(event.getPublisherName());
        view.setPublishTime(event.getPublishTime());
        view.setRemark(event.getRemark());
        view.setCreateTime(event.getCreateTime());
        return view;
    }
}
