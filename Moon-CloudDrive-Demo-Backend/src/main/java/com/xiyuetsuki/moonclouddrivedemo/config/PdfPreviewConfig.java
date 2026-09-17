package com.xiyuetsuki.moonclouddrivedemo.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "moon.preview.pdf")
public class PdfPreviewConfig {

    /** 渲染 DPI，默认 150（平衡清晰度和文件大小） */
    private int dpi = 150;

    /** 单页图片最大宽度（像素），超出等比缩放 */
    private int maxWidth = 1920;

    /** 图片格式：png / jpg */
    private String imageFormat = "png";

    /** 转换超时时间（秒） */
    private int convertTimeout = 30;

    /** 中文字体目录 */
    private String fontDir = "classpath:fonts/";
}