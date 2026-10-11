package com.bluewhale.sts.auth.common.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableLogic;
import lombok.Getter;
import lombok.Setter;

/**
 * 权限域实体基类 —— 统一承载**审计字段**与**逻辑删除**。
 *
 * <p>放在权限域而不是 {@code sts-common-db}，是因为审计里的 {@code create_by / update_by}
 * 需要知道"当前登录用户是谁"，而当前用户来自本域的认证上下文。
 * 若下沉到公共层，公共层就得为此定义一套 SPI 反被业务反向约束 ——
 * 等第二个域也需要审计时再上移，那时它的形态才由两个真实用例决定
 * （与"模块按需创建，不预置空壳"是同一条原则）。
 *
 * <p><b>属性名与列名同构，刻意不加 {@code @TableField("xxx")} 指定列名。</b>
 * 见 {@code docs/decisions/0009} 第 3 条：MyBatis-Plus 一旦发现"列名与属性名不一致"，
 * 会自动补 {@code 列名 AS 属性名} 的别名，而别名可能又撞上保留字。
 * 让 {@code createBy → create_by} 走全局的下划线转驼峰推导，不产生别名。
 *
 * <p><b>时间字段一律 {@code Long} 毫秒</b>（与 {@code bw_client_log} 一致）：
 * 绕开三套数据库在日期类型与时区上的差异，这是本仓已验证的一条省事做法。
 *
 * <p><b>{@code deleted} 语义全仓统一为 0=正常、1=已删除</b>，由 {@link TableLogic} 承接：
 * MyBatis-Plus 的默认逻辑删除值正是 1、未删除值 0，因此零配置即可。
 * 参照实现 {@code SystemTerminator} 在这一列上自相矛盾（不同表 0/1 含义相反），
 * 那是迁移时**必须修掉**的缺陷。
 */
@Getter
@Setter
public abstract class BaseEntity {

    /** 创建人用户主键。无请求上下文（如系统初始化）时为 null */
    @TableField(fill = FieldFill.INSERT)
    private Long createBy;

    /** 创建时间（毫秒） */
    @TableField(fill = FieldFill.INSERT)
    private Long createTime;

    /** 最后更新人用户主键 */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateBy;

    /** 最后更新时间（毫秒） */
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private Long updateTime;

    /** 逻辑删除标记：0 正常、1 已删除。查询自动追加 {@code deleted = 0} */
    @TableLogic
    private Integer deleted;

}
