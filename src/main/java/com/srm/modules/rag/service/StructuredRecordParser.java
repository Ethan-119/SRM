package com.srm.modules.rag.service;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.srm.modules.rag.vo.CleanRecord;
import com.srm.modules.rag.vo.CleanStats;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 结构化记录解析器：把「逐行分隔」的文本（tab / 逗号）识别成「一行一条」的向量文档。
 *
 * <p>用于 txt / csv 这类记录型数据。字段映射优先读表头；无表头时按固定列序
 * {@code id、标题、正文、来源、metadata}，其余列忽略。</p>
 *
 * <p>说明：分隔符按 tab / 逗号简单切分，不处理 CSV 引号内逗号（面向 tab 分隔档案数据够用）。</p>
 */
@Component
public class StructuredRecordParser {

    private static final Set<String> H_ID = Set.of("id", "docid", "doc_id", "编号", "文档id");
    private static final Set<String> H_TITLE = Set.of("title", "标题", "名称", "公司名");
    private static final Set<String> H_CONTENT = Set.of("content", "正文", "内容");
    private static final Set<String> H_SOURCE = Set.of("source", "来源", "类型");
    private static final Set<String> H_METADATA = Set.of("metadata", "meta", "元数据");

    /** 是否为「逐行一条一档」的记录型数据。 */
    public boolean isRecordText(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String t = text.replace("\r\n", "\n").replace('\r', '\n');
        List<String> lines = new ArrayList<>();
        for (String line : t.split("\n")) {
            String s = line.trim();
            if (!s.isEmpty()) {
                lines.add(s);
            }
        }
        if (lines.size() < 2) {
            return false;
        }
        char delim = detectDelimiter(lines);
        if (delim == 0) {
            return false;
        }
        int cols = -1;
        int consistent = 0;
        for (String line : lines) {
            int c = split(line, delim).size();
            if (c < 2) {
                return false;
            }
            if (cols < 0) {
                cols = c;
            }
            if (c == cols) {
                consistent++;
            }
        }
        return consistent >= (int) Math.ceil(lines.size() * 0.8);
    }

    /** 解析为逐条记录，并统计清洗数量。 */
    public ParseResult parse(String text) {
        boolean removedBom = false;
        int removedEmptyLines = 0;
        int removedDuplicateLines = 0;
        int removedControlChars = 0;

        String t = (text == null) ? "" : text;
        if (t.startsWith("\uFEFF")) {
            t = t.substring(1);
            removedBom = true;
        }
        t = t.replace("\r\n", "\n").replace('\r', '\n');

        StringBuilder filtered = new StringBuilder(t.length());
        for (int i = 0; i < t.length(); i++) {
            char c = t.charAt(i);
            if (c == '\n' || c == '\t') {
                filtered.append(c);
            } else if (c < 0x20) {
                removedControlChars++;
            } else {
                filtered.append(c);
            }
        }
        t = filtered.toString();

        List<String> lines = new ArrayList<>();
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (String line : t.split("\n", -1)) {
            String s = line.trim();
            if (s.isEmpty()) {
                removedEmptyLines++;
                continue;
            }
            if (!seen.add(s)) {
                removedDuplicateLines++;
                continue;
            }
            lines.add(s);
        }

        char delim = detectDelimiter(lines);
        boolean hasHeader = !lines.isEmpty() && isHeader(lines.get(0), delim);

        int idxId = 0;
        int idxTitle = 1;
        int idxContent = 2;
        int idxSource = 3;
        int idxMetadata = 4;
        int start = 0;
        if (hasHeader) {
            String[] header = split(lines.get(0), delim).toArray(new String[0]);
            idxId = indexOf(header, H_ID);
            idxTitle = indexOf(header, H_TITLE);
            idxContent = indexOf(header, H_CONTENT);
            idxSource = indexOf(header, H_SOURCE);
            idxMetadata = indexOf(header, H_METADATA);
            start = 1;
        }

        List<CleanRecord> records = new ArrayList<>();
        for (int i = start; i < lines.size(); i++) {
            List<String> cols = split(lines.get(i), delim);
            String id = cell(cols, idxId);
            String title = cell(cols, idxTitle);
            String content = cell(cols, idxContent);
            String source = cell(cols, idxSource);
            Map<String, Object> metadata = parseMetadata(cell(cols, idxMetadata));

            if (content.isBlank()) {
                content = lines.get(i).trim();
            }
            if (id.isBlank()) {
                id = "record-" + (i + 1);
            }
            records.add(new CleanRecord(id, title, content, source, metadata));
        }

        CleanStats stats = new CleanStats(removedBom, removedEmptyLines, removedDuplicateLines, removedControlChars);
        return new ParseResult(records, stats);
    }

    // ==================== 工具方法 ====================

    private char detectDelimiter(List<String> lines) {
        int tab = 0;
        int comma = 0;
        int sample = Math.min(lines.size(), 20);
        for (int i = 0; i < sample; i++) {
            for (char c : lines.get(i).toCharArray()) {
                if (c == '\t') {
                    tab++;
                } else if (c == ',') {
                    comma++;
                }
            }
        }
        if (tab > comma && tab > 0) {
            return '\t';
        }
        if (comma > 0) {
            return ',';
        }
        return 0;
    }

    private List<String> split(String line, char delim) {
        if (delim == '\t') {
            return Arrays.asList(line.split("\t", -1));
        }
        return Arrays.asList(line.split(",", -1));
    }

    private boolean isHeader(String line, char delim) {
        for (String cell : split(line, delim)) {
            String c = cell.trim().toLowerCase();
            if (H_ID.contains(c) || H_TITLE.contains(c) || H_CONTENT.contains(c)
                    || H_SOURCE.contains(c) || H_METADATA.contains(c)) {
                return true;
            }
        }
        return false;
    }

    private int indexOf(String[] header, Set<String> candidates) {
        for (int i = 0; i < header.length; i++) {
            if (candidates.contains(header[i].trim().toLowerCase())) {
                return i;
            }
        }
        return -1;
    }

    private String cell(List<String> cols, int idx) {
        if (idx < 0 || idx >= cols.size()) {
            return "";
        }
        String v = cols.get(idx);
        return v == null ? "" : v.trim();
    }

    private Map<String, Object> parseMetadata(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            JSONObject obj = JSONUtil.parseObj(raw);
            return obj;
        } catch (Exception e) {
            return null;
        }
    }

    /** 解析结果：逐条记录 + 清洗统计。 */
    public record ParseResult(List<CleanRecord> records, CleanStats stats) {
    }
}
