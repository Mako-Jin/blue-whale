package com.bluewhale.forum.service;

/**
 * 浏览计数服务：同人同帖滚动窗口内只计一次。
 *
 * <p>去重落库（{@code fm_thread_view}）而非 Redis：详情接口是读主路径，
 * 不该因为缓存不可用而 500；一行的代价换「刷新页面刷不出浏览量」这条硬规则。</p>
 *
 * @author BlueWhale
 */
public interface ViewCountService {

    /**
     * 判断本次浏览是否应计入浏览数，并把去重行推进到当前时间。
     *
     * @param threadId 帖子ID
     * @param userId 浏览人（未登录不会进入本方法，网关已挡）
     * @return {@code true} 表示本次应 +1
     */
    boolean touch(Long threadId, Long userId);
}
