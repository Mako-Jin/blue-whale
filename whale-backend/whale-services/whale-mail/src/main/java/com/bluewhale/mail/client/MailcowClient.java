package com.bluewhale.mail.client;

import com.bluewhale.common.core.exception.BizException;
import com.bluewhale.common.core.exception.ErrorCode;
import com.bluewhale.common.internaltoken.InternalTokenSupport;
import com.bluewhale.mail.config.MailProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Mailcow 管理 API 客户端：平台代开邮箱时用它建/删邮箱。
 *
 * <p><b>只在服务端使用</b>。API Key 是 Mailcow 的全权限凭据，能改任意邮箱口令；
 * 它不进前端、不进 VO、不进日志，只在进程内通过环境变量注入。</p>
 *
 * <p><b>为什么不用 IMAP/SMTP 开通</b>：协议层没有「创建邮箱」的语义。Mailcow 的
 * 账号体系归 Dovecot 配置与数据库管，只能走管理 API。</p>
 *
 * <p><b>错误处理口径</b>：Mailcow 用 HTTP 200 + 响应体里的 {@code type=error} 表达业务失败，
 * 因此不能只看状态码；{@code msg} 里通常含可读原因（如「邮箱已存在」），原样上抛便于排障。</p>
 *
 * @author BlueWhale
 */
@Slf4j
@Component
public class MailcowClient {

    /** 新增邮箱 */
    private static final String PATH_ADD_MAILBOX = "/api/v1/add/mailbox";

    /** 删除邮箱 */
    private static final String PATH_DELETE_MAILBOX = "/api/v1/delete/mailbox";

    /** 查询单个邮箱 */
    private static final String PATH_GET_MAILBOX = "/api/v1/get/mailbox/%s";

    /** API Key 请求头 */
    private static final String HEADER_API_KEY = "X-API-Key";

    /** 响应体中表示失败的 type 取值 */
    private static final String TYPE_ERROR = "error";

    /** 响应元素里的提示字段 */
    private static final String FIELD_MSG = "msg";

    /** 响应元素里的日志字段 */
    private static final String FIELD_LOG = "log";

    /** Mailcow 配置 */
    private final MailProperties.Mailcow config;

    /** HTTP 客户端；未配置基址时为 null */
    private final RestClient restClient;

