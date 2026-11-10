package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * SQL 切分器测试：按分号切分并以表名为标题。
 *
 * @author BlueWhale
 */
class SqlChunkerTest {

    private final SqlChunker chunker = new SqlChunker();

    @Test
    @DisplayName("CREATE TABLE 以表名为标题")
    void shouldUseTableNameAsTitle() {
        String sql = """
                CREATE TABLE sys_user (
                    id BIGINT NOT NULL
                );

                ALTER TABLE sys_user ADD COLUMN email VARCHAR(128);
                """;
        List<Chunk> chunks = chunker.chunk("backend/db/init.sql", "whale-auth", sql);
        assertEquals(2, chunks.size());
        assertEquals("sys_user", chunks.get(0).getTitle());
        assertEquals("sys_user", chunks.get(1).getTitle());
        assertEquals("sql", chunks.get(0).getKind());
        assertTrue(chunks.get(0).getStartLine() >= 1);
    }
}
