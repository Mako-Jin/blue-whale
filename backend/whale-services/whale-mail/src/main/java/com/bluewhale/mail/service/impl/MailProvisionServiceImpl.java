package com.bluewhale.mail.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.tenant.TenantContext;
import com.bluewhale.mail.client.MailcowClient;
import com.bluewhale.mail.config.MailProperties;
import com.bluewhale.mail.dto.MailProvisionRequest;
import com.bluewhale.mail.entity.MailAccount;
import com.bluewhale.mail.mapper.MailAccountMapper;
import com.bluewhale.mail.service.MailFolderService;
import com.bluewhale.mail.service.MailProvisionService;
import com.bluewhale.mail.support.MailAddressUtils;
import com.bluewhale.mail.support.MailSecretCipher;
import com.bluewhale.mail.vo.MailProvisionVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.List;
import java.util.Locale;

/**
 * 平台代开邮箱实现。
 *
 * <p><b>执行顺序</b>：查重 → 生成地址与口令 → 调 Mailcow 建邮箱 → 落库（账号 + 系统文件夹）。</p>
 *
 * <p><b>为什么 Mailcow 调用不在事务里</b>：一次 HTTP 最长等 10 秒，放在事务里会白占数据库连接，
 * 高峰期能把连接池耗光。因此只有最后「写账号 + 建文件夹」两步进事务，两者必须原子——
 * 有账号没文件夹会导致列表页空白。</p>
 *
 * <p><b>补偿</b>：Mailcow 建成功而落库失败时，立即回删邮箱。否则 Mailcow 里会留下一个
 * 无人认领的地址，下次代开又因「地址已存在」永远失败。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
public class MailProvisionServiceImpl implements MailProvisionService {

    /** 开通方式：平台代开 */
    private static final String SOURCE_AUTO = "AUTO";

    /** 默认账号类型 */
    private static final String DEFAULT_ACCOUNT_TYPE = "PERSONAL";

    /** 账号启用状态 */
    private static final int STATUS_ENABLED = 1;

    /** 初始口令长度：20 位可从 57 字符表取，约 116 bit 熵，足够抗离线爆破 */
    private static final int PASSWORD_LENGTH = 20;

    /** 口令字符表：剔除 I/O/l/0/1 等易混字符，便于人工转录时不写错 */
    private static final String PASSWORD_ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789";

    /** 本地部分最大长度（Mailcow 与多数 MTA 对 local part 的限制） */
    private static final int LOCAL_PART_MAX = 32;

    /** 口令随机源：跨线程共享，SecureRandom 本身线程安全 */
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MailAccountMapper accountMapper;

    private final MailFolderService folderService;

    private final MailProperties properties;

    private final MailcowClient mailcowClient;

    private final MailSecretCipher secretCipher;

    /** 只包住「写账号 + 建文件夹」两步的短事务 */
    private final TransactionTemplate transactionTemplate;

    /**
     * 构造器注入
     *
     * @param accountMapper 账号 Mapper
     * @param folderService 文件夹服务
     * @param properties 邮箱服务配置
     * @param mailcowClient Mailcow 管理客户端
     * @param secretCipher 口令加解密
     * @param transactionManager 事务管理器
     */
    public MailProvisionServiceImpl(MailAccountMapper accountMapper,
                                    MailFolderService folderService,
                                    MailProperties properties,
                                    MailcowClient mailcowClient,
                                    MailSecretCipher secretCipher,
                                    PlatformTransactionManager transactionManager) {
        this.accountMapper = accountMapper;
        this.folderService = folderService;
        this.properties = properties;
        this.mailcowClient = mailcowClient;
        this.secretCipher = secretCipher;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    public MailProvisionVO provision(MailProvisionRequest request) {
        MailProperties.Mailcow mailcow = properties.getMailcow();
        if (!mailcow.isEnabled() || !mailcowClient.isEnabled()) {
            return MailProvisionVO.of(MailProvisionVO.STATE_DISABLED, null,
                    "未启用平台代开邮箱：需配置 WHALE_MAIL_MAILCOW_ENABLED=true 与 BASE_URL / API_KEY");
        }
        String domain = properties.getDefaultDomain();
        if (!StringUtils.hasText(domain)) {
            return MailProvisionVO.of(MailProvisionVO.STATE_DISABLED, null,
                    "未配置默认邮箱域名 WHALE_MAIL_DOMAIN");
        }
        Long previous = TenantContext.get();
        TenantContext.set(request.getTenantId());
        try {
            return doProvision(request, domain.trim().toLowerCase(Locale.ROOT));
        } finally {
            TenantContext.set(previous);
        }
    }

    /**
     * 开通主流程（已绑定租户上下文）
     *
     * @param request 开通请求
     * @param domain 邮箱域名（小写）
     * @return 开通结果
     */
    private MailProvisionVO doProvision(MailProvisionRequest request, String domain) {
        MailAccount existing = findByUser(request.getUserId());
        if (existing != null) {
            return MailProvisionVO.of(MailProvisionVO.STATE_EXISTS, existing.getEmail(), "该用户已有邮箱账号");
        }
        MailProvisionVO configProblem = checkEndpoints();
        if (configProblem != null) {
            return configProblem;
        }
        String localPart = resolveLocalPart(request, domain);
        String email = localPart + "@" + domain;
        if (accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getEmail, email)) > 0) {
            return MailProvisionVO.of(MailProvisionVO.STATE_CONFLICT, email, "地址已被平台内其他账号占用");
        }
        // Mailcow 侧单独判一次：平台无记录但邮箱已存在，说明是上次开通的半成品或人工建的，
        // 这种情况绝不能直接改它的口令（可能是某人在用的邮箱），只能交人工决定
        if (mailcowClient.exists(email)) {
            return MailProvisionVO.of(MailProvisionVO.STATE_CONFLICT, email,
                    "地址已在 Mailcow 中存在但未绑定到平台账号，请先在 Mailcow 删除该邮箱，或在平台手工绑定");
        }
        String password = randomPassword();
        mailcowClient.createMailbox(localPart, domain, displayNameOf(request), password);
        try {
            MailAccount account = buildAccount(request, email, domain, password);
            transactionTemplate.executeWithoutResult(status -> {
                accountMapper.insert(account);
                folderService.ensureSystemFolders(account);
            });
            log.info("平台代开邮箱成功: tenantId={}, userId={}, email={}",
                    request.getTenantId(), request.getUserId(), email);
            return MailProvisionVO.of(MailProvisionVO.STATE_CREATED, email, null);
        } catch (RuntimeException e) {
            mailcowClient.deleteMailboxQuietly(email);
            log.error("平台代开邮箱落库失败，已回滚 Mailcow 邮箱: email={}, reason={}", email, e.getMessage());
            throw new BizException(ErrorCode.SYSTEM_ERROR, "邮箱已创建但落库失败，已回滚：" + e.getMessage());
        }
    }

    /**
     * 校验协议端点：没有 IMAP/SMTP 主机时账号即使建出来也连不上
     *
     * @return 配置缺失时的结果；配置齐全返回 null
     */
    private MailProvisionVO checkEndpoints() {
        if (!StringUtils.hasText(properties.getDefaultImapHost())
                || !StringUtils.hasText(properties.getDefaultSmtpHost())) {
            return MailProvisionVO.of(MailProvisionVO.STATE_DISABLED, null,
                    "未配置默认协议端点：需设置 WHALE_MAIL_IMAP_HOST 与 WHALE_MAIL_SMTP_HOST");
        }
        return null;
    }

    /**
     * 生成邮箱地址 @ 前的部分
     *
     * <p>用户名是中文或全是符号时无法直接做本地部分，回退成 {@code u{userId}}：
     * 先保证每个用户都有可用地址，再谈地址好不好看。</p>
     *
     * @param request 开通请求
     * @param domain 邮箱域名
     * @return 本地部分
     */
    private String resolveLocalPart(MailProvisionRequest request, String domain) {
        if (StringUtils.hasText(request.getEmail())) {
            String specified = MailAddressUtils.normalize(request.getEmail());
            if (!specified.endsWith("@" + domain)) {
                throw new BizException(ErrorCode.PARAM_ERROR, "指定邮箱地址必须属于域名 " + domain);
            }
            return specified.substring(0, specified.lastIndexOf('@'));
        }
        String base = sanitize(request.getUsername());
        if (!StringUtils.hasText(base)) {
            return "u" + request.getUserId();
        }
        // 撞名时拼上用户ID：确定性规则，重复调用得到同一结果，不会每次都生成新地址
        if (existsInPlatform(base, domain)) {
            return base + request.getUserId();
        }
        return base;
    }

    /**
     * 把用户名规整成合法的本地部分
     *
     * @param username 用户名
     * @return 只含小写字母、数字、点、下划线、连字符的字符串；全非法时返回空串
     */
    private String sanitize(String username) {
        String cleaned = username == null ? "" : username.trim().toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9._-]", "");
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.length() > LOCAL_PART_MAX) {
            cleaned = cleaned.substring(0, LOCAL_PART_MAX);
        }
        return cleaned;
    }

    /**
     * 判断地址在平台内是否已存在
     *
     * @param localPart 本地部分
     * @param domain 域名
     * @return 存在返回 true
     */
    private boolean existsInPlatform(String localPart, String domain) {
        return accountMapper.selectCount(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getEmail, localPart + "@" + domain)) > 0;
    }

    /**
     * 组装账号实体
     *
     * @param request 开通请求
     * @param email 邮箱地址
     * @param domain 邮箱域名
     * @param password 初始口令（明文，仅用于加密）
     * @return 账号实体
     */
    private MailAccount buildAccount(MailProvisionRequest request, String email, String domain, String password) {
        MailAccount account = new MailAccount();
        account.setUserId(request.getUserId());
        account.setEmail(email);
        account.setDomain(domain);
        account.setDisplayName(displayNameOf(request));
        account.setAccountType(DEFAULT_ACCOUNT_TYPE);
        account.setProvisionSource(SOURCE_AUTO);
        account.setStatus(STATUS_ENABLED);
        account.setSyncEnabled(1);
        account.setSecretCipher(secretCipher.encrypt(password));
        account.setImapHost(properties.getDefaultImapHost());
        account.setImapPort(properties.getDefaultImapPort());
        account.setImapSsl(properties.isDefaultImapSsl() ? 1 : 0);
        account.setSmtpHost(properties.getDefaultSmtpHost());
        account.setSmtpPort(properties.getDefaultSmtpPort());
        account.setSmtpSsl(properties.isDefaultSmtpSsl() ? 1 : 0);
        long quotaBytes = (long) properties.getMailcow().getQuotaMb() * 1024L * 1024L;
        account.setQuotaBytes(quotaBytes);
        account.setUsedBytes(0L);
        return account;
    }

    /**
     * 取显示名：优先请求值，其次用户名
     *
     * @param request 开通请求
     * @return 显示名
     */
    private String displayNameOf(MailProvisionRequest request) {
        return StringUtils.hasText(request.getDisplayName())
                ? request.getDisplayName().trim() : request.getUsername().trim();
    }

    /**
     * 查询用户现有账号
     *
     * @param userId 用户 ID
     * @return 账号；不存在返回 null
     */
    private MailAccount findByUser(Long userId) {
        List<MailAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MailAccount>()
                .eq(MailAccount::getUserId, userId)
                .eq(MailAccount::getStatus, STATUS_ENABLED)
                .last("LIMIT 1"));
        return accounts.isEmpty() ? null : accounts.get(0);
    }

    /**
     * 生成随机口令
     *
     * @return 明文口令
     */
    private String randomPassword() {
        StringBuilder builder = new StringBuilder(PASSWORD_LENGTH);
        for (int i = 0; i < PASSWORD_LENGTH; i++) {
            builder.append(PASSWORD_ALPHABET.charAt(RANDOM.nextInt(PASSWORD_ALPHABET.length())));
        }
        return builder.toString();
    }
}
