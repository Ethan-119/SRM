package com.srm.modules.rag.service;

import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;

/**
 * 文档正文解析器：把上传的文件（PDF / Word / CSV / 纯文本）抽取为纯文本，
 * 供 {@link TextChunker} 切块后向量化。
 */
@Slf4j
@Service
public class DocumentParserService {

    /** 根据文件扩展名分派解析器，返回纯文本正文。 */
    public String extractText(MultipartFile file) throws IOException {
        String ext = extension(file.getOriginalFilename());
        return switch (ext) {
            case "pdf" -> extractPdf(file);
            case "docx" -> extractDocx(file);
            case "csv" -> extractCsv(file);
            case "txt", "md" -> new String(file.getBytes(), StandardCharsets.UTF_8);
            default -> throw new IllegalArgumentException(
                    "不支持的文件类型: ." + ext + "（仅支持 pdf / docx / csv / txt / md）");
        };
    }

    private String extension(String name) {
        if (name == null) {
            return "";
        }
        int i = name.lastIndexOf('.');
        return i >= 0 ? name.substring(i + 1).toLowerCase() : "";
    }

    private String extractPdf(MultipartFile file) throws IOException {
        try (PDDocument doc = Loader.loadPDF(file.getBytes())) {
            return new PDFTextStripper().getText(doc);
        }
    }

    private String extractDocx(MultipartFile file) throws IOException {
        StringBuilder sb = new StringBuilder();
        try (XWPFDocument doc = new XWPFDocument(file.getInputStream())) {
            for (XWPFParagraph p : doc.getParagraphs()) {
                sb.append(p.getText()).append('\n');
            }
            // 表格内容同样抽取，避免遗漏关键信息
            for (XWPFTable table : doc.getTables()) {
                for (XWPFTableRow row : table.getRows()) {
                    for (XWPFTableCell cell : row.getTableCells()) {
                        sb.append(cell.getText().trim()).append('\t');
                    }
                    sb.append('\n');
                }
            }
        }
        return sb.toString();
    }

    private String extractCsv(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String text = new String(bytes, StandardCharsets.UTF_8);
        // CSV 常由 Excel 导出为 GBK 编码，出现替换符时回退到 GBK
        if (text.indexOf('\uFFFD') >= 0) {
            text = new String(bytes, Charset.forName("GBK"));
        }
        return text;
    }
}
