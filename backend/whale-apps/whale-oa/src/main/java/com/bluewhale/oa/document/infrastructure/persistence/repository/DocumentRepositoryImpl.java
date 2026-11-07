package com.bluewhale.oa.document.infrastructure.persistence.repository;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.oa.document.domain.model.DocArchiveRef;
import com.bluewhale.oa.document.domain.model.DocAttachment;
import com.bluewhale.oa.document.domain.model.DocIncoming;
import com.bluewhale.oa.document.domain.model.DocNoRule;
import com.bluewhale.oa.document.domain.model.DocOutgoing;
import com.bluewhale.oa.document.domain.model.DocQuery;
import com.bluewhale.oa.document.domain.model.DocTemplate;
import com.bluewhale.oa.document.domain.repository.DocumentRepository;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocArchiveRefEntity;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocAttachmentEntity;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocIncomingEntity;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocNoRuleEntity;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocOutgoingEntity;
import com.bluewhale.oa.document.infrastructure.persistence.entity.DocTemplateEntity;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocArchiveRefMapper;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocAttachmentMapper;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocIncomingMapper;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocNoRuleMapper;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocOutgoingMapper;
import com.bluewhale.oa.document.infrastructure.persistence.mapper.DocTemplateMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Repository;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 公文仓储实现。
 *
 * @author BlueWhale
 */
@Repository
@RequiredArgsConstructor
public class DocumentRepositoryImpl implements DocumentRepository {

    private final DocTemplateMapper templateMapper;

    private final DocNoRuleMapper noRuleMapper;

    private final DocOutgoingMapper outgoingMapper;

    private final DocIncomingMapper incomingMapper;

    private final DocAttachmentMapper attachmentMapper;

    private final DocArchiveRefMapper archiveRefMapper;