    /**
     * 按集成配置构建客户端
     *
     * @param properties 邮箱服务配置
     */
    public MailcowClient(MailProperties properties) {
        this.config = properties.getMailcow();
        if (!StringUtils.hasText(config.getBaseUrl())) {
            this.restClient = null;
            return;
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(config.getConnectTimeoutMillis());
        factory.setReadTimeout(config.getReadTimeoutMillis());
        this.restClient = InternalTokenSupport.builder()
                .baseUrl(trimTrailingSlash(config.getBaseUrl()))
                .requestFactory(factory)
                .build();
    }

    /**
     * 是否已启用代开能力（开关打开且基址与 API Key 都已配置）
     *
     * @return 可用返回 true
     */
    public boolean isEnabled() {
        return config.isEnabled() && restClient != null && StringUtils.hasText(config.getApiKey());
    }

    /**
     * 创建邮箱
     *
     * @param localPart 邮箱地址 @ 前的部分
     * @param domain 邮箱域名
     * @param displayName 显示名
     * @param password 初始口令（明文，仅本次请求使用）
     */
    public void createMailbox(String localPart, String domain, String displayName, String password) {
        requireEnabled();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("local_part", localPart);
        body.put("domain", domain);
        body.put("name", StringUtils.hasText(displayName) ? displayName : localPart);
        // Mailcow 的配额单位是 MiB，0 表示不限；这里显式传值，避免落到默认的「不限」
        body.put("quota", String.valueOf(config.getQuotaMb()));
        body.put("password", password);
        body.put("password2", password);
        body.put("active", "1");
        // 不强制首次登录改密：平台自己要拿这个口令登录 IMAP，员工在 Webmail 里一改，
        // 平台侧的密文立刻失效、同步全断。口令轮换应走平台侧统一入口（后续「重置邮箱口令」）。
        body.put("force_pw_update", "0");
        // 收发都要求 TLS：明文提交会让员工口令在链路上裸奔
        body.put("tls_enforce_in", "1");
        body.put("tls_enforce_out", "1");
        post(PATH_ADD_MAILBOX, body, "创建邮箱失败");
        log.info("Mailcow 邮箱已创建: {}@{}（配额 {} MiB）", localPart, domain, config.getQuotaMb());
    }

    /**
     * 删除邮箱（补偿用，失败只记日志不抛）
     *
     * <p>场景：Mailcow 已建成功、本地落库失败。此时若不管，会留下一个无人认领的邮箱，
     * 下次代开又会因「地址已存在」失败。删除是尽力而为——删不掉也要留痕。</p>
     *
     * @param email 完整邮箱地址
     */
    public void deleteMailboxQuietly(String email) {
        if (!isEnabled() || !StringUtils.hasText(email)) {
            return;
        }
        try {
            post(PATH_DELETE_MAILBOX, List.of(email), "删除邮箱失败");
            log.warn("Mailcow 邮箱已回滚删除: {}", email);
        } catch (RuntimeException e) {
            log.error("Mailcow 邮箱回滚删除失败，需人工处理: email={}, reason={}", email, e.getMessage());
        }
    }

    /**
     * 判断邮箱是否已存在
     *
     * @param email 完整邮箱地址
     * @return 存在返回 true
     */
    public boolean exists(String email) {
        if (!isEnabled()) {
            return false;
        }
        try {
            restClient.get()
                    .uri(String.format(PATH_GET_MAILBOX, email))
                    .header(HEADER_API_KEY, config.getApiKey())
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() {
                    });
            return true;
        } catch (RestClientResponseException e) {
            // 404 表示不存在；其它状态码不能当成「不存在」，否则会重复创建
            if (e.getStatusCode().value() == 404) {
                return false;
            }
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "查询 Mailcow 邮箱状态失败：" + e.getStatusCode().value());
        }
    }

    /**
     * 发送 POST 并校验 Mailcow 的业务返回
     *
     * @param path 路径
     * @param body 请求体
     * @param failMessage 失败提示前缀
     */
    private void post(String path, Object body, String failMessage) {
        requireEnabled();
        List<Map<String, Object>> response;
        try {
            response = restClient.post()
                    .uri(path)
                    .header(HEADER_API_KEY, config.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() {
                    });
        } catch (RestClientResponseException e) {
            // 不把响应体整体打出去：Mailcow 的部分错误响应会回显请求字段
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    failMessage + "：Mailcow 返回 " + e.getStatusCode().value());
        }
        String error = firstError(response);
        if (error != null) {
            throw new BizException(ErrorCode.SYSTEM_ERROR, failMessage + "：" + error);
        }
    }

    /**
     * 从响应数组中提取第一条错误信息
     *
     * @param response 响应数组
     * @return 错误信息；无错误返回 null
     */
    private String firstError(List<Map<String, Object>> response) {
        if (response == null || response.isEmpty()) {
            return "Mailcow 返回空响应";
        }
        for (Map<String, Object> item : response) {
            Object type = item.get("type");
            if (type != null && TYPE_ERROR.equals(String.valueOf(type))) {
                Object msg = item.get(FIELD_MSG);
                if (msg != null) {
                    return String.valueOf(msg);
                }
                Object logs = item.get(FIELD_LOG);
                return logs == null ? "未知错误" : String.valueOf(logs);
            }
        }
        return null;
    }

    /**
     * 校验代开能力可用
     */
    private void requireEnabled() {
        if (!isEnabled()) {
            throw new BizException(ErrorCode.SYSTEM_ERROR,
                    "未启用 Mailcow 代开能力：请配置 WHALE_MAIL_MAILCOW_ENABLED / BASE_URL / API_KEY");
        }
    }

    /**
     * 去掉基址结尾斜杠，避免拼出 {@code //api/v1}
     *
     * @param url 基址
     * @return 规整后的基址
     */
    private static String trimTrailingSlash(String url) {
        String trimmed = url.trim();
        return trimmed.endsWith("/") ? trimmed.substring(0, trimmed.length() - 1) : trimmed;
    }
}
