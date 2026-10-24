package com.bluewhale.im.vo;

import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * 消息游标分页结果。
 *
 * <p>按 {@code seq} 游标取数，不返回总条数；{@code hasMore} 表示沿当前方向是否还有更早/更新的消息。</p>
 *
 * @author BlueWhale
 */
@Data
@Builder
public class MessagePageVO {

    /** 消息记录（始终按 seq 升序返回） */
    private List<MessageVO> records;

    /** 是否还有更多 */
    private boolean hasMore;
}
