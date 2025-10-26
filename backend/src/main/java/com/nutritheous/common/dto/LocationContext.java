package com.nutritheous.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Represents location context derived from GPS coordinates via Google Maps API.
 * Provides semantic understanding of where a meal was consumed.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LocationContext {

    /**
     * Name of the place (e.g., "Chipotle Mexican Grill", "Home").
     */
    private String placeName;

    /**
     * Type of place (e.g., "restaurant", "cafe", "home", "gym", "office").
     */
    private String placeType;

    /**
     * Cuisine type if restaurant (e.g., "mexican", "italian", "fast_food").
     */
    private String cuisineType;

    /**
     * Price level from Google Maps (1-4, where $ to $$$$).
     */
    private Integer priceLevel;

    /**
     * Whether this is a restaurant/cafe.
     */
    @Builder.Default
    private boolean isRestaurant = false;

    /**
     * Whether this is a residential location (likely home).
     */
    @Builder.Default
    private boolean isHome = false;

    /**
     * Full formatted address from Google Maps.
     */
    private String address;

    /**
     * Whether location data is known/available.
     */
    @Builder.Default
    private boolean isKnown = false;

    /**
     * Create an unknown location context.
     */
    public static LocationContext unknown() {
        return LocationContext.builder()
                .isKnown(false)
                .build();
    }

    /**
     * Create a home location context.
     */
    public static LocationContext home(String address) {
        return LocationContext.builder()
                .placeName("Home")
                .placeType("home")
                .isHome(true)
                .isKnown(true)
                .address(address)
                .build();
    }

    /**
     * Create a restaurant location context.
     */
    public static LocationContext restaurant(String name, String cuisineType, Integer priceLevel, String address) {
        return LocationContext.builder()
                .placeName(name)
                .placeType("restaurant")
                .cuisineType(cuisineType)
                .priceLevel(priceLevel)
                .isRestaurant(true)
                .isKnown(true)
                .address(address)
                .build();
    }
}
