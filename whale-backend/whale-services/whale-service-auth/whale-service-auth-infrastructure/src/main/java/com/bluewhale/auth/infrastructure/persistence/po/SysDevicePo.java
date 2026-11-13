package com.bluewhale.auth.infrastructure.persistence.po;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录设备实体（权限中心扩展 M3：对应 {@code sys_device}，设备指纹 + 用户维度唯一）。
 *
 * @author BlueWhale
 */
@Data
@TableName("sys_device")
public class SysDevicePo implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    /** 租户 ID */
    private Long tenantId;
    /** 用户 ID */
    private Long userId;
    /** 设备指纹（租户+用户+指纹唯一） */
    private String deviceFingerprint;
    /** 设备名称 */
    private String deviceName;
    /** 设备类型：PC / MOBILE / TABLET / UNKNOWN */
    private String deviceType;
    /** 操作系统（User-Agent 解析快照） */
    private String os;
    /** 浏览器（User-Agent 解析快照） */
    private String browser;
    /** 是否信任设备：0 否 / 1 是 */
    private Integer trusted;
    /** 最近一次使用时间 */
    private LocalDateTime lastLoginTime;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
    private Integer deleted;
}
