package com.bluewhale.security.contract;

/**
 * 网关与认证服务之间的跨服务契约常量（字段名 / 请求头名 / claim 名）。
 *
 * <p>这些名字此前分别以字面量散落在网关解析代码与 auth 序列化 DTO 中，任何一侧改名都只会导致
 * 网关「静默解析为空」——例如 route-overrides 的 {@code data.items} 与 auth 实际返回的
 * {@code data[]} 形状不一致，网关会一直保留空快照而不报错。本类提供唯一事实源，
 * 两侧代码与契约测试共同引用；改名字必须同时改动本类，从而在编译期或 CI 中暴露漂移。</p>
 *
 * @author BlueWhale
 */
public final class GatewayContract {

    /**
     * 私有构造器：常量类，禁止实例化
     */
    private GatewayContract() {
    }

    /**
     * 统一响应信封字段
     */
    public static final class Envelope {

        /** 业务码字段 */
        public static final String CODE = "code";

        /** 业务数据字段 */
        public static final String DATA = "data";

        /** 数据体中的列表字段（仅部分接口使用） */
        public static final String ITEMS = "items";

        /** 成功业务码 */
        public static final int SUCCESS_CODE = 0;

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private Envelope() {
        }
    }

    /**
     * 网关访问名单（白名单 / 黑名单）字段
     */
    public static final class AccessList {

        /** 路径模式（Ant 通配） */
        public static final String PATH_PATTERN = "pathPattern";

        /** HTTP 方法 */
        public static final String HTTP_METHOD = "httpMethod";

        /** 作用域（平台级 / 租户级） */
        public static final String SCOPE = "scope";

        /** 租户 ID */
        public static final String TENANT_ID = "tenantId";

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private AccessList() {
        }
    }

    /**
     * 路由业务属性覆盖字段
     */
    public static final class RouteOverride {

        /** 路由 ID */
        public static final String ROUTE_ID = "routeId";

        /** 是否启用 */
        public static final String ENABLED = "enabled";

        /** 生效起始时间 */
        public static final String EFFECTIVE_FROM = "effectiveFrom";

        /** 生效截止时间 */
        public static final String EFFECTIVE_TO = "effectiveTo";

        /** 版本号 */
        public static final String VERSION = "version";

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private RouteOverride() {
        }
    }

    /**
     * 当前用户信息字段（{@code /auth/v1/me}）
     */
    public static final class CurrentUser {

        /** 权限码数组 */
        public static final String PERMISSIONS = "permissions";

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private CurrentUser() {
        }
    }

    /**
     * JWT 载荷 claim 名（与 auth 签发侧逐字一致）
     */
    public static final class Claims {

        /** 用户 ID */
        public static final String USER_ID = "userId";

        /** 租户 ID */
        public static final String TENANT_ID = "tenantId";

        /** 角色码（逗号分隔） */
        public static final String ROLES = "roles";

        /** 令牌类型 */
        public static final String TOKEN_TYPE = "tokenType";

        /** 强制改密标记 */
        public static final String PWD_CHANGE_REQUIRED = "pwdChangeRequired";

        /** refreshToken 的轮换 family 标识 */
        public static final String FAMILY_ID = "familyId";

        /** 访问令牌类型值 */
        public static final String TYPE_ACCESS = "access";

        /** 刷新令牌类型值 */
        public static final String TYPE_REFRESH = "refresh";

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private Claims() {
        }
    }

    /**
     * 跨服务请求头名
     */
    public static final class Headers {

        /** 业务追踪号（网关生成或透传） */
        public static final String TRACE_ID = "X-Trace-Id";

        /** W3C 分布式追踪上下文 */
        public static final String TRACEPARENT = "traceparent";

        /** 内网令牌 */
        public static final String INTERNAL_TOKEN = "X-Internal-Token";

        /** 用户 ID */
        public static final String USER_ID = "X-User-Id";

        /** 用户名 */
        public static final String USERNAME = "X-Username";

        /** 用户角色（逗号分隔） */
        public static final String USER_ROLES = "X-User-Roles";

        /** 租户 ID */
        public static final String TENANT_ID = "X-Tenant-Id";

        /** 权限码（逗号分隔） */
        public static final String PERMISSION_CODES = "X-Permission-Codes";

        /** 归一化后的请求语言（网关按 Accept-Language 解析后注入） */
        public static final String LANGUAGE = "X-Lang";

        /**
         * 私有构造器：常量类，禁止实例化
         */
        private Headers() {
        }
    }
}
