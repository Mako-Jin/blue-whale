package com.bluewhale.lms.cert.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.lms.cert.application.command.CertTemplateSaveCommand;
import com.bluewhale.lms.cert.application.service.CertTemplateAppService;
import com.bluewhale.lms.cert.application.view.CertTemplateView;
import com.bluewhale.lms.cert.domain.model.CertSource;
import com.bluewhale.lms.cert.domain.model.CertTemplate;
import com.bluewhale.lms.cert.domain.model.CertTemplateQuery;
import com.bluewhale.lms.cert.domain.model.CertTemplateStatus;
import com.bluewhale.lms.cert.domain.repository.CertTemplateRepository;
import com.bluewhale.lms.cert.domain.repository.CertificateRepository;
import com.bluewhale.lms.common.LmsActor;
import com.bluewhale.lms.common.LmsErrorCode;
import com.bluewhale.lms.config.LmsProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

/**
 * 证书模板应用服务实现。
 *
 * <p>模板是发证口径的唯一来源，因此编辑它要守住两条：</p>
 * <ol>
 *   <li><b>编码归一后查重</b>：{@code cert-01} 与 {@code CERT-01} 是同一个编码，先转大写再比对，
 *       否则唯一键报错会以数据库异常的形式抛给用户；</li>
 *   <li><b>已被引用不可删</b>：证书里存了名称与类别快照，删模板不会破坏历史展示，
 *       但会让「这张证按什么口径发的」无从追溯，所以只允许逻辑删除未被引用的模板，
 *       已发过证的模板要下架就用「停用」。</li>
 * </ol>
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class CertTemplateAppServiceImpl implements CertTemplateAppService {

    private final CertTemplateRepository certTemplateRepository;

    private final CertificateRepository certificateRepository;

    private final LmsProperties properties;

    @Override
    public PageResult<CertTemplateView> page(CertTemplateQuery query, LmsActor actor) {
        assertGate();
        long current = Math.max(query.getCurrent() == null ? 1L : query.getCurrent(), 1L);
        long size = Math.min(Math.max(query.getSize() == null ? 20L : query.getSize(), 1L),
                properties.getCertPageLimit());
        long total = certTemplateRepository.count(query);
        List<CertTemplateView> views = certTemplateRepository.page(query, (current - 1) * size, (int) size)
                .stream().map(CertTemplateView::from).toList();
        return PageResult.of(views, total, current, size);
    }

    @Override
    public CertTemplateView detail(Long id, LmsActor actor) {
        assertGate();
        return CertTemplateView.from(requireTemplate(id));
    }

    @Override
    @Transactional
    public Long create(CertTemplateSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        String code = CertTemplate.normalizeCode(command.getTemplateCode());
        certTemplateRepository.findByCode(code).ifPresent(existing -> {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_CODE_CONFLICT);
        });
        CertTemplate template = new CertTemplate();
        template.setTemplateCode(code);
        template.setStatus(CertTemplateStatus.ENABLED);
        applyCommand(template, command);
        template.validateBasics();
        return certTemplateRepository.save(template);
    }

    @Override
    @Transactional
    public void update(Long id, CertTemplateSaveCommand command, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CertTemplate template = requireTemplate(id);
        String code = CertTemplate.normalizeCode(command.getTemplateCode());
        if (!code.equals(template.getTemplateCode())) {
            certTemplateRepository.findByCode(code).ifPresent(existing -> {
                throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_CODE_CONFLICT);
            });
        }
        template.setTemplateCode(code);
        applyCommand(template, command);
        template.validateBasics();
        certTemplateRepository.save(template);
    }

    @Override
    @Transactional
    public void delete(Long id, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CertTemplate template = requireTemplate(id);
        long issued = certificateRepository.countByTemplate(template.getId());
        if (issued > 0) {
            throw LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_STATE_CONFLICT,
                    "该模板已发出 " + issued + " 张证书，不可删除，请改为停用");
        }
        certTemplateRepository.deleteById(id);
    }

    @Override
    @Transactional
    public void changeStatus(Long id, boolean enabled, LmsActor actor) {
        assertGate();
        actor.requireUserId();
        CertTemplate template = requireTemplate(id);
        template.setStatus(enabled ? CertTemplateStatus.ENABLED : CertTemplateStatus.DISABLED);
        certTemplateRepository.save(template);
    }

    /**
     * 断言需求门禁通过。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed()) {
            throw LmsErrorCode.exception(LmsErrorCode.GATE_CLOSED);
        }
    }

    /**
     * 加载模板，不存在按 404 处理。
     *
     * @param id 模板 ID
     * @return 模板
     */
    private CertTemplate requireTemplate(Long id) {
        return certTemplateRepository.findById(id)
                .orElseThrow(() -> LmsErrorCode.exception(LmsErrorCode.CERT_TEMPLATE_NOT_FOUND));
    }

    /**
     * 将保存命令写入模板。
     *
     * @param template 目标模板
     * @param command 保存命令
     */
    private void applyCommand(CertTemplate template, CertTemplateSaveCommand command) {
        template.setName(command.getName().trim());
        template.setCategory(normalizeCategory(command.getCategory()));
        template.setIssueBasis(CertSource.parse(command.getIssueBasis()));
        template.setValidMonths(command.getValidMonths() == null ? 0 : command.getValidMonths());
        template.setCertPrefix(CertTemplate.normalizePrefix(command.getCertPrefix()));
        template.setContent(command.getContent());
        template.setRemark(command.getRemark());
    }

    /**
     * 归一证书类别（空值回落为 GENERAL）。
     *
     * @param category 原始类别
     * @return 归一后的类别
     */
    private String normalizeCategory(String category) {
        if (category == null || category.isBlank()) {
            return "GENERAL";
        }
        return category.trim().toUpperCase(Locale.ROOT);
    }
}
