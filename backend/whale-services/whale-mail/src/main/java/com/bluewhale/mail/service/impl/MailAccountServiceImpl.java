package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailAccountSaveRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.service.MailAccountService;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.support.MailAddressUtils;
import com.bluewhale.mail.vo.MailAccountVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;

/**
 * 邮箱账号服务实现。
 *
 * <p>邮箱地址是账号的业务主键，创建后不可修改——改地址等价于换账号，
 * 旧地址下的邮件索引会全部失配，因此只允许改显示名、端点与同步开关。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MailAccountServiceImpl implements MailAccountService {

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 默认账号类型 */
    private static final String DEFAULT_ACCOUNT_TYPE = "PERSONAL";

    private final MailAccountMapper accountMapper;

    private final MailFolderService folderService;

    private final MailProperties properties;

    @Override
    public MailAccountVO current(Long userId) {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getUserId, userId)
                .eq(MailAccount::getStatus, STATUS_ENABLED)
                .orderByAsc(MailAccount::getId)
                .last("LIMIT 1"));
        return accounts.isEmpty() ? null : toVo(accounts.get(0));
    }

    @Override
    public List<MailAccountVO> listByUser(Long userId) {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getUserId, userId)
                .orderByAsc(MailAccount::getId));
        return accounts.stream().map(MailAccountServiceImpl::toVo).toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailAccountVO create(MailAccountSaveRequest request, Long userId) {
        String email = MailAddressUtils.normalize(request.getEmail());
        MailAccount account = new MailAccount();
        account.setUserId(userId);
        account.setEmail(email);
        account.setDomain(MailAddressUtils.domainOf(email));
        account.setDisplayName(StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim() : email);
        account.setAccountType(StringUtils.hasText(request.getAccountType())
                ? request.getAccountType().trim().toUpperCase() : DEFAULT_ACCOUNT_TYPE);
        account.setStatus(STATUS_ENABLED);
        account.setSyncEnabled(request.getSyncEnabled() == null || request.getSyncEnabled() ? 1 : 0);
        account.setSecretRef(StringUtils.hasText(request.getSecretRef()) ? request.getSecretRef().trim() : null);
        applyEndpoints(account, request);
        account.setQuotaBytes(0L);
        account.setUsedBytes(0L);

        try {
            accountMapper.insert(account);
        } catch (DuplicateKeyException e) {
            // 唯一键含逻辑删除行：已解绑的地址也占用，直接提示比静默失败更清晰
            throw new BizException(ErrorCode.PARAM_ERROR, "该邮箱地址已被绑定：" + email);
        }
        folderService.ensureSystemFolders(account);
        log.info("邮箱账号已绑定: id={}, email={}, userId={}", account.getId(), email, userId);
        return toVo(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MailAccountVO update(Long id, MailAccountSaveRequest request, Long userId) {
        MailAccount account = requireOwned(id, userId);
        if (StringUtils.hasText(request.getDisplayName())) {
            account.setDisplayName(request.getDisplayName().trim());
        }
        if (StringUtils.hasText(request.getSecretRef())) {
            account.setSecretRef(request.getSecretRef().trim());
        }
        if (request.getSyncEnabled() != null) {
            account.setSyncEnabled(request.getSyncEnabled() ? 1 : 0);
        }
        if (request.getAccountType() != null) {
            account.setAccountType(request.getAccountType().trim().toUpperCase());
        }
        applyEndpoints(account, request);
        accountMapper.updateById(account);
        log.info("邮箱账号已更新: id={}, email={}", id, account.getEmail());
        return toVo(account);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remove(Long id, Long userId) {
        MailAccount account = requireOwned(id, userId);
        accountMapper.deleteById(account.getId());
        log.info("邮箱账号已解绑: id={}, email={}", id, account.getEmail());
    }

    @Override
    public MailAccount requireOwned(Long accountId, Long userId) {
        if (accountId == null) {
            throw new BizException(ErrorCode.PARAM_ERROR, "邮箱账号不能为空");
        }
        MailAccount account = accountMapper.selectById(accountId);
        if (account == null) {
            throw new BizException(ErrorCode.NOT_FOUND, "邮箱账号不存在");
        }
        if (!Objects.equals(account.getUserId(), userId)) {
            throw new BizException(ErrorCode.FORBIDDEN, "无权访问该邮箱账号");
        }
        if (account.getStatus() == null || account.getStatus() != STATUS_ENABLED) {
            throw new BizException(ErrorCode.FORBIDDEN, "邮箱账号已停用");
        }
        return account;
    }

    @Override
    public Long requireDefaultAccountId(Long userId) {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getUserId, userId)
                .eq(MailAccount::getStatus, STATUS_ENABLED)
                .orderByAsc(MailAccount::getId)
                .last("LIMIT 1"));
        if (accounts.isEmpty()) {
            throw new BizException(ErrorCode.PARAM_ERROR, "尚未绑定邮箱账号，请先完成邮箱绑定");
        }
        return accounts.get(0).getId();
    }

    /**
     * 合并请求端、账号现值与全局默认的协议端点
     *
     * <p>优先级：请求值 → 账号现值 → 全局默认。更新场景必须保留现值，
     * 否则「只改显示名」会因端点被清空而误报「未配置 IMAP 主机」。</p>
     *
     * @param account 账号实体
     * @param request 请求
     */
    private void applyEndpoints(MailAccount account, MailAccountSaveRequest request) {
        account.setImapHost(pick(request.getImapHost(), account.getImapHost(), properties.getDefaultImapHost()));
        account.setImapPort(pick(request.getImapPort(), account.getImapPort(), properties.getDefaultImapPort()));
        account.setImapSsl(pickSsl(request.getImapSsl(), account.getImapSsl(), properties.isDefaultImapSsl()));
        account.setSmtpHost(pick(request.getSmtpHost(), account.getSmtpHost(), properties.getDefaultSmtpHost()));
        account.setSmtpPort(pick(request.getSmtpPort(), account.getSmtpPort(), properties.getDefaultSmtpPort()));
        account.setSmtpSsl(pickSsl(request.getSmtpSsl(), account.getSmtpSsl(), properties.isDefaultSmtpSsl()));
        if (!StringUtils.hasText(account.getImapHost())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置 IMAP 主机：请填写或设置 WHALE_MAIL_IMAP_HOST");
        }
        if (!StringUtils.hasText(account.getSmtpHost())) {
            throw new BizException(ErrorCode.PARAM_ERROR, "未配置 SMTP 主机：请填写或设置 WHALE_MAIL_SMTP_HOST");
        }
    }

    /**
     * 取值优先使用请求值，其次现值，最后全局默认
     *
     * @param requested 请求值
     * @param current 账号现值
     * @param fallback 全局默认
     * @return 最终值
     */
    private static String pick(String requested, String current, String fallback) {
        if (StringUtils.hasText(requested)) {
            return requested.trim();
        }
        return StringUtils.hasText(current) ? current : fallback;
    }

    /**
     * 端口取值：请求 → 现值（大于 0）→ 默认
     *
     * @param requested 请求值
     * @param current 现值
     * @param fallback 默认值
     * @return 最终端口
     */
    private static Integer pick(Integer requested, Integer current, int fallback) {
        if (requested != null && requested > 0) {
            return requested;
        }
        if (current != null && current > 0) {
            return current;
        }
        return fallback;
    }

    /**
     * SSL 开关取值：请求 → 现值 → 默认
     *
     * @param requested 请求值
     * @param current 现值（1 开 / 0 关）
     * @param fallback 默认值
     * @return 最终开关（1 开 / 0 关）
     */
    private static Integer pickSsl(Boolean requested, Integer current, boolean fallback) {
        if (requested != null) {
            return requested ? 1 : 0;
        }
        if (current != null) {
            return current;
        }
        return fallback ? 1 : 0;
    }

    /**
     * 实体转视图（不含凭据引用）
     *
     * @param account 账号实体
     * @return 账号视图
     */
    private static MailAccountVO toVo(MailAccount account) {
        MailAccountVO vo = new MailAccountVO();
        vo.setId(account.getId());
        vo.setEmail(account.getEmail());
        vo.setDisplayName(account.getDisplayName());
        vo.setDomain(account.getDomain());
        vo.setAccountType(account.getAccountType());
        vo.setStatus(account.getStatus());
        vo.setSyncEnabled(account.getSyncEnabled());
        vo.setImapHost(account.getImapHost());
        vo.setImapPort(account.getImapPort());
        vo.setSmtpHost(account.getSmtpHost());
        vo.setSmtpPort(account.getSmtpPort());
        vo.setQuotaBytes(account.getQuotaBytes());
        vo.setUsedBytes(account.getUsedBytes());
        vo.setLastSyncTime(account.getLastSyncTime());
        return vo;
    }
}
