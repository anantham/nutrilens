package com.nutritheous.service;

import com.nutritheous.common.dto.LocationContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for LocationContextService.
 * Tests Google Maps API integration, place identification, and error handling.
 *
 * NOTE: These tests are designed to run without real API calls.
 * For full integration testing, use Google Maps API test credentials.
 */
class LocationContextServiceTest {

    private LocationContextService locationContextService;

    @BeforeEach
    void setUp() {
        // Initialize with no API key (tests API key validation)
        locationContextService = new LocationContextService("");
    }

    // ============================================================================
    // Test 1: API Key Configuration
    // ============================================================================

    @Test
    void testInit_WithoutAPIKey_DisablesService() {
        LocationContextService service = new LocationContextService("");
        service.init();

        assertFalse(service.isConfigured(), "Service should be disabled without API key");
    }

    @Test
    void testInit_WithNullAPIKey_DisablesService() {
        LocationContextService service = new LocationContextService(null);
        service.init();

        assertFalse(service.isConfigured(), "Service should be disabled with null API key");
    }

    @Test
    void testInit_WithBlankAPIKey_DisablesService() {
        LocationContextService service = new LocationContextService("   ");
        service.init();

        assertFalse(service.isConfigured(), "Service should be disabled with blank API key");
    }

    @Test
    void testInit_WithValidAPIKey_EnablesService() {
        LocationContextService service = new LocationContextService("test-api-key-123");
        service.init();

        assertTrue(service.isConfigured(), "Service should be enabled with valid API key");

        // Cleanup
        service.cleanup();
    }

    @Test
    void testCleanup_WithNullContext_DoesNotThrow() {
        LocationContextService service = new LocationContextService("");
        service.init(); // geoContext remains null

        // Should not throw exception
        assertDoesNotThrow(service::cleanup, "Cleanup should handle null context gracefully");
    }

    // ============================================================================
    // Test 2: Null and Invalid Input Handling
    // ============================================================================

    @Test
    void testGetLocationContext_NullLatitude_ReturnsUnknown() {
        locationContextService.init();

        LocationContext result = locationContextService.getLocationContext(null, -122.4194);

        assertNotNull(result);
        assertFalse(result.isKnown(), "Should return unknown location for null latitude");
    }

    @Test
    void testGetLocationContext_NullLongitude_ReturnsUnknown() {
        locationContextService.init();

        LocationContext result = locationContextService.getLocationContext(37.7749, null);

        assertNotNull(result);
        assertFalse(result.isKnown());
    }

    @Test
    void testGetLocationContext_BothNull_ReturnsUnknown() {
        locationContextService.init();

        LocationContext result = locationContextService.getLocationContext(null, null);

        assertNotNull(result);
        assertFalse(result.isKnown());
    }

    @Test
    void testGetLocationContext_WithoutAPIKey_ReturnsUnknown() {
        // Service without API key
        LocationContextService service = new LocationContextService("");
        service.init();

        LocationContext result = service.getLocationContext(37.7749, -122.4194);

        assertNotNull(result);
        assertFalse(result.isKnown(), "Should return unknown when API not configured");
    }

    // ============================================================================
    // Test 3: Place Type Extraction
    // ============================================================================

