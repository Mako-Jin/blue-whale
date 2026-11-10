package com.bluewhale.file.domain;

/**
 * 文件服务领域层（DDD 四层架构最内层）。
 *
 * <p><b>依赖规则</b>：只依赖 JDK 与项目核心库（如 {@code com.bluewhale.common.core.exception}）；
 * <b>禁止</b>出现 Spring / MyBatis-Plus / Web / 数据库注解与类型。外部能力（持久化、对象存储）
 * 一律以接口（端口）在本层声明，由 {@code infrastructure} 与存储插件适配实现。</p>
 *
 * <p>子包：{@code file}（文件聚合与仓储端口）、{@code model.vo}（值对象）、{@code storage}（存储端口与插件 SPI）。</p>
 *
 * @author BlueWhale
 */
