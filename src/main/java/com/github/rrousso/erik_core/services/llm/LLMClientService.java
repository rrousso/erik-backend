package com.github.rrousso.erik_core.services.llm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.rrousso.erik_core.domain.enums.ModelType;
import com.github.rrousso.erik_core.domain.models.ConversationHistory;
import com.github.rrousso.erik_core.dto.openrouter.OpenRouterError;
import com.github.rrousso.erik_core.dto.openrouter.OpenRouterResponse;
import com.github.rrousso.erik_core.exceptions.configuration.MissingConfigException;
import com.github.rrousso.erik_core.exceptions.llm.LLMApiException;
import com.github.rrousso.erik_core.exceptions.llm.LLMInvalidResponseException;
import com.github.rrousso.erik_core.exceptions.llm.LLMParsingException;
import com.github.rrousso.erik_core.services.config.LLMConfigService;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

/**
 * Spring-managed LLM client service with support for multiple model types.
 * Routes calls to appropriate models based on task type.
 * 
 * REFACTORED: Now uses Jackson ObjectMapper for robust JSON parsing instead of manual string manipulation.
 */
@Service
public class LLMClientService {
    
    private static final Logger log = LoggerFactory.getLogger(LLMClientService.class);
    
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;
    
    private final HttpClient client;
    private final LLMConfigService configService;
    private final ObjectMapper objectMapper;
    
