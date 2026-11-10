package com.bluewhale.common.mybatis.config;

import com.baomidou.mybatisplus.annotation.DbType;

import java.util.Locale;
import java.util.Map;

/**
 * {@code whale.db.type} 配置值到 MyBatis-Plus {@link DbType} 方言枚举的解析器。
 *
 * <p>静态纯函数，便于单元测试。非法取值直接抛出 {@link IllegalArgumentException}
 * （启动期 fail fast），错误信息列出全部合法取值。当前支持五种取值：
 * {@code mysql} / {@code dm} / {@code kingbase} / {@code opengauss} / {@code oceanbase}；
 * 其中 OceanBase 按 MySQL 兼容模式接入（复用 MySQL 方言）。</p>
 *
 * @author BlueWhale
 */
public final class DbTypeParser {

    /** 未配置 whale.db.type 时的默认方言（与历史版本写死 MYSQL 的行为一致） */
    private static final DbType DEFAULT_DB_TYPE = DbType.MYSQL;

    /** 合法取值清单，用于 fail fast 错误信息 */
    private static final String SUPPORTED_TYPES = "mysql, dm, kingbase, opengauss, oceanbase";

    /** whale.db.type 合法取值到 MyBatis-Plus 方言枚举的映射（OceanBase 按 MySQL 模式接入） */
    private static final Map<String, DbType> MAPPINGS = Map.of(
            "mysql", DbType.MYSQL,
            "dm", DbType.DM,
            "kingbase", DbType.KINGBASE_ES,
            "opengauss", DbType.OPENGAUSS,
            "oceanbase", DbType.MYSQL);

    /**
     * 工具类禁止实例化
     */
    private DbTypeParser() {
    }

    /**
     * 解析 whale.db.type 配置值为 MyBatis-Plus 方言枚举。
     *
     * <p>大小写不敏感并忽略首尾空白；{@code null} 或空白视为未配置，回落默认 MySQL，
     * 保证不配置 {@code whale.db.type} 时与历史版本行为完全一致。</p>
     *
     * @param type whale.db.type 配置值（可为 null 或空白）
     * @return 对应的 MyBatis-Plus 方言枚举
     * @throws IllegalArgumentException 配置值不在合法取值范围内（fail fast）
     */
    public static DbType parse(String type) {
        if (type == null || type.isBlank()) {
            return DEFAULT_DB_TYPE;
        }
        DbType dbType = MAPPINGS.get(type.trim().toLowerCase(Locale.ROOT));
        if (dbType == null) {
            throw new IllegalArgumentException(
                    "Unsupported whale.db.type '" + type + "', legal values: [" + SUPPORTED_TYPES + "]");
        }
        return dbType;
    }
}
