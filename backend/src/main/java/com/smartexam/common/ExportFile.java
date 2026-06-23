package com.smartexam.common;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

/**
 * 一个可下载的导出文件（文件名 + 内容字节）。文件名用 RFC 5987 编码写入
 * Content-Disposition，浏览器与 Excel 均可正确识别中文文件名。
 */
public record ExportFile(String filename, byte[] content) {

    public ResponseEntity<byte[]> toDownload() {
        ContentDisposition disposition = ContentDisposition.attachment()
                .filename(filename, StandardCharsets.UTF_8)
                .build();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .body(content);
    }
}
