package com.srm.modules.rag.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 文本切块器：把长文档切成若干片段，避免单次 embedding 超长、同时保证检索粒度。
 *
 * <p>策略：先按段落切分，短段落合并到接近 {@code chunkSize} 的块；
 * 超长段落进一步按中英文句末标点切分，单句仍超长则硬切。</p>
 */
@Component
public class TextChunker {

    private final int chunkSize;

    public TextChunker(@Value("${srm.rag.chunk-size:800}") int chunkSize) {
        this.chunkSize = chunkSize;
    }

    public List<String> chunk(String text) {
        List<String> chunks = new ArrayList<>();
        if (text == null || text.isBlank()) {
            return chunks;
        }
        String normalized = text.replace("\r\n", "\n").replace('\r', '\n');

        // 1. 段落级粗切，超长段落按句子进一步切分
        List<String> segments = new ArrayList<>();
        for (String para : normalized.split("\\n+")) {
            String p = para.trim();
            if (p.isEmpty()) {
                continue;
            }
            segments.addAll(splitLong(p));
        }

        // 2. 合并短片段到接近 chunkSize 的块
        StringBuilder buf = new StringBuilder();
        for (String seg : segments) {
            if (seg.length() >= chunkSize) {
                if (buf.length() > 0) {
                    chunks.add(buf.toString().trim());
                    buf.setLength(0);
                }
                chunks.add(seg);
            } else if (buf.length() + seg.length() + 1 <= chunkSize) {
                if (buf.length() > 0) {
                    buf.append('\n');
                }
                buf.append(seg);
            } else {
                chunks.add(buf.toString().trim());
                buf.setLength(0);
                buf.append(seg);
            }
        }
        if (buf.length() > 0) {
            chunks.add(buf.toString().trim());
        }
        return chunks;
    }

    /** 超长文本按句末标点切分，单句仍超长则硬切。 */
    private List<String> splitLong(String s) {
        List<String> out = new ArrayList<>();
        if (s.length() <= chunkSize) {
            out.add(s);
            return out;
        }
        String[] sentences = s.split("(?<=[。！？!?；;])");
        StringBuilder buf = new StringBuilder();
        for (String sent : sentences) {
            if (sent.isEmpty()) {
                continue;
            }
            if (buf.length() + sent.length() <= chunkSize) {
                buf.append(sent);
            } else {
                if (buf.length() > 0) {
                    out.add(buf.toString());
                    buf.setLength(0);
                }
                while (sent.length() > chunkSize) {
                    out.add(sent.substring(0, chunkSize));
                    sent = sent.substring(chunkSize);
                }
                buf.append(sent);
            }
        }
        if (buf.length() > 0) {
            out.add(buf.toString());
        }
        return out;
    }
}
