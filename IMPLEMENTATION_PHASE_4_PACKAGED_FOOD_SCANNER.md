# Phase 4: Packaged Food Scanner - Implementation Plan

**Goal**: Enable accurate nutrition tracking for packaged foods using hybrid barcode/QR lookup + OCR validation with transparency features to detect manufacturer discrepancies and misleading claims.

**Timeline**: 6 weeks (3 sub-phases)

---

## Table of Contents
1. [Overview](#overview)
2. [Architecture](#architecture)
3. [Phase 4A: Barcode Scanning & Database Lookup](#phase-4a-barcode-scanning--database-lookup-week-1-2)
4. [Phase 4B: OCR Extraction & Validation](#phase-4b-ocr-extraction--validation-week-3-4)
5. [Phase 4C: Transparency & Claims Validation](#phase-4c-transparency--claims-validation-week-5-6)
6. [API Integrations](#api-integrations)
7. [Database Schema](#database-schema)
8. [Success Metrics](#success-metrics)
9. [Cost Analysis](#cost-analysis)

---

## Overview

### Problem Statement
**Current gaps:**
- No support for packaged foods (chips, protein bars, cereals, etc.)
- Missing accurate manufacturer-declared nutrition data
- No way to verify marketing claims ("Low Fat", "High Protein")
- No transparency about ingredient order and additives
- No detection of serving size manipulation

**User need:**
> "When I'm eating some food, if it's a packaged food and I take a photo of the packaging. Maybe the QR code is there, maybe the brand name is there. Will the app also be able to pull the ingredients as per what the manufacturer has declared?"

### Solution: Hybrid Approach

```
┌─────────────────────────────────────────────────────────────┐
│  User Takes Photo of Packaged Food                          │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  PARALLEL PROCESSING (3 simultaneous operations)            │
├─────────────────────────────────────────────────────────────┤
│  1. Barcode/QR Scan     │ Google ML Kit Vision (on-device) │
│  2. OCR Nutrition Label │ OpenAI Vision API                 │
│  3. OCR Brand/Product   │ OpenAI Vision API                 │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  DATABASE LOOKUP (waterfall strategy)                       │
├─────────────────────────────────────────────────────────────┤
│  1. Try OpenFoodFacts (free, 2.3M products, global)         │
│  2. If not found → Try USDA FoodData Central (US)           │
│  3. If not found → Use OCR-extracted data                   │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  VALIDATION & TRANSPARENCY                                   │
├─────────────────────────────────────────────────────────────┤
│  • Compare database vs OCR (flag discrepancies)             │
│  • Validate marketing claims (FDA/EU regulations)           │
│  • Highlight concerning ingredients (additives, allergens)  │
│  • Detect serving size manipulation                         │
│  • Calculate realistic portions                             │
└─────────────────────────────────────────────────────────────┘
                           ↓
┌─────────────────────────────────────────────────────────────┐
│  USER CHOICE                                                 │
├─────────────────────────────────────────────────────────────┤
│  • Show both sources side-by-side                           │
│  • User picks which to trust                                │
│  • Option to report to OpenFoodFacts                        │
│  • Save to meal log                                         │
└─────────────────────────────────────────────────────────────┘
```

---

## Architecture

### Component Diagram

```
┌────────────────────────────────────────────────────────────────┐
│                        Flutter App                              │
├────────────────────────────────────────────────────────────────┤
│  • Camera + Barcode Scanner (google_ml_kit)                    │
│  • Product Scanner Screen                                      │
│  • Nutrition Comparison Widget (DB vs OCR)                     │
│  • Claims Validation Widget (green ✓ / red ✗)                 │
│  • Transparency Report Screen                                  │
└────────────────────────────────────────────────────────────────┘
                              ↓ HTTP
┌────────────────────────────────────────────────────────────────┐
│                    Spring Boot Backend                          │
├────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  PackagedFoodController                                   │ │
│  │  - POST /api/packaged-foods/scan                         │ │
│  │  - GET /api/packaged-foods/barcode/{code}               │ │
│  │  - GET /api/packaged-foods/{id}/transparency-report     │ │
│  └──────────────────────────────────────────────────────────┘ │
│                              ↓                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  PackagedFoodService                                      │ │
│  │  - detectBarcode()        - lookupDatabase()             │ │
│  │  - extractNutritionLabel() - validateClaims()            │ │
│  │  - compareSourcesForDiscrepancies()                      │ │
│  └──────────────────────────────────────────────────────────┘ │
│                              ↓                                  │
│  ┌────────────────┬───────────────────┬─────────────────────┐ │
│  │ BarcodeService │ NutritionOcrSvc   │ ValidationService   │ │
│  │ - scan()       │ - extractLabel()  │ - validateClaims()  │ │
│  │ - validate()   │ - parseValues()   │ - checkServingSize()│ │
│  └────────────────┴───────────────────┴─────────────────────┘ │
│                              ↓                                  │
│  ┌────────────────┬───────────────────┬─────────────────────┐ │
│  │ OpenFoodFacts  │ USDA FoodData API │ OpenAI Vision API   │ │
│  │ Client         │ Client            │ (already exists)    │ │
│  └────────────────┴───────────────────┴─────────────────────┘ │
│                              ↓                                  │
│  ┌──────────────────────────────────────────────────────────┐ │
│  │  Repositories                                             │ │
│  │  - PackagedProductRepository                             │ │
│  │  - ProductValidationRepository                           │ │
│  │  - UserProductCorrectionRepository                       │ │
│  └──────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────┘
                              ↓
┌────────────────────────────────────────────────────────────────┐
│                    PostgreSQL Database                          │
├────────────────────────────────────────────────────────────────┤
│  • packaged_products (cached products)                         │
│  • product_validations (discrepancy reports)                   │
│  • user_product_corrections (crowdsourced fixes)               │
│  • packaged_product_meals (link to meals table)               │
└────────────────────────────────────────────────────────────────┘
```

---

## Phase 4A: Barcode Scanning & Database Lookup (Week 1-2)

### Goal
Scan barcodes/QR codes and retrieve accurate nutrition data from public databases.

### Tasks

#### Backend Development

**1. Database Migration (V16)**
```sql
-- V16__add_packaged_products.sql

CREATE TABLE packaged_products (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- Identification
    barcode VARCHAR(50) UNIQUE NOT NULL,  -- UPC-A (12), EAN-13, QR
    product_name TEXT NOT NULL,
    brand TEXT,
    product_name_normalized TEXT,  -- Lowercase for search

    -- Nutrition per 100g (standardized for comparison)
    calories_per_100g INTEGER,
    protein_per_100g DOUBLE PRECISION,
    fat_per_100g DOUBLE PRECISION,
    saturated_fat_per_100g DOUBLE PRECISION,
    carbohydrates_per_100g DOUBLE PRECISION,
    fiber_per_100g DOUBLE PRECISION,
    sugar_per_100g DOUBLE PRECISION,
    sodium_per_100mg DOUBLE PRECISION,
    cholesterol_per_100mg DOUBLE PRECISION,

    -- Serving information
    serving_size_g DOUBLE PRECISION,
    serving_size_text VARCHAR(100),  -- "1 cup (30g)"
    servings_per_container DOUBLE PRECISION,

    -- Ingredients & Allergens
    ingredients_list TEXT[],  -- Ordered by weight
    allergens TEXT[],
    additives TEXT[],         -- E-numbers: E621, E951, etc.

    -- Classification
    nova_score INTEGER CHECK (nova_score BETWEEN 1 AND 4),
    is_organic BOOLEAN DEFAULT false,
    is_gluten_free BOOLEAN DEFAULT false,
    is_vegan BOOLEAN DEFAULT false,
    is_vegetarian BOOLEAN DEFAULT false,

    -- Data source tracking
    data_source VARCHAR(50) NOT NULL,  -- 'openfoodfacts', 'usda', 'ocr', 'user'
    external_id VARCHAR(100),          -- ID in source database
    last_synced_at TIMESTAMP,

    -- Metadata
    image_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_packaged_barcode ON packaged_products(barcode);
CREATE INDEX idx_packaged_brand ON packaged_products(brand);
CREATE INDEX idx_packaged_name ON packaged_products(product_name_normalized);
CREATE INDEX idx_packaged_source ON packaged_products(data_source);

-- Link packaged products to meals
CREATE TABLE packaged_product_meals (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    packaged_product_id UUID NOT NULL REFERENCES packaged_products(id) ON DELETE CASCADE,
    quantity_consumed DOUBLE PRECISION,  -- How many servings
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    UNIQUE(meal_id, packaged_product_id)
);

CREATE INDEX idx_packaged_meals_meal ON packaged_product_meals(meal_id);
CREATE INDEX idx_packaged_meals_product ON packaged_product_meals(packaged_product_id);
```

**2. JPA Entities**

`PackagedProduct.java`:
```java
@Entity
@Table(name = "packaged_products")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagedProduct {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(unique = true, nullable = false, length = 50)
    private String barcode;

    @Column(nullable = false)
    private String productName;

    private String brand;

    private String productNameNormalized;

    // Nutrition per 100g
    private Integer caloriesPer100g;
    private Double proteinPer100g;
    private Double fatPer100g;
    private Double saturatedFatPer100g;
    private Double carbohydratesPer100g;
    private Double fiberPer100g;
    private Double sugarPer100g;
    private Double sodiumPer100mg;
    private Double cholesterolPer100mg;

    // Serving info
    private Double servingSizeG;
    private String servingSizeText;
    private Double servingsPerContainer;

    // Ingredients
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] ingredientsList;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] allergens;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(columnDefinition = "text[]")
    private String[] additives;

    // Classification
    private Integer novaScore;
    private Boolean isOrganic;
    private Boolean isGlutenFree;
    private Boolean isVegan;
    private Boolean isVegetarian;

    // Data source
    @Column(nullable = false, length = 50)
    private String dataSource;  // openfoodfacts, usda, ocr, user

    private String externalId;
    private LocalDateTime lastSyncedAt;

    private String imageUrl;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
```

`PackagedProductMeal.java`:
```java
@Entity
@Table(name = "packaged_product_meals")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagedProductMeal {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meal_id", nullable = false)
    private Meal meal;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "packaged_product_id", nullable = false)
    private PackagedProduct packagedProduct;

    private Double quantityConsumed;  // Number of servings

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}
```

**3. Repositories**

`PackagedProductRepository.java`:
```java
@Repository
public interface PackagedProductRepository extends JpaRepository<PackagedProduct, UUID> {

    Optional<PackagedProduct> findByBarcode(String barcode);

    List<PackagedProduct> findByBrandIgnoreCase(String brand);

    @Query("SELECT p FROM PackagedProduct p WHERE " +
           "LOWER(p.productNameNormalized) LIKE LOWER(CONCAT('%', :searchTerm, '%')) OR " +
           "LOWER(p.brand) LIKE LOWER(CONCAT('%', :searchTerm, '%'))")
    List<PackagedProduct> searchByNameOrBrand(@Param("searchTerm") String searchTerm);

    List<PackagedProduct> findByDataSource(String dataSource);

    @Query("SELECT p FROM PackagedProduct p WHERE p.lastSyncedAt < :cutoffDate")
    List<PackagedProduct> findStaleProducts(@Param("cutoffDate") LocalDateTime cutoffDate);
}
```

**4. OpenFoodFacts API Client**

`OpenFoodFactsClient.java`:
```java
@Service
@Slf4j
public class OpenFoodFactsClient {

    private static final String BASE_URL = "https://world.openfoodfacts.org/api/v2";
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public OpenFoodFactsClient(RestTemplate restTemplate, ObjectMapper objectMapper) {
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * Lookup product by barcode.
     * Returns null if not found.
     */
    public PackagedProduct lookupProduct(String barcode) {
        try {
            String url = String.format("%s/product/%s.json", BASE_URL, barcode);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return null;
            }

            JsonNode root = objectMapper.readTree(response.getBody());

            // Check if product found
            if (root.get("status").asInt() == 0) {
                log.info("Product not found in OpenFoodFacts: {}", barcode);
                return null;
            }

            JsonNode product = root.get("product");

            return PackagedProduct.builder()
                    .barcode(barcode)
                    .productName(getStringValue(product, "product_name"))
                    .brand(getStringValue(product, "brands"))
                    .productNameNormalized(
                        getStringValue(product, "product_name").toLowerCase())

                    // Nutrition per 100g
                    .caloriesPer100g(getIntValue(product, "nutriments", "energy-kcal_100g"))
                    .proteinPer100g(getDoubleValue(product, "nutriments", "proteins_100g"))
                    .fatPer100g(getDoubleValue(product, "nutriments", "fat_100g"))
                    .saturatedFatPer100g(getDoubleValue(product, "nutriments", "saturated-fat_100g"))
                    .carbohydratesPer100g(getDoubleValue(product, "nutriments", "carbohydrates_100g"))
                    .fiberPer100g(getDoubleValue(product, "nutriments", "fiber_100g"))
                    .sugarPer100g(getDoubleValue(product, "nutriments", "sugars_100g"))
                    .sodiumPer100mg(getDoubleValue(product, "nutriments", "sodium_100g") * 1000) // g to mg

                    // Serving info
                    .servingSizeG(getDoubleValue(product, "serving_quantity"))
                    .servingSizeText(getStringValue(product, "serving_size"))

                    // Ingredients
                    .ingredientsList(getStringArray(product, "ingredients_text"))
                    .allergens(getStringArray(product, "allergens_tags"))
                    .additives(getStringArray(product, "additives_tags"))

                    // Classification
                    .novaScore(getIntValue(product, "nova_group"))
                    .isOrganic(hasLabel(product, "en:organic"))
                    .isGlutenFree(hasLabel(product, "en:gluten-free"))
                    .isVegan(hasLabel(product, "en:vegan"))
                    .isVegetarian(hasLabel(product, "en:vegetarian"))

                    // Metadata
                    .dataSource("openfoodfacts")
                    .externalId(barcode)
                    .lastSyncedAt(LocalDateTime.now())
                    .imageUrl(getStringValue(product, "image_url"))

                    .build();

        } catch (Exception e) {
            log.error("Failed to lookup product in OpenFoodFacts: {}", barcode, e);
            return null;
        }
    }

    private String getStringValue(JsonNode node, String... path) {
        JsonNode current = node;
        for (String key : path) {
            current = current.get(key);
            if (current == null) return null;
        }
        return current.asText();
    }

    private Integer getIntValue(JsonNode node, String... path) {
        String value = getStringValue(node, path);
        return value != null ? Integer.parseInt(value) : null;
    }

    private Double getDoubleValue(JsonNode node, String... path) {
        String value = getStringValue(node, path);
        return value != null ? Double.parseDouble(value) : null;
    }

    private String[] getStringArray(JsonNode node, String key) {
        JsonNode arrayNode = node.get(key);
        if (arrayNode == null || !arrayNode.isArray()) {
            // Try as comma-separated string
            String text = getStringValue(node, key);
            if (text != null && !text.isEmpty()) {
                return text.split(",");
            }
            return new String[0];
        }

        List<String> result = new ArrayList<>();
        for (JsonNode item : arrayNode) {
            result.add(item.asText());
        }
        return result.toArray(new String[0]);
    }

    private boolean hasLabel(JsonNode product, String label) {
        JsonNode labels = product.get("labels_tags");
        if (labels == null || !labels.isArray()) return false;

        for (JsonNode item : labels) {
            if (item.asText().equalsIgnoreCase(label)) {
                return true;
            }
        }
        return false;
    }
}
```

**5. USDA FoodData Central Client**

`USDAFoodDataClient.java`:
```java
@Service
@Slf4j
public class USDAFoodDataClient {

    private static final String BASE_URL = "https://api.nal.usda.gov/fdc/v1";

    private final RestTemplate restTemplate;
    private final String apiKey;

    public USDAFoodDataClient(
            RestTemplate restTemplate,
            @Value("${usda.api.key}") String apiKey) {
        this.restTemplate = restTemplate;
        this.apiKey = apiKey;
    }

    /**
     * Search for product by barcode (GTIN/UPC).
     * Returns null if not found.
     */
    public PackagedProduct searchByBarcode(String barcode) {
        try {
            String url = String.format("%s/foods/search?query=%s&api_key=%s&dataType=Branded",
                    BASE_URL, barcode, apiKey);

            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            if (!response.getStatusCode().is2xxSuccessful()) {
                return null;
            }

            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(response.getBody());

            JsonNode foods = root.get("foods");
            if (foods == null || foods.isEmpty()) {
                log.info("Product not found in USDA FoodData: {}", barcode);
                return null;
            }

            // Take first result
            JsonNode food = foods.get(0);

            // Extract nutrients
            Map<String, Double> nutrients = new HashMap<>();
            JsonNode foodNutrients = food.get("foodNutrients");
            if (foodNutrients != null && foodNutrients.isArray()) {
                for (JsonNode nutrient : foodNutrients) {
                    String name = nutrient.get("nutrientName").asText();
                    double value = nutrient.get("value").asDouble();
                    nutrients.put(name, value);
                }
            }

            return PackagedProduct.builder()
                    .barcode(barcode)
                    .productName(food.get("description").asText())
                    .brand(food.has("brandOwner") ? food.get("brandOwner").asText() : null)
                    .productNameNormalized(food.get("description").asText().toLowerCase())

                    // Map USDA nutrients to our schema (per 100g)
                    .caloriesPer100g(nutrients.get("Energy").intValue())
                    .proteinPer100g(nutrients.get("Protein"))
                    .fatPer100g(nutrients.get("Total lipid (fat)"))
                    .saturatedFatPer100g(nutrients.get("Fatty acids, total saturated"))
                    .carbohydratesPer100g(nutrients.get("Carbohydrate, by difference"))
                    .fiberPer100g(nutrients.get("Fiber, total dietary"))
                    .sugarPer100g(nutrients.get("Sugars, total including NLEA"))
                    .sodiumPer100mg(nutrients.get("Sodium"))

                    // Serving info
                    .servingSizeG(food.has("servingSize") ? food.get("servingSize").asDouble() : null)
                    .servingSizeText(food.has("servingSizeUnit") ? food.get("servingSizeUnit").asText() : null)

                    // Ingredients
                    .ingredientsList(parseIngredients(food.get("ingredients")))

                    // Metadata
                    .dataSource("usda")
                    .externalId(food.get("fdcId").asText())
                    .lastSyncedAt(LocalDateTime.now())

                    .build();

        } catch (Exception e) {
            log.error("Failed to search USDA FoodData: {}", barcode, e);
            return null;
        }
    }

    private String[] parseIngredients(JsonNode ingredientsNode) {
        if (ingredientsNode == null) return new String[0];

        String ingredientsText = ingredientsNode.asText();
        if (ingredientsText.isEmpty()) return new String[0];

        // Split by comma, trim each
        return Arrays.stream(ingredientsText.split(","))
                .map(String::trim)
                .toArray(String[]::new);
    }
}
```

**6. Barcode Detection Service**

`BarcodeService.java`:
```java
@Service
@Slf4j
public class BarcodeService {

    /**
     * Extract barcode from image using OpenAI Vision.
     * Returns barcode string or null if not found.
     */
    public String extractBarcodeFromImage(String imageDataUri) {
        // For Phase 4A, we'll enhance OpenAI Vision prompt to detect barcodes
        // Phase 4B will add dedicated barcode scanner (Google ML Kit on Flutter side)

        // This is a fallback OCR-based extraction
        // Prompt OpenAI to look for barcode numbers

        log.info("Extracting barcode from image using OCR");
        // Implementation will use OpenAI Vision with specific prompt
        // See enhancePromptForPackagedFood() below

        return null; // Placeholder
    }

    /**
     * Validate barcode format.
     */
    public boolean isValidBarcode(String barcode) {
        if (barcode == null || barcode.isEmpty()) {
            return false;
        }

        // Remove any non-numeric characters
        String digits = barcode.replaceAll("[^0-9]", "");

        // Check length (UPC-A: 12, EAN-13: 13, EAN-8: 8)
        int length = digits.length();
        if (length != 8 && length != 12 && length != 13) {
            return false;
        }

        // Validate checksum (optional but recommended)
        return validateChecksum(digits);
    }

    private boolean validateChecksum(String barcode) {
        // Implement UPC/EAN checksum validation
        // https://en.wikipedia.org/wiki/International_Article_Number

        int[] digits = barcode.chars().map(c -> c - '0').toArray();
        int checkDigit = digits[digits.length - 1];

        int sum = 0;
        for (int i = 0; i < digits.length - 1; i++) {
            int weight = (i % 2 == 0) ? 1 : 3;
            sum += digits[i] * weight;
        }

        int calculatedCheck = (10 - (sum % 10)) % 10;
        return calculatedCheck == checkDigit;
    }
}
```

**7. Main Service: PackagedFoodService**

`PackagedFoodService.java`:
```java
@Service
@Slf4j
public class PackagedFoodService {

    private final PackagedProductRepository packagedProductRepository;
    private final OpenFoodFactsClient openFoodFactsClient;
    private final USDAFoodDataClient usdaFoodDataClient;
    private final BarcodeService barcodeService;

    public PackagedFoodService(
            PackagedProductRepository packagedProductRepository,
            OpenFoodFactsClient openFoodFactsClient,
            USDAFoodDataClient usdaFoodDataClient,
            BarcodeService barcodeService) {
        this.packagedProductRepository = packagedProductRepository;
        this.openFoodFactsClient = openFoodFactsClient;
        this.usdaFoodDataClient = usdaFoodDataClient;
        this.barcodeService = barcodeService;
    }

    /**
     * Lookup product by barcode with caching and waterfall strategy.
     *
     * Strategy:
     * 1. Check local database cache
     * 2. Try OpenFoodFacts (free, global)
     * 3. Try USDA FoodData Central (US products)
     * 4. Return null if not found
     */
    @Transactional
    public PackagedProduct lookupByBarcode(String barcode) {
        log.info("Looking up product by barcode: {}", barcode);

        // Validate barcode
        if (!barcodeService.isValidBarcode(barcode)) {
            log.warn("Invalid barcode format: {}", barcode);
            throw new IllegalArgumentException("Invalid barcode format");
        }

        // 1. Check local cache
        Optional<PackagedProduct> cached = packagedProductRepository.findByBarcode(barcode);
        if (cached.isPresent()) {
            PackagedProduct product = cached.get();

            // Check if data is stale (older than 30 days)
            if (product.getLastSyncedAt().isAfter(LocalDateTime.now().minusDays(30))) {
                log.info("Found product in cache: {}", product.getProductName());
                return product;
            }

            log.info("Cached product is stale, refreshing from API");
        }

        // 2. Try OpenFoodFacts
        PackagedProduct product = openFoodFactsClient.lookupProduct(barcode);
        if (product != null) {
            log.info("Found product in OpenFoodFacts: {}", product.getProductName());
            return packagedProductRepository.save(product);
        }

        // 3. Try USDA FoodData Central
        product = usdaFoodDataClient.searchByBarcode(barcode);
        if (product != null) {
            log.info("Found product in USDA FoodData: {}", product.getProductName());
            return packagedProductRepository.save(product);
        }

        log.info("Product not found in any database: {}", barcode);
        return null;
    }

    /**
     * Get product by ID (from local database).
     */
    public PackagedProduct getProductById(UUID productId) {
        return packagedProductRepository.findById(productId)
                .orElseThrow(() -> new ResourceNotFoundException("Product not found: " + productId));
    }

    /**
     * Search products by name or brand.
     */
    public List<PackagedProduct> searchProducts(String searchTerm) {
        return packagedProductRepository.searchByNameOrBrand(searchTerm);
    }
}
```

**8. REST Controller**

`PackagedFoodController.java`:
```java
@RestController
@RequestMapping("/api/packaged-foods")
@Tag(name = "Packaged Foods", description = "Barcode scanning and packaged food lookup")
@SecurityRequirement(name = "bearerAuth")
@Slf4j
public class PackagedFoodController {

    private final PackagedFoodService packagedFoodService;

    public PackagedFoodController(PackagedFoodService packagedFoodService) {
        this.packagedFoodService = packagedFoodService;
    }

    /**
     * Lookup product by barcode.
     */
    @GetMapping("/barcode/{barcode}")
    @Operation(summary = "Lookup by barcode", description = "Find product by UPC/EAN barcode")
    public ResponseEntity<PackagedProductResponse> lookupByBarcode(
            @PathVariable String barcode,
            Authentication authentication) {

        PackagedProduct product = packagedFoodService.lookupByBarcode(barcode);

        if (product == null) {
            return ResponseEntity.notFound().build();
        }

        return ResponseEntity.ok(PackagedProductResponse.fromEntity(product));
    }

    /**
     * Search products by name or brand.
     */
    @GetMapping("/search")
    @Operation(summary = "Search products", description = "Search by product name or brand")
    public ResponseEntity<List<PackagedProductResponse>> searchProducts(
            @RequestParam String q,
            Authentication authentication) {

        List<PackagedProduct> products = packagedFoodService.searchProducts(q);

        List<PackagedProductResponse> responses = products.stream()
                .map(PackagedProductResponse::fromEntity)
                .collect(Collectors.toList());

        return ResponseEntity.ok(responses);
    }

    /**
     * Get product details by ID.
     */
    @GetMapping("/{productId}")
    @Operation(summary = "Get product", description = "Get product details by ID")
    public ResponseEntity<PackagedProductResponse> getProduct(
            @PathVariable UUID productId,
            Authentication authentication) {

        PackagedProduct product = packagedFoodService.getProductById(productId);
        return ResponseEntity.ok(PackagedProductResponse.fromEntity(product));
    }
}
```

**9. DTOs**

`PackagedProductResponse.java`:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PackagedProductResponse {

    private UUID id;
    private String barcode;
    private String productName;
    private String brand;

    // Nutrition per 100g
    private Integer caloriesPer100g;
    private Double proteinPer100g;
    private Double fatPer100g;
    private Double saturatedFatPer100g;
    private Double carbohydratesPer100g;
    private Double fiberPer100g;
    private Double sugarPer100g;
    private Double sodiumPer100mg;

    // Serving info
    private Double servingSizeG;
    private String servingSizeText;
    private Double servingsPerContainer;

    // Ingredients
    private List<String> ingredientsList;
    private List<String> allergens;
    private List<String> additives;

    // Classification
    private Integer novaScore;
    private Boolean isOrganic;
    private Boolean isGlutenFree;
    private Boolean isVegan;
    private Boolean isVegetarian;

    // Metadata
    private String dataSource;
    private String imageUrl;
    private LocalDateTime lastSyncedAt;

    public static PackagedProductResponse fromEntity(PackagedProduct product) {
        return PackagedProductResponse.builder()
                .id(product.getId())
                .barcode(product.getBarcode())
                .productName(product.getProductName())
                .brand(product.getBrand())
                .caloriesPer100g(product.getCaloriesPer100g())
                .proteinPer100g(product.getProteinPer100g())
                .fatPer100g(product.getFatPer100g())
                .saturatedFatPer100g(product.getSaturatedFatPer100g())
                .carbohydratesPer100g(product.getCarbohydratesPer100g())
                .fiberPer100g(product.getFiberPer100g())
                .sugarPer100g(product.getSugarPer100g())
                .sodiumPer100mg(product.getSodiumPer100mg())
                .servingSizeG(product.getServingSizeG())
                .servingSizeText(product.getServingSizeText())
                .servingsPerContainer(product.getServingsPerContainer())
                .ingredientsList(product.getIngredientsList() != null
                    ? Arrays.asList(product.getIngredientsList())
                    : Collections.emptyList())
                .allergens(product.getAllergens() != null
                    ? Arrays.asList(product.getAllergens())
                    : Collections.emptyList())
                .additives(product.getAdditives() != null
                    ? Arrays.asList(product.getAdditives())
                    : Collections.emptyList())
                .novaScore(product.getNovaScore())
                .isOrganic(product.getIsOrganic())
                .isGlutenFree(product.getIsGlutenFree())
                .isVegan(product.getIsVegan())
                .isVegetarian(product.getIsVegetarian())
                .dataSource(product.getDataSource())
                .imageUrl(product.getImageUrl())
                .lastSyncedAt(product.getLastSyncedAt())
                .build();
    }
}
```

#### Flutter Development (Minimal for Phase 4A)

For Phase 4A, Flutter side is minimal - just barcode input field for testing:

```dart
// Manual barcode entry for testing
TextField(
  decoration: InputDecoration(labelText: 'Barcode'),
  onSubmitted: (barcode) async {
    final response = await http.get(
      Uri.parse('$baseUrl/api/packaged-foods/barcode/$barcode'),
      headers: {'Authorization': 'Bearer $token'},
    );
    // Display product details
  },
)
```

Phase 4B will add camera-based barcode scanning.

#### Testing Checklist for Phase 4A

- [ ] V16 migration creates packaged_products table
- [ ] OpenFoodFacts API returns product for valid barcode (test: `3017620422003` - Nutella)
- [ ] USDA API returns product for US barcode
- [ ] Products are cached in database
- [ ] Stale products (>30 days) are refreshed from API
- [ ] Barcode validation rejects invalid formats
- [ ] GET /api/packaged-foods/barcode/{barcode} returns 200 for found product
- [ ] GET /api/packaged-foods/barcode/{barcode} returns 404 for unknown product
- [ ] Search endpoint returns results for product name
- [ ] Ingredients list is properly parsed and stored

---

## Phase 4B: OCR Extraction & Validation (Week 3-4)

### Goal
Extract nutrition label data using OCR and compare with database for discrepancy detection.

### Tasks

#### Backend Development

**1. Database Migration (V17)**

```sql
-- V17__add_product_validations.sql

CREATE TABLE product_validations (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    packaged_product_id UUID NOT NULL REFERENCES packaged_products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- Sources being compared
    database_source VARCHAR(50),  -- 'openfoodfacts', 'usda'
    ocr_source VARCHAR(50) DEFAULT 'ocr',

    -- Discrepancies found
    has_discrepancies BOOLEAN DEFAULT false,
    discrepancy_count INTEGER DEFAULT 0,

    -- Specific field discrepancies (JSON)
    discrepancies JSONB,

    -- Example discrepancy format:
    -- {
    --   "calories": {"database": 120, "ocr": 130, "diff_percent": 8.3},
    --   "protein": {"database": 5.0, "ocr": 4.5, "diff_percent": -10.0}
    -- }

    -- OCR extracted data (full nutrition label as JSON)
    ocr_data JSONB,

    -- User feedback
    user_confirmed_source VARCHAR(50),  -- 'database', 'ocr', or null
    user_notes TEXT,

    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_validation_product ON product_validations(packaged_product_id);
CREATE INDEX idx_validation_user ON product_validations(user_id);
CREATE INDEX idx_validation_discrepancies ON product_validations(has_discrepancies);
```

**2. DTOs**

`NutritionLabelOcrResult.java`:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NutritionLabelOcrResult {

    // Product identification
    private String productName;
    private String brand;
    private String barcode;  // If detected in image

    // Serving information
    private String servingSize;
    private Integer servingsPerContainer;

    // Nutrition per serving (as printed on label)
    private Integer calories;
    private Double proteinG;
    private Double fatG;
    private Double saturatedFatG;
    private Double transFatG;
    private Double cholesterolMg;
    private Double sodiumMg;
    private Double carbohydratesG;
    private Double fiberG;
    private Double sugarG;
    private Double addedSugarG;

    // Ingredients
    private List<String> ingredientsList;
    private List<String> allergens;

    // Health claims detected
    private List<String> healthClaims;  // "Low Fat", "High Protein", etc.

    // OCR confidence
    private Double confidence;

    // Raw OCR text
    private String rawOcrText;
}
```

`DiscrepancyReport.java`:
```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DiscrepancyReport {

    private UUID productId;
    private String productName;
    private String brand;

    private String databaseSource;
    private String ocrSource;

    private boolean hasDiscrepancies;
    private int discrepancyCount;

    private List<FieldDiscrepancy> discrepancies;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FieldDiscrepancy {
        private String fieldName;
        private Object databaseValue;
        private Object ocrValue;
        private Double differencePercent;
        private Severity severity;  // MINOR, MODERATE, MAJOR
    }

    public enum Severity {
        MINOR,      // <5% difference
        MODERATE,   // 5-15% difference
        MAJOR       // >15% difference
    }
}
```

**3. Service: NutritionOcrService**

`NutritionOcrService.java`:
```java
@Service
@Slf4j
public class NutritionOcrService {

    private final OpenAIVisionService openAIVisionService;
    private final ImageProcessingService imageProcessingService;

    public NutritionOcrService(
            OpenAIVisionService openAIVisionService,
            ImageProcessingService imageProcessingService) {
        this.openAIVisionService = openAIVisionService;
        this.imageProcessingService = imageProcessingService;
    }

    /**
     * Extract nutrition label from image using OCR.
     */
    public NutritionLabelOcrResult extractNutritionLabel(MultipartFile image) {
        try {
            log.info("Extracting nutrition label from image");

            // Process image
            String imageDataUri = imageProcessingService.processImageFromUpload(image);

            // Enhanced prompt for nutrition label OCR
            String prompt = buildNutritionLabelPrompt();

            // Call OpenAI Vision
            String response = openAIVisionService.analyzeImageRaw(imageDataUri, prompt);

            // Parse response
            return parseNutritionLabelResponse(response);

        } catch (Exception e) {
            log.error("Failed to extract nutrition label", e);
            throw new RuntimeException("OCR extraction failed: " + e.getMessage(), e);
        }
    }

    private String buildNutritionLabelPrompt() {
        return """
                You are a nutrition label OCR expert. Analyze this image and extract ALL information from the nutrition facts label.

                IMPORTANT: This is a packaged food product. Look for:
                1. Nutrition Facts panel (US) or Nutrition Information (EU/Canada)
                2. Barcode/UPC code (if visible)
                3. Product name and brand
                4. Ingredients list (maintain order - highest to lowest by weight)
                5. Allergen information
                6. Any health claims or certifications

                Return ONLY a valid JSON object with this structure:
                {
                  "product_name": "Crunchy Peanut Butter",
                  "brand": "Jif",
                  "barcode": "051500255223",

                  "serving_size": "2 tbsp (32g)",
                  "servings_per_container": 15,

                  "nutrition_per_serving": {
                    "calories": 190,
                    "protein_g": 7.0,
                    "fat_g": 16.0,
                    "saturated_fat_g": 3.0,
                    "trans_fat_g": 0.0,
                    "cholesterol_mg": 0.0,
                    "sodium_mg": 140.0,
                    "carbohydrates_g": 8.0,
                    "fiber_g": 2.0,
                    "sugar_g": 3.0,
                    "added_sugar_g": 2.0
                  },

                  "ingredients_list": ["roasted peanuts", "sugar", "palm oil", "salt"],
                  "allergens": ["peanuts"],

                  "health_claims": ["Good Source of Protein"],

                  "confidence": 0.95,
                  "raw_ocr_text": "Full text extracted from image..."
                }

                Rules:
                - Extract ALL numbers exactly as shown on label
                - Maintain ingredients order (by weight, descending)
                - Identify allergens explicitly or from "Contains:" statement
                - Extract health claims from front of package
                - If label shows per 100g, note that in raw_ocr_text
                - confidence: 0-1 based on image quality

                If this is NOT a nutrition label, return:
                {"error": "Not a nutrition facts label"}
                """;
    }

    private NutritionLabelOcrResult parseNutritionLabelResponse(String jsonResponse) {
        try {
            ObjectMapper mapper = new ObjectMapper();
            JsonNode root = mapper.readTree(jsonResponse);

            // Check for error
            if (root.has("error")) {
                throw new RuntimeException("Not a nutrition label: " + root.get("error").asText());
            }

            JsonNode nutrition = root.get("nutrition_per_serving");

            return NutritionLabelOcrResult.builder()
                    .productName(root.get("product_name").asText())
                    .brand(root.has("brand") ? root.get("brand").asText() : null)
                    .barcode(root.has("barcode") ? root.get("barcode").asText() : null)
                    .servingSize(root.get("serving_size").asText())
                    .servingsPerContainer(root.get("servings_per_container").asInt())
                    .calories(nutrition.get("calories").asInt())
                    .proteinG(nutrition.get("protein_g").asDouble())
                    .fatG(nutrition.get("fat_g").asDouble())
                    .saturatedFatG(nutrition.get("saturated_fat_g").asDouble())
                    .transFatG(nutrition.has("trans_fat_g") ? nutrition.get("trans_fat_g").asDouble() : null)
                    .cholesterolMg(nutrition.get("cholesterol_mg").asDouble())
                    .sodiumMg(nutrition.get("sodium_mg").asDouble())
                    .carbohydratesG(nutrition.get("carbohydrates_g").asDouble())
                    .fiberG(nutrition.get("fiber_g").asDouble())
                    .sugarG(nutrition.get("sugar_g").asDouble())
                    .addedSugarG(nutrition.has("added_sugar_g") ? nutrition.get("added_sugar_g").asDouble() : null)
                    .ingredientsList(parseStringArray(root, "ingredients_list"))
                    .allergens(parseStringArray(root, "allergens"))
                    .healthClaims(parseStringArray(root, "health_claims"))
                    .confidence(root.get("confidence").asDouble())
                    .rawOcrText(root.get("raw_ocr_text").asText())
                    .build();

        } catch (Exception e) {
            log.error("Failed to parse OCR response", e);
            throw new RuntimeException("Failed to parse OCR result", e);
        }
    }

    private List<String> parseStringArray(JsonNode root, String key) {
        if (!root.has(key)) return Collections.emptyList();

        JsonNode array = root.get(key);
        if (!array.isArray()) return Collections.emptyList();

        List<String> result = new ArrayList<>();
        for (JsonNode item : array) {
            result.add(item.asText());
        }
        return result;
    }
}
```

**4. Service: ProductValidationService**

`ProductValidationService.java`:
```java
@Service
@Slf4j
public class ProductValidationService {

    private final ProductValidationRepository validationRepository;

    // Discrepancy threshold (%)
    private static final double MINOR_THRESHOLD = 5.0;    // <5% = minor
    private static final double MODERATE_THRESHOLD = 15.0; // 5-15% = moderate
    // >15% = major

    public ProductValidationService(ProductValidationRepository validationRepository) {
        this.validationRepository = validationRepository;
    }

    /**
     * Compare database product with OCR-extracted label.
     * Returns discrepancy report.
     */
    public DiscrepancyReport compareSourcesForDiscrepancies(
            PackagedProduct dbProduct,
            NutritionLabelOcrResult ocrResult) {

        log.info("Comparing database vs OCR for product: {}", dbProduct.getProductName());

        List<DiscrepancyReport.FieldDiscrepancy> discrepancies = new ArrayList<>();

        // Convert OCR per-serving to per-100g for fair comparison
        double servingSizeG = parseServingSize(ocrResult.getServingSize());

        // Compare calories
        if (dbProduct.getCaloriesPer100g() != null && ocrResult.getCalories() != null) {
            double ocrCaloriesPer100g = (ocrResult.getCalories() / servingSizeG) * 100;
            discrepancies.addAll(compareField("calories",
                    dbProduct.getCaloriesPer100g().doubleValue(),
                    ocrCaloriesPer100g));
        }

        // Compare protein
        if (dbProduct.getProteinPer100g() != null && ocrResult.getProteinG() != null) {
            double ocrProteinPer100g = (ocrResult.getProteinG() / servingSizeG) * 100;
            discrepancies.addAll(compareField("protein",
                    dbProduct.getProteinPer100g(),
                    ocrProteinPer100g));
        }

        // Compare fat
        if (dbProduct.getFatPer100g() != null && ocrResult.getFatG() != null) {
            double ocrFatPer100g = (ocrResult.getFatG() / servingSizeG) * 100;
            discrepancies.addAll(compareField("fat",
                    dbProduct.getFatPer100g(),
                    ocrFatPer100g));
        }

        // Compare carbs
        if (dbProduct.getCarbohydratesPer100g() != null && ocrResult.getCarbohydratesG() != null) {
            double ocrCarbsPer100g = (ocrResult.getCarbohydratesG() / servingSizeG) * 100;
            discrepancies.addAll(compareField("carbohydrates",
                    dbProduct.getCarbohydratesPer100g(),
                    ocrCarbsPer100g));
        }

        // Compare sodium
        if (dbProduct.getSodiumPer100mg() != null && ocrResult.getSodiumMg() != null) {
            double ocrSodiumPer100mg = (ocrResult.getSodiumMg() / servingSizeG) * 100;
            discrepancies.addAll(compareField("sodium",
                    dbProduct.getSodiumPer100mg(),
                    ocrSodiumPer100mg));
        }

        boolean hasDiscrepancies = !discrepancies.isEmpty();

        return DiscrepancyReport.builder()
                .productId(dbProduct.getId())
                .productName(dbProduct.getProductName())
                .brand(dbProduct.getBrand())
                .databaseSource(dbProduct.getDataSource())
                .ocrSource("ocr")
                .hasDiscrepancies(hasDiscrepancies)
                .discrepancyCount(discrepancies.size())
                .discrepancies(discrepancies)
                .build();
    }

    private List<DiscrepancyReport.FieldDiscrepancy> compareField(
            String fieldName,
            Double dbValue,
            Double ocrValue) {

        if (dbValue == null || ocrValue == null) {
            return Collections.emptyList();
        }

        double diff = Math.abs(dbValue - ocrValue);
        double diffPercent = (diff / dbValue) * 100.0;

        // Only report if difference is significant (>5%)
        if (diffPercent < MINOR_THRESHOLD) {
            return Collections.emptyList();
        }

        DiscrepancyReport.Severity severity;
        if (diffPercent < MODERATE_THRESHOLD) {
            severity = DiscrepancyReport.Severity.MINOR;
        } else if (diffPercent < 25.0) {
            severity = DiscrepancyReport.Severity.MODERATE;
        } else {
            severity = DiscrepancyReport.Severity.MAJOR;
        }

        return List.of(DiscrepancyReport.FieldDiscrepancy.builder()
                .fieldName(fieldName)
                .databaseValue(dbValue)
                .ocrValue(ocrValue)
                .differencePercent(diffPercent)
                .severity(severity)
                .build());
    }

    private double parseServingSize(String servingSizeText) {
        // Extract grams from serving size text
        // Examples: "2 tbsp (32g)", "1 cup (240g)", "30g"

        Pattern pattern = Pattern.compile("(\\d+)g");
        Matcher matcher = pattern.matcher(servingSizeText);

        if (matcher.find()) {
            return Double.parseDouble(matcher.group(1));
        }

        // Default fallback if parsing fails
        return 100.0;
    }

    /**
     * Save validation result to database.
     */
    @Transactional
    public ProductValidation saveValidation(
            PackagedProduct product,
            NutritionLabelOcrResult ocrResult,
            DiscrepancyReport discrepancyReport,
            UUID userId) {

        // Convert to JSONB format
        ObjectMapper mapper = new ObjectMapper();
        JsonNode discrepanciesJson = mapper.valueToTree(discrepancyReport.getDiscrepancies());
        JsonNode ocrDataJson = mapper.valueToTree(ocrResult);

        ProductValidation validation = ProductValidation.builder()
                .packagedProduct(product)
                .userId(userId)
                .databaseSource(product.getDataSource())
                .ocrSource("ocr")
                .hasDiscrepancies(discrepancyReport.isHasDiscrepancies())
                .discrepancyCount(discrepancyReport.getDiscrepancyCount())
                .discrepancies(discrepanciesJson)
                .ocrData(ocrDataJson)
                .build();

        return validationRepository.save(validation);
    }
}
```

**5. Enhanced Controller Endpoint**

Add to `PackagedFoodController.java`:
```java
/**
 * Scan packaged food: Extract barcode + OCR label, lookup database, compare.
 */
@PostMapping("/scan")
@Operation(summary = "Scan packaged food",
           description = "Extract nutrition label via OCR and compare with database")
public ResponseEntity<PackagedFoodScanResult> scanPackagedFood(
        @RequestParam("image") MultipartFile image,
        Authentication authentication) {

    UUID userId = UUID.fromString(authentication.getName());

    try {
        // 1. OCR: Extract nutrition label
        NutritionLabelOcrResult ocrResult = nutritionOcrService.extractNutritionLabel(image);

        // 2. Lookup in database (if barcode detected)
        PackagedProduct dbProduct = null;
        if (ocrResult.getBarcode() != null) {
            dbProduct = packagedFoodService.lookupByBarcode(ocrResult.getBarcode());
        }

        // 3. Compare sources (if both available)
        DiscrepancyReport discrepancyReport = null;
        if (dbProduct != null) {
            discrepancyReport = validationService.compareSourcesForDiscrepancies(
                    dbProduct, ocrResult);

            // Save validation result
            validationService.saveValidation(dbProduct, ocrResult, discrepancyReport, userId);
        }

        // 4. Build response
        PackagedFoodScanResult result = PackagedFoodScanResult.builder()
                .ocrResult(ocrResult)
                .databaseProduct(dbProduct != null ? PackagedProductResponse.fromEntity(dbProduct) : null)
                .discrepancyReport(discrepancyReport)
                .build();

        return ResponseEntity.ok(result);

    } catch (Exception e) {
        log.error("Failed to scan packaged food", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class PackagedFoodScanResult {
    private NutritionLabelOcrResult ocrResult;
    private PackagedProductResponse databaseProduct;
    private DiscrepancyReport discrepancyReport;
}
```

#### Testing Checklist for Phase 4B

- [ ] OCR extracts nutrition label accurately from test images
- [ ] Barcode is detected from label image
- [ ] Database lookup succeeds if barcode found
- [ ] Discrepancy detection identifies significant differences (>5%)
- [ ] Severity levels (MINOR/MODERATE/MAJOR) assigned correctly
- [ ] Serving size conversions (per serving → per 100g) are accurate
- [ ] Validation results saved to product_validations table
- [ ] POST /api/packaged-foods/scan returns complete comparison
- [ ] OCR works with US Nutrition Facts and EU Nutrition Information formats

---

## Phase 4C: Transparency & Claims Validation (Week 5-6)

### Goal
Validate marketing claims, detect serving size manipulation, highlight concerning ingredients, and empower users with transparency.

### Tasks

#### Backend Development

**1. Service: MarketingClaimsValidator**

`MarketingClaimsValidator.java`:
```java
@Service
@Slf4j
public class MarketingClaimsValidator {

    /**
     * Validate health claims against FDA/EU regulations.
     * Returns list of validated/invalidated claims with evidence.
     */
    public List<ClaimValidation> validateClaims(
            PackagedProduct product,
            List<String> claimsOnPackage) {

        List<ClaimValidation> validations = new ArrayList<>();

        for (String claim : claimsOnPackage) {
            ClaimValidation validation = validateClaim(product, claim);
            validations.add(validation);
        }

        return validations;
    }

    private ClaimValidation validateClaim(PackagedProduct product, String claim) {
        String normalizedClaim = claim.toLowerCase().trim();

        // FDA Nutrient Content Claims (21 CFR 101.54-101.62)

        // "Low Fat"
        if (normalizedClaim.contains("low fat")) {
            return validateLowFat(product, claim);
        }

        // "Fat Free"
        if (normalizedClaim.contains("fat free") || normalizedClaim.contains("0 fat")) {
            return validateFatFree(product, claim);
        }

        // "Low Sodium"
        if (normalizedClaim.contains("low sodium")) {
            return validateLowSodium(product, claim);
        }

        // "High Protein"
        if (normalizedClaim.contains("high protein") || normalizedClaim.contains("protein rich")) {
            return validateHighProtein(product, claim);
        }

        // "Good Source of Fiber"
        if (normalizedClaim.contains("good source") && normalizedClaim.contains("fiber")) {
            return validateGoodSourceFiber(product, claim);
        }

        // "Whole Grain"
        if (normalizedClaim.contains("whole grain")) {
            return validateWholeGrain(product, claim);
        }

        // Unknown claim - cannot validate
        return ClaimValidation.builder()
                .claim(claim)
                .isValid(null)  // Unknown
                .message("Unable to validate this claim automatically")
                .build();
    }

    // FDA: Low Fat = ≤3g per serving
    private ClaimValidation validateLowFat(PackagedProduct product, String claim) {
        if (product.getFatPer100g() == null || product.getServingSizeG() == null) {
            return ClaimValidation.builder()
                    .claim(claim)
                    .isValid(null)
                    .message("Insufficient data to validate")
                    .build();
        }

        double fatPerServing = (product.getFatPer100g() / 100.0) * product.getServingSizeG();
        boolean isValid = fatPerServing <= 3.0;

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .actualValue(fatPerServing)
                .requiredValue(3.0)
                .message(isValid
                    ? String.format("✓ Valid: %.1fg fat per serving (≤3g)", fatPerServing)
                    : String.format("✗ Invalid: %.1fg fat per serving (exceeds 3g limit)", fatPerServing))
                .regulation("FDA 21 CFR 101.62(b)(2)")
                .build();
    }

    // FDA: Fat Free = <0.5g per serving
    private ClaimValidation validateFatFree(PackagedProduct product, String claim) {
        if (product.getFatPer100g() == null || product.getServingSizeG() == null) {
            return ClaimValidation.builder().claim(claim).isValid(null).build();
        }

        double fatPerServing = (product.getFatPer100g() / 100.0) * product.getServingSizeG();
        boolean isValid = fatPerServing < 0.5;

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .actualValue(fatPerServing)
                .requiredValue(0.5)
                .message(isValid
                    ? String.format("✓ Valid: %.2fg fat per serving (<0.5g)", fatPerServing)
                    : String.format("✗ Invalid: %.2fg fat per serving (must be <0.5g)", fatPerServing))
                .regulation("FDA 21 CFR 101.62(b)(1)")
                .build();
    }

    // FDA: Low Sodium = ≤140mg per serving
    private ClaimValidation validateLowSodium(PackagedProduct product, String claim) {
        if (product.getSodiumPer100mg() == null || product.getServingSizeG() == null) {
            return ClaimValidation.builder().claim(claim).isValid(null).build();
        }

        double sodiumPerServing = (product.getSodiumPer100mg() / 100.0) * product.getServingSizeG();
        boolean isValid = sodiumPerServing <= 140.0;

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .actualValue(sodiumPerServing)
                .requiredValue(140.0)
                .message(isValid
                    ? String.format("✓ Valid: %.0fmg sodium per serving (≤140mg)", sodiumPerServing)
                    : String.format("✗ Invalid: %.0fmg sodium per serving (exceeds 140mg)", sodiumPerServing))
                .regulation("FDA 21 CFR 101.61(b)(4)")
                .build();
    }

    // FDA: High Protein = ≥10g per serving OR ≥20% DV
    private ClaimValidation validateHighProtein(PackagedProduct product, String claim) {
        if (product.getProteinPer100g() == null || product.getServingSizeG() == null) {
            return ClaimValidation.builder().claim(claim).isValid(null).build();
        }

        double proteinPerServing = (product.getProteinPer100g() / 100.0) * product.getServingSizeG();
        boolean isValid = proteinPerServing >= 10.0;

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .actualValue(proteinPerServing)
                .requiredValue(10.0)
                .message(isValid
                    ? String.format("✓ Valid: %.1fg protein per serving (≥10g)", proteinPerServing)
                    : String.format("✗ Invalid: %.1fg protein per serving (must be ≥10g)", proteinPerServing))
                .regulation("FDA 21 CFR 101.54(b)")
                .build();
    }

    // FDA: Good Source of Fiber = 2.5-4.9g per serving
    private ClaimValidation validateGoodSourceFiber(PackagedProduct product, String claim) {
        if (product.getFiberPer100g() == null || product.getServingSizeG() == null) {
            return ClaimValidation.builder().claim(claim).isValid(null).build();
        }

        double fiberPerServing = (product.getFiberPer100g() / 100.0) * product.getServingSizeG();
        boolean isValid = fiberPerServing >= 2.5 && fiberPerServing <= 4.9;

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .actualValue(fiberPerServing)
                .requiredValue(2.5)
                .message(isValid
                    ? String.format("✓ Valid: %.1fg fiber per serving (2.5-4.9g)", fiberPerServing)
                    : String.format("✗ Invalid: %.1fg fiber per serving (must be 2.5-4.9g)", fiberPerServing))
                .regulation("FDA 21 CFR 101.54(b)")
                .build();
    }

    // Whole Grain: Check if whole grain is first ingredient
    private ClaimValidation validateWholeGrain(PackagedProduct product, String claim) {
        if (product.getIngredientsList() == null || product.getIngredientsList().length == 0) {
            return ClaimValidation.builder().claim(claim).isValid(null).build();
        }

        String firstIngredient = product.getIngredientsList()[0].toLowerCase();
        boolean isValid = firstIngredient.contains("whole") &&
                         (firstIngredient.contains("wheat") ||
                          firstIngredient.contains("grain") ||
                          firstIngredient.contains("oat"));

        return ClaimValidation.builder()
                .claim(claim)
                .isValid(isValid)
                .message(isValid
                    ? String.format("✓ Valid: First ingredient is '%s'", product.getIngredientsList()[0])
                    : String.format("✗ Invalid: First ingredient is '%s' (not whole grain)", product.getIngredientsList()[0]))
                .regulation("FDA Whole Grain Health Claim")
                .build();
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ClaimValidation {
        private String claim;
        private Boolean isValid;  // null = cannot validate
        private Double actualValue;
        private Double requiredValue;
        private String message;
        private String regulation;
    }
}
```

**2. Service: ServingSizeAnalyzer**

`ServingSizeAnalyzer.java`:
```java
@Service
@Slf4j
public class ServingSizeAnalyzer {

    /**
     * Detect serving size manipulation.
     * Compares manufacturer's serving size with realistic portion.
     */
    public ServingSizeAnalysis analyzeServingSize(PackagedProduct product) {

        if (product.getServingSizeG() == null || product.getServingsPerContainer() == null) {
            return ServingSizeAnalysis.builder()
                    .isSuspicious(false)
                    .message("Insufficient data for analysis")
                    .build();
        }

        double servingSizeG = product.getServingSizeG();
        double totalContainerG = servingSizeG * product.getServingsPerContainer();

        // Category-specific realistic portions
        String category = determineCategory(product);
        RealisticPortion realistic = getRealisticPortion(category);

        // Calculate realistic servings
        double realisticServings = totalContainerG / realistic.portionSizeG;

        // Flag as suspicious if manufacturer's servings are >50% more than realistic
        boolean isSuspicious = product.getServingsPerContainer() > (realisticServings * 1.5);

        // Calculate nutrition per realistic serving
        double caloriesPerRealistic = (product.getCaloriesPer100g() / 100.0) * realistic.portionSizeG;
        double caloriesPerLabel = (product.getCaloriesPer100g() / 100.0) * servingSizeG;

        return ServingSizeAnalysis.builder()
                .isSuspicious(isSuspicious)
                .labelServingSizeG(servingSizeG)
                .labelServingsPerContainer(product.getServingsPerContainer())
                .realisticServingSizeG(realistic.portionSizeG)
                .realisticServingsPerContainer(realisticServings)
                .caloriesPerLabelServing((int) caloriesPerLabel)
                .caloriesPerRealisticServing((int) caloriesPerRealistic)
                .message(isSuspicious
                    ? String.format("⚠️ Suspicious: Label claims %.0f servings, realistic is %.1f servings. " +
                                    "Realistic portion: %.0fg = %d cal (vs label: %.0fg = %d cal)",
                            product.getServingsPerContainer(), realisticServings,
                            realistic.portionSizeG, (int) caloriesPerRealistic,
                            servingSizeG, (int) caloriesPerLabel)
                    : "Serving size appears reasonable")
                .build();
    }

    private String determineCategory(PackagedProduct product) {
        String name = product.getProductName().toLowerCase();

        if (name.contains("ice cream") || name.contains("gelato")) return "ice_cream";
        if (name.contains("chip") || name.contains("crisp")) return "chips";
        if (name.contains("cereal")) return "cereal";
        if (name.contains("cookie") || name.contains("biscuit")) return "cookies";
        if (name.contains("soda") || name.contains("soft drink")) return "soda";
        if (name.contains("juice")) return "juice";
        if (name.contains("yogurt")) return "yogurt";
        if (name.contains("protein bar") || name.contains("energy bar")) return "bar";

        return "general";
    }

    private RealisticPortion getRealisticPortion(String category) {
        // Based on nutritional guidelines and actual consumption patterns
        return switch (category) {
            case "ice_cream" -> new RealisticPortion(150.0, "1 cup");  // Not 1/2 cup!
            case "chips" -> new RealisticPortion(60.0, "2 oz");
            case "cereal" -> new RealisticPortion(55.0, "1 cup");
            case "cookies" -> new RealisticPortion(40.0, "2 cookies");
            case "soda" -> new RealisticPortion(355.0, "12 fl oz can");
            case "juice" -> new RealisticPortion(240.0, "1 cup");
            case "yogurt" -> new RealisticPortion(170.0, "6 oz");
            case "bar" -> new RealisticPortion(60.0, "1 bar");
            default -> new RealisticPortion(100.0, "100g");
        };
    }

    @Data
    @AllArgsConstructor
    private static class RealisticPortion {
        private double portionSizeG;
        private String description;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ServingSizeAnalysis {
        private boolean isSuspicious;
        private Double labelServingSizeG;
        private Double labelServingsPerContainer;
        private Double realisticServingSizeG;
        private Double realisticServingsPerContainer;
        private Integer caloriesPerLabelServing;
        private Integer caloriesPerRealisticServing;
        private String message;
    }
}
```

**3. Service: IngredientAnalyzer**

`IngredientAnalyzer.java`:
```java
@Service
@Slf4j
public class IngredientAnalyzer {

    /**
     * Analyze ingredients list for concerning additives and patterns.
     */
    public IngredientAnalysis analyzeIngredients(PackagedProduct product) {

        if (product.getIngredientsList() == null || product.getIngredientsList().length == 0) {
            return IngredientAnalysis.builder()
                    .totalIngredients(0)
                    .build();
        }

        List<String> ingredients = Arrays.asList(product.getIngredientsList());

        // Detect ultra-processed indicators
        List<String> concerns = new ArrayList<>();
        List<String> additives = new ArrayList<>();

        for (String ingredient : ingredients) {
            String lower = ingredient.toLowerCase();

            // E-numbers (EU food additives)
            if (lower.matches(".*e\\d{3}.*")) {
                additives.add(ingredient);
                concerns.add("Contains E-number: " + ingredient);
            }

            // Common concerning additives
            if (lower.contains("artificial color") || lower.contains("artificial flavour")) {
                concerns.add("Contains artificial additives: " + ingredient);
            }

            if (lower.contains("high fructose corn syrup")) {
                concerns.add("Contains high fructose corn syrup");
            }

            if (lower.contains("partially hydrogenated")) {
                concerns.add("⚠️ Contains trans fats: " + ingredient);
            }

            if (lower.contains("monosodium glutamate") || lower.contains("msg")) {
                concerns.add("Contains MSG (E621)");
            }
        }

        // Check sugar position
        int sugarPosition = findIngredientPosition(ingredients, List.of("sugar", "glucose", "fructose", "syrup"));

        if (sugarPosition >= 0 && sugarPosition < 3) {
            concerns.add(String.format("⚠️ Sugar is ingredient #%d (high sugar content)", sugarPosition + 1));
        }

        // Ultra-processed indicators (>5 ingredients is common threshold)
        boolean isLikelyUltraProcessed = ingredients.size() > 5 &&
                                        (!additives.isEmpty() || !concerns.isEmpty());

        return IngredientAnalysis.builder()
                .totalIngredients(ingredients.size())
                .ingredientsList(ingredients)
                .concerns(concerns)
                .additives(additives)
                .sugarPosition(sugarPosition)
                .isLikelyUltraProcessed(isLikelyUltraProcessed)
                .novaScore(product.getNovaScore())
                .build();
    }

    private int findIngredientPosition(List<String> ingredients, List<String> searchTerms) {
        for (int i = 0; i < ingredients.size(); i++) {
            String ingredient = ingredients.get(i).toLowerCase();
            for (String term : searchTerms) {
                if (ingredient.contains(term)) {
                    return i;
                }
            }
        }
        return -1;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class IngredientAnalysis {
        private int totalIngredients;
        private List<String> ingredientsList;
        private List<String> concerns;
        private List<String> additives;
        private Integer sugarPosition;  // -1 if not found
        private boolean isLikelyUltraProcessed;
        private Integer novaScore;
    }
}
```

**4. Transparency Report Endpoint**

Add to `PackagedFoodController.java`:
```java
/**
 * Get complete transparency report for a packaged product.
 */
@GetMapping("/{productId}/transparency-report")
@Operation(summary = "Get transparency report",
           description = "Complete analysis: claims validation, serving size, ingredients")
public ResponseEntity<TransparencyReport> getTransparencyReport(
        @PathVariable UUID productId,
        Authentication authentication) {

    PackagedProduct product = packagedFoodService.getProductById(productId);

    // 1. Validate marketing claims
    List<String> claimsOnPackage = extractClaimsFromProduct(product);
    List<MarketingClaimsValidator.ClaimValidation> claimValidations =
            marketingClaimsValidator.validateClaims(product, claimsOnPackage);

    // 2. Analyze serving size
    ServingSizeAnalyzer.ServingSizeAnalysis servingSizeAnalysis =
            servingSizeAnalyzer.analyzeServingSize(product);

    // 3. Analyze ingredients
    IngredientAnalyzer.IngredientAnalysis ingredientAnalysis =
            ingredientAnalyzer.analyzeIngredients(product);

    // 4. Get discrepancy report (if available)
    DiscrepancyReport discrepancyReport =
            validationService.getLatestDiscrepancyReport(productId);

    TransparencyReport report = TransparencyReport.builder()
            .product(PackagedProductResponse.fromEntity(product))
            .claimValidations(claimValidations)
            .servingSizeAnalysis(servingSizeAnalysis)
            .ingredientAnalysis(ingredientAnalysis)
            .discrepancyReport(discrepancyReport)
            .overallScore(calculateTransparencyScore(
                    claimValidations, servingSizeAnalysis, ingredientAnalysis))
            .build();

    return ResponseEntity.ok(report);
}

private List<String> extractClaimsFromProduct(PackagedProduct product) {
    // Extract from product name, health notes, etc.
    // For now, return empty list (claims should come from OCR in Phase 4B)
    return Collections.emptyList();
}

private Integer calculateTransparencyScore(
        List<MarketingClaimsValidator.ClaimValidation> claims,
        ServingSizeAnalyzer.ServingSizeAnalysis servingSize,
        IngredientAnalyzer.IngredientAnalysis ingredients) {

    int score = 100;

    // Deduct for invalid claims
    long invalidClaims = claims.stream().filter(c -> Boolean.FALSE.equals(c.getIsValid())).count();
    score -= (int) (invalidClaims * 15);

    // Deduct for suspicious serving size
    if (servingSize.isSuspicious()) {
        score -= 20;
    }

    // Deduct for concerning ingredients
    score -= ingredients.getConcerns().size() * 5;

    // Deduct for ultra-processed
    if (ingredients.isLikelyUltraProcessed()) {
        score -= 10;
    }

    return Math.max(0, score);
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public static class TransparencyReport {
    private PackagedProductResponse product;
    private List<MarketingClaimsValidator.ClaimValidation> claimValidations;
    private ServingSizeAnalyzer.ServingSizeAnalysis servingSizeAnalysis;
    private IngredientAnalyzer.IngredientAnalysis ingredientAnalysis;
    private DiscrepancyReport discrepancyReport;
    private Integer overallScore;  // 0-100
}
```

#### Flutter Development (Complete Phase 4C)

**1. Barcode Scanner Screen**

```dart
class PackagedFoodScannerScreen extends StatefulWidget {
  @override
  _PackagedFoodScannerScreenState createState() => _PackagedFoodScannerScreenState();
}

class _PackagedFoodScannerScreenState extends State<PackagedFoodScannerScreen> {
  final BarcodeScanner _barcodeScanner = BarcodeScanner();
  bool _isScanning = false;
  PackagedFoodScanResult? _scanResult;

  Future<void> _scanBarcode() async {
    setState(() => _isScanning = true);

    // Use google_ml_kit for barcode scanning
    final barcode = await _barcodeScanner.scan();

    if (barcode != null) {
      // Lookup product
      final response = await http.get(
        Uri.parse('$baseUrl/api/packaged-foods/barcode/$barcode'),
        headers: {'Authorization': 'Bearer $token'},
      );

      if (response.statusCode == 200) {
        setState(() {
          _scanResult = PackagedFoodScanResult.fromJson(jsonDecode(response.body));
          _isScanning = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: Text('Scan Packaged Food')),
      body: _isScanning
          ? Center(child: CircularProgressIndicator())
          : _scanResult != null
              ? _buildScanResult()
              : _buildScanPrompt(),
      floatingActionButton: FloatingActionButton(
        onPressed: _scanBarcode,
        child: Icon(Icons.qr_code_scanner),
      ),
    );
  }

  Widget _buildScanResult() {
    return SingleChildScrollView(
      padding: EdgeInsets.all(16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          // Product info
          _buildProductCard(),
          SizedBox(height: 16),

          // Transparency score
          _buildTransparencyScore(),
          SizedBox(height: 16),

          // Claims validation
          _buildClaimsValidation(),
          SizedBox(height: 16),

          // Serving size warning
          if (_scanResult!.servingSizeAnalysis.isSuspicious)
            _buildServingSizeWarning(),

          // Ingredient concerns
          if (_scanResult!.ingredientAnalysis.concerns.isNotEmpty)
            _buildIngredientConcerns(),

          // Discrepancy report
          if (_scanResult!.discrepancyReport?.hasDiscrepancies ?? false)
            _buildDiscrepancyReport(),
        ],
      ),
    );
  }

  Widget _buildTransparencyScore() {
    final score = _scanResult!.overallScore;
    Color scoreColor = score >= 80 ? Colors.green : score >= 60 ? Colors.orange : Colors.red;

    return Card(
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Row(
          children: [
            CircularProgressIndicator(
              value: score / 100,
              backgroundColor: Colors.grey[300],
              valueColor: AlwaysStoppedAnimation(scoreColor),
            ),
            SizedBox(width: 16),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Transparency Score', style: Theme.of(context).textTheme.titleMedium),
                  Text('$score/100', style: TextStyle(fontSize: 24, color: scoreColor, fontWeight: FontWeight.bold)),
                ],
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildClaimsValidation() {
    return Card(
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Marketing Claims', style: Theme.of(context).textTheme.titleMedium),
            SizedBox(height: 8),
            ..._scanResult!.claimValidations.map((claim) => ListTile(
              leading: Icon(
                claim.isValid ? Icons.check_circle : Icons.error,
                color: claim.isValid ? Colors.green : Colors.red,
              ),
              title: Text(claim.claim),
              subtitle: Text(claim.message),
            )),
          ],
        ),
      ),
    );
  }

  Widget _buildServingSizeWarning() {
    final analysis = _scanResult!.servingSizeAnalysis;

    return Card(
      color: Colors.orange[50],
      child: Padding(
        padding: EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Icon(Icons.warning, color: Colors.orange),
                SizedBox(width: 8),
                Text('Serving Size Warning', style: TextStyle(fontWeight: FontWeight.bold)),
              ],
            ),
            SizedBox(height: 8),
            Text(analysis.message),
            SizedBox(height: 8),
            Row(
              children: [
                Expanded(
                  child: Column(
                    children: [
                      Text('Label Serving', style: TextStyle(fontSize: 12)),
                      Text('${analysis.labelServingSizeG}g = ${analysis.caloriesPerLabelServing} cal',
                           style: TextStyle(fontWeight: FontWeight.bold)),
                    ],
                  ),
                ),
                Expanded(
                  child: Column(
                    children: [
                      Text('Realistic Serving', style: TextStyle(fontSize: 12)),
                      Text('${analysis.realisticServingSizeG}g = ${analysis.caloriesPerRealisticServing} cal',
                           style: TextStyle(fontWeight: FontWeight.bold, color: Colors.orange)),
                    ],
                  ),
                ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}
```

#### Testing Checklist for Phase 4C

- [ ] Marketing claims validator correctly identifies valid/invalid claims
- [ ] Serving size analyzer flags suspicious portions (e.g., ice cream 1/2 cup vs 1 cup)
- [ ] Ingredient analyzer detects E-numbers and artificial additives
- [ ] Ingredient analyzer identifies sugar position in ingredients list
- [ ] Transparency score calculated correctly (0-100)
- [ ] GET /api/packaged-foods/{id}/transparency-report returns complete report
- [ ] Flutter UI displays transparency score with color coding
- [ ] Claims validation shown with ✓/✗ indicators
- [ ] Serving size warning displayed when suspicious
- [ ] Ingredient concerns highlighted in UI

---

## API Integrations

### OpenFoodFacts API
- **Base URL**: `https://world.openfoodfacts.org/api/v2`
- **Endpoint**: `/product/{barcode}.json`
- **Authentication**: None required (free, open API)
- **Rate Limiting**: None officially, but be respectful
- **Data Format**: JSON
- **Coverage**: 2.3M+ products globally
- **Cost**: **FREE**

**Example Request:**
```bash
curl https://world.openfoodfacts.org/api/v2/product/3017620422003.json
```

**Example Response Fields:**
```json
{
  "status": 1,
  "product": {
    "product_name": "Nutella",
    "brands": "Ferrero",
    "nutriments": {
      "energy-kcal_100g": 539,
      "proteins_100g": 6.3,
      "fat_100g": 30.9,
      "carbohydrates_100g": 57.5,
      "sugars_100g": 56.3,
      "fiber_100g": null,
      "sodium_100g": 0.107
    },
    "ingredients_text": "Sugar, Palm Oil, Hazelnuts (13%), Skimmed Milk Powder (8.7%), Fat-Reduced Cocoa (7.4%), Emulsifier: Lecithins (Soya), Vanillin",
    "allergens_tags": ["en:milk", "en:nuts", "en:soybeans"],
    "nova_group": 4,
    "serving_size": "15g"
  }
}
```

### USDA FoodData Central API
- **Base URL**: `https://api.nal.usda.gov/fdc/v1`
- **Endpoint**: `/foods/search`
- **Authentication**: API key required (free for non-commercial)
- **Rate Limiting**: 1000 requests/hour
- **Data Format**: JSON
- **Coverage**: 400K+ foods (US-focused, includes branded foods)
- **Cost**: **FREE** (requires API key from [fdc.nal.usda.gov](https://fdc.nal.usda.gov/api-key-signup.html))

**Example Request:**
```bash
curl "https://api.nal.usda.gov/fdc/v1/foods/search?query=051500255223&api_key=YOUR_API_KEY&dataType=Branded"
```

**Configuration:**
```properties
# application.properties
usda.api.key=${USDA_API_KEY:your_api_key_here}
```

---

## Database Schema

### Complete Schema for Phase 4

```sql
-- Phase 4A
CREATE TABLE packaged_products (
    id UUID PRIMARY KEY,
    barcode VARCHAR(50) UNIQUE NOT NULL,
    product_name TEXT NOT NULL,
    brand TEXT,
    product_name_normalized TEXT,

    -- Nutrition per 100g
    calories_per_100g INTEGER,
    protein_per_100g DOUBLE PRECISION,
    fat_per_100g DOUBLE PRECISION,
    saturated_fat_per_100g DOUBLE PRECISION,
    carbohydrates_per_100g DOUBLE PRECISION,
    fiber_per_100g DOUBLE PRECISION,
    sugar_per_100g DOUBLE PRECISION,
    sodium_per_100mg DOUBLE PRECISION,
    cholesterol_per_100mg DOUBLE PRECISION,

    -- Serving info
    serving_size_g DOUBLE PRECISION,
    serving_size_text VARCHAR(100),
    servings_per_container DOUBLE PRECISION,

    -- Ingredients & allergens
    ingredients_list TEXT[],
    allergens TEXT[],
    additives TEXT[],

    -- Classification
    nova_score INTEGER CHECK (nova_score BETWEEN 1 AND 4),
    is_organic BOOLEAN DEFAULT false,
    is_gluten_free BOOLEAN DEFAULT false,
    is_vegan BOOLEAN DEFAULT false,
    is_vegetarian BOOLEAN DEFAULT false,

    -- Data source
    data_source VARCHAR(50) NOT NULL,
    external_id VARCHAR(100),
    last_synced_at TIMESTAMP,

    -- Metadata
    image_url TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE packaged_product_meals (
    id UUID PRIMARY KEY,
    meal_id UUID NOT NULL REFERENCES meals(id) ON DELETE CASCADE,
    packaged_product_id UUID NOT NULL REFERENCES packaged_products(id) ON DELETE CASCADE,
    quantity_consumed DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(meal_id, packaged_product_id)
);

-- Phase 4B
CREATE TABLE product_validations (
    id UUID PRIMARY KEY,
    packaged_product_id UUID NOT NULL REFERENCES packaged_products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    database_source VARCHAR(50),
    ocr_source VARCHAR(50) DEFAULT 'ocr',
    has_discrepancies BOOLEAN DEFAULT false,
    discrepancy_count INTEGER DEFAULT 0,
    discrepancies JSONB,
    ocr_data JSONB,
    user_confirmed_source VARCHAR(50),
    user_notes TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Phase 4C (optional)
CREATE TABLE user_product_corrections (
    id UUID PRIMARY KEY,
    packaged_product_id UUID NOT NULL REFERENCES packaged_products(id) ON DELETE CASCADE,
    user_id UUID NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    field_name VARCHAR(50),
    original_value DOUBLE PRECISION,
    corrected_value DOUBLE PRECISION,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

-- Indexes
CREATE INDEX idx_packaged_barcode ON packaged_products(barcode);
CREATE INDEX idx_packaged_brand ON packaged_products(brand);
CREATE INDEX idx_packaged_name ON packaged_products(product_name_normalized);
CREATE INDEX idx_packaged_source ON packaged_products(data_source);
CREATE INDEX idx_packaged_meals_meal ON packaged_product_meals(meal_id);
CREATE INDEX idx_packaged_meals_product ON packaged_product_meals(packaged_product_id);
CREATE INDEX idx_validation_product ON product_validations(packaged_product_id);
CREATE INDEX idx_validation_user ON product_validations(user_id);
CREATE INDEX idx_validation_discrepancies ON product_validations(has_discrepancies);
```

---

## Success Metrics

### Phase 4A: Barcode Scanning
- **API Success Rate**: >90% barcode lookup success
- **Cache Hit Rate**: >70% requests served from local database
- **Performance**: <500ms average response time for barcode lookup
- **Coverage**: Test with 100+ popular products across categories

### Phase 4B: OCR Validation
- **OCR Accuracy**: >85% correct extraction of nutrition label fields
- **Discrepancy Detection**: Successfully identify >80% of known mismatches
- **User Trust**: >60% of users choose database over OCR when both available
- **False Positives**: <10% false discrepancy warnings

### Phase 4C: Transparency
- **Claim Validation**: >90% accuracy on FDA-regulated claims
- **Serving Size Detection**: Correctly flag >75% of manipulated portions
- **User Engagement**: >40% of users view transparency report
- **Transparency Score**: Average score distribution: 60-80 range (most products)

### Overall Phase 4
- **User Adoption**: 30% of users scan at least 1 packaged food per week
- **Database Growth**: 1000+ unique products cached within first month
- **User Retention**: Users who scan packaged foods have 15% higher retention
- **Accuracy Improvement**: Packaged food nutrition accuracy: >95% vs 70% for home-cooked AI estimation

---

## Cost Analysis

### API Costs
- **OpenFoodFacts**: FREE (unlimited)
- **USDA FoodData Central**: FREE (1000 req/hour limit)
- **OpenAI Vision** (OCR):
  - Cost: $0.01 per image (gpt-4o-mini with vision)
  - Estimated usage: 1000 scans/day = $10/day = $300/month
  - **Optimization**: Cache OCR results to avoid re-scanning same products

### Infrastructure Costs
- **Database Storage**: +50MB per 10K products = negligible
- **Compute**: No additional backend resources needed
- **Total Additional Cost**: ~$300/month (OCR only)

### Cost Optimization Strategies
1. **Cache Aggressively**: Store OCR results for 30 days
2. **Barcode-First**: Always try barcode lookup before OCR
3. **User Contributions**: Let users submit corrections to crowdsource data
4. **Batch Processing**: Queue non-urgent OCR requests for off-peak times

### ROI Justification
- **Premium Feature**: Packaged food scanning could justify $2-5/month premium tier
- **User Value**: Saves users ~5 min per packaged food vs manual entry
- **Transparency**: Unique differentiator - no competitor offers FDA claim validation
- **Break-even**: 100 premium users at $3/month = $300/month (covers OCR costs)

---

## Example Use Cases

### Use Case 1: Protein Bar Transparency
**User**: Scans Quest Nutrition Protein Bar

**System Response:**
```
Product: Quest Protein Bar - Chocolate Chip Cookie Dough
Brand: Quest Nutrition
Barcode: 888849000067

✓ Database: Found in OpenFoodFacts
✓ OCR: Extracted nutrition label

CLAIMS VALIDATION:
✓ "High Protein": Valid (20g per serving ≥ 10g) - FDA 21 CFR 101.54
✗ "Low Sugar": Invalid (4g per serving exceeds 3g limit) - FDA 21 CFR 101.60
✓ "High Fiber": Valid (14g per serving ≥ 5g)

SERVING SIZE: ✓ Reasonable (60g bar = 1 serving)

INGREDIENTS ANALYSIS:
⚠️ Contains 12 ingredients (processed)
⚠️ Contains sucralose (artificial sweetener)
Nova Score: 4 (Ultra-processed)

TRANSPARENCY SCORE: 72/100
- Valid claims: +30
- Invalid claim (low sugar): -15
- Ultra-processed: -10
- Artificial sweetener: -5

DISCREPANCIES: None (database matches label)
```

### Use Case 2: Ice Cream Serving Size Manipulation
**User**: Scans Ben & Jerry's Pint

**System Response:**
```
Product: Ben & Jerry's Cherry Garcia Ice Cream
Barcode: 076840101306

⚠️ SERVING SIZE WARNING:
Label claims: 4 servings per pint (1/2 cup = 130 cal)
Realistic portion: 2 servings per pint (1 cup = 260 cal)

The manufacturer uses a 1/2 cup serving size to make calories appear lower.
Most people eat 1 cup per sitting.

REALISTIC NUTRITION (1 cup):
- Calories: 260 (vs label: 130)
- Fat: 14g (vs label: 7g)
- Sugar: 28g (vs label: 14g)

TRANSPARENCY SCORE: 65/100
- Suspicious serving size: -20
- High sugar content: -15
```

### Use Case 3: Whole Grain Claim Validation
**User**: Scans "Whole Grain" Bread

**System Response:**
```
Product: Nature's Own Whole Wheat Bread
Barcode: 072250020206

CLAIMS VALIDATION:
✗ "Made with Whole Grains": MISLEADING
First ingredient: Enriched wheat flour (NOT whole grain)
Second ingredient: Whole wheat flour

FDA Regulation: "Whole grain" claim requires whole grain as PRIMARY ingredient.

INGREDIENTS ANALYSIS:
1. Enriched wheat flour (refined)
2. Whole wheat flour
3. Water
4. Sugar
⚠️ Sugar is #4 ingredient

TRANSPARENCY SCORE: 45/100
- Invalid whole grain claim: -25
- Refined flour primary: -20
- Misleading marketing: -10
```

---

## Timeline Summary

| Phase | Duration | Key Deliverables | Dependencies |
|-------|----------|------------------|--------------|
| **4A: Barcode & Database** | Week 1-2 | V16 migration, OpenFoodFacts client, USDA client, barcode lookup API | None |
| **4B: OCR Validation** | Week 3-4 | V17 migration, OCR extraction, discrepancy detection, comparison API | Phase 4A |
| **4C: Transparency** | Week 5-6 | Claims validator, serving size analyzer, ingredient analyzer, transparency report | Phase 4B |

**Total Duration**: 6 weeks

**After Phase 4**: System is production-ready for packaged food scanning with full transparency features.

---

## Next Steps After Phase 4

### Phase 5: Hybrid Meals (Optional)
Combine packaged foods + home-cooked ingredients in a single meal:
- Example: "Brown rice (home-cooked) + Trader Joe's Simmer Sauce (packaged)"
- Meal = sum of ingredient library (Phase 3) + packaged product (Phase 4)

### Phase 6: Restaurant Menu Scanning (Optional)
Apply similar OCR approach to restaurant menus:
- Extract menu items and prices
- Link to known nutrition databases (Nutritionix has many restaurants)
- Build personal restaurant database

### Phase 7: Community Database
Allow users to contribute corrections and new products:
- Submit to OpenFoodFacts automatically
- Build NutriLens community database for products not in OpenFoodFacts
- Gamification: Points for contributing accurate data

---

## Conclusion

This hybrid approach (barcode lookup + OCR validation + transparency analysis) provides:

1. **Accuracy**: Manufacturer-declared data is legally regulated
2. **Transparency**: Expose misleading claims and serving size tricks
3. **Education**: Teach users about FDA regulations and nutrition literacy
4. **Trust**: Show discrepancies between label and database
5. **Speed**: Instant barcode lookup, OCR fallback
6. **Cost-Effective**: Free APIs + affordable OCR
7. **Unique Value**: No competitor offers FDA claim validation

**Total Investment**: 6 weeks development, ~$300/month operating cost, potential premium feature revenue.

This makes NutriLens the most transparent nutrition tracking app on the market. 🎯
