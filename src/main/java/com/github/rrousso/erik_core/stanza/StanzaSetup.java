package com.github.rrousso.erik_core.stanza;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all the details extracted from Void conversation to set up a Stanza.
 */
public class StanzaSetup {
    
    private List<String> characters = new ArrayList<>();
    private String setting = "";
    private String premise = "";
    private String userRole = "";
    private String tone = "";
    private List<String> specialRules = new ArrayList<>();
    
    public List<String> getCharacters() {
        return characters;
    }
    
    public void setCharacters(List<String> characters) {
        this.characters = characters;
    }
    
    public String getSetting() {
        return setting;
    }
    
    public void setSetting(String setting) {
        this.setting = setting;
    }
    
    public String getPremise() {
        return premise;
    }
    
    public void setPremise(String premise) {
        this.premise = premise;
    }
    
    public String getUserRole() {
        return userRole;
    }
    
    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }
    
    public String getTone() {
        return tone;
    }
    
    public void setTone(String tone) {
        this.tone = tone;
    }
    
    public List<String> getSpecialRules() {
        return specialRules;
    }
    
    public void setSpecialRules(List<String> specialRules) {
        this.specialRules = specialRules;
    }
    
    /**
     * Convert to a narrative-friendly string for the narrator system prompt
     */
    public String toNarratorContext() {
        StringBuilder sb = new StringBuilder();
        
        sb.append("CURRENT STANZA SETUP:\n\n");
        
        if (!setting.isEmpty()) {
            sb.append("Setting: ").append(setting).append("\n\n");
        }
        
        if (!premise.isEmpty()) {
            sb.append("Premise: ").append(premise).append("\n\n");
        }
        
        if (!userRole.isEmpty()) {
            sb.append("User's Role: ").append(userRole).append("\n\n");
        }
        
        if (!characters.isEmpty()) {
            sb.append("Characters Present:\n");
            for (String character : characters) {
                sb.append("- ").append(character).append("\n");
            }
            sb.append("\n");
        }
        
        if (!tone.isEmpty()) {
            sb.append("Tone/Genre: ").append(tone).append("\n\n");
        }
        
        if (!specialRules.isEmpty()) {
            sb.append("Special Rules:\n");
            for (String rule : specialRules) {
                sb.append("- ").append(rule).append("\n");
            }
            sb.append("\n");
        }
        
        return sb.toString();
    }
    
    /**
     * Parse from JSON-ish response from Erik
     */
    public static StanzaSetup parseFromErikResponse(String response) {
        StanzaSetup setup = new StanzaSetup();
        
        response = response.replaceAll("```json", "").replaceAll("```", "").trim();
        
        setup.setting = extractField(response, "setting");
        setup.premise = extractField(response, "premise");
        setup.userRole = extractField(response, "userRole");
        setup.tone = extractField(response, "tone");
        
        setup.characters = extractArray(response, "characters");
        setup.specialRules = extractArray(response, "specialRules");
        
        return setup;
    }
    
    private static String extractField(String json, String fieldName) {
        int fieldStart = json.indexOf("\"" + fieldName + "\"");
        if (fieldStart == -1) return "";
        
        int colonPos = json.indexOf(":", fieldStart);
        if (colonPos == -1) return "";
        
        int valueStart = json.indexOf("\"", colonPos);
        if (valueStart == -1) return "";
        
        int valueEnd = valueStart + 1;
        while (valueEnd < json.length()) {
            if (json.charAt(valueEnd) == '"' && json.charAt(valueEnd - 1) != '\\') {
                break;
            }
            valueEnd++;
        }
        
        if (valueEnd >= json.length()) return "";
        
        return json.substring(valueStart + 1, valueEnd)
            .replace("\\n", "\n")
            .replace("\\\"", "\"");
    }
    
    private static List<String> extractArray(String json, String fieldName) {
        List<String> result = new ArrayList<>();
        
        int fieldStart = json.indexOf("\"" + fieldName + "\"");
        if (fieldStart == -1) return result;
        
        int arrayStart = json.indexOf("[", fieldStart);
        if (arrayStart == -1) return result;
        
        int arrayEnd = findMatchingBracket(json, arrayStart);
        if (arrayEnd == -1) return result;
        
        String arrayContent = json.substring(arrayStart + 1, arrayEnd);
        result = parseArrayItems(arrayContent);
        
        return result;
    }
    
    private static int findMatchingBracket(String json, int openBracket) {
        int depth = 1;
        for (int i = openBracket + 1; i < json.length(); i++) {
            if (json.charAt(i) == '[') depth++;
            else if (json.charAt(i) == ']') {
                depth--;
                if (depth == 0) return i;
            }
        }
        return -1;
    }
    
    private static List<String> parseArrayItems(String arrayContent) {
        List<String> items = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inString = false;
        boolean escaped = false;
        
        for (int i = 0; i < arrayContent.length(); i++) {
            char c = arrayContent.charAt(i);
            
            if (escaped) {
                current.append(c);
                escaped = false;
                continue;
            }
            
            if (c == '\\') {
                escaped = true;
                continue;
            }
            
            if (c == '"') {
                inString = !inString;
                continue;
            }
            
            if (c == ',' && !inString) {
                String item = current.toString().trim();
                if (!item.isEmpty()) {
                    items.add(item);
                }
                current = new StringBuilder();
                continue;
            }
            
            if (inString) {
                current.append(c);
            }
        }
        
        String item = current.toString().trim();
        if (!item.isEmpty()) {
            items.add(item);
        }
        
        return items;
    }
    
    public void printDebug() {
        System.out.println("\n[DEBUG] StanzaSetup:");
        System.out.println("  Setting: " + setting);
        System.out.println("  Premise: " + premise);
        System.out.println("  User Role: " + userRole);
        System.out.println("  Tone: " + tone);
        System.out.println("  Characters: " + characters);
        System.out.println("  Special Rules: " + specialRules);
    }
}
