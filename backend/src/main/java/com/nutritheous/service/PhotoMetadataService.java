package com.nutritheous.service;

import com.drew.imaging.ImageMetadataReader;
import com.drew.imaging.ImageProcessingException;
import com.drew.lang.GeoLocation;
import com.drew.metadata.Metadata;
import com.drew.metadata.exif.ExifIFD0Directory;
import com.drew.metadata.exif.ExifSubIFDDirectory;
import com.drew.metadata.exif.GpsDirectory;
import com.nutritheous.common.dto.PhotoMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;

/**
 * Service to extract metadata from photo EXIF data.
 * Extracts GPS coordinates, timestamps, and device information.
 */
@Service
@Slf4j
public class PhotoMetadataService {

    /**
     * Extract metadata from uploaded photo.
     *
     * @param image Uploaded photo file
     * @return PhotoMetadata containing EXIF data
     */
    public PhotoMetadata extractMetadata(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            log.debug("No image provided, returning empty metadata");
            return PhotoMetadata.empty();
        }

        try {
            // Read metadata from image
            Metadata metadata = ImageMetadataReader.readMetadata(image.getInputStream());

            PhotoMetadata.PhotoMetadataBuilder builder = PhotoMetadata.builder();

            // Extract GPS coordinates
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);
            if (gpsDirectory != null) {
                GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                if (geoLocation != null && !geoLocation.isZero()) {
                    builder.latitude(geoLocation.getLatitude());
                    builder.longitude(geoLocation.getLongitude());
                    log.info("Extracted GPS coordinates: lat={}, lng={}",
                            geoLocation.getLatitude(), geoLocation.getLongitude());
                }
            }

            // Extract timestamp from EXIF
            ExifSubIFDDirectory exifSubIFD = metadata.getFirstDirectoryOfType(ExifSubIFDDirectory.class);
            if (exifSubIFD != null) {
                Date captureDate = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME_ORIGINAL);
                if (captureDate == null) {
                    captureDate = exifSubIFD.getDate(ExifSubIFDDirectory.TAG_DATETIME);
                }
                if (captureDate != null) {
                    LocalDateTime capturedAt = Instant.ofEpochMilli(captureDate.getTime())
                            .atZone(ZoneId.systemDefault())
                            .toLocalDateTime();
                    builder.capturedAt(capturedAt);
                    log.info("Extracted capture timestamp: {}", capturedAt);
                }
            }

            // Extract device information
            ExifIFD0Directory exifIFD0 = metadata.getFirstDirectoryOfType(ExifIFD0Directory.class);
            if (exifIFD0 != null) {
                String make = exifIFD0.getString(ExifIFD0Directory.TAG_MAKE);
                String model = exifIFD0.getString(ExifIFD0Directory.TAG_MODEL);

                if (make != null) {
                    builder.deviceMake(make.trim());
                }
                if (model != null) {
                    builder.deviceModel(model.trim());
                }

                if (make != null || model != null) {
                    log.info("Extracted device info: make={}, model={}", make, model);
                }
            }

            PhotoMetadata photoMetadata = builder.build();

            if (!photoMetadata.hasAnyData()) {
                log.warn("No EXIF metadata found in image (may be stripped or not supported)");
            }

            return photoMetadata;

        } catch (ImageProcessingException e) {
            log.error("Failed to process image metadata (unsupported format or corrupted): {}", e.getMessage());
            return PhotoMetadata.empty();
        } catch (IOException e) {
            log.error("Failed to read image stream: {}", e.getMessage(), e);
            return PhotoMetadata.empty();
        } catch (Exception e) {
            log.error("Unexpected error extracting metadata: {}", e.getMessage(), e);
            return PhotoMetadata.empty();
        }
    }

    /**
     * Check if image has GPS data without full extraction.
     *
     * @param image Uploaded photo file
     * @return true if GPS data exists
     */
    public boolean hasGPSData(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return false;
        }

        try {
            Metadata metadata = ImageMetadataReader.readMetadata(image.getInputStream());
            GpsDirectory gpsDirectory = metadata.getFirstDirectoryOfType(GpsDirectory.class);

            if (gpsDirectory != null) {
                GeoLocation geoLocation = gpsDirectory.getGeoLocation();
                return geoLocation != null && !geoLocation.isZero();
            }

            return false;
        } catch (Exception e) {
            log.debug("Failed to check GPS data: {}", e.getMessage());
            return false;
        }
    }
}
