package com.nutritheous.analyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.common.exception.AnalyzerException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.util.*;

/**
 * Service for analyzing food images using OpenAI's Vision API.
 * Uses direct HTTP calls to support the vision multi-content message format.
 */
@Service
@Slf4j
public class OpenAIVisionService {

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;
    private final String apiUrl;
    private final String apiKey;
    private final String model;
    private final int maxTokens;

    public OpenAIVisionService(
            RestTemplate restTemplate,
            @Value("${openai.api.key}") String apiKey,
            @Value("${openai.api.url:https://api.openai.com/v1/chat/completions}") String apiUrl,
            @Value("${openai.api.model:gpt-4o-mini}") String model,
            @Value("${openai.api.max-tokens:800}") int maxTokens) {

        this.restTemplate = restTemplate;
        this.objectMapper = new ObjectMapper();
        this.apiUrl = apiUrl;
        this.apiKey = apiKey;
        this.model = model;
        this.maxTokens = maxTokens;

        log.info("Vision Service initialized - URL: {}, Model: {}, Max Tokens: {}",
                apiUrl, model, maxTokens);
    }

    /**
     * Analyzes a food image and returns nutritional information.
     *
     * @param imageDataUri Base64 encoded image with data URI prefix (e.g., "data:image/jpeg;base64,...")
     * @param userDescription Optional user-provided description to help with analysis
     * @param locationContext Optional location context from GPS + Google Maps
     * @param mealTime Optional meal time for time-based context
     * @return AnalysisResponse with nutritional information
     * @throws AnalyzerException If analysis fails
     */
    public AnalysisResponse analyzeImage(
            String imageDataUri,
            String userDescription,
            com.nutritheous.common.dto.LocationContext locationContext,
            java.time.LocalDateTime mealTime
    ) throws AnalyzerException {
        log.info("Starting OpenAI Vision analysis with user description: {}, location: {}, time: {}",
                userDescription,
                locationContext != null ? locationContext.getPlaceName() : "unknown",
                mealTime);

        try {
            // Build the request payload with multi-content format
            Map<String, Object> imageUrlContent = Map.of(
                "type", "image_url",
                "image_url", Map.of("url", imageDataUri)
            );

            Map<String, Object> textContent = Map.of(
                "type", "text",
                "text", getAnalysisPrompt(userDescription, locationContext, mealTime)
            );

            Map<String, Object> message = Map.of(
                "role", "user",
                "content", Arrays.asList(textContent, imageUrlContent)
            );

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", Arrays.asList(message)
            );

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Create HTTP entity
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make the API call
            log.debug("Calling Vision API: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AnalyzerException("OpenAI API returned error: " + response.getStatusCode());
            }

            // Parse the response
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            if (!responseJson.has("choices") || responseJson.get("choices").isEmpty()) {
                throw new AnalyzerException("No response from OpenAI API");
            }

            // Extract the content
            String content = responseJson.get("choices").get(0)
                    .get("message")
                    .get("content")
                    .asText();

            log.info("Received OpenAI response");
            log.debug("Response content: {}", content);

            // Parse and return the analysis
            return parseResponse(content);

        } catch (AnalyzerException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI Vision analysis failed", e);
            throw new AnalyzerException("OpenAI analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Parses the OpenAI response and converts it to AnalysisResponse.
     */
    private AnalysisResponse parseResponse(String content) throws AnalyzerException {
        try {
            // Clean up the response (remove markdown code blocks if present)
            String cleanedContent = cleanJsonResponse(content);
            log.debug("Cleaned response: {}", cleanedContent);

            // Store raw content for ingredient extraction
            String rawAiResponse = cleanedContent;

            // Parse JSON
            JsonNode jsonNode = objectMapper.readTree(cleanedContent);

            // Check for error response
            if (jsonNode.has("error")) {
                throw new AnalyzerException("AI returned error: " + jsonNode.get("error").asText());
            }

            // Map to AnalysisResponse
            AnalysisResponse response = AnalysisResponse.builder()
                    .servingSize(getStringValue(jsonNode, "serving_size"))
                    .calories(getIntValue(jsonNode, "calories"))
                    .proteinG(getDoubleValue(jsonNode, "protein_g"))
                    .fatG(getDoubleValue(jsonNode, "fat_g"))
                    .saturatedFatG(getDoubleValue(jsonNode, "saturated_fat_g"))
                    .carbohydratesG(getDoubleValue(jsonNode, "carbohydrates_g"))
                    .fiberG(getDoubleValue(jsonNode, "fiber_g"))
                    .sugarG(getDoubleValue(jsonNode, "sugar_g"))
                    .sodiumMg(getDoubleValue(jsonNode, "sodium_mg"))
                    .cholesterolMg(getDoubleValue(jsonNode, "cholesterol_mg"))
                    .ingredients(getStringList(jsonNode, "ingredients"))
                    .allergens(getStringList(jsonNode, "allergens"))
                    .healthNotes(getStringValue(jsonNode, "health_notes"))
                    .confidence(getDoubleValue(jsonNode, "confidence"))
                    // Enhanced AI-extracted fields
                    .cookingMethod(getStringValue(jsonNode, "cooking_method"))
                    .novaScore(getDoubleValue(jsonNode, "nova_score"))
                    .isUltraProcessed(getBooleanValue(jsonNode, "is_ultra_processed"))
                    .isFried(getBooleanValue(jsonNode, "is_fried"))
                    .hasRefinedGrains(getBooleanValue(jsonNode, "has_refined_grains"))
                    .estimatedGi(getIntValue(jsonNode, "estimated_gi"))
                    .estimatedGl(getIntValue(jsonNode, "estimated_gl"))
                    .plantCount(getIntValue(jsonNode, "plant_count"))
                    .uniquePlants(getStringList(jsonNode, "unique_plants"))
                    .isFermented(getBooleanValue(jsonNode, "is_fermented"))
                    .proteinSourceType(getStringValue(jsonNode, "protein_source_type"))
                    .fatQuality(getStringValue(jsonNode, "fat_quality"))
                    .mealTypeGuess(getStringValue(jsonNode, "meal_type_guess"))
                    .rawAiResponse(rawAiResponse)  // Store raw JSON for ingredient extraction
                    .build();

            log.info("Successfully parsed nutrition response");
            return response;

        } catch (IOException e) {
            log.error("Failed to parse AI response", e);
            throw new AnalyzerException("Failed to parse AI response: " + e.getMessage(), e);
        }
    }

    /**
     * Cleans JSON response by removing markdown code blocks.
     */
    private String cleanJsonResponse(String content) {
        if (content == null) {
            return "";
        }

        content = content.trim();

        // Remove markdown code blocks
        if (content.startsWith("```json")) {
            content = content.substring(7);
        } else if (content.startsWith("```")) {
            content = content.substring(3);
        }

        if (content.endsWith("```")) {
            content = content.substring(0, content.length() - 3);
        }

        return content.trim();
    }

    /**
     * Helper method to safely extract string values from JSON.
     */
    private String getStringValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asText() : null;
    }

    /**
     * Helper method to safely extract integer values from JSON.
     */
    private Integer getIntValue(JsonNode node, String fieldName) {
        return node.has(fieldName) ? node.get(fieldName).asInt() : null;
    }

    /**
     * Helper method to safely extract double values from JSON.
     */
    private Double getDoubleValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return null;
        }

        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isNull()) {
            return null;
        }

        return fieldNode.asDouble();
    }

    /**
     * Helper method to safely extract boolean values from JSON.
     */
    private Boolean getBooleanValue(JsonNode node, String fieldName) {
        if (!node.has(fieldName)) {
            return null;
        }

        JsonNode fieldNode = node.get(fieldName);
        if (fieldNode.isNull()) {
            return null;
        }

        return fieldNode.asBoolean();
    }

    /**
     * Helper method to safely extract string lists from JSON.
     */
    private List<String> getStringList(JsonNode node, String fieldName) {
        if (!node.has(fieldName) || !node.get(fieldName).isArray()) {
            return new ArrayList<>();
        }

        List<String> list = new ArrayList<>();
        for (JsonNode item : node.get(fieldName)) {
            list.add(item.asText());
        }
        return list;
    }

    /**
     * Analyzes a text description only (no image) and returns nutritional information.
     *
     * @param description User-provided description of the meal
     * @param locationContext Optional location context from GPS + Google Maps
     * @param mealTime Optional meal time for time-based context
     * @return AnalysisResponse with nutritional information
     * @throws AnalyzerException If analysis fails
     */
    public AnalysisResponse analyzeTextOnly(
            String description,
            com.nutritheous.common.dto.LocationContext locationContext,
            java.time.LocalDateTime mealTime
    ) throws AnalyzerException {
        log.info("Starting OpenAI text-only analysis for: {}, location: {}, time: {}",
                description,
                locationContext != null ? locationContext.getPlaceName() : "unknown",
                mealTime);

        try {
            // Build the request payload with text-only format
            Map<String, Object> message = Map.of(
                "role", "user",
                "content", getTextOnlyPrompt(description, locationContext, mealTime)
            );

            Map<String, Object> requestBody = Map.of(
                "model", model,
                "max_tokens", maxTokens,
                "messages", Arrays.asList(message)
            );

            // Set up headers
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            headers.setBearerAuth(apiKey);

            // Create HTTP entity
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            // Make the API call
            log.debug("Calling Vision API for text-only analysis: {}", apiUrl);
            ResponseEntity<String> response = restTemplate.exchange(
                    apiUrl,
                    HttpMethod.POST,
                    entity,
                    String.class
            );

            if (!response.getStatusCode().is2xxSuccessful()) {
                throw new AnalyzerException("OpenAI API returned error: " + response.getStatusCode());
            }

            // Parse the response
            JsonNode responseJson = objectMapper.readTree(response.getBody());

            if (!responseJson.has("choices") || responseJson.get("choices").isEmpty()) {
                throw new AnalyzerException("No response from OpenAI API");
            }

            // Extract the content
            String content = responseJson.get("choices").get(0)
                    .get("message")
                    .get("content")
                    .asText();

            log.info("Received OpenAI text-only response");
            log.debug("Response content: {}", content);

            // Parse and return the analysis
            return parseResponse(content);

        } catch (AnalyzerException e) {
            throw e;
        } catch (Exception e) {
            log.error("OpenAI text-only analysis failed", e);
            throw new AnalyzerException("OpenAI text analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Returns the AI prompt for nutritional analysis.
     * Incorporates user description, location context, and time context if available.
     */
    private String getAnalysisPrompt(
            String userDescription,
            com.nutritheous.common.dto.LocationContext locationContext,
            java.time.LocalDateTime mealTime
    ) {
        String userContext = "";
        if (userDescription != null && !userDescription.isBlank()) {
            userContext = "\n\nUSER'S DESCRIPTION: \"" + userDescription + "\"\n" +
                         "Use this description to better understand the food. For example, if they mention 'with sugar' or 'black coffee', " +
                         "adjust your nutritional analysis accordingly. The user's description provides important context about preparation, " +
                         "ingredients, or portions that may not be visible in the image.\n";
        }

        String locationHint = buildLocationContext(locationContext);
        String timeContext = buildTimeContext(mealTime);

        return """
                You are a nutrition analysis expert. Analyze the food in this image and provide detailed nutritional information.
                """ + userContext + locationHint + timeContext + """

                CRITICAL: Return ONLY a valid JSON object. No markdown, no code blocks, no explanation - just pure JSON.

                Required JSON structure:
                {
                  "serving_size": "estimated serving size (e.g., '1 plate', '2 slices', '300g')",
                  "calories": 0,
                  "protein_g": 0.0,
                  "fat_g": 0.0,
                  "saturated_fat_g": 0.0,
                  "carbohydrates_g": 0.0,
                  "fiber_g": 0.0,
                  "sugar_g": 0.0,
                  "sodium_mg": 0.0,
                  "cholesterol_mg": 0.0,
                  "ingredients": ["main ingredient 1", "ingredient 2"],
                  "allergens": ["potential allergen 1", "allergen 2"],
                  "health_notes": "brief health insights (high protein, low carb, etc.)",
                  "confidence": 0.85,

                  "cooking_method": "grilled",
                  "nova_score": 1.5,
                  "is_ultra_processed": false,
                  "is_fried": false,
                  "has_refined_grains": false,
                  "estimated_gi": 45,
                  "estimated_gl": 13,
                  "plant_count": 2,
                  "unique_plants": ["broccoli", "garlic"],
                  "is_fermented": false,
                  "protein_source_type": "animal",
                  "fat_quality": "healthy",
                  "meal_type_guess": "dinner",

                  "ingredient_breakdown": [
                    {
                      "name": "ingredient name",
                      "category": "grain",
                      "quantity": 100.0,
                      "unit": "g",
                      "calories": 120,
                      "protein_g": 4.5,
                      "fat_g": 1.2,
                      "saturated_fat_g": 0.3,
                      "carbohydrates_g": 22.0,
                      "fiber_g": 2.5,
                      "sugar_g": 0.5,
                      "sodium_mg": 150.0
                    }
                  ]
                }

                Field definitions:
                - cooking_method: raw/steamed/boiled/grilled/baked/fried/roasted/pressure_cooked/sauteed (choose most prominent method)
                - nova_score: 1-4 decimal (1=unprocessed whole foods, 2=processed culinary ingredients, 3=processed foods, 4=ultra-processed)
                - is_ultra_processed: true if packaged snacks, soda, frozen meals, fast food, candy
                - is_fried: true if deep fried or pan fried in significant oil
                - has_refined_grains: true if contains white bread, white rice, regular pasta, or refined flour products
                - estimated_gi: glycemic index 0-100 (low <55, medium 55-69, high 70+). Consider cooking method and food form.
                - estimated_gl: glycemic load (low <10, medium 10-19, high 20+). GL = (GI × carbs) / 100
                - plant_count: number of unique plant species visible (tomato=1, onion=1, etc.)
                - unique_plants: list each plant by name (e.g., ["broccoli", "tomato", "basil", "rice"])
                - is_fermented: true if contains yogurt, kefir, kimchi, sauerkraut, tempeh, miso, sourdough, kombucha
                - protein_source_type: "animal" (meat/poultry/fish), "plant" (legumes/tofu), "dairy" (milk/cheese/yogurt), "seafood", "mixed", or "none"
                - fat_quality: "healthy" (olive oil, avocado, nuts, fish), "neutral" (butter, lean meat), "unhealthy" (trans fats, deep fried)
                - meal_type_guess: "breakfast", "lunch", "dinner", or "snack" based on food types and portion
                - ingredient_breakdown: CRITICAL - Break down meal into individual ingredients with quantities and nutrition per serving
                  * name: specific ingredient (e.g., "idli", "sambar", "ghee")
                  * category: grain/protein/vegetable/fat/dairy/spice/condiment/beverage
                  * quantity: estimated amount for this serving
                  * unit: g, ml, piece, tsp, tbsp, cup (prefer metric)
                  * nutrition: per THIS serving (not per 100g)
                  * For complex meals (South Indian, restaurant), identify each component
                  * Sum of ingredient nutrition should match overall meal nutrition
                  * Return empty array [] if breakdown not possible

                Rules:
                - All numeric fields must be numbers (not strings)
                - All boolean fields must be true or false (not strings)
                - Use 0 for unknown numeric values (never use null or omit fields)
                - For nova_score, consider: whole foods=1, oils/butter/salt/sugar=2, canned/processed=3, packaged/industrial=4
                - For GI/GL, use typical values: white bread~75, brown rice~50, lentils~30, vegetables~15-30
                - Count ALL distinct plants, including herbs and spices if visible
                - Be conservative with is_ultra_processed - only true for industrially manufactured foods

                If this is NOT a food image, return exactly:
                {"error": "Not a food item"}

                Remember: Return ONLY the JSON object, nothing else.
                If unsure about exact values, provide approximate estimates based on similar foods rather than leaving fields empty.
                """;
    }

    /**
     * Returns the AI prompt for text-only nutritional analysis.
     * Incorporates location context and time context if available.
     */
    private String getTextOnlyPrompt(
            String description,
            com.nutritheous.common.dto.LocationContext locationContext,
            java.time.LocalDateTime mealTime
    ) {
        String locationHint = buildLocationContext(locationContext);
        String timeContext = buildTimeContext(mealTime);

        return """
                You are a nutrition analysis expert. Based on the text description provided by the user, estimate the nutritional information for the meal.

                USER'S MEAL DESCRIPTION: "%s"
                """ + locationHint + timeContext + """

                Analyze this description and provide your best estimate of the nutritional content. Consider typical portion sizes and preparation methods.

                CRITICAL: Return ONLY a valid JSON object. No markdown, no code blocks, no explanation - just pure JSON.

                Required JSON structure:
                {
                  "serving_size": "estimated serving size (e.g., '1 plate', '2 slices', '300g')",
                  "calories": 0,
                  "protein_g": 0.0,
                  "fat_g": 0.0,
                  "saturated_fat_g": 0.0,
                  "carbohydrates_g": 0.0,
                  "fiber_g": 0.0,
                  "sugar_g": 0.0,
                  "sodium_mg": 0.0,
                  "cholesterol_mg": 0.0,
                  "ingredients": ["main ingredient 1", "ingredient 2"],
                  "allergens": ["potential allergen 1", "allergen 2"],
                  "health_notes": "brief health insights (high protein, low carb, etc.)",
                  "confidence": 0.65,

                  "cooking_method": "grilled",
                  "nova_score": 1.5,
                  "is_ultra_processed": false,
                  "is_fried": false,
                  "has_refined_grains": false,
                  "estimated_gi": 45,
                  "estimated_gl": 13,
                  "plant_count": 2,
                  "unique_plants": ["broccoli", "garlic"],
                  "is_fermented": false,
                  "protein_source_type": "animal",
                  "fat_quality": "healthy",
                  "meal_type_guess": "dinner",

                  "ingredient_breakdown": [
                    {
                      "name": "ingredient name",
                      "category": "grain",
                      "quantity": 100.0,
                      "unit": "g",
                      "calories": 120,
                      "protein_g": 4.5,
                      "fat_g": 1.2,
                      "saturated_fat_g": 0.3,
                      "carbohydrates_g": 22.0,
                      "fiber_g": 2.5,
                      "sugar_g": 0.5,
                      "sodium_mg": 150.0
                    }
                  ]
                }

                Field definitions:
                - cooking_method: infer from description (fried, grilled, baked, boiled, steamed, raw, etc.)
                - nova_score: 1-4 based on processing level described
                - is_ultra_processed: true if fast food, packaged snacks, soda mentioned
                - is_fried: true if description mentions frying or fried foods
                - has_refined_grains: true if white bread/rice/pasta mentioned
                - estimated_gi/gl: based on typical values for foods mentioned
                - plant_count: count unique plants mentioned
                - unique_plants: list plants by name
                - is_fermented: true if yogurt, kimchi, sourdough, etc. mentioned
                - protein_source_type: infer from description
                - fat_quality: infer from cooking method and ingredients
                - meal_type_guess: infer from description and typical meal patterns
                - ingredient_breakdown: Break down meal into individual ingredients based on description
                  * Estimate quantities and nutrition for each ingredient
                  * Return empty array [] if description too vague

                Rules:
                - All numeric fields must be numbers (not strings)
                - All boolean fields must be true or false (not strings)
                - Use 0 for unknown values (never use null or omit required fields)
                - confidence should be 0.5-0.7 for text-only estimates (lower than image analysis)

                Remember: Return ONLY the JSON object, nothing else.
                Provide reasonable estimates based on typical nutritional values for similar foods.
                """.formatted(description);
    }

    /**
     * Build location context hint for AI prompt.
     */
    private String buildLocationContext(com.nutritheous.common.dto.LocationContext loc) {
        if (loc == null || !loc.isKnown()) {
            return "";
        }

        StringBuilder context = new StringBuilder("\n\nLOCATION CONTEXT:\n");

        if (loc.isRestaurant()) {
            context.append(String.format("- Photo taken at: %s (%s)\n",
                    loc.getPlaceName(), loc.getPlaceType()));

            if (loc.getCuisineType() != null) {
                context.append(String.format("- Cuisine type: %s\n", loc.getCuisineType()));
            }

            if (loc.getPriceLevel() != null) {
                String priceIndicator = "$".repeat(loc.getPriceLevel());
                context.append(String.format("- Price level: %s\n", priceIndicator));
                context.append("- Adjust portion sizes and preparation for restaurant context\n");
                context.append("- Restaurant meals often have higher sodium and fat\n");
                context.append("- Consider typical restaurant serving sizes (often larger than home portions)\n");
            }
        } else if (loc.isHome()) {
            context.append("- Photo taken at home (likely home-cooked)\n");
            context.append("- Home-cooked meals typically less processed\n");
            context.append("- Consider typical home portion sizes\n");
        } else if (loc.getPlaceType().equals("cafe")) {
            context.append(String.format("- Photo taken at: %s (cafe)\n", loc.getPlaceName()));
            context.append("- Consider typical cafe portions and preparations\n");
        } else if (loc.getPlaceType().equals("gym")) {
            context.append("- Photo taken at gym (likely pre/post-workout meal)\n");
            context.append("- May indicate performance-focused nutrition\n");
        } else if (loc.getPlaceType().equals("grocery_store") || loc.getPlaceType().equals("convenience_store")) {
            context.append("- Photo taken at store (likely packaged food)\n");
            context.append("- Consider higher processing levels for store-bought items\n");
        }

        return context.toString();
    }

    /**
     * Build time context hint for AI prompt.
     */
    private String buildTimeContext(java.time.LocalDateTime mealTime) {
        if (mealTime == null) {
            return "";
        }

        int hour = mealTime.getHour();
        String timeOfDay;
        String typicalMeal;
        String portionGuidance;

        if (hour >= 5 && hour < 10) {
            timeOfDay = "early morning";
            typicalMeal = "breakfast";
            portionGuidance = "Consider typical breakfast portion sizes (often smaller than lunch/dinner)";
        } else if (hour >= 10 && hour < 12) {
            timeOfDay = "late morning";
            typicalMeal = "brunch";
            portionGuidance = "Consider brunch portion sizes (often larger than breakfast)";
        } else if (hour >= 12 && hour < 15) {
            timeOfDay = "midday";
            typicalMeal = "lunch";
            portionGuidance = "Consider typical lunch portion sizes";
        } else if (hour >= 15 && hour < 17) {
            timeOfDay = "afternoon";
            typicalMeal = "snack";
            portionGuidance = "Consider smaller snack-sized portions";
        } else if (hour >= 17 && hour < 21) {
            timeOfDay = "evening";
            typicalMeal = "dinner";
            portionGuidance = "Consider typical dinner portion sizes (often largest meal)";
        } else {
            timeOfDay = "late night";
            typicalMeal = "late snack";
            portionGuidance = "Consider smaller late-night snack portions";
        }

        return String.format("""

                TIME CONTEXT:
                - Photo taken at: %s (%s)
                - Typical meal for this time: %s
                - %s
                - Adjust portion estimates for time of day
                """,
                mealTime.format(java.time.format.DateTimeFormatter.ofPattern("h:mm a")),
                timeOfDay,
                typicalMeal,
                portionGuidance
        );
    }
}
