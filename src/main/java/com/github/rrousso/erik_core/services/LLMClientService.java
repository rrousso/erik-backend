package com.github.rrousso.erik_core.services;

import com.github.rrousso.erik_core.Entities.ConversationHistory;
import com.github.rrousso.erik_core.Entities.ModelType;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

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
 */
@Service
public class LLMClientService {
    
    private static final Logger log = LoggerFactory.getLogger(LLMClientService.class);
    
    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private static final int CONNECT_TIMEOUT_SECONDS = 30;
    private static final int REQUEST_TIMEOUT_SECONDS = 120;
    
    private final HttpClient client;
    private final ConfigService configService;
    
    public LLMClientService(ConfigService configService) {
        this.configService = configService;
        
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
    public String callNarrator(String systemPrompt, String userPrompt) throws Exception {
        return call(ModelType.NARRATIVE, systemPrompt, userPrompt);
    }
    
    /**
     * Simple system + user prompt call with model type selection
     */
    public String call(ModelType modelType, String systemPrompt, String userPrompt) throws Exception {
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
            List<ConversationHistory.Message> history) throws Exception {
        return callWithHistory(ModelType.NARRATIVE, systemPrompt, userPrompt, history);
    }
    
    /**
     * Call with full conversation history and model type selection
     */
    public String callWithHistory(
            ModelType modelType,
            String systemPrompt,
            String userPrompt,
            List<ConversationHistory.Message> history) throws Exception {

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
                configService.getNarrative().getModel(),
                configService.getNarrative().getTemperature(),
                configService.getNarrative().getMaxTokens()
            );
            case ANALYTICAL -> new ModelConfig(
                configService.getAnalytical().getModel(),
                configService.getAnalytical().getTemperature(),
                configService.getAnalytical().getMaxTokens()
            );
        };
    }
    
    /**
     * Shared request sending logic
     */
    private String sendRequest(String body, String modelType) throws Exception {
        String apiKey = configService.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("API key not configured");
            throw new RuntimeException("API key not configured. Set OPENROUTER_API_KEY environment variable or in application.yml");
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

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        log.info("Received response (HTTP {})", response.statusCode());
        log.debug("Response body length: {} chars", response.body().length());

        String extractedContent = extractContent(response.body());

        return extractedContent;
    }

    private String jsonEscape(String text) {
        return "\"" + text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t") + "\"";
    }

    private String extractContent(String json) {
        if (json.contains("\"error\"")) {
            log.error("Error in API response: {}", json);
            return "[ERROR IN RESPONSE]: " + json;
        }
        
        int contentIdx = json.indexOf("\"content\":");
        if (contentIdx == -1) {
            log.error("No content field found in response");
            return "[ERROR: No content field found]";
        }
        
        int start = json.indexOf("\"", contentIdx + 10);
        if (start == -1) {
            log.error("Malformed content field in response");
            return "[ERROR: Malformed content field]";
        }
        
        StringBuilder content = new StringBuilder();
        int i = start + 1;
        
        while (i < json.length()) {
            char c = json.charAt(i);
            
            if (c == '\\' && i + 1 < json.length()) {
                char next = json.charAt(i + 1);
                if (next == 'n') {
                    content.append('\n');
                    i += 2;
                } else if (next == '"') {
                    content.append('"');
                    i += 2;
                } else if (next == '\\') {
                    content.append('\\');
                    i += 2;
                } else if (next == 'r') {
                    content.append('\r');
                    i += 2;
                } else if (next == 't') {
                    content.append('\t');
                    i += 2;
                } else {
                    content.append(c);
                    i++;
                }
            } else if (c == '"') {
                break;
            } else {
                content.append(c);
                i++;
            }
        }
        
        return content.toString();
    }
    
    /**
     * Get current narrative model name
     */
    public String getNarrativeModel() {
        return configService.getNarrative().getModel();
    }
    
    /**
     * Get current analytical model name
     */
    public String getAnalyticalModel() {
        return configService.getAnalytical().getModel();
    }
    
    /**
     * Internal class to hold model configuration
     */
    private record ModelConfig(String model, double temperature, int maxTokens) {}
}