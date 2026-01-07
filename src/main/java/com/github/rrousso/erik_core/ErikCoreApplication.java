package com.github.rrousso.erik_core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;

import com.github.rrousso.erik_core.console.ConsoleRunner;

@SpringBootApplication
public class ErikCoreApplication {

    public static void main(String[] args) {
        // Start Spring Boot context
        ConfigurableApplicationContext context = SpringApplication.run(ErikCoreApplication.class, args);
        
        // Get the console runner bean and start the interactive console
        ConsoleRunner consoleRunner = context.getBean(ConsoleRunner.class);
        consoleRunner.run();
        
        // Shutdown Spring context after console exits
        context.close();
    }
}
