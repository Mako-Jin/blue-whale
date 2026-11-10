package com.bluewhale.agent.support.ingest.chunker;

import com.bluewhale.agent.support.ingest.Chunk;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Java 切分器测试：类头与方法单元识别。
 *
 * @author BlueWhale
 */
class JavaChunkerTest {

    private final JavaChunker chunker = new JavaChunker();

    @Test
    @DisplayName("识别类头与方法单元")
    void shouldSplitClassAndMethods() {
        String source = """
                package demo;

                public class Demo {
                    private int value;

                    public int getValue() {
                        return value;
                    }

                    public void setValue(int value) {
                        this.value = value;
                    }
                }
                """;
        List<Chunk> chunks = chunker.chunk("backend/Demo.java", "whale-demo", source);
        assertTrue(chunks.stream().anyMatch(chunk -> "Demo".equals(chunk.getTitle())));
        assertTrue(chunks.stream().anyMatch(chunk -> "Demo#getValue".equals(chunk.getTitle())));
        assertTrue(chunks.stream().anyMatch(chunk -> "Demo#setValue".equals(chunk.getTitle())));
        assertTrue(chunks.stream().allMatch(chunk -> "code".equals(chunk.getKind())));
    }
}
