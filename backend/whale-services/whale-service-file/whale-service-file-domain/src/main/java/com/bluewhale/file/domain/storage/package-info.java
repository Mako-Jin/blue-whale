package com.bluewhale.file.domain.storage;

/**
 * 存储子域端口：文件存储端口、存储插件 SPI 与插件目录查询端口。
 *
 * <p>接口定义于领域层，由 {@code infrastructure}（插件注册表）与各存储插件 jar 适配实现，
 * 业务侧只依赖端口，不感知本地磁盘 / MinIO 等具体介质。</p>
 *
 * @author BlueWhale
 */
