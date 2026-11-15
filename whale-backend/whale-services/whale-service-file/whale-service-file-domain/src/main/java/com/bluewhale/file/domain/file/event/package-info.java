package com.bluewhale.file.domain.file.event;

/**
 * 文件资产领域事件：锁定 / 解锁 / 删除 / 恢复的已发生事实。
 *
 * <p>事件仅用于同进程内解耦（审计、缓存失效、索引同步等），不承诺跨进程投递；
 * 需要可靠投递时应由应用层另接 outbox / MQ，领域包不感知具体消息实现。</p>
 *
 * @author BlueWhale
 */