    @Override
    public PageResult<DocTemplate> pageTemplates(DocQuery query) {
        LambdaQueryWrapper<DocTemplateEntity> wrapper = Wrappers.lambdaQuery(DocTemplateEntity.class)
                .eq(StringUtils.hasText(query.getStatus()), DocTemplateEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDocType()), DocTemplateEntity::getDocType, query.getDocType())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(DocTemplateEntity::getCode, query.getKeyword())
                        .or()
                        .like(DocTemplateEntity::getName, query.getKeyword()))
                .orderByDesc(DocTemplateEntity::getUpdateTime);
        Page<DocTemplateEntity> page = templateMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page.convert(this::toTemplate));
    }

    @Override
    public List<DocTemplate> listTemplates(String docType) {
        return templateMapper.selectList(Wrappers.lambdaQuery(DocTemplateEntity.class)
                        .eq(StringUtils.hasText(docType), DocTemplateEntity::getDocType, docType)
                        .orderByAsc(DocTemplateEntity::getCode)
                        .orderByDesc(DocTemplateEntity::getVersion))
                .stream()
                .map(this::toTemplate)
                .toList();
    }

    @Override
    public DocTemplate findTemplate(Long id) {
        DocTemplateEntity entity = templateMapper.selectById(id);
        return entity == null ? null : toTemplate(entity);
    }

    @Override
    public boolean templateExists(String code, Integer version, Long excludeId) {
        return templateMapper.selectCount(Wrappers.lambdaQuery(DocTemplateEntity.class)
                .eq(DocTemplateEntity::getCode, code)
                .eq(DocTemplateEntity::getVersion, version)
                .ne(excludeId != null, DocTemplateEntity::getId, excludeId)) > 0;
    }

    @Override
    public void saveTemplate(DocTemplate template) {
        DocTemplateEntity entity = new DocTemplateEntity();
        BeanUtils.copyProperties(template, entity);
        templateMapper.insert(entity);
        template.setId(entity.getId());
    }

    @Override
    public void updateTemplate(DocTemplate template) {
        DocTemplateEntity entity = new DocTemplateEntity();
        BeanUtils.copyProperties(template, entity);
        entity.setUpdateTime(LocalDateTime.now());
        templateMapper.updateById(entity);
    }

    @Override
    public void deleteTemplate(Long id) {
        templateMapper.deleteById(id);
    }

    @Override
    public DocNoRule findRule(Long id) {
        DocNoRuleEntity entity = noRuleMapper.selectById(id);
        return entity == null ? null : toRule(entity);
    }

    @Override
    public DocNoRule findRuleByCode(String code) {
        DocNoRuleEntity entity = noRuleMapper.selectOne(Wrappers.lambdaQuery(DocNoRuleEntity.class)
                .eq(DocNoRuleEntity::getCode, code)
                .last("LIMIT 1"));
        return entity == null ? null : toRule(entity);
    }

    @Override
    public List<DocNoRule> listRules() {
        return noRuleMapper.selectList(Wrappers.lambdaQuery(DocNoRuleEntity.class)
                        .orderByAsc(DocNoRuleEntity::getCode))
                .stream()
                .map(this::toRule)
                .toList();
    }

    @Override
    public void saveRule(DocNoRule rule) {
        DocNoRuleEntity entity = new DocNoRuleEntity();
        BeanUtils.copyProperties(rule, entity);
        noRuleMapper.insert(entity);
        rule.setId(entity.getId());
    }

    @Override
    public void updateRule(DocNoRule rule) {
        DocNoRuleEntity entity = new DocNoRuleEntity();
        BeanUtils.copyProperties(rule, entity);
        entity.setUpdateTime(LocalDateTime.now());
        noRuleMapper.updateById(entity);
    }

    @Override
    public DocOutgoing findOutgoing(Long id) {
        DocOutgoingEntity entity = outgoingMapper.selectById(id);
        return entity == null ? null : toOutgoing(entity);
    }

    @Override
    public PageResult<DocOutgoing> pageOutgoing(DocQuery query) {
        LambdaQueryWrapper<DocOutgoingEntity> wrapper = Wrappers.lambdaQuery(DocOutgoingEntity.class)
                .eq(StringUtils.hasText(query.getStatus()), DocOutgoingEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDocType()), DocOutgoingEntity::getDocType, query.getDocType())
                .eq(query.getSecurityLevel() != null, DocOutgoingEntity::getSecurityLevel,
                        query.getSecurityLevel())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(DocOutgoingEntity::getDocNo, query.getKeyword())
                        .or()
                        .like(DocOutgoingEntity::getTitle, query.getKeyword()))
                .orderByDesc(DocOutgoingEntity::getCreateTime);
        Page<DocOutgoingEntity> page = outgoingMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page.convert(this::toOutgoing));
    }

    @Override
    public void saveOutgoing(DocOutgoing outgoing) {
        DocOutgoingEntity entity = new DocOutgoingEntity();
        BeanUtils.copyProperties(outgoing, entity);
        outgoingMapper.insert(entity);
        outgoing.setId(entity.getId());
    }

    @Override
    public void updateOutgoing(DocOutgoing outgoing) {
        DocOutgoingEntity entity = new DocOutgoingEntity();
        BeanUtils.copyProperties(outgoing, entity);
        entity.setUpdateTime(LocalDateTime.now());
        outgoingMapper.updateById(entity);
    }

    @Override
    public void deleteOutgoing(Long id) {
        outgoingMapper.deleteById(id);
    }

    @Override
    public DocIncoming findIncoming(Long id) {
        DocIncomingEntity entity = incomingMapper.selectById(id);
        return entity == null ? null : toIncoming(entity);
    }

    @Override
    public PageResult<DocIncoming> pageIncoming(DocQuery query) {
        LambdaQueryWrapper<DocIncomingEntity> wrapper = Wrappers.lambdaQuery(DocIncomingEntity.class)
                .eq(StringUtils.hasText(query.getStatus()), DocIncomingEntity::getStatus, query.getStatus())
                .eq(query.getSecurityLevel() != null, DocIncomingEntity::getSecurityLevel,
                        query.getSecurityLevel())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(DocIncomingEntity::getIncomingNo, query.getKeyword())
                        .or()
                        .like(DocIncomingEntity::getTitle, query.getKeyword())
                        .or()
                        .like(DocIncomingEntity::getSourceUnit, query.getKeyword()))
                .orderByDesc(DocIncomingEntity::getReceivedDate)
                .orderByDesc(DocIncomingEntity::getCreateTime);
        Page<DocIncomingEntity> page = incomingMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page.convert(this::toIncoming));
    }

    @Override
    public void saveIncoming(DocIncoming incoming) {
        DocIncomingEntity entity = new DocIncomingEntity();
        BeanUtils.copyProperties(incoming, entity);
        incomingMapper.insert(entity);
        incoming.setId(entity.getId());
    }

    @Override
    public void updateIncoming(DocIncoming incoming) {
        DocIncomingEntity entity = new DocIncomingEntity();
        BeanUtils.copyProperties(incoming, entity);
        entity.setUpdateTime(LocalDateTime.now());
        incomingMapper.updateById(entity);
    }

    @Override
    public void deleteIncoming(Long id) {
        incomingMapper.deleteById(id);
    }

    @Override
    public List<DocAttachment> listAttachments(String docType, Long docId) {
        return attachmentMapper.selectList(Wrappers.lambdaQuery(DocAttachmentEntity.class)
                        .eq(DocAttachmentEntity::getDocType, docType)
                        .eq(DocAttachmentEntity::getDocId, docId)
                        .orderByAsc(DocAttachmentEntity::getSortNo)
                        .orderByAsc(DocAttachmentEntity::getId))
                .stream()
                .map(this::toAttachment)
                .toList();
    }

    @Override
    public DocAttachment findAttachment(Long id) {
        DocAttachmentEntity entity = attachmentMapper.selectById(id);
        return entity == null ? null : toAttachment(entity);
    }

    @Override
    public void saveAttachment(DocAttachment attachment) {
        DocAttachmentEntity entity = new DocAttachmentEntity();
        BeanUtils.copyProperties(attachment, entity);
        attachmentMapper.insert(entity);
        attachment.setId(entity.getId());
    }

    @Override
    public void deleteAttachments(String docType, Long docId) {
        attachmentMapper.delete(Wrappers.lambdaQuery(DocAttachmentEntity.class)
                .eq(DocAttachmentEntity::getDocType, docType)
                .eq(DocAttachmentEntity::getDocId, docId));
    }

    @Override
    public DocArchiveRef findArchiveRef(String docType, Long docId) {
        DocArchiveRefEntity entity = archiveRefMapper.selectOne(Wrappers.lambdaQuery(DocArchiveRefEntity.class)
                .eq(DocArchiveRefEntity::getDocType, docType)
                .eq(DocArchiveRefEntity::getDocId, docId)
                .last("LIMIT 1"));
        return entity == null ? null : toArchiveRef(entity);
    }

    @Override
    public DocArchiveRef findArchiveRef(Long id) {
        DocArchiveRefEntity entity = archiveRefMapper.selectById(id);
        return entity == null ? null : toArchiveRef(entity);
    }

    @Override
    public PageResult<DocArchiveRef> pageArchiveRefs(DocQuery query) {
        LambdaQueryWrapper<DocArchiveRefEntity> wrapper = Wrappers.lambdaQuery(DocArchiveRefEntity.class)
                .eq(StringUtils.hasText(query.getStatus()), DocArchiveRefEntity::getStatus, query.getStatus())
                .eq(StringUtils.hasText(query.getDocType()), DocArchiveRefEntity::getDocType,
                        query.getDocType())
                .and(StringUtils.hasText(query.getKeyword()), w -> w
                        .like(DocArchiveRefEntity::getSourceId, query.getKeyword())
                        .or()
                        .like(DocArchiveRefEntity::getArchiveItemId, query.getKeyword()))
                .orderByDesc(DocArchiveRefEntity::getUpdateTime);
        Page<DocArchiveRefEntity> page = archiveRefMapper.selectPage(query.toPage(), wrapper);
        return PageResult.of(page.convert(this::toArchiveRef));
    }

    @Override
    public void saveArchiveRef(DocArchiveRef ref) {
        DocArchiveRefEntity entity = new DocArchiveRefEntity();
        BeanUtils.copyProperties(ref, entity);
        archiveRefMapper.insert(entity);
        ref.setId(entity.getId());
    }

    @Override
    public void updateArchiveRef(DocArchiveRef ref) {
        DocArchiveRefEntity entity = new DocArchiveRefEntity();
        BeanUtils.copyProperties(ref, entity);
        entity.setUpdateTime(LocalDateTime.now());
        archiveRefMapper.updateById(entity);
    }

    private DocTemplate toTemplate(DocTemplateEntity entity) {
        DocTemplate model = new DocTemplate();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private DocNoRule toRule(DocNoRuleEntity entity) {
        DocNoRule model = new DocNoRule();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private DocOutgoing toOutgoing(DocOutgoingEntity entity) {
        DocOutgoing model = new DocOutgoing();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private DocIncoming toIncoming(DocIncomingEntity entity) {
        DocIncoming model = new DocIncoming();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private DocAttachment toAttachment(DocAttachmentEntity entity) {
        DocAttachment model = new DocAttachment();
        BeanUtils.copyProperties(entity, model);
        return model;
    }

    private DocArchiveRef toArchiveRef(DocArchiveRefEntity entity) {
        DocArchiveRef model = new DocArchiveRef();
        BeanUtils.copyProperties(entity, model);
        return model;
    }
}
