package com.logai.agent;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.logai.exception.LogsenseAISerializationException;
import com.logai.exception.LogsenseAIToolResolutionException;
import com.logai.service.OllamaClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class AgentService {

    private static final Logger log = LoggerFactory.getLogger(AgentService.class);
    private static final String OLLAMA_ANALYZE = "OLLAMA_ANALYZE";
    private final ToolRouter toolRouter;
    private final OllamaClient ollamaClient;
    private final ObjectMapper objectMapper;

    public AgentService(ToolRouter toolRouter, OllamaClient ollamaClient, ObjectMapper objectMapper) {
        this.toolRouter = toolRouter;
        this.ollamaClient = ollamaClient;
        this.objectMapper = objectMapper;
    }

    public String handle(String logMessage) {
        String decision = decide(logMessage);
        log.info("LLM-beslutning: {}", decision);
        return toolRouter.execute(decision, logMessage);
    }

    private String decide(String logMessage) {
        String raw;
        try {
            raw = ollamaClient.generate(buildPrompt(logMessage));
            return resolveTool(raw);
        } catch (LogsenseAISerializationException e) {
            log.error("Kunne ikke ekstrahere JSON fra LLM-respons: {} → bruker OLLAMA_ANALYZE", e.getMessage());
            return OLLAMA_ANALYZE;
        } catch (JsonProcessingException e) {
            log.error("Kunne ikke parse JSON fra LLM-respons: {} → bruker OLLAMA_ANALYZE", e.getMessage());
            return OLLAMA_ANALYZE;
        } catch (LogsenseAIToolResolutionException e) {
            log.error("Ugyldig verktøynavn fra LLM: {} → bruker OLLAMA_ANALYZE", e.getMessage());
            return OLLAMA_ANALYZE;
        }
    }

    private String buildPrompt(String logMessage) {
        return """
                Du er en AI-agent som velger riktig analyseverktøy for en loggmelding.
                
                Tilgjengelige verktøy:
                - DB_ANALYZE     : Bruk når loggen er relatert til databasefeil, SQL, tilkoblinger, spørringer eller tidsavbrudd mot DB
                - KAFKA_ANALYZE  : Bruk når loggen er relatert til Kafka, meldingskø, topic, consumer, producer eller broker
                - OLLAMA_ANALYZE : Bruk for alle andre logger som krever generell AI-resonnering
                
                Svar KUN med et gyldig JSON-objekt, ingen forklaring, ingen markdown:
                {"tool": "DB_ANALYZE"}
                
                Logg som skal analyseres:
                %s
                """.formatted(logMessage);
    }

    private String resolveTool(String raw) throws JsonProcessingException {
        String cleaned = extractJson(raw);
        Map<String, String> parsed = objectMapper.readValue(cleaned, new TypeReference<>() {
                }
        );

        String toolValue = parsed.get("tool");
        if (toolValue == null) {
            throw new LogsenseAIToolResolutionException("Manglende 'tool'-felt i LLM-respons: " + raw);
        }

        return normalizeToolName(toolValue);
    }

    private String normalizeToolName(String toolValue) {
        String tool = toolValue.trim().toUpperCase();
        return switch (tool) {
            case "DB_ANALYZE", "KAFKA_ANALYZE", OLLAMA_ANALYZE -> tool;
            default -> throw new LogsenseAIToolResolutionException("Ukjent verktøy fra LLM: " + tool);
        };
    }

    private String extractJson(String raw) {
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start == -1 || end == -1 || end < start) {
            throw new LogsenseAISerializationException("Ingen JSON funnet i LLM-respons: " + raw);
        }
        return raw.substring(start, end + 1);
    }
}