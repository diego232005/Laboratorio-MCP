package com.tdea.mcp_server_tdea;

import com.tdea.mcp_server_tdea.service.BankingService;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class McpServerTdeaApplication {

    public static void main(String[] args) {
        SpringApplication.run(McpServerTdeaApplication.class, args);
    }

    @Bean
    public ToolCallbackProvider bankingTools(BankingService bankingService) {
        return MethodToolCallbackProvider.builder()
                .toolObjects(bankingService)
                .build();
    }
}
