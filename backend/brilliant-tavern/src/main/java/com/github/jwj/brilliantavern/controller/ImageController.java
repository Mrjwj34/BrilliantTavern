package com.github.jwj.brilliantavern.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.core.io.UrlResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.net.MalformedURLException;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 图片静态资源访问控制器
 */
@Slf4j
@RestController
@RequestMapping("/images")
@CrossOrigin(origins = "*")
public class ImageController {

    @Value("${app.uploads.images.dir:uploads/images}")
    private String imagesDir;

    /**
     * 获取生成的图片
     */
    @GetMapping("/{date}/{filename}")
    public ResponseEntity<Resource> getImage(@PathVariable String date, @PathVariable String filename) {
        try {
            // 确保使用绝对路径
            Path imagesBasePath = Paths.get(imagesDir).isAbsolute() 
                    ? Paths.get(imagesDir) 
                    : Paths.get(System.getProperty("user.dir"), imagesDir);
            Path filePath = imagesBasePath.resolve(date).resolve(filename);
            Resource resource = new UrlResource(filePath.toUri());
            
            log.debug("尝试访问图片: imagesDir={}, filePath={}, exists={}", 
                    imagesDir, filePath, resource.exists());
            
            if (resource.exists() && resource.isReadable()) {
                // 根据文件扩展名确定Content-Type
                String contentType = getContentType(filename);
                
                return ResponseEntity.ok()
                        .contentType(MediaType.parseMediaType(contentType))
                        .header(HttpHeaders.CACHE_CONTROL, "max-age=86400") // 缓存24小时
                        .body(resource);
            } else {
                log.warn("图片文件不存在或不可读: {}", filePath);
                return ResponseEntity.notFound().build();
            }
        } catch (MalformedURLException e) {
            log.error("获取图片失败: date={}, filename={}", date, filename, e);
            return ResponseEntity.badRequest().build();
        }
    }

    /**
     * 根据文件名获取Content-Type
     */
    private String getContentType(String filename) {
        String extension = filename.substring(filename.lastIndexOf('.') + 1).toLowerCase();
        return switch (extension) {
            case "jpg", "jpeg" -> "image/jpeg";
            case "png" -> "image/png";
            case "gif" -> "image/gif";
            case "webp" -> "image/webp";
            case "bmp" -> "image/bmp";
            default -> "image/jpeg"; // 默认JPEG
        };
    }
}