    @Test
    void testExtractPlaceType_Restaurant_ReturnsRestaurant() throws Exception {
        String[] types = {"restaurant", "food", "point_of_interest"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("restaurant", result);
    }

    @Test
    void testExtractPlaceType_Cafe_ReturnsCafe() throws Exception {
        String[] types = {"cafe", "food", "establishment"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("cafe", result);
    }

    @Test
    void testExtractPlaceType_GroceryStore_ReturnsGroceryStore() throws Exception {
        String[] types = {"grocery_or_supermarket", "store"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("grocery_store", result);
    }

    @Test
    void testExtractPlaceType_Supermarket_ReturnsGroceryStore() throws Exception {
        String[] types = {"supermarket", "store"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("grocery_store", result);
    }

    @Test
    void testExtractPlaceType_Gym_ReturnsGym() throws Exception {
        String[] types = {"gym", "health"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("gym", result);
    }

    @Test
    void testExtractPlaceType_Unknown_ReturnsOther() throws Exception {
        String[] types = {"hospital", "medical"};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("other", result);
    }

    @Test
    void testExtractPlaceType_EmptyArray_ReturnsOther() throws Exception {
        String[] types = {};

        String result = invokePrivateMethod("extractPlaceType", String[].class, types);

        assertEquals("other", result);
    }

    // ============================================================================
    // Test 4: Restaurant Type Detection
    // ============================================================================

    @Test
    void testIsRestaurantType_Restaurant_ReturnsTrue() throws Exception {
        String[] types = {"restaurant", "point_of_interest"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect restaurant type");
    }

    @Test
    void testIsRestaurantType_Cafe_ReturnsTrue() throws Exception {
        String[] types = {"cafe", "establishment"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect cafe as restaurant type");
    }

    @Test
    void testIsRestaurantType_Bar_ReturnsTrue() throws Exception {
        String[] types = {"bar", "night_club"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect bar as restaurant type");
    }

    @Test
    void testIsRestaurantType_Bakery_ReturnsTrue() throws Exception {
        String[] types = {"bakery", "store"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect bakery as restaurant type");
    }

    @Test
    void testIsRestaurantType_MealTakeaway_ReturnsTrue() throws Exception {
        String[] types = {"meal_takeaway", "restaurant"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect takeaway as restaurant type");
    }

    @Test
    void testIsRestaurantType_Food_ReturnsTrue() throws Exception {
        String[] types = {"food", "store"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertTrue(result, "Should detect food as restaurant type");
    }

    @Test
    void testIsRestaurantType_NonRestaurant_ReturnsFalse() throws Exception {
        String[] types = {"hospital", "medical", "health"};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertFalse(result, "Should not detect hospital as restaurant");
    }

    @Test
    void testIsRestaurantType_EmptyArray_ReturnsFalse() throws Exception {
        String[] types = {};

        Boolean result = invokePrivateMethod("isRestaurantType", String[].class, types);

        assertFalse(result, "Should return false for empty types");
    }

    // ============================================================================
    // Test 5: Cuisine Type Extraction
    // ============================================================================

    @Test
    void testExtractCuisineType_Chinese_ReturnsChinese() throws Exception {
        String[] types = {"restaurant", "chinese_restaurant", "food"};

        String result = invokePrivateMethod("extractCuisineType", String[].class, types);

        assertEquals("chinese", result);
    }

    @Test
    void testExtractCuisineType_Italian_ReturnsItalian() throws Exception {
        String[] types = {"restaurant", "italian_restaurant"};

        String result = invokePrivateMethod("extractCuisineType", String[].class, types);

        assertEquals("italian", result);
    }

    @Test
    void testExtractCuisineType_Mexican_ReturnsMexican() throws Exception {
        String[] types = {"restaurant", "mexican_restaurant"};

        String result = invokePrivateMethod("extractCuisineType", String[].class, types);

        assertEquals("mexican", result);
    }

    @Test
    void testExtractCuisineType_FastFood_ReturnsFastFood() throws Exception {
        String[] types = {"restaurant", "fast_food"};

        String result = invokePrivateMethod("extractCuisineType", String[].class, types);

        assertEquals("fast_food", result);
    }

    @Test
    void testExtractCuisineType_Unknown_ReturnsNull() throws Exception {
        String[] types = {"restaurant", "establishment"};

        String result = invokePrivateMethod("extractCuisineType", String[].class, types);

        assertNull(result, "Should return null for unknown cuisine");
    }

    // ============================================================================
    // Test 6: Edge Cases and Error Scenarios
    // ============================================================================

    @Test
    void testGetLocationContext_InvalidCoordinates_HandlesGracefully() {
        LocationContextService service = new LocationContextService("test-key");
        service.init();

        // Test with invalid latitude (out of range)
        LocationContext result = service.getLocationContext(91.0, -122.4194);

        assertNotNull(result, "Should handle invalid coordinates gracefully");

        service.cleanup();
    }

    @Test
    void testIsConfigured_BeforeInit_ReturnsFalse() {
        LocationContextService service = new LocationContextService("test-key");

        assertFalse(service.isConfigured(), "Should not be configured before init()");
    }

    @Test
    void testIsConfigured_AfterInit_ReturnsTrue() {
        LocationContextService service = new LocationContextService("test-key");
        service.init();

        assertTrue(service.isConfigured(), "Should be configured after init()");

        service.cleanup();
    }

    // ============================================================================
    // Helper Methods for Testing Private Methods
    // ============================================================================

    @SuppressWarnings("unchecked")
    private <T> T invokePrivateMethod(String methodName, Class<?> paramType, Object param) throws Exception {
        return (T) ReflectionTestUtils.invokeMethod(locationContextService, methodName, param);
    }

    // ============================================================================
    // Integration Test Notes
    // ============================================================================

    /*
     * FULL INTEGRATION TESTS WITH GOOGLE MAPS API:
     *
     * To run integration tests with real API:
     *
     * 1. Set up test configuration with real API key:
     *    @TestPropertySource(properties = {"google.maps.api.key=YOUR_TEST_API_KEY"})
     *
     * 2. Use @SpringBootTest to load full application context
     *
     * 3. Test real locations:
     *
     *    @Test
     *    void testGetLocationContext_ChipotleSanFrancisco() {
     *        // Chipotle at 2200 Market St, San Francisco
     *        LocationContext result = locationContextService.getLocationContext(37.7663, -122.4312);
     *
     *        assertTrue(result.isKnown());
     *        assertTrue(result.isRestaurant());
     *        assertNotNull(result.getPlaceName());
     *        assertTrue(result.getPlaceName().toLowerCase().contains("chipotle"));
     *        assertEquals("restaurant", result.getPlaceType());
     *    }
     *
     * 4. Test residential address:
     *
     *    @Test
     *    void testGetLocationContext_ResidentialArea() {
     *        // Residential coordinates
     *        LocationContext result = locationContextService.getLocationContext(37.7749, -122.4194);
     *
     *        assertTrue(result.isKnown());
     *        assertTrue(result.isHome());
     *        assertNotNull(result.getAddress());
     *    }
     *
     * 5. Test rate limiting and error handling:
     *    - Exceeding API quota
     *    - Network timeouts
     *    - Invalid API key response
     */
}
