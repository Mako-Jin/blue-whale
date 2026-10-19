package com.bluewhale.auth.infrastructure.persistence;

/**
 * 持久化实现层：PO（既有 {@code SysUser} 等 {@code @TableName} 实体复用为 PO）、仓储实现
 * （实现 {@code domain} 层仓储端口）与 PO↔领域对象转换（REQ-ARCH-001 / T-DDD-8）。
 *
 * @author BlueWhale
 */
