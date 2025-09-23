package com.github.jwj.brilliantavern.service;

import com.github.jwj.brilliantavern.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

/**
 * 文件上传服务
 */
@Slf4j
@Service
public class FileUploadService {

    @Value("${app.upload.max-file-size:10MB}")
    private String maxFileSize;

    @Value("${server.servlet.context-path:/api}")
    private String contextPath;

    @Value("${server.port:8080}")
    private String serverPort;

    private static final String UPLOAD_DIR = "uploads";
    private static final String AVATAR_DIR = "avatars";
    
    // 支持的图片格式
    private static final String[] ALLOWED_IMAGE_TYPES = {
        "image/jpeg", "image/jpg", "image/png", "image/gif", "image/webp"
    };
    
    // 最大文件大小 (5MB)
    private static final long MAX_FILE_SIZE = 5 * 1024 * 1024;

    /**
     * 上传角色头像
     */
    public String uploadAvatar(MultipartFile file, UUID userId) {
        // 验证文件
        validateImageFile(file);
        
        try {
            // 创建上传目录
            Path uploadPath = createUploadDirectory(AVATAR_DIR);
            
            // 生成唯一文件名
            String originalFileName = file.getOriginalFilename();
            String fileExtension = getFileExtension(originalFileName);
            String fileName = userId + "_" + UUID.randomUUID().toString() + fileExtension;
            
            // 保存文件
            Path filePath = uploadPath.resolve(fileName);
            Files.copy(file.getInputStream(), filePath, StandardCopyOption.REPLACE_EXISTING);
            
            // 构建访问URL
            String fileUrl = String.format("http://localhost:%s%s/uploads/%s/%s", 
                serverPort, contextPath, AVATAR_DIR, fileName);
            
            log.info("头像上传成功: 用户={}, 文件={}, URL={}", userId, fileName, fileUrl);
            return fileUrl;
            
        } catch (IOException e) {
            log.error("头像上传失败: 用户={}, 错误={}", userId, e.getMessage(), e);
            throw new BusinessException(500, "文件保存失败");
        }
    }

    /**
     * 验证图片文件
     */
    private void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(400, "请选择要上传的文件");
        }

        // 检查文件大小
        if (file.getSize() > MAX_FILE_SIZE) {
            throw new BusinessException(400, "文件大小不能超过5MB");
        }

        // 检查文件类型
        String contentType = file.getContentType();
        if (contentType == null || !isValidImageType(contentType)) {
            throw new BusinessException(400, "只支持JPG、PNG、GIF、WebP格式的图片文件");
        }

        // 检查文件名
        String originalFileName = file.getOriginalFilename();
        if (originalFileName == null || originalFileName.trim().isEmpty()) {
            throw new BusinessException(400, "文件名不能为空");
        }
    }

    /**
     * 检查是否为支持的图片格式
     */
    private boolean isValidImageType(String contentType) {
        for (String allowedType : ALLOWED_IMAGE_TYPES) {
            if (allowedType.equals(contentType)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 获取文件扩展名
     */
    private String getFileExtension(String fileName) {
        if (fileName == null || fileName.lastIndexOf('.') == -1) {
            return "";
        }
        return fileName.substring(fileName.lastIndexOf('.'));
    }

    /**
     * 创建上传目录
     */
    private Path createUploadDirectory(String subDir) throws IOException {
        Path uploadPath = Paths.get(UPLOAD_DIR, subDir);
        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }
        return uploadPath;
    }
}
