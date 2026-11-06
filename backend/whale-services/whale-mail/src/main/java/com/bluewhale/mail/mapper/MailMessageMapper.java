package com.bluewhale.mail.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.bluewhale.mail.entity.MailMessage;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 邮件索引 Mapper。
 *
 * <p>全文检索走 MySQL ngram 全文索引（{@code ft_mail_message}），列顺序必须与索引定义
 * 完全一致，否则 MySQL 报「Can't find FULLTEXT index matching the column list」。</p>
 *
 * @author BlueWhale
 */
public interface MailMessageMapper extends BaseMapper<MailMessage> {

    /**
     * 按相关度检索邮件ID。
     *
     * <p>只返回 ID：分页在调用方内存里按相关度顺序切，避免把 MATCH 分数带进分页插件。
     * BOOLEAN MODE 的表达式由 {@code MailSearchText} 构造，已做元字符剥离与短语包裹。</p>
     *
     * @param tenantId 租户ID（自定义 SQL 不经过租户拦截器，须显式过滤）
     * @param accountId 邮箱账号ID
     * @param folderId 文件夹ID；{@code null} 表示跨文件夹
     * @param expr BOOLEAN MODE 检索表达式
     * @param limit 最多返回条数
     * @return 邮件ID列表（相关度降序）
     */
    @Select("""
            <script>
            SELECT id FROM mail_message
            WHERE tenant_id = #{tenantId}
              AND deleted = 0
              AND account_id = #{accountId}
              <if test="folderId != null">AND folder_id = #{folderId}</if>
              AND MATCH(subject, from_addr, from_name, preview, body_text) AGAINST (#{expr} IN BOOLEAN MODE)
            ORDER BY MATCH(subject, from_addr, from_name, preview, body_text) AGAINST (#{expr} IN BOOLEAN MODE) DESC,
                     sent_time DESC, id DESC
            LIMIT #{limit}
            </script>
            """)
    List<Long> searchFullText(@Param("tenantId") Long tenantId,
                              @Param("accountId") Long accountId,
                              @Param("folderId") Long folderId,
                              @Param("expr") String expr,
                              @Param("limit") int limit);
}
