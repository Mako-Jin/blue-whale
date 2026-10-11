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
 * <p>{@code isWhitelist} 标出免鉴权资源（如登录、健康检查）：
 * 把白名单放进资源表而不是代码里的一个静态集合，是为了它能被后台查看与审计 ——
 * "哪些接口不需要登录"是安全评审必看的一份清单。
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

    /** 是否启用：0 否 1 是 */
    private Integer isEnabled;

    /** 是否免鉴权白名单：0 否 1 是 */
    private Integer isWhitelist;

    /** 是否已废弃：0 否 1 是 */
    private Integer isDeprecated;

    /** 资源版本号 */
    private String resourceVersion;

    private String description;

}
