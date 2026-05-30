package com.logai.controller;

import com.logai.model.LogDocument;
import com.logai.repository.LogSearchRepository;
import com.logai.service.LogStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogControllerTest {

    private static final String LOG_TOPIC = "log-events";
    private static final String SOURCE = "payroll-service";
    private static final String EMAIL = "user@example.com";
    private static final String LOG_MESSAGE = "Salary calculation failed";
    private static final String STATUS = "ERROR";
    private static final String KEYWORD = "timeout";

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private LogStorageService storageService;

    @Mock
    private LogSearchRepository logSearchRepository;

    @Mock
    private Authentication authentication;

    @InjectMocks
    private LogController logController;

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(logController, "logTopic", LOG_TOPIC);
    }

    private LogDocument logDocument(String message) {
        LogDocument doc = new LogDocument();
        doc.setCorrelationId("corr-001");
        doc.setMessage(message);
        doc.setStatus(LogControllerTest.STATUS);
        doc.setSource(LogControllerTest.SOURCE);
        return doc;
    }

    @Nested
    @DisplayName("sendLog()")
    class SendLog {

        @Test
        @DisplayName("returns 202 Accepted")
        void returns202() {
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<Map<String, String>> response = logController.sendLog(LOG_MESSAGE, authentication);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        }

        @Test
        @DisplayName("returns PENDING status in body")
        void returnsPendingStatus() {
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<Map<String, String>> response = logController.sendLog(LOG_MESSAGE, authentication);

            assertThat(response.getBody()).containsEntry("status", "PENDING");
        }

        @Test
        @DisplayName("returns correlationId in body")
        void returnsCorrelationId() {
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<Map<String, String>> response = logController.sendLog(LOG_MESSAGE, authentication);

            assertThat(response.getBody()).containsKey("correlationId");
            assertThat(response.getBody().get("correlationId")).isNotBlank();
        }

        @Test
        @DisplayName("saves pending log with correlationId and message")
        void savesPendingLog() {
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<Map<String, String>> response = logController.sendLog(LOG_MESSAGE, authentication);

            String correlationId = response.getBody().get("correlationId");
            verify(storageService).savePending(correlationId, LOG_MESSAGE);
        }

        @Test
        @DisplayName("sends message to Kafka with correlationId as key")
        void sendsToKafka() {
            when(authentication.getName()).thenReturn(EMAIL);

            ResponseEntity<Map<String, String>> response = logController.sendLog(LOG_MESSAGE, authentication);

            String correlationId = response.getBody().get("correlationId");
            verify(kafkaTemplate).send(LOG_TOPIC, correlationId, LOG_MESSAGE);
        }

        @Test
        @DisplayName("generates unique correlationId per request")
        void generatesUniqueCorrelationId() {
            when(authentication.getName()).thenReturn(EMAIL);

            String id1 = logController.sendLog(LOG_MESSAGE, authentication).getBody().get("correlationId");
            String id2 = logController.sendLog(LOG_MESSAGE, authentication).getBody().get("correlationId");

            assertThat(id1).isNotEqualTo(id2);
        }
    }

    @Nested
    @DisplayName("search()")
    class Search {

        @Test
        @DisplayName("returns 200 with matching documents")
        void returns200WithResults() {
            List<LogDocument> docs = List.of(logDocument("ERROR: timeout on DB"));
            when(logSearchRepository.findByMessageContaining(KEYWORD)).thenReturn(docs);

            ResponseEntity<List<LogDocument>> response = logController.search(KEYWORD);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(docs);
        }

        @Test
        @DisplayName("returns empty list when no documents match")
        void returnsEmptyList() {
            when(logSearchRepository.findByMessageContaining(KEYWORD)).thenReturn(List.of());

            ResponseEntity<List<LogDocument>> response = logController.search(KEYWORD);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("delegates to repository with correct keyword")
        void delegatesToRepository() {
            when(logSearchRepository.findByMessageContaining(KEYWORD)).thenReturn(List.of());

            logController.search(KEYWORD);

            verify(logSearchRepository).findByMessageContaining(KEYWORD);
        }
    }

    @Nested
    @DisplayName("searchByStatus()")
    class SearchByStatus {

        @Test
        @DisplayName("returns 200 with matching documents")
        void returns200WithResults() {
            List<LogDocument> docs = List.of(logDocument("DB error"));
            when(logSearchRepository.findByStatus(STATUS)).thenReturn(docs);

            ResponseEntity<List<LogDocument>> response = logController.searchByStatus(STATUS);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(docs);
        }

        @Test
        @DisplayName("returns empty list when no documents match status")
        void returnsEmptyList() {
            when(logSearchRepository.findByStatus(STATUS)).thenReturn(List.of());

            ResponseEntity<List<LogDocument>> response = logController.searchByStatus(STATUS);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("delegates to repository with correct status")
        void delegatesToRepository() {
            when(logSearchRepository.findByStatus(STATUS)).thenReturn(List.of());

            logController.searchByStatus(STATUS);

            verify(logSearchRepository).findByStatus(STATUS);
        }
    }

    @Nested
    @DisplayName("searchBySource()")
    class SearchBySource {

        @Test
        @DisplayName("returns 200 with matching documents")
        void returns200WithResults() {
            List<LogDocument> docs = List.of(logDocument("Kafka error"));
            when(logSearchRepository.findBySource(SOURCE)).thenReturn(docs);

            ResponseEntity<List<LogDocument>> response = logController.searchBySource(SOURCE);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isEqualTo(docs);
        }

        @Test
        @DisplayName("returns empty list when no documents match source")
        void returnsEmptyList() {
            when(logSearchRepository.findBySource(SOURCE)).thenReturn(List.of());

            ResponseEntity<List<LogDocument>> response = logController.searchBySource(SOURCE);

            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("delegates to repository with correct source")
        void delegatesToRepository() {
            when(logSearchRepository.findBySource(SOURCE)).thenReturn(List.of());

            logController.searchBySource(SOURCE);

            verify(logSearchRepository).findBySource(SOURCE);
        }
    }
}