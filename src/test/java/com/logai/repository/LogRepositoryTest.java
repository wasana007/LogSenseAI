package com.logai.repository;

import com.logai.model.LogEntry;
import com.logai.model.LogStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
class LogRepositoryTest {

    private static final String CORRELATION_ID = "abc-123";
    private static final String MESSAGE = "ERROR: something went wrong";

    @Autowired
    private LogRepository logRepository;

    @BeforeEach
    void setUp() {
        logRepository.deleteAll();
    }

    private LogEntry logEntry(String correlationId, String message) {
        LogEntry entry = new LogEntry();
        entry.setCorrelationId(correlationId);
        entry.setMessage(message);
        entry.setStatus(LogStatus.PENDING);
        return entry;
    }

    @Nested
    @DisplayName("findByCorrelationId()")
    class FindByCorrelationId {

        @Test
        @DisplayName("returns entry when correlationId exists")
        void returnsEntryWhenExists() {
            logRepository.save(logEntry(CORRELATION_ID, MESSAGE));

            Optional<LogEntry> result = logRepository.findByCorrelationId(CORRELATION_ID);

            assertThat(result).isPresent();
            assertThat(result.get().getCorrelationId()).isEqualTo(CORRELATION_ID);
            assertThat(result.get().getMessage()).isEqualTo(MESSAGE);
        }

        @Test
        @DisplayName("returns empty when correlationId does not exist")
        void returnsEmptyWhenNotFound() {
            Optional<LogEntry> result = logRepository.findByCorrelationId("nonexistent");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("returns correct entry when multiple entries exist")
        void returnsCorrectEntryAmongMultiple() {
            logRepository.save(logEntry("id-1", "message one"));
            logRepository.save(logEntry("id-2", "message two"));

            Optional<LogEntry> result = logRepository.findByCorrelationId("id-2");

            assertThat(result).isPresent();
            assertThat(result.get().getMessage()).isEqualTo("message two");
        }
    }

    @Nested
    @DisplayName("existsByCorrelationId()")
    class ExistsByCorrelationId {

        @Test
        @DisplayName("returns true when correlationId exists")
        void returnsTrueWhenExists() {
            logRepository.save(logEntry(CORRELATION_ID, MESSAGE));

            boolean exists = logRepository.existsByCorrelationId(CORRELATION_ID);

            assertThat(exists).isTrue();
        }

        @Test
        @DisplayName("returns false when correlationId does not exist")
        void returnsFalseWhenNotFound() {
            boolean exists = logRepository.existsByCorrelationId("nonexistent");

            assertThat(exists).isFalse();
        }
    }

    @Nested
    @DisplayName("constraints")
    class Constraints {

        @Test
        @DisplayName("throws on duplicate correlationId")
        void throwsOnDuplicateCorrelationId() {
            logRepository.save(logEntry(CORRELATION_ID, "first"));

            assertThrows(Exception.class, () -> {
                logRepository.saveAndFlush(logEntry(CORRELATION_ID, "duplicate"));
            });
        }

        @Test
        @DisplayName("throws when correlationId is null")
        void throwsWhenCorrelationIdIsNull() {
            LogEntry entry = new LogEntry();
            entry.setMessage(MESSAGE);

            assertThrows(Exception.class, () -> {
                logRepository.saveAndFlush(entry);
            });
        }
    }
}