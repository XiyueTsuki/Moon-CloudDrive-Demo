package com.xiyuetsuki.moonclouddrivedemo.service.impl;

import cn.dev33.satoken.stp.StpUtil;
import com.xiyuetsuki.moonclouddrivedemo.config.PdfPreviewConfig;
import com.xiyuetsuki.moonclouddrivedemo.domain.dto.PdfPreviewResponse;
import com.xiyuetsuki.moonclouddrivedemo.domain.entity.File;
import com.xiyuetsuki.moonclouddrivedemo.exception.BusinessException;
import com.xiyuetsuki.moonclouddrivedemo.mapper.FileMapper;
import com.xiyuetsuki.moonclouddrivedemo.service.PdfConvertService;
import com.xiyuetsuki.moonclouddrivedemo.util.OssUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * PDF 转图片服务实现
 * <p>
 * 核心流程：
 * <ol>
 *   <li>从 OSS 下载 PDF 文件到内存</li>
 *   <li>使用 PDFBox 逐页渲染为 BufferedImage</li>
 *   <li>将每页 PNG 上传到 OSS 的 pdf-preview/{fileId}/page_{n}.png</li>
 *   <li>全部上传完成后写入 .page_count 元文件（标记转换完成 + 记录总页数）</li>
 *   <li>生成预签名 URL 列表返回前端</li>
 * </ol>
 * <p>
 * 防重与一致性：
 * <ul>
 *   <li>使用 ConcurrentHashMap.putIfAbsent 原子操作避免并发触发重复转换</li>
 *   <li>以 OSS 中的 .page_count 元文件作为转换完成的权威标记，服务重启后仍可复用</li>
 *   <li>返回 ready 前校验第 1 页真实存在，避免脏数据导致 NoSuchKey</li>
 * </ul>
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PdfConvertServiceImpl implements PdfConvertService {

    private static final String PDF_PREVIEW_PREFIX = "pdf-preview/";
    private static final String META_FILENAME = ".page_count";

    private final Map<Long, String> convertStatusCache = new ConcurrentHashMap<>();

    private final Map<Long, Integer> pageCountCache = new ConcurrentHashMap<>();

    private final OssUtil ossUtil;
    private final FileMapper fileMapper;
    private final PdfPreviewConfig config;

    @Override
    public PdfPreviewResponse getPdfPreview(Long fileId) {
        getOwnPdfFile(fileId);

        String cachedStatus = convertStatusCache.get(fileId);
        if ("ready".equals(cachedStatus)) {
            Integer cachedPages = pageCountCache.get(fileId);
            if (cachedPages != null && verifyPageExists(fileId, 1)) {
                return buildReadyResponse(fileId, cachedPages);
            }
            log.warn("缓存标记为 ready 但 OSS 图片缺失, 重新转换: fileId={}", fileId);
            convertStatusCache.remove(fileId);
            pageCountCache.remove(fileId);
        }
        if ("converting".equals(cachedStatus)) {
            return convertingResponse();
        }
        if ("failed".equals(cachedStatus)) {
            return failedResponse();
        }

        String prev = convertStatusCache.putIfAbsent(fileId, "converting");
        if (prev != null) {
            if ("ready".equals(prev)) {
                Integer cachedPages = pageCountCache.get(fileId);
                if (cachedPages != null && verifyPageExists(fileId, 1)) {
                    return buildReadyResponse(fileId, cachedPages);
                }
                convertStatusCache.remove(fileId);
                pageCountCache.remove(fileId);
            } else if ("converting".equals(prev)) {
                return convertingResponse();
            } else if ("failed".equals(prev)) {
                return failedResponse();
            }
        }

        int existingPages = readPageCountFromOss(fileId);
        if (existingPages > 0 && verifyPageExists(fileId, 1)) {
            pageCountCache.put(fileId, existingPages);
            convertStatusCache.put(fileId, "ready");
            return buildReadyResponse(fileId, existingPages);
        }

        if (existingPages > 0) {
            log.warn("OSS 元文件存在但第 1 页图片缺失, 重新转换: fileId={}", fileId);
            pageCountCache.remove(fileId);
        }

        File file = getOwnPdfFile(fileId);
        String finalStatus = convertStatusCache.getOrDefault(fileId, "converting");
        if (!"converting".equals(finalStatus)) {
            convertStatusCache.put(fileId, "converting");
        }
        CompletableFuture.runAsync(() -> doConvert(fileId, file));

        return convertingResponse();
    }

    @Override
    public byte[] getPageImage(Long fileId, int pageNum) {
        String ossKey = buildPageOssKey(fileId, pageNum);
        try (InputStream is = ossUtil.getObject(ossKey).getObjectContent();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int len;
            while ((len = is.read(buffer)) != -1) {
                baos.write(buffer, 0, len);
            }
            return baos.toByteArray();
        } catch (Exception e) {
            log.error("读取 PDF 页面图片失败: fileId={}, pageNum={}", fileId, pageNum, e);
            throw new BusinessException("页面图片加载失败");
        }
    }

    @Override
    public void clearConvertCache(Long fileId) {
        convertStatusCache.remove(fileId);
        pageCountCache.remove(fileId);
        log.info("PDF 转换缓存已清理: fileId={}", fileId);
    }

    private void doConvert(Long fileId, File file) {
        try {
            log.info("开始异步转换 PDF: fileId={}, filename={}", fileId, file.getOriginalFilename());

            byte[] pdfBytes;
            try (InputStream is = ossUtil.getObject(file.getStoredFilename()).getObjectContent();
                 ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int len;
                while ((len = is.read(buffer)) != -1) {
                    baos.write(buffer, 0, len);
                }
                pdfBytes = baos.toByteArray();
            }

            try (PDDocument document = Loader.loadPDF(pdfBytes)) {
                PDFRenderer renderer = new PDFRenderer(document);
                int totalPages = document.getNumberOfPages();
                pageCountCache.put(fileId, totalPages);

                for (int i = 0; i < totalPages; i++) {
                    BufferedImage image = renderer.renderImageWithDPI(i, config.getDpi(), ImageType.RGB);
                    BufferedImage scaled = scaleImageIfNeeded(image);

                    ByteArrayOutputStream imageBaos = new ByteArrayOutputStream();
                    ImageIO.write(scaled, config.getImageFormat().toUpperCase(), imageBaos);
                    byte[] imageBytes = imageBaos.toByteArray();

                    String ossKey = buildPageOssKey(fileId, i + 1);
                    ossUtil.uploadWithKey(new ByteArrayInputStream(imageBytes), ossKey,
                            "image/" + config.getImageFormat());

                    log.debug("PDF 页面转换完成: fileId={}, page={}/{}", fileId, i + 1, totalPages);
                }

                writePageCountToOss(fileId, totalPages);
            }

            convertStatusCache.put(fileId, "ready");
            log.info("PDF 转换完成: fileId={}, totalPages={}", fileId, pageCountCache.get(fileId));

        } catch (Exception e) {
            log.error("PDF 转换失败: fileId={}", fileId, e);
            convertStatusCache.put(fileId, "failed");
        }
    }

    private PdfPreviewResponse buildReadyResponse(Long fileId, int totalPages) {
        List<String> urls = new ArrayList<>();
        for (int i = 1; i <= totalPages; i++) {
            String ossKey = buildPageOssKey(fileId, i);
            urls.add(ossUtil.generatePresignedUrlForPreview(ossKey, 30, TimeUnit.MINUTES));
        }
        return PdfPreviewResponse.builder()
                .status("ready")
                .totalPages(totalPages)
                .pageUrls(urls)
                .build();
    }

    private String buildPageOssKey(Long fileId, int pageNum) {
        return PDF_PREVIEW_PREFIX + fileId + "/page_" + pageNum + "." + config.getImageFormat();
    }

    private String buildMetaOssKey(Long fileId) {
        return PDF_PREVIEW_PREFIX + fileId + "/" + META_FILENAME;
    }

    /**
     * 验证指定页的图片在 OSS 中真实存在。
     */
    private boolean verifyPageExists(Long fileId, int pageNum) {
        try {
            String key = buildPageOssKey(fileId, pageNum);
            ossUtil.getObject(key).getObjectContent().close();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从 OSS 读取已转换完成的页数。
     * 以 .page_count 元文件为权威标记，文件不存在或读取失败返回 0。
     */
    private int readPageCountFromOss(Long fileId) {
        try {
            String metaKey = buildMetaOssKey(fileId);
            try (InputStream is = ossUtil.getObject(metaKey).getObjectContent()) {
                byte[] bytes = is.readAllBytes();
                int count = Integer.parseInt(new String(bytes, StandardCharsets.UTF_8).trim());
                if (count > 0) {
                    log.debug("OSS 已有转换结果: fileId={}, totalPages={}", fileId, count);
                    return count;
                }
            }
        } catch (Exception e) {
            log.debug("OSS 中无转换元文件: fileId={}", fileId);
        }
        return 0;
    }

    /**
     * 所有页面上传完成后，将总页数写入 OSS 元文件作为转换完成的标记。
     */
    private void writePageCountToOss(Long fileId, int totalPages) {
        try {
            String metaKey = buildMetaOssKey(fileId);
            byte[] metaBytes = String.valueOf(totalPages).getBytes(StandardCharsets.UTF_8);
            ossUtil.uploadWithKey(new ByteArrayInputStream(metaBytes), metaKey, "text/plain");
            log.debug("已写入 OSS 元文件: fileId={}, totalPages={}", fileId, totalPages);
        } catch (Exception e) {
            log.error("写入 OSS 元文件失败: fileId={}", fileId, e);
        }
    }

    /**
     * 等比缩放图片，确保宽度不超过 maxWidth
     */
    private BufferedImage scaleImageIfNeeded(BufferedImage original) {
        int width = original.getWidth();
        int height = original.getHeight();
        int maxWidth = config.getMaxWidth();

        if (width <= maxWidth) {
            return original;
        }

        double ratio = (double) maxWidth / width;
        int newWidth = maxWidth;
        int newHeight = (int) (height * ratio);

        BufferedImage scaled = new BufferedImage(newWidth, newHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = scaled.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return scaled;
    }

    private File getOwnPdfFile(Long fileId) {
        Long userId = StpUtil.getLoginIdAsLong();
        File file = fileMapper.selectByUserIdAndId(userId, fileId);
        if (file == null) {
            throw new BusinessException("文件不存在或无权访问");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            throw new BusinessException("仅支持 PDF 文件预览");
        }
        return file;
    }

    private static PdfPreviewResponse convertingResponse() {
        return PdfPreviewResponse.builder()
                .status("converting")
                .totalPages(0)
                .pageUrls(List.of())
                .build();
    }

    private static PdfPreviewResponse failedResponse() {
        return PdfPreviewResponse.builder()
                .status("failed")
                .errorMessage("PDF 转换失败，请稍后重试")
                .build();
    }
}