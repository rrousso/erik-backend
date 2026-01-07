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
 * Spring-managed LLM client service
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
     * Simple system + user prompt call
     */
    public String callNarrator(String systemPrompt, String userPrompt) throws Exception {
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
            configService.getModel(),
            jsonEscape(systemPrompt),
            jsonEscape(userPrompt),
            configService.getTemperature(),
            configService.getMaxTokens()
        );
        
        return sendRequest(body);
    }
    
    /**
     * Call with full conversation history
     */
    public String callWithHistory(
            String systemPrompt, 
            String userPrompt,
            List<ConversationHistory.Message> history) throws Exception {
        
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
            "  \"model\": \"" + configService.getModel() + "\",\n" +
            "  \"messages\": " + messagesJson.toString() + ",\n" +
            "  \"temperature\": " + configService.getTemperature() + ",\n" +
            "  \"max_tokens\": " + configService.getMaxTokens() + "\n" +
            "}";
        
        return sendRequest(body);
    }
    
    /**
     * Shared request sending logic
     */
    private String sendRequest(String body) throws Exception {
        String apiKey = configService.getApiKey();
        if (apiKey == null || apiKey.isEmpty()) {
            throw new RuntimeException("API key not configured. Set OPENROUTER_API_KEY environment variable or in application.yml");
        }
        
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Authorization", "Bearer " + apiKey)
            .header("Content-Type", "application/json")
            .header("HTTP-Referer", "http://localhost")
            .header("X-Title", "Erik-Assistant")
            .POST(HttpRequest.BodyPublishers.ofString(body))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        return extractContent(response.body());
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
    
    public String getCurrentModel() {
        return configService.getModel();
    }
}
