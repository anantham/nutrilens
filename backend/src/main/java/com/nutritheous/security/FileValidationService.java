package com.nutritheous.security;

import com.nutritheous.common.exception.FileStorageException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Service for validating uploaded files to prevent security vulnerabilities.
 * Validates file size, type, content, and prevents malicious uploads.
 */
@Service
@Slf4j
public class FileValidationService {

    // Maximum file size: 10 MB
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;

    // Minimum file size: 1 KB (prevents empty/corrupted files)
    private static final long MIN_FILE_SIZE = 1024;

    // Allowed MIME types for images
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/jpg",
            "image/png",
            "image/heic",
            "image/heif",
            "image/webp"
    );

    // Allowed file extensions
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            ".jpg",
            ".jpeg",
            ".png",
            ".heic",
            ".heif",
            ".webp"
    );

    // Magic bytes for common image formats (for content-based validation)
    private static final List<byte[]> IMAGE_SIGNATURES = Arrays.asList(
            new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF}, // JPEG
            new byte[]{(byte) 0x89, 0x50, 0x4E, 0x47}, // PNG
            new byte[]{0x52, 0x49, 0x46, 0x46}, // WEBP (RIFF)
            new byte[]{0x00, 0x00, 0x00, 0x18, 0x66, 0x74, 0x79, 0x70}, // HEIC/HEIF
            new byte[]{0x00, 0x00, 0x00, 0x1C, 0x66, 0x74, 0x79, 0x70} // HEIC/HEIF (alternative)
    );

    /**
     * Validates an uploaded image file comprehensively.
     *
     * @param file The uploaded file to validate
     * @throws FileStorageException if validation fails
     */
    public void validateImageFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new FileStorageException("File is required and cannot be empty");
        }

        validateFileSize(file);
        validateFileExtension(file);
        validateMimeType(file);
        validateFileContent(file);

        log.debug("File validation successful: {} ({} bytes, {})",
                file.getOriginalFilename(), file.getSize(), file.getContentType());
    }

    /**
     * Validates file size is within acceptable limits.
     */
    private void validateFileSize(MultipartFile file) {
        long size = file.getSize();

        if (size < MIN_FILE_SIZE) {
            throw new FileStorageException(
                    String.format("File is too small (%d bytes). Minimum size is %d bytes.",
                            size, MIN_FILE_SIZE)
            );
        }

        if (size > MAX_FILE_SIZE) {
            throw new FileStorageException(
                    String.format("File is too large (%d bytes). Maximum size is %d bytes (%.1f MB).",
                            size, MAX_FILE_SIZE, MAX_FILE_SIZE / (1024.0 * 1024.0))
            );
        }
    }

    /**
     * Validates file extension is in the allowed list.
     */
    private void validateFileExtension(MultipartFile file) {
        String filename = file.getOriginalFilename();

        if (filename == null || filename.isBlank()) {
            throw new FileStorageException("File must have a valid filename");
        }

        // Prevent path traversal attacks
        if (filename.contains("..") || filename.contains("/") || filename.contains("\\")) {
            throw new FileStorageException("Invalid filename: contains path traversal characters");
        }

        String extension = getFileExtension(filename).toLowerCase();

        if (!ALLOWED_EXTENSIONS.contains(extension)) {
            throw new FileStorageException(
                    String.format("File extension '%s' is not allowed. Allowed extensions: %s",
                            extension, ALLOWED_EXTENSIONS)
            );
        }
    }

    /**
     * Validates MIME type matches allowed image types.
     */
    private void validateMimeType(MultipartFile file) {
        String mimeType = file.getContentType();

        if (mimeType == null || mimeType.isBlank()) {
            throw new FileStorageException("File must have a valid MIME type");
        }

        if (!ALLOWED_MIME_TYPES.contains(mimeType.toLowerCase())) {
            throw new FileStorageException(
                    String.format("MIME type '%s' is not allowed. Allowed types: %s",
                            mimeType, ALLOWED_MIME_TYPES)
            );
        }
    }

    /**
     * Validates file content by checking magic bytes (file signature).
     * This prevents attackers from uploading malicious files with spoofed extensions.
     */
    private void validateFileContent(MultipartFile file) {
        try {
            byte[] fileBytes = file.getBytes();

            if (fileBytes.length < 8) {
                throw new FileStorageException("File content is too small to be a valid image");
            }

            boolean isValidImage = IMAGE_SIGNATURES.stream()
                    .anyMatch(signature -> matchesSignature(fileBytes, signature));

            if (!isValidImage) {
                throw new FileStorageException(
                        "File content does not match any known image format. " +
                        "This may indicate a corrupted or malicious file."
                );
            }

        } catch (IOException e) {
            log.error("Failed to read file content for validation: {}", e.getMessage());
            throw new FileStorageException("Failed to validate file content", e);
        }
    }

    /**
     * Checks if file bytes start with the given signature.
     */
    private boolean matchesSignature(byte[] fileBytes, byte[] signature) {
        if (fileBytes.length < signature.length) {
            return false;
        }

        for (int i = 0; i < signature.length; i++) {
            if (fileBytes[i] != signature[i]) {
                return false;
            }
        }

        return true;
    }

    /**
     * Extracts file extension from filename.
     */
    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');

        if (lastDotIndex == -1 || lastDotIndex == filename.length() - 1) {
            return "";
        }

        return filename.substring(lastDotIndex);
    }

    /**
     * Sanitizes filename by removing potentially dangerous characters.
     */
    public String sanitizeFilename(String filename) {
        if (filename == null) {
            return "unnamed";
        }

        // Remove path separators and special characters
        return filename.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
