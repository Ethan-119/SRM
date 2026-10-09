package com.srm.modules.rag.vo;

/**
 * 数据清洗统计。
 *
 * @param removedBom            是否去除了 UTF-8 BOM
 * @param removedEmptyLines     去除的空行数
 * @param removedDuplicateLines 去除的重复行数
 * @param removedControlChars   去除的非法控制字符数
 */
public record CleanStats(boolean removedBom, int removedEmptyLines, int removedDuplicateLines, int removedControlChars) {
}
