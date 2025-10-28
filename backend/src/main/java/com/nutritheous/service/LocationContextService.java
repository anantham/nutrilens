package com.nutritheous.service;

import com.google.maps.GeoApiContext;
import com.google.maps.GeocodingApi;
import com.google.maps.PlacesApi;
import com.google.maps.errors.ApiException;
import com.google.maps.model.*;
import com.nutritheous.common.dto.LocationContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.io.IOException;

/**
 * Service to get location context from GPS coordinates using Google Maps API.
 * Provides reverse geocoding and place identification.
 */
@Service
@Slf4j
public class LocationContextService {

    private final String apiKey;
    private GeoApiContext geoContext;

    public LocationContextService(@Value("${google.maps.api.key:}") String apiKey) {
        this.apiKey = apiKey;
    }

    @PostConstruct
    public void init() {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("Google Maps API key not configured - location context will be disabled");
            return;
        }

        this.geoContext = new GeoApiContext.Builder()
                .apiKey(apiKey)
                .build();

        log.info("Google Maps API context initialized");
    }

    @PreDestroy
    public void cleanup() {
        if (geoContext != null) {
            geoContext.shutdown();
        }
    }

    /**
     * Get location context from GPS coordinates.
     *
     * @param latitude  Latitude coordinate
     * @param longitude Longitude coordinate
     * @return LocationContext with place information
     */
    public LocationContext getLocationContext(Double latitude, Double longitude) {
        if (latitude == null || longitude == null) {
            log.debug("No GPS coordinates provided");
            return LocationContext.unknown();
        }

        if (geoContext == null) {
            log.warn("Google Maps API not configured, returning unknown location");
            return LocationContext.unknown();
        }

        try {
            LatLng coordinates = new LatLng(latitude, longitude);

            // First try to find nearby places (restaurants, cafes, etc.)
            PlacesSearchResponse placesResponse = PlacesApi.nearbySearchQuery(geoContext, coordinates)
                    .radius(50)  // 50 meters
                    .rankby(RankBy.DISTANCE)
                    .await();

            if (placesResponse.results.length > 0) {
                PlacesSearchResult place = placesResponse.results[0];
                return buildLocationContextFromPlace(place);
            }

            // If no nearby places, do reverse geocoding to determine if residential
            GeocodingResult[] geocodingResults = GeocodingApi.reverseGeocode(geoContext, coordinates).await();

            if (geocodingResults.length > 0) {
                return buildLocationContextFromGeocoding(geocodingResults[0]);
            }

            log.debug("No location information found for coordinates: {}, {}", latitude, longitude);
            return LocationContext.unknown();

        } catch (ApiException e) {
            log.error("Google Maps API error: {} - {}", e.getMessage(), e.getClass().getSimpleName());
            return LocationContext.unknown();
        } catch (InterruptedException e) {
            log.error("Google Maps API request interrupted", e);
            Thread.currentThread().interrupt();
            return LocationContext.unknown();
        } catch (IOException e) {
            log.error("Google Maps API IO error: {}", e.getMessage());
            return LocationContext.unknown();
        } catch (Exception e) {
            log.error("Unexpected error getting location context: {}", e.getMessage(), e);
            return LocationContext.unknown();
        }
    }

    /**
     * Build location context from a Google Places result.
     */
    private LocationContext buildLocationContextFromPlace(PlacesSearchResult place) {
        log.info("Found place: {} ({})", place.name, place.types != null && place.types.length > 0 ? place.types[0] : "unknown");

        LocationContext.LocationContextBuilder builder = LocationContext.builder()
                .placeName(place.name)
                .address(place.vicinity)
                .isKnown(true);

        // Determine place type
        if (place.types != null && place.types.length > 0) {
            String primaryType = extractPlaceType(place.types);
            builder.placeType(primaryType);

            // Check if restaurant/cafe
            boolean isRestaurant = isRestaurantType(place.types);
            builder.isRestaurant(isRestaurant);

            if (isRestaurant) {
                String cuisineType = extractCuisineType(place.types);
                builder.cuisineType(cuisineType);
            }
        }

        // Price level (1-4 scale)
        // Note: priceLevel field may not be available in all Google Maps API versions
        // TODO: Check PlacesSearchResult API for priceLevel access
        // if (place.priceLevel != null) {
        //     builder.priceLevel(place.priceLevel.ordinal() + 1);  // Convert enum to 1-4
        // }

        return builder.build();
    }

    /**
     * Build location context from reverse geocoding result.
     */
    private LocationContext buildLocationContextFromGeocoding(GeocodingResult result) {
        log.info("Reverse geocoded to: {}", result.formattedAddress);

        // Check if residential area (likely home)
        boolean isResidential = false;
        if (result.types != null) {
            for (AddressType type : result.types) {
                if (type == AddressType.STREET_ADDRESS ||
                    type == AddressType.PREMISE ||
                    type == AddressType.SUBPREMISE) {
                    isResidential = true;
                    break;
                }
            }
        }

        if (isResidential) {
            return LocationContext.home(result.formattedAddress);
        }

        // Generic location
        return LocationContext.builder()
                .placeType("other")
                .address(result.formattedAddress)
                .isKnown(true)
                .build();
    }

    /**
     * Extract primary place type from Google Places types.
     */
    private String extractPlaceType(String[] types) {
        for (String type : types) {
            switch (type) {
                case "restaurant":
                    return "restaurant";
                case "cafe":
                    return "cafe";
                case "bar":
                    return "bar";
                case "bakery":
                    return "bakery";
                case "grocery_or_supermarket":
                case "supermarket":
                    return "grocery_store";
                case "gym":
                    return "gym";
                case "convenience_store":
                    return "convenience_store";
                case "meal_takeaway":
                    return "takeaway";
                case "meal_delivery":
                    return "delivery";
            }
        }
        return "other";
    }

    /**
     * Check if place is a restaurant/food establishment.
     */
    private boolean isRestaurantType(String[] types) {
        for (String type : types) {
            if (type.equals("restaurant") ||
                type.equals("cafe") ||
                type.equals("bar") ||
                type.equals("bakery") ||
                type.equals("meal_takeaway") ||
                type.equals("food")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract cuisine type from Google Places types.
     */
    private String extractCuisineType(String[] types) {
        for (String type : types) {
            // Google Maps doesn't directly provide cuisine types in the type field
            // This is a simplified approach - real implementation might use
            // place details API for more accurate cuisine information
            if (type.contains("chinese")) return "chinese";
            if (type.contains("italian")) return "italian";
            if (type.contains("mexican")) return "mexican";
            if (type.contains("indian")) return "indian";
            if (type.contains("japanese")) return "japanese";
            if (type.contains("thai")) return "thai";
            if (type.contains("american")) return "american";
            if (type.contains("fast_food")) return "fast_food";
        }
        return null;  // Unknown cuisine
    }

    /**
     * Check if API is configured and ready.
     */
    public boolean isConfigured() {
        return geoContext != null;
    }
}
