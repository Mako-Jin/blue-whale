package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import com.bluewhale.mail.dto.MailDraftSaveRequest;
import com.bluewhale.mail.entity.MailDraft;
import com.bluewhale.mail.mapper.MailDraftMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailDraftService;
import com.bluewhale.mail.support.MailAddressUtils;
import com.bluewhale.mail.vo.MailDraftVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * 草稿服务实现。
 *
 * <p>M0 草稿不持久化附件引用（{@code attachmentsJson} 留空）：附件草稿需要与
 * 文件服务的临时上传生命周期联动，简单存 JSON 会产生孤儿文件，留待 M1 一并设计。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailDraftServiceImpl implements MailDraftService {

    /** 单页最大条数 */
    private static final long MAX_PAGE_SIZE = 100L;

    private final MailDraftMapper draftMapper;

    private final MailAccountService accountService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailDraftVO save(Long id, MailDraftSaveRequest request, Long userId) {
        accountService.requireOwned(request.getAccountId(), userId);
        MailDraft draft;
        if (id == null) {
            draft = new MailDraft();
            draft.setAccountId(request.getAccountId());
        } else {
            draft = draftMapper.selectById(id);
            if (draft == null) {
                throw new BizException(ErrorCode.NOT_FOUND, "mail.draft.not-found", "草稿不存在");
            }
            if (!Objects.equals(draft.getAccountId(), request.getAccountId())) {
                throw new BizException(ErrorCode.PARAM_ERROR, "mail.draft.account-mismatch", "草稿归属账号与请求不一致");
            }
        }
        draft.setDraftKey(request.getDraftKey());
        draft.setSubject(request.getSubject());
        draft.setToAddrs(MailAddressUtils.join(request.getTo()));
        draft.setCcAddrs(MailAddressUtils.join(request.getCc()));
        draft.setBccAddrs(MailAddressUtils.join(request.getBcc()));
        draft.setBodyHtml(request.getBodyHtml());
        draft.setBodyText(request.getBodyText());
        if (id == null) {
            draftMapper.insert(draft);
        } else {
            draftMapper.updateById(draft);
        }
        return toVo(draft);
    }

    @Override
    public PageResult<MailDraftVO> page(long current, long size, Long accountId, Long userId) {
        Long resolvedAccountId = accountId == null ? accountService.requireDefaultAccountId(userId) : accountId;
        accountService.requireOwned(resolvedAccountId, userId);
        long pageSize = Math.min(Math.max(size, 1L), MAX_PAGE_SIZE);
        Page<MailDraft> page = draftMapper.selectPage(new Page<>(current, pageSize),
                new LambdaQueryWrapper<MailDraft>()
                        .eq(MailDraft::getAccountId, resolvedAccountId)
                        .orderByDesc(MailDraft::getUpdateTime));
        List<MailDraftVO> records = page.getRecords().stream().map(MailDraftServiceImpl::toVo).toList();
        return PageResult.of(records, page.getTotal(), page.getCurrent(), page.getSize());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, Long userId) {
        MailDraft draft = draftMapper.selectById(id);
        if (draft == null) {
            return;
        }
        accountService.requireOwned(draft.getAccountId(), userId);
        draftMapper.deleteById(id);
    }

    /**
     * 实体转视图
     *
     * @param draft 草稿实体
     * @return 草稿视图
     */
    private static MailDraftVO toVo(MailDraft draft) {
        MailDraftVO vo = new MailDraftVO();
        vo.setId(draft.getId());
        vo.setAccountId(draft.getAccountId());
        vo.setDraftKey(draft.getDraftKey());
        vo.setSubject(draft.getSubject());
        vo.setToAddrs(draft.getToAddrs());
        vo.setCcAddrs(draft.getCcAddrs());
        vo.setBccAddrs(draft.getBccAddrs());
        vo.setBodyHtml(draft.getBodyHtml());
        vo.setBodyText(draft.getBodyText());
        vo.setAttachments(List.of());
        vo.setUpdateTime(draft.getUpdateTime());
        return vo;
    }
}
