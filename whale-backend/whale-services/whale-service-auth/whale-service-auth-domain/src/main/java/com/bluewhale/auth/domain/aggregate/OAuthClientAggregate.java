package com.bluewhale.auth.domain.aggregate;

import com.bluewhale.auth.domain.model.vo.ClientId;
import com.bluewhale.auth.domain.model.vo.GrantType;
import com.bluewhale.auth.domain.model.vo.RedirectUriSet;
import com.bluewhale.auth.domain.model.vo.ScopeSet;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.domain.exception.DomainException;
import com.bluewhale.common.domain.model.Entity;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * OAuth2 客户端聚合根（对应 {@code sys_oauth_client}，权限中心扩展 M0）。
 *
 * <p><b>不变量</b>：① {@code clientId} 全局唯一；② 客户端密钥只保存哈希，明文不进入聚合；
 * ③ 至少一种授权类型；④ 启用 {@code authorization_code} 时必须配置回调地址白名单，换票按精确匹配；
 * ⑤ scope 非空；⑥ 停用客户端不得换票（{@link #isEnabled()} 由应用层守卫）。</p>
 *
 * <p>本类为纯领域对象，不依赖 Spring / MyBatis / Web 等框架类型。</p>
 *
 * @author BlueWhale
 */
public class OAuthClientAggregate implements Entity<Long> {

    /** 状态：启用 */
    public static final int STATUS_ENABLED = 1;
    /** 状态：停用 */
    public static final int STATUS_DISABLED = 0;
    /** 访问令牌默认有效期（秒） */
    private static final int DEFAULT_ACCESS_TOKEN_TTL = 7200;
    /** 刷新令牌默认有效期（秒） */
    private static final int DEFAULT_REFRESH_TOKEN_TTL = 604800;

    private final Long id;
    private final ClientId clientId;
    private String clientSecretHash;
    private String clientName;
    private RedirectUriSet redirectUris;
    private Set<GrantType> grantTypes;
    private ScopeSet scopes;
    private int accessTokenTtl;
    private int refreshTokenTtl;
    private boolean autoApprove;
    private int status;
    private final LocalDateTime createTime;
    private LocalDateTime updateTime;

    /**
     * 私有构造器：集中校验聚合不变量
     *
     * @param id 主键（新建时为 {@code null}）
     * @param clientId 客户端标识
     * @param clientSecretHash 客户端密钥哈希
     * @param clientName 客户端名称
     * @param redirectUris 回调地址白名单
     * @param grantTypes 授权类型集合
     * @param scopes 授权范围
     * @param accessTokenTtl 访问令牌有效期（秒）
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     * @param autoApprove 是否免确认授权
     * @param status 状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     */
    private OAuthClientAggregate(Long id, ClientId clientId, String clientSecretHash, String clientName,
                                 RedirectUriSet redirectUris, Set<GrantType> grantTypes, ScopeSet scopes,
                                 int accessTokenTtl, int refreshTokenTtl, boolean autoApprove, int status,
                                 LocalDateTime createTime, LocalDateTime updateTime) {
        validateBasics(clientId, clientSecretHash, clientName, grantTypes, scopes);
        RedirectUriSet resolvedRedirects = redirectUris == null ? RedirectUriSet.of(null) : redirectUris;
        validateRedirects(grantTypes, resolvedRedirects);
        validateTtl(accessTokenTtl, refreshTokenTtl);
        validateStatus(status);
        this.id = id;
        this.clientId = clientId;
        this.clientSecretHash = clientSecretHash;
        this.clientName = clientName;
        this.redirectUris = resolvedRedirects;
        this.grantTypes = new LinkedHashSet<>(grantTypes);
        this.scopes = scopes;
        this.accessTokenTtl = accessTokenTtl;
        this.refreshTokenTtl = refreshTokenTtl;
        this.autoApprove = autoApprove;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    /**
     * 校验客户端基础字段
     *
     * @param clientId 客户端标识
     * @param clientSecretHash 客户端密钥哈希
     * @param clientName 客户端名称
     * @param grantTypes 授权类型集合
     * @param scopes 授权范围
     */
    private static void validateBasics(ClientId clientId, String clientSecretHash, String clientName,
                                       Set<GrantType> grantTypes, ScopeSet scopes) {
        if (clientId == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "客户端标识不能为空");
        }
        if (clientSecretHash == null || clientSecretHash.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "客户端密钥哈希不能为空");
        }
        if (clientName == null || clientName.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "客户端名称不能为空");
        }
        if (grantTypes == null || grantTypes.isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "至少需要一种授权类型");
        }
        if (scopes == null) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "授权范围不能为空");
        }
    }

    /**
     * 校验回调地址与授权类型的匹配关系
     *
     * @param grantTypes 授权类型集合
     * @param redirectUris 回调地址白名单
     */
    private static void validateRedirects(Set<GrantType> grantTypes, RedirectUriSet redirectUris) {
        if (grantTypes.contains(GrantType.AUTHORIZATION_CODE) && redirectUris.isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "授权码模式必须配置回调地址白名单");
        }
    }

    /**
     * 校验令牌有效期
     *
     * @param accessTokenTtl 访问令牌有效期（秒）
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     */
    private static void validateTtl(int accessTokenTtl, int refreshTokenTtl) {
        if (accessTokenTtl <= 0 || refreshTokenTtl <= 0) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "令牌有效期必须大于 0 秒");
        }
    }

    /**
     * 校验状态值
     *
     * @param status 状态
     */
    private static void validateStatus(int status) {
        if (status != STATUS_ENABLED && status != STATUS_DISABLED) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "状态值非法，仅支持 1（启用）/ 0（停用）");
        }
    }

    /**
     * 工厂方法：注册 OAuth2 客户端（默认启用）
     *
     * @param clientId 客户端标识
     * @param clientSecretHash 客户端密钥哈希（由应用层经端口计算，聚合不接触明文）
     * @param clientName 客户端名称
     * @param redirectUris 回调地址白名单
     * @param grantTypes 授权类型集合
     * @param scopes 授权范围
     * @param accessTokenTtl 访问令牌有效期（秒）
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     * @param autoApprove 是否免确认授权
     * @return 新建客户端聚合
     */
    public static OAuthClientAggregate create(String clientId, String clientSecretHash, String clientName,
                                              Collection<String> redirectUris, Collection<String> grantTypes,
                                              Collection<String> scopes, int accessTokenTtl,
                                              int refreshTokenTtl, boolean autoApprove) {
        LocalDateTime now = LocalDateTime.now();
        return new OAuthClientAggregate(null, ClientId.of(clientId), clientSecretHash, clientName,
                RedirectUriSet.of(redirectUris), parseGrantTypes(grantTypes), ScopeSet.of(scopes),
                accessTokenTtl, refreshTokenTtl, autoApprove, STATUS_ENABLED, now, now);
    }

    /**
     * 由持久化数据重建聚合
     *
     * @param id 主键
     * @param clientId 客户端标识
     * @param clientSecretHash 客户端密钥哈希
     * @param clientName 客户端名称
     * @param redirectUris 逗号分隔的回调地址
     * @param grantTypes 逗号分隔的授权类型
     * @param scopes 逗号分隔的授权范围
     * @param accessTokenTtl 访问令牌有效期（秒）
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     * @param autoApprove 是否免确认授权
     * @param status 状态
     * @param createTime 创建时间
     * @param updateTime 更新时间
     * @return 客户端聚合
     */
    public static OAuthClientAggregate reconstitute(Long id, String clientId, String clientSecretHash,
                                                    String clientName, String redirectUris, String grantTypes,
                                                    String scopes, Integer accessTokenTtl, Integer refreshTokenTtl,
                                                    Integer autoApprove, Integer status,
                                                    LocalDateTime createTime, LocalDateTime updateTime) {
        return new OAuthClientAggregate(id, ClientId.of(clientId), clientSecretHash, clientName,
                RedirectUriSet.fromText(redirectUris), parseGrantTypes(split(grantTypes)), ScopeSet.fromText(scopes),
                accessTokenTtl == null ? DEFAULT_ACCESS_TOKEN_TTL : accessTokenTtl,
                refreshTokenTtl == null ? DEFAULT_REFRESH_TOKEN_TTL : refreshTokenTtl,
                autoApprove != null && autoApprove == 1,
                status == null ? STATUS_DISABLED : status, createTime, updateTime);
    }

    /**
     * 行为：更新客户端配置（不改变 clientId 与密钥）
     *
     * @param clientName 客户端名称
     * @param redirectUris 回调地址白名单
     * @param grantTypes 授权类型集合
     * @param scopes 授权范围
     * @param accessTokenTtl 访问令牌有效期（秒）
     * @param refreshTokenTtl 刷新令牌有效期（秒）
     * @param autoApprove 是否免确认授权
     */
    public void updateSettings(String clientName, Collection<String> redirectUris, Collection<String> grantTypes,
                               Collection<String> scopes, int accessTokenTtl, int refreshTokenTtl,
                               boolean autoApprove) {
        OAuthClientAggregate validated = new OAuthClientAggregate(id, clientId, clientSecretHash, clientName,
                RedirectUriSet.of(redirectUris), parseGrantTypes(grantTypes), ScopeSet.of(scopes),
                accessTokenTtl, refreshTokenTtl, autoApprove, status, createTime, updateTime);
        this.clientName = validated.clientName;
        this.redirectUris = validated.redirectUris;
        this.grantTypes = validated.grantTypes;
        this.scopes = validated.scopes;
        this.accessTokenTtl = validated.accessTokenTtl;
        this.refreshTokenTtl = validated.refreshTokenTtl;
        this.autoApprove = validated.autoApprove;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：替换客户端密钥哈希（明文由应用层生成并经端口哈希）
     *
     * @param clientSecretHash 新密钥哈希
     */
    public void changeSecret(String clientSecretHash) {
        if (clientSecretHash == null || clientSecretHash.isBlank()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "客户端密钥哈希不能为空");
        }
        this.clientSecretHash = clientSecretHash;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：启用客户端
     */
    public void enable() {
        this.status = STATUS_ENABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 行为：停用客户端（停用后不得换票）
     */
    public void disable() {
        this.status = STATUS_DISABLED;
        this.updateTime = LocalDateTime.now();
    }

    /**
     * 判断客户端是否启用
     *
     * @return 启用返回 {@code true}
     */
    public boolean isEnabled() {
        return status == STATUS_ENABLED;
    }

    /**
     * 判断是否支持指定授权类型
     *
     * @param grantType 授权类型
     * @return 支持返回 {@code true}
     */
    public boolean supports(GrantType grantType) {
        return grantTypes.contains(grantType);
    }

    /**
     * 精确校验回调地址是否在白名单内
     *
     * @param redirectUri 待校验回调地址
     * @return 命中返回 {@code true}
     */
    public boolean allowsRedirectUri(String redirectUri) {
        return redirectUris.contains(redirectUri);
    }

    /**
     * 解析授权类型集合（空格/逗号分隔均兼容）
     *
     * @param values 授权类型字面值集合
     * @return 授权类型集合
     */
    private static Set<GrantType> parseGrantTypes(Collection<String> values) {
        Set<GrantType> types = new LinkedHashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null && !value.isBlank()) {
                    types.add(GrantType.of(value.trim()));
                }
            }
        }
        if (types.isEmpty()) {
            throw new DomainException(ErrorCode.PARAM_ERROR, "至少需要一种授权类型");
        }
        return types;
    }

    /**
     * 按逗号拆分并去除空白项
     *
     * @param text 逗号分隔文本
     * @return 拆分结果
     */
    private static Collection<String> split(String text) {
        if (text == null || text.isBlank()) {
            return java.util.List.of();
        }
        java.util.List<String> items = new java.util.ArrayList<>();
        for (String item : text.split(",")) {
            if (!item.isBlank()) {
                items.add(item.trim());
            }
        }
        return items;
    }

    /**
     * 获取主键
     *
     * @return 主键
     */
    @Override
    public Long getId() {
        return id;
    }

    /**
     * 获取客户端标识
     *
     * @return 客户端标识
     */
    public ClientId getClientId() {
        return clientId;
    }

    /**
     * 获取客户端密钥哈希
     *
     * @return BCrypt 哈希
     */
    public String getClientSecretHash() {
        return clientSecretHash;
    }

    /**
     * 获取客户端名称
     *
     * @return 客户端名称
     */
    public String getClientName() {
        return clientName;
    }

    /**
     * 获取回调地址白名单
     *
     * @return 回调地址白名单
     */
    public RedirectUriSet getRedirectUris() {
        return redirectUris;
    }

    /**
     * 获取授权类型集合
     *
     * @return 授权类型集合（只读）
     */
    public Set<GrantType> getGrantTypes() {
        return Set.copyOf(grantTypes);
    }

    /**
     * 获取授权范围
     *
     * @return 授权范围
     */
    public ScopeSet getScopes() {
        return scopes;
    }

    /**
     * 获取访问令牌有效期
     *
     * @return 有效期（秒）
     */
    public int getAccessTokenTtl() {
        return accessTokenTtl;
    }

    /**
     * 获取刷新令牌有效期
     *
     * @return 有效期（秒）
     */
    public int getRefreshTokenTtl() {
        return refreshTokenTtl;
    }

    /**
     * 是否免确认授权
     *
     * @return 免确认返回 {@code true}
     */
    public boolean isAutoApprove() {
        return autoApprove;
    }

    /**
     * 获取状态
     *
     * @return 状态（1 启用 / 0 停用）
     */
    public int getStatus() {
        return status;
    }

    /**
     * 获取创建时间
     *
     * @return 创建时间
     */
    public LocalDateTime getCreateTime() {
        return createTime;
    }

    /**
     * 获取更新时间
     *
     * @return 更新时间
     */
    public LocalDateTime getUpdateTime() {
        return updateTime;
    }
}
