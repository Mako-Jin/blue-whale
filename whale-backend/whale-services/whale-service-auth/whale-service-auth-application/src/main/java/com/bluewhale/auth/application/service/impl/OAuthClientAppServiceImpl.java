package com.bluewhale.auth.application.service.impl;

import com.bluewhale.auth.application.model.command.SaveOAuthClientCommand;
import com.bluewhale.auth.application.model.query.OAuthClientPageQuery;
import com.bluewhale.auth.application.model.vo.OAuthClientSecretVO;
import com.bluewhale.auth.application.model.vo.OAuthClientVO;
import com.bluewhale.auth.application.port.ClientSecretVerifier;
import com.bluewhale.auth.application.service.OAuthClientAppService;
import com.bluewhale.auth.domain.aggregate.OAuthClientAggregate;
import com.bluewhale.auth.domain.repository.OAuthClientRepository;
import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.core.result.PageResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * OAuth2 客户端应用服务实现（权限中心扩展 M0）。
 *
 * <p>密钥生命周期：应用层生成明文 → {@link ClientSecretVerifier} 计算哈希 → 聚合只持有哈希；
 * 明文仅在 {@link #create} 与 {@link #rotateSecret} 的返回值中出现一次，不落库、不写日志。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthClientAppServiceImpl implements OAuthClientAppService {

    /** 客户端标识前缀 */
    private static final String CLIENT_ID_PREFIX = "bw_";
    /** 客户端标识随机字节数 */
    private static final int CLIENT_ID_BYTES = 12;
    /** 客户端密钥随机字节数 */
    private static final int CLIENT_SECRET_BYTES = 32;
    /** 访问令牌默认有效期（秒） */
    private static final int DEFAULT_ACCESS_TOKEN_TTL = 7200;
    /** 刷新令牌默认有效期（秒） */
    private static final int DEFAULT_REFRESH_TOKEN_TTL = 604800;

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final OAuthClientRepository oauthClientRepository;
    private final ClientSecretVerifier clientSecretVerifier;

    /**
     * 分页查询客户端
     *
     * @param query 分页查询
     * @return 客户端分页结果
     */
    @Override
    public PageResult<OAuthClientVO> page(OAuthClientPageQuery query) {
        long current = Math.max(query.current(), 1L);
        long size = Math.max(query.size(), 1L);
        OAuthClientRepository.OAuthClientPage page = oauthClientRepository.page(
                current, size, query.keyword(), query.status());
        List<OAuthClientVO> records = page.records().stream().map(OAuthClientVO::from).toList();
        return PageResult.of(records, page.total(), current, size);
    }

    /**
     * 注册客户端
     *
     * @param command 保存命令
     * @return 含密钥明文的视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuthClientSecretVO create(SaveOAuthClientCommand command) {
        String clientId = StringUtils.hasText(command.clientId())
                ? command.clientId().trim()
                : generateClientId();
        if (oauthClientRepository.existsByClientId(clientId)) {
            throw new BizException(ErrorCode.PARAM_ERROR, "auth.oauth.client-id-exists",
                    "客户端标识已存在：" + clientId, clientId);
        }
        String rawSecret = generateClientSecret();
        OAuthClientAggregate client = OAuthClientAggregate.create(clientId,
                clientSecretVerifier.hash(rawSecret), command.clientName(), command.redirectUris(),
                command.grantTypes(), command.scopes(),
                command.accessTokenTtl() == null ? DEFAULT_ACCESS_TOKEN_TTL : command.accessTokenTtl(),
                command.refreshTokenTtl() == null ? DEFAULT_REFRESH_TOKEN_TTL : command.refreshTokenTtl(),
                Boolean.TRUE.equals(command.autoApprove()));
        if (command.status() != null && command.status() == OAuthClientAggregate.STATUS_DISABLED) {
            client.disable();
        }
        OAuthClientAggregate saved = oauthClientRepository.save(client);
        log.info("注册 OAuth2 客户端成功, id={}, clientId={}", saved.getId(), saved.getClientId().value());
        return new OAuthClientSecretVO(saved.getId(), saved.getClientId().value(), rawSecret);
    }

    /**
     * 编辑客户端配置
     *
     * @param id 主键
     * @param command 保存命令
     * @return 更新后的客户端视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuthClientVO update(Long id, SaveOAuthClientCommand command) {
        OAuthClientAggregate client = require(id);
        client.updateSettings(command.clientName(), command.redirectUris(), command.grantTypes(),
                command.scopes(),
                command.accessTokenTtl() == null ? client.getAccessTokenTtl() : command.accessTokenTtl(),
                command.refreshTokenTtl() == null ? client.getRefreshTokenTtl() : command.refreshTokenTtl(),
                command.autoApprove() == null ? client.isAutoApprove() : command.autoApprove());
        if (command.status() != null) {
            if (command.status() == OAuthClientAggregate.STATUS_ENABLED) {
                client.enable();
            } else {
                client.disable();
            }
        }
        return OAuthClientVO.from(oauthClientRepository.save(client));
    }

    /**
     * 轮换客户端密钥
     *
     * @param id 主键
     * @return 含新密钥明文的视图对象
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public OAuthClientSecretVO rotateSecret(Long id) {
        OAuthClientAggregate client = require(id);
        String rawSecret = generateClientSecret();
        client.changeSecret(clientSecretVerifier.hash(rawSecret));
        OAuthClientAggregate saved = oauthClientRepository.save(client);
        log.info("轮换 OAuth2 客户端密钥成功, id={}, clientId={}", saved.getId(), saved.getClientId().value());
        return new OAuthClientSecretVO(saved.getId(), saved.getClientId().value(), rawSecret);
    }

    /**
     * 停用客户端
     *
     * @param id 主键
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void disable(Long id) {
        OAuthClientAggregate client = require(id);
        client.disable();
        oauthClientRepository.save(client);
    }

    /**
     * 按主键加载客户端，不存在时抛 404
     *
     * @param id 主键
     * @return 客户端聚合
     */
    private OAuthClientAggregate require(Long id) {
        return oauthClientRepository.findById(id)
                .orElseThrow(() -> new BizException(ErrorCode.NOT_FOUND,
                        "auth.oauth.client-not-found", "OAuth 客户端不存在"));
    }

    /**
     * 生成客户端标识：bw_ + 12 字节十六进制
     *
     * @return 客户端标识
     */
    private String generateClientId() {
        byte[] bytes = new byte[CLIENT_ID_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return CLIENT_ID_PREFIX + HexFormat.of().formatHex(bytes);
    }

    /**
     * 生成客户端密钥明文：32 字节 Base64URL（无填充）
     *
     * @return 密钥明文
     */
    private String generateClientSecret() {
        byte[] bytes = new byte[CLIENT_SECRET_BYTES];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
