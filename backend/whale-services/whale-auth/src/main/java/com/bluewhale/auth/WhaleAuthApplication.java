package com.bluewhale.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 权限中心启动类
 *
 * <p>支持 RBAC（基于角色的访问控制）+ ABAC（基于属性的访问控制）</p>
 * <p>权限层级：系统 > 菜单 > 接口 > 按钮 > 数据 > 属性</p>
 *
 * <ul>
 *   <li><b>系统级</b>：租户/应用隔离</li>
 *   <li><b>菜单级</b>：前端页面菜单可见性</li>
 *   <li><b>接口级</b>：后端 API 访问权限</li>
 *   <li><b>按钮级</b>：页面按钮/操作权限</li>
 *   <li><b>数据级</b>：行级数据范围过滤（个人/本部门/本部门及下级/全部/自定义）</li>
 *   <li><b>属性级</b>：列级字段脱敏/可见性控制</li>
 * </ul>
 *
 * @author BlueWhale
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
public class WhaleAuthApplication {

    /**
     * 应用入口：启动权限中心服务
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        SpringApplication.run(WhaleAuthApplication.class, args);
    }
}
