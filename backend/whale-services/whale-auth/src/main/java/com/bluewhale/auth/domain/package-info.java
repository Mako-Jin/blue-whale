package com.bluewhale.auth.domain;

/**
 * 领域层（DDD 四层架构最内层，REQ-ARCH-001 / AUTH-14）。
 *
 * <p><b>依赖规则</b>：只依赖 JDK 与项目核心库（如 {@code com.bluewhale.common.core.exception}）；
 * <b>禁止</b>出现 Spring / MyBatis-Plus / Web / 数据库 注解与类型。外部能力（持久化、消息、缓存）
 * 一律以接口（端口）在本层声明，由 {@code infrastructure} 层适配实现。</p>
 *
 * <p>子包：{@code model}（聚合与值对象）、{@code repository}（仓储接口，迁移第 2 步引入）。</p>
 *
 * @author BlueWhale
 */
