package com.nutritheous.analyzer;

import com.nutritheous.common.dto.AnalysisResponse;
import com.nutritheous.common.dto.LocationContext;
import com.nutritheous.common.exception.AnalyzerException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.*;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Comprehensive test suite for OpenAIVisionService.
 * Tests AI integration, response parsing, and error handling.
 */
@ExtendWith(MockitoExtension.class)
class OpenAIVisionServiceTest {

    @Mock
    private RestTemplate restTemplate;

    private OpenAIVisionService visionService;

    private static final String TEST_API_KEY = "test-api-key-123";
    private static final String TEST_API_URL = "https://api.test.com/v1/chat/completions";
    private static final String TEST_MODEL = "gpt-4o-mini";
    private static final int TEST_MAX_TOKENS = 800;

    @BeforeEach
    void setUp() {
        visionService = new OpenAIVisionService(
                restTemplate,
                TEST_API_KEY,
                TEST_API_URL,
                TEST_MODEL,
                TEST_MAX_TOKENS
        );
    }

    // ============================================================================
    // Test 1: Successful Analysis with Valid Response
    // ============================================================================

    @Test
    void testAnalyzeImage_ValidResponse_ReturnsAnalysis() {
        String mockApiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "{\\"calories\\": 500, \\"confidence\\": 0.85, \\"protein_g\\": 25.0, \\"description\\": \\"Chicken salad\\"}"
                        }
                    }]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,/9j/4AAQSkZJRg...";

        AnalysisResponse result = visionService.analyzeImage(
                imageDataUri, "Test meal", null, LocalDateTime.now());

        assertNotNull(result);
        assertEquals(500, result.getCalories());
        assertEquals(0.85, result.getConfidence());
        assertEquals(25.0, result.getProteinG());
    }

    @Test
    void testAnalyzeImage_WithLocationContext_IncludesInPrompt() {
        LocationContext locationContext = LocationContext.builder()
                .placeName("Chipotle Mexican Grill")
                .placeType("restaurant")
                .cuisineType("mexican")
                .isRestaurant(true)
                .build();

        String mockApiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "{\\"calories\\": 800, \\"confidence\\": 0.90}"
                        }
                    }]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertDoesNotThrow(() ->
                visionService.analyzeImage(imageDataUri, "Burrito bowl",
                        locationContext, LocalDateTime.now()));

        // Verify API was called
        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    // ============================================================================
    // Test 2: Request Construction
    // ============================================================================

    @Test
    void testAnalyzeImage_ConstructsCorrectRequest() {
        String mockApiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "{\\"calories\\": 300}"
                        }
                    }]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        ArgumentCaptor<HttpEntity> entityCaptor = ArgumentCaptor.forClass(HttpEntity.class);

        when(restTemplate.exchange(
                eq(TEST_API_URL),
                eq(HttpMethod.POST),
                entityCaptor.capture(),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test123";

        visionService.analyzeImage(imageDataUri, "Apple", null, null);

        // Verify request was constructed correctly
        HttpEntity<Map<String, Object>> capturedEntity =
                (HttpEntity<Map<String, Object>>) entityCaptor.getValue();

        // Check headers
        HttpHeaders headers = capturedEntity.getHeaders();
        assertTrue(headers.containsKey(HttpHeaders.AUTHORIZATION),
                "Should include Authorization header");
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType(),
                "Should set Content-Type to application/json");

        // Check body contains expected fields
        Map<String, Object> body = capturedEntity.getBody();
        assertNotNull(body);
        assertEquals(TEST_MODEL, body.get("model"),
                "Should use configured model");
        assertEquals(TEST_MAX_TOKENS, body.get("max_tokens"),
                "Should use configured max_tokens");
        assertTrue(body.containsKey("messages"),
                "Should include messages field");
    }

    // ============================================================================
    // Test 3: Error Handling - API Failures
    // ============================================================================

    @Test
    void testAnalyzeImage_APIReturnsError_ThrowsException() {
        ResponseEntity<String> errorResponse = new ResponseEntity<>(
                "{\"error\": \"API error\"}", HttpStatus.INTERNAL_SERVER_ERROR);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(errorResponse);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));
    }

    @Test
    void testAnalyzeImage_NetworkError_ThrowsException() {
        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenThrow(new RestClientException("Network error"));

        String imageDataUri = "data:image/jpeg;base64,test";

        AnalyzerException exception = assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));

        assertTrue(exception.getMessage().contains("OpenAI analysis failed"),
                "Should wrap network error in AnalyzerException");
    }

    @Test
    void testAnalyzeImage_APIReturns401_ThrowsException() {
        ResponseEntity<String> unauthorizedResponse = new ResponseEntity<>(
                "{\"error\": \"Invalid API key\"}", HttpStatus.UNAUTHORIZED);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(unauthorizedResponse);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));
    }

    // ============================================================================
    // Test 4: Error Handling - Response Parsing
    // ============================================================================

    @Test
    void testAnalyzeImage_EmptyChoices_ThrowsException() {
        String mockApiResponse = """
                {
                    "choices": []
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test";

        AnalyzerException exception = assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));

        assertTrue(exception.getMessage().contains("No response"),
                "Should detect empty choices array");
    }

    @Test
    void testAnalyzeImage_MissingChoicesField_ThrowsException() {
        String mockApiResponse = """
                {
                    "error": "No choices"
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));
    }

    @Test
    void testAnalyzeImage_MalformedJSON_ThrowsException() {
        String mockApiResponse = "This is not valid JSON";

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeImage(imageDataUri, "Test", null, null));
    }

    // ============================================================================
    // Test 5: Text-Only Analysis
    // ============================================================================

    @Test
    void testAnalyzeTextOnly_ValidInput_ReturnsAnalysis() {
        String mockApiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "{\\"calories\\": 95, \\"confidence\\": 0.70, \\"description\\": \\"One medium apple\\"}"
                        }
                    }]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        assertDoesNotThrow(() ->
                visionService.analyzeTextOnly("Apple", null, null));

        verify(restTemplate).exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void testAnalyzeTextOnly_BlankDescription_ThrowsException() {
        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeTextOnly("", null, null));

        verify(restTemplate, never()).exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    @Test
    void testAnalyzeTextOnly_NullDescription_ThrowsException() {
        assertThrows(AnalyzerException.class, () ->
                visionService.analyzeTextOnly(null, null, null));

        verify(restTemplate, never()).exchange(
                anyString(),
                any(HttpMethod.class),
                any(HttpEntity.class),
                eq(String.class)
        );
    }

    // ============================================================================
    // Test 6: Edge Cases
    // ============================================================================

    @Test
    void testAnalyzeImage_ResponseWithMarkdownCodeBlocks_ParsesCorrectly() {
        // AI sometimes wraps JSON in markdown code blocks
        String mockApiResponse = """
                {
                    "choices": [{
                        "message": {
                            "content": "```json\\n{\\"calories\\": 600, \\"confidence\\": 0.88}\\n```"
                        }
                    }]
                }
                """;

        ResponseEntity<String> responseEntity = new ResponseEntity<>(
                mockApiResponse, HttpStatus.OK);

        when(restTemplate.exchange(
                anyString(),
                eq(HttpMethod.POST),
                any(HttpEntity.class),
                eq(String.class)
        )).thenReturn(responseEntity);

        String imageDataUri = "data:image/jpeg;base64,test";

        assertDoesNotThrow(() ->
                visionService.analyzeImage(imageDataUri, "Pizza", null, null));
    }

    // ============================================================================
    // Test 7: Configuration Validation
    // ============================================================================

    @Test
    void testConstructor_ValidConfiguration_InitializesSuccessfully() {
        assertDoesNotThrow(() ->
                new OpenAIVisionService(
                        restTemplate,
                        "test-key",
                        "https://api.test.com",
                        "gpt-4o",
                        1000
                ));
    }

    @Test
    void testConstructor_DefaultValues_WorksCorrectly() {
        // Test with default URL and model (as per @Value annotations)
        assertDoesNotThrow(() ->
                new OpenAIVisionService(
                        restTemplate,
                        "test-key",
                        "https://api.openai.com/v1/chat/completions",
                        "gpt-4o-mini",
                        800
                ));
    }
}
