package com.retailshop.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "bot")
public class BotProperties {

    private boolean enabled = true;
    private boolean openaiEnabled = true;
    private boolean memoryEnabled = true;
    private boolean conversationSummaryEnabled = true;

    @Min(1)
    private int memoryTopK = 5;

    private OpenAi openai = new OpenAi();
    private Qdrant qdrant = new Qdrant();

    @Getter
    @Setter
    public static class OpenAi {
        private String apiKey = "";
        private String model = "gpt-4.1-mini";
        private String embeddingModel = "text-embedding-3-small";

        @Min(1)
        private int embeddingDimensions = 1536;
    }

    @Getter
    @Setter
    public static class Qdrant {
        private String host = "localhost";

        @Min(1)
        private int port = 6333;

        private String apiKey = "";
        private String collectionCustomerMemory = "krishnai_customer_memory";
        private boolean useTls = false;
        private int timeoutSeconds = 8;
    }
}