    public LLMClientService(LLMConfigService configService) {
        this.configService = configService;
        this.objectMapper = new ObjectMapper();
        
        // Configure HTTP client with timeouts
        this.client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS))
            .build();
        
        log.info("LLMClientService initialized with {}s connect timeout and {}s request timeout",
            CONNECT_TIMEOUT_SECONDS, REQUEST_TIMEOUT_SECONDS);
    }

    /**
     * Simple system + user prompt call (legacy method - defaults to NARRATIVE)
     */
    public String callNarrator(String systemPrompt, String userPrompt) {
        return call(ModelType.NARRATIVE, systemPrompt, userPrompt);
    }
    
    /**
     * Simple system + user prompt call with model type selection
     */
    public String call(ModelType modelType, String systemPrompt, String userPrompt){
        // Input validation
        Objects.requireNonNull(modelType, "modelType cannot be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
        
        ModelConfig config = getModelConfig(modelType);

        log.info("Preparing simple call to {} model: {}", modelType, config.model);

        String body = String.format(Locale.US, """
        {
          "model": "%s",
          "messages": [
            { "role": "system", "content": %s },
            { "role": "user", "content": %s }
          ],
          "temperature": %.1f,
          "max_tokens": %d
        }
        """,
            config.model,
            jsonEscape(systemPrompt),
            jsonEscape(userPrompt),
            config.temperature,
            config.maxTokens
        );

        log.debug("Request body length: {} chars", body.length());

        return sendRequest(body, modelType.toString());
    }
    
    /**
     * Call with full conversation history (defaults to NARRATIVE)
     */
    public String callWithHistory(
            String systemPrompt, 
            String userPrompt,
            List<ConversationHistory.Message> history) {
        return callWithHistory(ModelType.NARRATIVE, systemPrompt, userPrompt, history);
    }
    
    /**
     * Call with full conversation history and model type selection
     */
    public String callWithHistory(
            ModelType modelType,
            String systemPrompt,
            String userPrompt,
            List<ConversationHistory.Message> history) {

        // Input validation
        Objects.requireNonNull(modelType, "modelType cannot be null");
        Objects.requireNonNull(systemPrompt, "systemPrompt cannot be null");
        Objects.requireNonNull(userPrompt, "userPrompt cannot be null");
        Objects.requireNonNull(history, "history cannot be null");

        ModelConfig config = getModelConfig(modelType);

        log.info("Preparing callWithHistory to {} model: {} with {} history messages", 
            modelType, config.model, history.size());

        StringBuilder messagesJson = new StringBuilder();
        messagesJson.append("[");

        // Add system message
        messagesJson.append(String.format(
            "{ \"role\": \"system\", \"content\": %s }",
            jsonEscape(systemPrompt)
        ));

        // Add conversation history
        for (ConversationHistory.Message msg : history) {
            messagesJson.append(",");
            messagesJson.append(String.format(
                "{ \"role\": \"%s\", \"content\": %s }",
                msg.getRole(),
                jsonEscape(msg.getContent())
            ));
        }

        // Add final user prompt if not empty
        if (!userPrompt.isEmpty()) {
            messagesJson.append(",");
            messagesJson.append(String.format(
                "{ \"role\": \"user\", \"content\": %s }",
                jsonEscape(userPrompt)
            ));
        }

        messagesJson.append("]");

        String body = "{\n" +
            "  \"model\": \"" + config.model + "\",\n" +
            "  \"messages\": " + messagesJson.toString() + ",\n" +
            "  \"temperature\": " + config.temperature + ",\n" +
            "  \"max_tokens\": " + config.maxTokens + "\n" +
            "}";

        log.debug("Request body length: {} chars", body.length());

        return sendRequest(body, modelType.toString());
    }
    
    /**
     * Get model configuration based on type
     */
    private ModelConfig getModelConfig(ModelType modelType) {
        return switch (modelType) {
            case NARRATIVE -> new ModelConfig(
                configService.getNarrativeConfig().getModel(),
                configService.getNarrativeConfig().getTemperature(),
                configService.getNarrativeConfig().getMaxTokens()
            );
            case ANALYTICAL -> new ModelConfig(
                configService.getAnalyticalConfig().getModel(),
                configService.getAnalyticalConfig().getTemperature(),
                configService.getAnalyticalConfig().getMaxTokens()
            );
        };
    }
    
    /**
     * Shared request sending logic
     */
    private String sendRequest(String body, String modelType) {
        String apiKey = configService.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("API key not configured");
            throw new MissingConfigException("OPENROUTER_API_KEY",
                    "Set environment variable or add to application.properties");
        }

        log.debug("Sending request to OpenRouter API...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "http://localhost")
            .header("X-Title", "Erik-Assistant")
            .timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS))
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response;
        try {
            response = client.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            log.error("Network error calling OpenRouter API", e);
            throw new LLMApiException("Network error calling LLM API", e);
        } catch (InterruptedException e) {
            log.error("Request interrupted", e);
            Thread.currentThread().interrupt();  // Restore interrupt status
            throw new LLMApiException("Request interrupted", e);
        }

        log.info("Received response (HTTP {})", response.statusCode());
        log.debug("Response body length: {} chars", response.body().length());

        String extractedContent = parseResponse(response.body());

        return extractedContent;
    }

    /**
     * Escape string for JSON (still needed for building request body)
     */
    private String jsonEscape(String text) {
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    /**
     * Parse OpenRouter API response using Jackson.
     * 
     * REFACTORED: Replaced ~50 lines of manual char-by-char parsing with Jackson ObjectMapper.
     * 
     * Handles:
     * - Successful responses: extracts content from first choice
     * - Error responses: throws exception with detailed error message
     * - Malformed JSON: throws exception with parsing error
     * 
     * @param responseBody Raw JSON response from OpenRouter API
     * @return The content text from the assistant's message
     * @throws Exception if response contains error or cannot be parsed
     */
    private String parseResponse(String responseBody){
        // First, try to parse as error response
        if (responseBody.contains("\"error\"")) {
            try {
                OpenRouterError errorResponse = objectMapper.readValue(responseBody, OpenRouterError.class);
                String errorMsg = errorResponse.toString();
                log.error("OpenRouter API returned error: {}", errorMsg);
                throw new LLMApiException("OpenRouter API returned error", errorMsg);
            } catch (Exception e) {
                // If error parsing fails, log the raw response
                log.error("Failed to parse error response: {}", responseBody);
                throw new LLMApiException("OpenRouter API returned error", responseBody);
            }
        }
        
        // Parse as successful response
        try {
            OpenRouterResponse successResponse = objectMapper.readValue(responseBody, OpenRouterResponse.class);
            
            String content = successResponse.getContent();
            
            if (content == null) {
                log.error("No content in OpenRouter response. Choices: {}", 
                    successResponse.getChoices() != null ? successResponse.getChoices().size() : 0);
                throw new LLMInvalidResponseException("No content in response");
            }
            
            log.debug("Successfully extracted content ({} chars)", content.length());
            return content;
            
        } catch (Exception e) {
            log.error("Failed to parse OpenRouter response", e);
            log.debug("Response body: {}", responseBody);
            throw new LLMParsingException("OpenRouter API response", responseBody, e);
        }
    }
    
    /**
     * Get current narrative model name
     */
    public String getNarrativeModel() {
        return configService.getNarrativeConfig().getModel();
    }
    
    /**
     * Get current analytical model name
     */
    public String getAnalyticalModel() {
        return configService.getAnalyticalConfig().getModel();
    }
    
    /**
     * Internal class to hold model configuration
     */
    private record ModelConfig(String model, double temperature, int maxTokens) {}
}