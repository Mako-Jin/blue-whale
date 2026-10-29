package com.bluewhale.doc.application.service.impl;

import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.doc.application.service.DocApprovalService;
import com.bluewhale.doc.application.service.DocNotificationService;
import com.bluewhale.doc.application.service.DocVersionService;
import com.bluewhale.doc.config.DocProperties;
import com.bluewhale.doc.domain.exception.DocErrorCode;
import com.bluewhale.doc.domain.model.DocPageStatus;
import com.bluewhale.doc.infrastructure.client.DocWorkflowClient;
import com.bluewhale.doc.infrastructure.persistence.entity.DocPage;
import com.bluewhale.doc.infrastructure.persistence.mapper.DocPageMapper;
import com.bluewhale.doc.interfaces.dto.DocApprovalCallbackRequest;
import com.bluewhale.doc.interfaces.vo.DocApprovalVO;
import com.bluewhale.doc.interfaces.vo.DocPageSaveVO;
import com.bluewhale.doc.support.DocAccessGuard;
import com.bluewhale.doc.support.DocOperator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 发布审批应用服务实现。
 *
 * <p>审批开关关闭时提交等同于发布；开启后由工作流实例承接，回调与读取时同步均按
 * {@code (pageId, instanceId, PENDING)} 条件更新，重复回调天然幂等。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DocApprovalServiceImpl implements DocApprovalService {

    /** 审批状态：待审批 */
    private static final String APPROVAL_PENDING = "PENDING";

    /** 审批状态：通过 */
    private static final String APPROVAL_APPROVED = "APPROVED";

    /** 审批状态：驳回/撤销 */
    private static final String APPROVAL_REJECTED = "REJECTED";

    /** 审批状态：未提交 */
    private static final String APPROVAL_NONE = "NONE";

    /** 工作流状态：通过 */
    private static final int WORKFLOW_APPROVED = 1;

    /** 工作流状态：驳回 */
    private static final int WORKFLOW_REJECTED = 2;

    /** 工作流状态：撤销 */
    private static final int WORKFLOW_CANCELLED = 3;

    /** 发布快照类型 */
    private static final String KIND_PUBLISH = "PUBLISH";

    private final DocPageMapper pageMapper;

    private final DocAccessGuard accessGuard;

    private final DocWorkflowClient workflowClient;

    private final DocVersionService versionService;

    private final DocNotificationService notificationService;

    private final DocProperties docProperties;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public DocPageSaveVO submit(Long pageId, DocOperator operator) {
        DocPage page = requireSubmittablePage(pageId, operator.userId());
        if (!docProperties.isApprovalEnabled()) {
            return publishDirectly(page, operator.userId());
        }
        if (APPROVAL_PENDING.equals(page.getApprovalStatus()) && page.getApprovalInstanceId() != null) {
            return toSaveVO(page);
        }
        Long definitionId = docProperties.getApprovalDefinitionId();
        if (definitionId == null) {
            throw DocErrorCode.exception(DocErrorCode.APPROVAL_UNAVAILABLE);
        }
        Long instanceId;
        try {
            instanceId = workflowClient.start(definitionId, "文档发布审批：" + page.getTitle(),
                    businessKey(page), variables(page, operator), operator);
        } catch (RuntimeException ex) {
            log.warn("发布审批发起失败: pageId={}, error={}", pageId, ex.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "发布审批发起失败，请稍后重试");
        }
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, pageId)
                .set(DocPage::getStatus, DocPageStatus.PENDING_APPROVAL.name())
                .set(DocPage::getApprovalInstanceId, instanceId)
                .set(DocPage::getApprovalStatus, APPROVAL_PENDING)
                .set(DocPage::getApprovalSubmittedAt, LocalDateTime.now())
                .set(DocPage::getUpdateTime, LocalDateTime.now());
        pageMapper.update(null, wrapper);
        DocPage updated = pageMapper.selectById(pageId);
        notificationService.approvalSubmitted(TenantContext.requireId(), updated, instanceId, operator.userId());
        log.info("发布审批已提交: pageId={}, instanceId={}, operator={}", pageId, instanceId, operator.userId());
        return toSaveVO(updated);
    }

    @Override
    public DocApprovalVO status(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePageRead(pageId, userId);
        DocApprovalVO vo = new DocApprovalVO();
        vo.setPageId(page.getId());
        vo.setPageStatus(page.getStatus());
        vo.setApprovalStatus(page.getApprovalStatus() == null ? APPROVAL_NONE : page.getApprovalStatus());
        vo.setApprovalInstanceId(page.getApprovalInstanceId());
        vo.setSubmittedAt(page.getApprovalSubmittedAt());
        vo.setApprovalEnabled(docProperties.isApprovalEnabled());
        return vo;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean handleCallback(DocApprovalCallbackRequest request) {
        Integer status = request.getStatus();
        if (status == null || status < WORKFLOW_APPROVED || status > WORKFLOW_CANCELLED) {
            throw new BizException(ErrorCode.PARAM_ERROR, "审批状态非法");
        }
        return applyResult(request.getInstanceId(), status, request.getComment());
    }

    @Override
    public void syncIfPending(DocPage page) {
        if (!docProperties.isApprovalEnabled() || page == null
                || !DocPageStatus.PENDING_APPROVAL.name().equals(page.getStatus())
                || page.getApprovalInstanceId() == null
                || !APPROVAL_PENDING.equals(page.getApprovalStatus())) {
            return;
        }
        try {
            Integer status = workflowClient.fetchStatus(page.getApprovalInstanceId());
            if (status != null && status >= WORKFLOW_APPROVED && status <= WORKFLOW_CANCELLED) {
                applyResult(page.getApprovalInstanceId(), status, "读取页面时同步");
            }
        } catch (RuntimeException ex) {
            log.warn("审批状态同步失败: pageId={}, error={}", page.getId(), ex.getMessage());
        }
    }

    /**
     * 应用审批结果；条件更新保证回调幂等。
     *
     * @param instanceId 流程实例ID
     * @param status     流程状态
     * @param comment    审批意见
     * @return 是否发生状态变更
     */
    private boolean applyResult(Long instanceId, Integer status, String comment) {
        DocPage page = pageMapper.selectOne(Wrappers.<DocPage>lambdaQuery()
                .eq(DocPage::getApprovalInstanceId, instanceId)
                .eq(DocPage::getApprovalStatus, APPROVAL_PENDING)
                .last("LIMIT 1"));
        if (page == null) {
            log.info("审批回调已处理或实例不匹配，忽略: instanceId={}, status={}", instanceId, status);
            return false;
        }
        boolean approved = status == WORKFLOW_APPROVED;
        LambdaUpdateWrapper<DocPage> wrapper = Wrappers.lambdaUpdate();
        wrapper.eq(DocPage::getId, page.getId())
                .eq(DocPage::getApprovalInstanceId, instanceId)
                .eq(DocPage::getApprovalStatus, APPROVAL_PENDING)
                .set(DocPage::getApprovalStatus, approved ? APPROVAL_APPROVED : APPROVAL_REJECTED)
                .set(DocPage::getUpdateTime, LocalDateTime.now());
        if (approved) {
            wrapper.set(DocPage::getStatus, DocPageStatus.PUBLISHED.name())
                    .set(DocPage::getPublishedRevision, page.getRevision())
                    .set(DocPage::getPublishedAt, LocalDateTime.now());
        } else {
            wrapper.set(DocPage::getStatus, DocPageStatus.DRAFT.name());
        }
        int rows = pageMapper.update(null, wrapper);
        if (rows == 0) {
            return false;
        }
        DocPage updated = pageMapper.selectById(page.getId());
        if (approved) {
            versionService.snapshot(updated, KIND_PUBLISH, comment, null);
        }
        Long tenantId = TenantContext.get();
        if (tenantId != null) {
            notificationService.approvalResult(tenantId, updated, instanceId, approved);
        }
        log.info("发布审批结果已应用: pageId={}, instanceId={}, approved={}, comment={}",
                page.getId(), instanceId, approved, comment);
        return true;
    }

    /**
     * 审批关闭时直接发布。
     *
     * @param page   页面
     * @param userId 操作人
     * @return 保存结果
     */
    private DocPageSaveVO publishDirectly(DocPage page, Long userId) {
        page.setStatus(DocPageStatus.PUBLISHED.name());
        page.setPublishedRevision(page.getRevision());
        page.setPublishedAt(LocalDateTime.now());
        page.setApprovalStatus(APPROVAL_APPROVED);
        page.setApprovalInstanceId(null);
        page.setApprovalSubmittedAt(LocalDateTime.now());
        page.setUpdateTime(LocalDateTime.now());
        pageMapper.updateById(page);
        DocPage updated = pageMapper.selectById(page.getId());
        versionService.snapshot(updated, KIND_PUBLISH, "审批未启用，直接发布", userId);
        log.info("页面已直接发布: id={}, revision={}, operator={}", page.getId(), updated.getRevision(), userId);
        return toSaveVO(updated);
    }

    /**
     * 读取并校验页面可提交审批。
     *
     * @param pageId 页面ID
     * @param userId 用户ID
     * @return 页面
     */
    private DocPage requireSubmittablePage(Long pageId, Long userId) {
        DocPage page = accessGuard.requirePage(pageId);
        accessGuard.requirePageEdit(page, userId);
        if (DocPageStatus.ARCHIVED.name().equals(page.getStatus())) {
            throw DocErrorCode.exception(DocErrorCode.APPROVAL_STATE_CONFLICT);
        }
        return page;
    }

    /**
     * 构造审批业务键。
     *
     * @param page 页面
     * @return 业务键
     */
    private String businessKey(DocPage page) {
        return "doc:" + page.getId() + ":v" + page.getRevision();
    }

    /**
     * 构造流程变量。
     *
     * @param page     页面
     * @param operator 操作人
     * @return 流程变量
     */
    private Map<String, Object> variables(DocPage page, DocOperator operator) {
        Map<String, Object> variables = new LinkedHashMap<>();
        variables.put("pageId", String.valueOf(page.getId()));
        variables.put("spaceId", String.valueOf(page.getSpaceId()));
        variables.put("title", page.getTitle());
        variables.put("revision", page.getRevision());
        variables.put("definitionCode", docProperties.getApprovalDefinitionCode());
        variables.put("applicant", operator.username());
        return variables;
    }

    /**
     * 页面转保存结果。
     *
     * @param page 页面
     * @return 保存结果
     */
    private DocPageSaveVO toSaveVO(DocPage page) {
        DocPageSaveVO vo = new DocPageSaveVO();
        vo.setId(page.getId());
        vo.setRevision(page.getRevision());
        vo.setStatus(page.getStatus());
        vo.setSavedAt(page.getUpdateTime());
        return vo;
    }
}
