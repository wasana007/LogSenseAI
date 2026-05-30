package com.logai.repository;

import com.logai.model.LogDocument;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LogSearchRepositoryTest {

    private static final String CORRELATION_ID_1 = "corr-001";
    private static final String CORRELATION_ID_2 = "corr-002";
    private static final String KEYWORD_TIMEOUT = "timeout";
    private static final String STATUS_ERROR = "ERROR";
    private static final String SOURCE_KAFKA = "kafka-service";
    private static final String SOURCE_A = "service-a";
    private static final String SOURCE_B = "service-b";

    @Mock
    private LogSearchRepository logSearchRepository;

    private LogDocument logDocument(String correlationId, String message, String source) {
        LogDocument doc = new LogDocument();
        doc.setCorrelationId(correlationId);
        doc.setMessage(message);
        doc.setStatus(STATUS_ERROR);
        doc.setSource(source);
        return doc;
    }

    @Nested
    @DisplayName("findByMessageContaining()")
    class FindByMessageContaining {

        @Test
        @DisplayName("returns documents containing keyword in message")
        void returnsMatchingDocuments() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "ERROR: timeout on DB", SOURCE_A)
            );
            when(logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT);

            assertThat(result)
                    .hasSize(1)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactly(CORRELATION_ID_1);
            verify(logSearchRepository).findByMessageContaining(KEYWORD_TIMEOUT);
        }

        @Test
        @DisplayName("returns empty list when no documents match keyword")
        void returnsEmptyWhenNoMatch() {
            when(logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT)).thenReturn(List.of());

            List<LogDocument> result = logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns multiple documents when multiple match keyword")
        void returnsMultipleMatchingDocuments() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "ERROR: timeout on DB", SOURCE_A),
                    logDocument(CORRELATION_ID_2, "ERROR: timeout on Kafka", SOURCE_B)
            );
            when(logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findByMessageContaining(KEYWORD_TIMEOUT);

            assertThat(result)
                    .hasSize(2)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactlyInAnyOrder(CORRELATION_ID_1, CORRELATION_ID_2);
        }
    }

    @Nested
    @DisplayName("findByStatus()")
    class FindByStatus {

        @Test
        @DisplayName("returns documents matching given status")
        void returnsMatchingByStatus() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "DB error", SOURCE_A)
            );
            when(logSearchRepository.findByStatus(STATUS_ERROR)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findByStatus(STATUS_ERROR);

            assertThat(result)
                    .hasSize(1)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactly(CORRELATION_ID_1);
            verify(logSearchRepository).findByStatus(STATUS_ERROR);
        }

        @Test
        @DisplayName("returns empty list when no documents match status")
        void returnsEmptyWhenNoStatusMatch() {
            when(logSearchRepository.findByStatus(STATUS_ERROR)).thenReturn(List.of());

            List<LogDocument> result = logSearchRepository.findByStatus(STATUS_ERROR);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns all documents matching status when multiple exist")
        void returnsAllMatchingStatus() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "DB error", SOURCE_A),
                    logDocument(CORRELATION_ID_2, "Kafka error", SOURCE_B)
            );
            when(logSearchRepository.findByStatus(STATUS_ERROR)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findByStatus(STATUS_ERROR);

            assertThat(result)
                    .hasSize(2)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactlyInAnyOrder(CORRELATION_ID_1, CORRELATION_ID_2);
        }
    }

    @Nested
    @DisplayName("findBySource()")
    class FindBySource {

        @Test
        @DisplayName("returns documents matching given source")
        void returnsMatchingBySource() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "Kafka error", SOURCE_KAFKA)
            );
            when(logSearchRepository.findBySource(SOURCE_KAFKA)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findBySource(SOURCE_KAFKA);

            assertThat(result)
                    .hasSize(1)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactly(CORRELATION_ID_1);
            verify(logSearchRepository).findBySource(SOURCE_KAFKA);
        }

        @Test
        @DisplayName("returns empty list when no documents match source")
        void returnsEmptyWhenNoSourceMatch() {
            when(logSearchRepository.findBySource(SOURCE_KAFKA)).thenReturn(List.of());

            List<LogDocument> result = logSearchRepository.findBySource(SOURCE_KAFKA);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns all documents matching source when multiple exist")
        void returnsAllMatchingSource() {
            List<LogDocument> docs = List.of(
                    logDocument(CORRELATION_ID_1, "Kafka error 1", SOURCE_KAFKA),
                    logDocument(CORRELATION_ID_2, "Kafka error 2", SOURCE_KAFKA)
            );
            when(logSearchRepository.findBySource(SOURCE_KAFKA)).thenReturn(docs);

            List<LogDocument> result = logSearchRepository.findBySource(SOURCE_KAFKA);

            assertThat(result)
                    .hasSize(2)
                    .extracting(LogDocument::getCorrelationId)
                    .containsExactlyInAnyOrder(CORRELATION_ID_1, CORRELATION_ID_2);
        }
    }
}