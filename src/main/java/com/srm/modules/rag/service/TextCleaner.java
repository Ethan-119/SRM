package com.srm.modules.rag.service;

import com.srm.modules.rag.vo.CleanStats;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 纯文本清洗器：对解析后的文本做无损清洗（去 BOM、统一换行、去控制字符、去空行、折叠空白、去重复行）。
 *
 * <p>注意：会把 tab 折叠成空格，因此只用于「长文」；逐行分隔的结构化数据请走
 * {@link StructuredRecordParser}（它保留分隔符）。</p>
 */
@Component
public class TextCleaner {

    public CleanResult clean(String raw) {
        boolean removedBom = false;
        int removedEmptyLines = 0;
        int removedDuplicateLines = 0;
        int removedControlChars = 0;

        String text = (raw == null) ? "" : raw;

        if (text.startsWith("\uFEFF")) {
            text = text.substring(1);
            removedBom = true;
        }
        text = text.replace("\r\n", "\n").replace('\r', '\n');

        // 去非法控制字符（保留 \n 与 \t）
        StringBuilder filtered = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\n' || c == '\t') {
                filtered.append(c);
            } else if (c < 0x20) {
                removedControlChars++;
            } else {
                filtered.append(c);
            }
        }
        text = filtered.toString();

        // 逐行：折叠空白 + 去空行 + 去重复行
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (String line : text.split("\n", -1)) {
            String cleaned = line.replaceAll("[\\t ]+", " ").trim();
            if (cleaned.isEmpty()) {
                removedEmptyLines++;
                continue;
            }
            if (!seen.add(cleaned)) {
                removedDuplicateLines++;
                continue;
            }
            out.add(cleaned);
        }

        CleanStats stats = new CleanStats(removedBom, removedEmptyLines, removedDuplicateLines, removedControlChars);
        return new CleanResult(String.join("\n", out), stats);
    }

    /** 清洗结果：清洗后的文本 + 统计。 */
    public record CleanResult(String text, CleanStats stats) {
    }
}
