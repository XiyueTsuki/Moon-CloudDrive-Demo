package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PreviewInfoResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.TextPreviewResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.PreviewService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;

/**
 * 文件预览服务实现
 * <p>
 * 核心策略：根据文件扩展名分流到不同预览方式
 * <pre>
 * 图片  → OSS 图片处理 URL（最大宽高 + 水印）
 * 视频  → OSS 预签名 inline URL
 * 音频  → OSS 预签名 inline URL
 * PDF   → OSS 预签名 inline URL
 * 文本  → 从 OSS 读取内容返给前端高亮渲染
 * 其他  → 返回 unsupported
 * </pre>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PreviewServiceImpl implements PreviewService {

    // ==================== 文件类型常量集合 ====================

    private static final Set<String> IMAGE_TYPES = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg", "ico", "tiff", "tif"
    );
    private static final Set<String> VIDEO_TYPES = Set.of(
            "mp4", "mov", "avi", "mkv", "webm", "flv", "wmv", "m4v"
    );
    private static final Set<String> AUDIO_TYPES = Set.of(
            "mp3", "wav", "flac", "ogg", "aac", "wma", "m4a", "opus"
    );
    private static final String PDF_TYPE = "pdf";
    private static final Set<String> TEXT_TYPES = Set.of(
            "txt", "md", "json", "xml", "yaml", "yml", "properties", "ini", "cfg", "conf",
            "java", "py", "js", "ts", "jsx", "tsx", "html", "htm", "css", "scss", "less",
            "sql", "sh", "bash", "bat", "cmd", "ps1", "log",
            "c", "cpp", "h", "hpp", "cs", "go", "rs", "kt", "swift", "rb", "php", "lua"
    );

    /** 文本预览最大字节数限制：1MB */
    private static final long MAX_TEXT_PREVIEW_BYTES = 1 * 1024 * 1024;

    /** 流式代理传输缓冲区大小 */
    private static final int STREAM_BUFFER_SIZE = 8192;

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;

    @Value("${moon.preview.max-text-bytes:" + MAX_TEXT_PREVIEW_BYTES + "}")
    private long maxTextPreviewBytes;

    // ==================== 预览信息（主入口） ====================

    @Override
    public PreviewInfoResponse getPreviewInfo(Long fileId) {
        File file = getOwnFile(fileId);
        String ext = extractExtension(file.getOriginalFilename()).toLowerCase();

        if (IMAGE_TYPES.contains(ext)) {
            return buildImagePreview(file, ext);
        }
        if (VIDEO_TYPES.contains(ext)) {
            return buildMediaPreview(file, "video", ext);
        }
        if (AUDIO_TYPES.contains(ext)) {
            return buildMediaPreview(file, "audio", ext);
        }
        if (PDF_TYPE.equals(ext)) {
            return buildPdfPreviewInfo(file, ext);
        }
        if (TEXT_TYPES.contains(ext)) {
            return buildTextInfo(file, ext);
        }

        return buildUnsupported(file, ext);
    }

    // ==================== 文本内容 ====================

    @Override
    public TextPreviewResponse getTextContent(Long fileId) {
        File file = getOwnFile(fileId);
        String ext = extractExtension(file.getOriginalFilename()).toLowerCase();

        if (!TEXT_TYPES.contains(ext)) {
            throw new BusinessException("该文件类型不支持文本预览");
        }
        if (file.getFileSize() != null && file.getFileSize() > maxTextPreviewBytes) {
            throw new BusinessException(
                    "文本文件过大（" + (file.getFileSize() / 1024) + "KB），超过 "
                            + (maxTextPreviewBytes / 1024) + "KB 预览限制，请下载后查看");
        }

        String content = readTextFromOss(file.getStoredFilename());
        String language = mapLanguage(ext);

        return TextPreviewResponse.builder()
                .content(content)
                .language(language)
                .encoding("UTF-8")
                .build();
    }

    // ==================== 流式代理 ====================

    @Override
    public void previewStream(Long fileId, HttpServletResponse response) {
        File file = getOwnFile(fileId);

        try (InputStream is = ossUtil.getObject(file.getStoredFilename()).getObjectContent();
             OutputStream os = response.getOutputStream()) {

            String mimeType = file.getContentType() != null ? file.getContentType() : "application/octet-stream";
            response.setContentType(mimeType);
            response.setHeader("Content-Disposition", "inline; filename=\""
                    + URLEncoder.encode(file.getOriginalFilename(), StandardCharsets.UTF_8) + "\"");
            if (file.getFileSize() != null) {
                response.setContentLengthLong(file.getFileSize());
            }

            byte[] buffer = new byte[STREAM_BUFFER_SIZE];
            int len;
            while ((len = is.read(buffer)) != -1) {
                os.write(buffer, 0, len);
            }
            os.flush();
        } catch (IOException e) {
            log.error("预览流式传输失败: fileId={}", fileId, e);
            throw new BusinessException("文件流读取失败");
        }
    }

    // ==================== 私有方法 ====================

    /** 校验文件归属权并返回 File 实体 */
    private File getOwnFile(Long fileId) {
        Long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null || file.getIsFolder() != null && file.getIsFolder() == 1) {
            throw new BusinessException("文件不存在或无权操作");
        }
        return file;
    }

    /** 从文件名提取扩展名 */
    private String extractExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }
        return filename.substring(filename.lastIndexOf('.') + 1);
    }

    /** 构建图片预览信息（OSS 预签名 URL） */
    private PreviewInfoResponse buildImagePreview(File file, String ext) {
        return PreviewInfoResponse.builder()
                .previewType("image")
                .previewUrl(ossUtil.generatePresignedUrlForPreview(file.getStoredFilename()))
                .fileName(file.getOriginalFilename())
                .mimeType(mapMimeType("image", ext))
                .fileSize(file.getFileSize())
                .build();
    }

    /** 构建视频/音频/PDF 预览信息（OSS 预签名 URL） */
    private PreviewInfoResponse buildMediaPreview(File file, String previewType, String ext) {
        return PreviewInfoResponse.builder()
                .previewType(previewType)
                .previewUrl(ossUtil.generatePresignedUrlForPreview(file.getStoredFilename()))
                .fileName(file.getOriginalFilename())
                .mimeType(mapMimeType(previewType, ext))
                .fileSize(file.getFileSize())
                .build();
    }

    /** 构建文本预览信息（不返回 URL，前端另行调用 /preview/text 获取内容） */
    private PreviewInfoResponse buildTextInfo(File file, String ext) {
        return PreviewInfoResponse.builder()
                .previewType("text")
                .previewUrl(null)
                .fileName(file.getOriginalFilename())
                .mimeType("text/plain")
                .fileSize(file.getFileSize())
                .language(mapLanguage(ext))
                .build();
    }

    /** 构建不支持的预览类型 */
    private PreviewInfoResponse buildUnsupported(File file, String ext) {
        return PreviewInfoResponse.builder()
                .previewType("unsupported")
                .previewUrl(null)
                .fileName(file.getOriginalFilename())
                .mimeType(mapMimeType("unsupported", ext))
                .fileSize(file.getFileSize())
                .build();
    }

    /** 构建 PDF 预览信息（服务端转图片模式） */
    private PreviewInfoResponse buildPdfPreviewInfo(File file, String ext) {
        return PreviewInfoResponse.builder()
                .previewType("pdf_image")
                .previewUrl(null)
                .fileName(file.getOriginalFilename())
                .mimeType(mapMimeType("pdf", ext))
                .fileSize(file.getFileSize())
                .build();
    }

    /** 从 OSS 读取文本文件内容并转换为 UTF-8 字符串 */
    private String readTextFromOss(String storedFilename) {
        try (InputStream is = ossUtil.getObject(storedFilename).getObjectContent()) {
            byte[] bytes = StreamUtils.copyToByteArray(is);
            return new String(bytes, StandardCharsets.UTF_8);
        } catch (IOException e) {
            log.error("读取OSS文本文件失败: {}", storedFilename, e);
            throw new BusinessException("文件读取失败");
        }
    }

    /**
     * 扩展名 → 前端语言标识映射
     * <p>
     * 返回的 language 可直接传递给 highlight.js 的 class 属性
     * （如 class="language-java"）或 Monaco Editor 的 language 参数
     */
    private String mapLanguage(String ext) {
        return LANGUAGE_MAP.getOrDefault(ext, "plaintext");
    }

    private static final Map<String, String> LANGUAGE_MAP = Map.ofEntries(
            Map.entry("java", "java"),
            Map.entry("py", "python"),
            Map.entry("js", "javascript"),
            Map.entry("ts", "typescript"),
            Map.entry("jsx", "javascript"),
            Map.entry("tsx", "typescript"),
            Map.entry("html", "xml"),
            Map.entry("htm", "xml"),
            Map.entry("css", "css"),
            Map.entry("scss", "scss"),
            Map.entry("less", "less"),
            Map.entry("json", "json"),
            Map.entry("xml", "xml"),
            Map.entry("yaml", "yaml"),
            Map.entry("yml", "yaml"),
            Map.entry("md", "markdown"),
            Map.entry("sql", "sql"),
            Map.entry("sh", "bash"),
            Map.entry("bash", "bash"),
            Map.entry("bat", "dos"),
            Map.entry("cmd", "dos"),
            Map.entry("ps1", "powershell"),
            Map.entry("c", "c"),
            Map.entry("cpp", "cpp"),
            Map.entry("h", "c"),
            Map.entry("hpp", "cpp"),
            Map.entry("cs", "csharp"),
            Map.entry("go", "go"),
            Map.entry("rs", "rust"),
            Map.entry("kt", "kotlin"),
            Map.entry("swift", "swift"),
            Map.entry("rb", "ruby"),
            Map.entry("php", "php"),
            Map.entry("lua", "lua"),
            Map.entry("txt", "plaintext"),
            Map.entry("log", "plaintext"),
            Map.entry("ini", "ini"),
            Map.entry("cfg", "ini"),
            Map.entry("conf", "ini"),
            Map.entry("properties", "ini")
    );

    /** 类型 + 扩展名 → MIME 类型映射 */
    private String mapMimeType(String previewType, String ext) {
        return switch (previewType) {
            case "image" -> "image/" + (ext.equals("jpg") ? "jpeg" : ext.equals("svg") ? "svg+xml" : ext);
            case "video" -> "video/" + ext;
            case "audio" -> switch (ext) {
                case "mp3" -> "audio/mpeg";
                case "wav" -> "audio/wav";
                case "ogg", "opus" -> "audio/ogg";
                case "flac" -> "audio/flac";
                case "m4a" -> "audio/mp4";
                default -> "audio/" + ext;
            };
            case "pdf" -> "application/pdf";
            default -> "application/octet-stream";
        };
    }
}