package com.nutritheous.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Represents metadata extracted from a photo's EXIF data.
 * Used to provide context to AI analysis (location, time, device).
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PhotoMetadata {

    /**
     * Latitude coordinate from GPS data (WGS84).
     */
    private Double latitude;

    /**
     * Longitude coordinate from GPS data (WGS84).
     */
    private Double longitude;

    /**
     * Timestamp when the photo was captured (from EXIF DateTimeOriginal).
     */
    private LocalDateTime capturedAt;

    /**
     * Device make (e.g., "Apple").
     */
    private String deviceMake;

    /**
     * Device model (e.g., "iPhone 15 Pro").
     */
    private String deviceModel;

    /**
     * Check if GPS coordinates are available.
     */
    public boolean hasGPS() {
        return latitude != null && longitude != null;
    }

    /**
     * Check if any metadata was extracted.
     */
    public boolean hasAnyData() {
        return latitude != null || longitude != null || capturedAt != null ||
               deviceMake != null || deviceModel != null;
    }

    /**
     * Create an empty metadata instance.
     */
    public static PhotoMetadata empty() {
        return PhotoMetadata.builder().build();
    }
}
