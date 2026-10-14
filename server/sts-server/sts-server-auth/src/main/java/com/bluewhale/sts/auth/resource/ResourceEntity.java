package com.bluewhale.sts.auth.resource;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.bluewhale.sts.auth.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

/**
 * 资源实体，对应表 {@code bw_auth_resource}。
 *
 * <p>资源是鉴权的**判定对象**：菜单、页面、按钮、API 都是一种资源。
 * {@code resourceCode} 就是 {@code @RequiresPermission("...")} 里写的那个字符串，
 * 全库唯一 —— 它是对外承诺的稳定标识，改它等于改所有引用点，因此要当作契约对待。
 *
 * <p><b>为什么资源要有 {@code requestUrl} / {@code requestMethod}：</b>
 * 它们让资源可以自动登记（扫描 Controller 生成），而不必手工维护一张
 * "URL 到权限"的映射表；同时也让"这个接口需要什么权限"这件事在库里可查。
 *
 * <p><b>{@code isWhitelist} 是一列人工备注，它不参与判定、也当不了那份清单。</b>
 * 免鉴权由"接口上**没有** {@code @RequiresPermission}"决定（{@code AuthInterceptor} 的规则
 * 是白名单反着写的，见 {@code docs/decisions/0026}），那份真清单在
 * {@link com.bluewhale.sts.auth.common.AuthPublicEndpoints} 里。
 * 本列**不可能是**那份清单的原因很直接：种子资源由
 * {@link com.bluewhale.sts.auth.common.AuthPermissions#ALL} 建出，
 * 也就是**只有带权限码的接口才有行** —— 而真正公开的那些（登录、健康检查、上报）
 * 在这里**一行都没有**，于是这一列恒为 0，读起来却像"没有接口是公开的"。
 * （此前的注释把它写成"把白名单放进资源表以便查看与审计"，那句话从未成立，
 * 见 {@code docs/decisions/0052}。）
 */
@Getter
@Setter
@TableName("bw_auth_resource")
public class ResourceEntity extends BaseEntity {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 所属租户；平台级资源为 null */
    private Long tenantId;

    /** 资源标识，即 {@code @RequiresPermission} 的值，全局唯一 */
    private String resourceCode;

    private String resourceName;

    /** 类型：SYSTEM / SERVER / SERVICE / MODULE / MENU / PAGE / API / BUTTON / DATA */
    private String resourceType;

    /** 业务分类 */
    private String resourceCategory;

    /** 父资源标识 */
    private String parentCode;

    private String requestUrl;

    private String requestMethod;

    private String icon;

    private Integer sortNo;

    /** 是否启用：0 否 1 是。**唯一在本张表上真正参与判定的开关**（判定 SQL 读 {@code is_enabled = 1}） */
    private Integer isEnabled;

    /** 人工备注：0 否 1 是。**没有任何读取方**，免鉴权见类注释 */
    private Integer isWhitelist;

    /** 人工备注：0 否 1 是。**没有任何读取方**（界面也没有它的控件） */
    private Integer isDeprecated;

    /** 资源版本号 */
    private String resourceVersion;

    private String description;

}
