package com.github.rrousso.erik_core.services.debug;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.rrousso.erik_core.config.ErikProperties;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Centralized service for debug file output.
 * 
 * All debug file writing goes through this service, which:
 * - Checks if debug output is enabled (via config)
 * - Handles directory creation
 * - Adds timestamps
 * - Catches IO exceptions gracefully
 * 
 * To enable debug output, set in application.yml:
 *   erik.debug.enabled: true
 * 
 * This keeps debug files out of production while still allowing
 * developers to easily inspect LLM outputs during development.
 */
@Service
public class DebugOutputService {
    
    private static final Logger log = LoggerFactory.getLogger(DebugOutputService.class);
    
    private final boolean debugEnabled;
    private final String debugOutputDir;
    
    public DebugOutputService(ErikProperties properties) {
        this.debugEnabled = properties.getDebug().isEnabled();
        this.debugOutputDir = properties.getDebug().getOutputDir();
        
        if (debugEnabled) {
            log.info("Debug output ENABLED - Files will be written to: {}", debugOutputDir);
        } else {
            log.info("Debug output DISABLED - No debug files will be created");
        }
    }
    
    /**
     * Write debug output to a file (only if debug is enabled).
     * 
     * @param filename Filename relative to debug output directory (e.g., "initialization_result.txt")
     * @param content Content to write
     * @param header Optional header to prepend (e.g., "Initialization Result")
     */
    public void write(String filename, String content, String header) {
        if (!debugEnabled) {
            return; // Skip if debug is disabled
        }
        
        try {
            Path filePath = Paths.get(debugOutputDir, filename);
            
            // Ensure directory exists
            Files.createDirectories(filePath.getParent());
            
            // Build formatted output with timestamp
            String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            StringBuilder output = new StringBuilder();
            
            output.append("=".repeat(80)).append("\n");
            if (header != null && !header.isEmpty()) {
                output.append(header).append("\n");
            }
            output.append("Timestamp: ").append(timestamp).append("\n");
            output.append("=".repeat(80)).append("\n\n");
            output.append(content);
            output.append("\n\n");
            
            // Write to file (overwrite mode - always shows latest)
            Files.writeString(filePath, output.toString(),
                StandardOpenOption.CREATE,
                StandardOpenOption.TRUNCATE_EXISTING);
            
            log.debug("Debug output written to: {}", filePath.toAbsolutePath());
            
        } catch (IOException e) {
            log.warn("Failed to write debug file: {} - {}", filename, e.getMessage());
            // Don't throw - debug output is non-critical
        }
    }
    
    /**
     * Write debug output without a header.
     */
    public void write(String filename, String content) {
        write(filename, content, null);
    }
    
    /**
     * Check if debug output is enabled.
     * 
     * Services can use this to skip expensive debug formatting
     * when debug output is disabled.
     */
    public boolean isEnabled() {
        return debugEnabled;
    }
}