package com.bluewhale.crm.service.application.service.impl;

import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.crm.common.CrmActor;
import com.bluewhale.crm.common.CrmErrorCode;
import com.bluewhale.crm.config.CrmProperties;
import com.bluewhale.crm.config.CrmServiceProperties;
import com.bluewhale.crm.service.application.command.KbRefCommand;
import com.bluewhale.crm.service.application.service.KbAppService;
import com.bluewhale.crm.service.application.view.KbRefView;
import com.bluewhale.crm.service.domain.model.KbRef;
import com.bluewhale.crm.service.domain.model.KbRefQuery;
import com.bluewhale.crm.service.domain.port.DocPagePort;
import com.bluewhale.crm.service.domain.repository.KbRefRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 知识引用应用服务实现。
 *
 * @author BlueWhale
 */
@Service
@RequiredArgsConstructor
public class KbAppServiceImpl implements KbAppService {

    /** 检索返回上限 */
    private static final int SEARCH_LIMIT = 20;

    /** 状态：启用 */
    private static final String STATUS_ENABLED = "ENABLED";

    /** 可见范围：内部 */
    private static final String SCOPE_INTERNAL = "INTERNAL";

    private final KbRefRepository kbRefRepository;

    private final DocPagePort docPagePort;

    private final CrmProperties properties;

    private final CrmServiceProperties serviceProperties;

    @Override
    public PageResult<KbRefView> page(KbRefQuery query, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        KbRefQuery safe = query == null ? new KbRefQuery() : query;
        List<KbRefView> records = kbRefRepository.page(safe).stream().map(KbRefView::from).toList();
        return PageResult.of(records, kbRefRepository.count(safe), safe.getCurrent(), safe.getSize());
    }

    @Override
    public KbRefView detail(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        return KbRefView.from(requireRef(id));
    }

    @Override
    public List<KbRefView> search(String keyword, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        KbRefQuery query = new KbRefQuery();
        query.setKeyword(keyword);
        query.setStatus(STATUS_ENABLED);
        query.setCurrent(1);
        query.setSize(SEARCH_LIMIT);
        return kbRefRepository.page(query).stream().map(KbRefView::from).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbRefView create(KbRefCommand command, CrmActor actor) {
        assertGate();
        Long userId = actor.requireUserId();
        KbRef ref = new KbRef();
        apply(ref, command, userId);
        ref.setHitCount(0);
        ref.setStatus(command.getStatus() == null ? STATUS_ENABLED : command.getStatus());
        kbRefRepository.insert(ref);
        return KbRefView.from(ref);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbRefView update(Long id, KbRefCommand command, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        KbRef ref = requireRef(id);
        apply(ref, command, ref.getOwnerId());
        if (command.getStatus() != null) {
            ref.setStatus(command.getStatus());
        }
        kbRefRepository.update(ref);
        return KbRefView.from(ref);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        requireRef(id);
        kbRefRepository.deleteById(id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public KbRefView hit(Long id, CrmActor actor) {
        assertGate();
        actor.requireUserId();
        KbRef ref = requireRef(id);
        if (!STATUS_ENABLED.equals(ref.getStatus())) {
            throw CrmErrorCode.exception(CrmErrorCode.PARAM_INVALID, "知识引用已停用，不能记录命中");
        }
        kbRefRepository.incrementHit(id);
        ref.recordHit();
        return KbRefView.from(ref);
    }

    /**
     * 应用命令到知识引用。
     *
     * @param ref 知识引用
     * @param command 命令
     * @param userId 操作人
     */
    private void apply(KbRef ref, KbRefCommand command, Long userId) {
        ref.setTitle(command.getTitle().trim());
        ref.setCategory(trimToNull(command.getCategory()));
        ref.setProductTags(trimToNull(command.getProductTags()));
        ref.setFaultTags(trimToNull(command.getFaultTags()));
        ref.setVisibleScope(command.getVisibleScope() == null ? SCOPE_INTERNAL : command.getVisibleScope());
        ref.setDocId(command.getDocId());
        ref.setDocVersion(command.getDocVersion());
        ref.setOwnerId(command.getOwnerId() != null ? command.getOwnerId() : userId);
        ref.setDeptId(command.getDeptId());
        applyDoc(ref);
    }

    /**
     * 校验文档引用并写可用性；不可用只降级。
     *
     * @param ref 知识引用
     */
    private void applyDoc(KbRef ref) {
        if (ref.getDocId() == null) {
            ref.markDocAvailable(false);
            return;
        }
        DocPagePort.DocPageSnapshot snapshot = docPagePort.findPublicPage(ref.getDocId()).orElse(null);
        ref.markDocAvailable(snapshot != null);
        if (snapshot != null && ref.getDocVersion() == null) {
            ref.setDocVersion(snapshot.revision());
        }
    }

    /**
     * 读取知识引用，不存在按无权访问处理。
     *
     * @param id 主键
     * @return 知识引用
     */
    private KbRef requireRef(Long id) {
        KbRef ref = kbRefRepository.findById(id);
        if (ref == null) {
            throw CrmErrorCode.exception(CrmErrorCode.KB_REF_NOT_FOUND);
        }
        return ref;
    }

    /**
     * 去空白，空串转为 null。
     *
     * @param value 原始值
     * @return 文本或 null
     */
    private String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String text = value.trim();
        return text.isEmpty() ? null : text;
    }

    /**
     * 服务门禁校验。
     */
    private void assertGate() {
        if (!properties.isDemandGatePassed() || !serviceProperties.isEnabled()) {
            throw CrmErrorCode.exception(CrmErrorCode.GATE_CLOSED);
        }
    }
}
