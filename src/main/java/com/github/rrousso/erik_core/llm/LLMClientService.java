package com.github.rrousso.erik_core.llm;

import com.github.rrousso.erik_core.config.ConfigService;
import com.github.rrousso.erik_core.conversation.ConversationHistory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Locale;

/**
 * Spring-managed LLM client service with support for multiple model types.
 * Routes calls to appropriate models based on task type.
 */
@Service
public class LLMClientService {

    private static final String API_URL = "https://openrouter.ai/api/v1/chat/completions";
    private final HttpClient client = HttpClient.newHttpClient();
    private final ConfigService configService;
    
    public LLMClientService(ConfigService configService) {
        this.configService = configService;
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
        ModelConfig config = getModelConfig(modelType);

        System.out.println("\n[LLM] Preparing simple call to " + modelType + " model: " + config.model);

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

        System.out.println("[LLM] Request body (" + body.length() + " chars):");
        System.out.println("--- BEGIN REQUEST BODY ---");
        System.out.println(body);
        System.out.println("--- END REQUEST BODY ---");

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

        ModelConfig config = getModelConfig(modelType);

        System.out.println("\n[LLM] Preparing callWithHistory to " + modelType + " model: " + config.model);
        System.out.println("[LLM] History message count: " + history.size());

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

        System.out.println("[LLM] Request body (" + body.length() + " chars):");
        System.out.println("--- BEGIN REQUEST BODY ---");
        System.out.println(body);
        System.out.println("--- END REQUEST BODY ---");

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
            throw new RuntimeException("API key not configured. Set OPENROUTER_API_KEY environment variable or in application.yml");
        }

        System.out.println("[LLM] Sending request to OpenRouter API...");

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "http://localhost")
            .header("X-Title", "Erik-Assistant")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());

        System.out.println("[LLM] Received response (HTTP " + response.statusCode() + ")");
        System.out.println("[LLM] Raw response body (" + response.body().length() + " chars):");
        System.out.println("--- BEGIN RESPONSE BODY ---");
        System.out.println(response.body());
        System.out.println("--- END RESPONSE BODY ---");

        String extractedContent = extractContent(response.body());

        System.out.println("[LLM] Extracted content (" + extractedContent.length() + " chars):");
        System.out.println("--- BEGIN EXTRACTED CONTENT ---");
        System.out.println(extractedContent);
        System.out.println("--- END EXTRACTED CONTENT ---");

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
            return "[ERROR IN RESPONSE]: " + json;
        }
        
        int contentIdx = json.indexOf("\"content\":");
        if (contentIdx == -1) {
            return "[ERROR: No content field found]";
        }
        
        int start = json.indexOf("\"", contentIdx + 10);
        if (start == -1) {
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