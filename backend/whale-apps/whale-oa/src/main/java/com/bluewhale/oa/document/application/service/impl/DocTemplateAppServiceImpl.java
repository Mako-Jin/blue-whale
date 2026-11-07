package com.bluewhale.oa.document.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.common.OaErrorCode;
import com.bluewhale.oa.document.application.command.DocumentCommands;
import com.bluewhale.oa.document.application.service.DocTemplateAppService;
import com.bluewhale.oa.document.application.view.DocumentViews;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocStatus;
import com.bluewhale.oa.document.domain.model.DocTemplate;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Set;

/**
 * 公文模板应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class DocTemplateAppServiceImpl implements DocTemplateAppService {

    /** 合法适用范围 */
    private static final Set<String> DOC_TYPES = Set.of(DocStatus.TYPE_OUTGOING, DocStatus.TYPE_INCOMING);

    /** 合法模板状态 */
    private static final Set<String> STATUSES = Set.of(
            DocStatus.TEMPLATE_DRAFT, DocStatus.TEMPLATE_RELEASED, DocStatus.TEMPLATE_DISABLED);

    private final DocumentRepository repository;

    @Override
    public PageResult<DocumentViews.Template> page(DocQuery query) {
        PageResult<DocTemplate> source = repository.pageTemplates(query);
        List<DocumentViews.Template> records = source.getRecords().stream().map(this::toView).toList();
        return PageResult.of(records, source.getTotal(), source.getCurrent(), source.getSize());
    }

    @Override
    public List<DocumentViews.Template> all(String docType) {
        return repository.listTemplates(docType).stream().map(this::toView).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Template create(DocumentCommands.TemplateSave command) {
        String docType = normalizeDocType(command.getDocType());
        Integer version = command.getVersion() == null ? 1 : command.getVersion();
        if (repository.templateExists(command.getCode(), version, null)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_TEMPLATE_EXISTS);
        }
        DocTemplate template = new DocTemplate();
        apply(template, command);
        template.setDocType(docType);
        template.setVersion(version);
        template.setStatus(normalizeStatus(command.getStatus()));
        repository.saveTemplate(template);
        return toView(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocumentViews.Template update(Long id, DocumentCommands.TemplateSave command) {
        DocTemplate template = requireTemplate(id);
        String docType = normalizeDocType(command.getDocType());
        Integer version = command.getVersion() == null ? template.getVersion() : command.getVersion();
        if (repository.templateExists(command.getCode(), version, id)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_TEMPLATE_EXISTS);
        }
        apply(template, command);
        template.setDocType(docType);
        template.setVersion(version);
        template.setStatus(normalizeStatus(command.getStatus()));
        repository.updateTemplate(template);
        return toView(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireTemplate(id);
        repository.deleteTemplate(id);
    }

    private DocTemplate requireTemplate(Long id) {
        DocTemplate template = repository.findTemplate(id);
        if (template == null) {
            throw OaErrorCode.exception(OaErrorCode.DOC_TEMPLATE_NOT_FOUND);
        }
        return template;
    }

    private void apply(DocTemplate template, DocumentCommands.TemplateSave command) {
        template.setCode(command.getCode().trim());
        template.setName(command.getName().trim());
        template.setHeaderText(command.getHeaderText());
        template.setFooterText(command.getFooterText());
        template.setBodyTemplate(command.getBodyTemplate());
        template.setRemark(command.getRemark());
    }

    private String normalizeDocType(String docType) {
        String value = StringUtils.hasText(docType) ? docType.trim().toUpperCase() : DocStatus.TYPE_OUTGOING;
        if (!DOC_TYPES.contains(value)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return value;
    }

    private String normalizeStatus(String status) {
        String value = StringUtils.hasText(status) ? status.trim().toUpperCase() : DocStatus.TEMPLATE_DRAFT;
        if (!STATUSES.contains(value)) {
            throw OaErrorCode.exception(OaErrorCode.DOC_INVALID);
        }
        return value;
    }

    private DocumentViews.Template toView(DocTemplate template) {
        DocumentViews.Template view = new DocumentViews.Template();
        BeanUtils.copyProperties(template, view);
        return view;
    }
}
