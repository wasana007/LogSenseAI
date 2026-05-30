package com.logai.service;

import com.logai.model.LogDocument;
import com.logai.model.LogEntry;
import com.logai.model.LogStatus;
import com.logai.repository.LogRepository;
import com.logai.repository.LogSearchRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogStorageServiceTest {

    private static final String CORRELATION_ID = "corr-123";
    private static final String MESSAGE = "ERROR: something went wrong";
    private static final String RESULT = "DB issue detected";
    private static final String ERROR_MESSAGE = "FEIL: LLM unavailable";

    @Mock
    private LogRepository repository;

    @Mock
    private LogSearchRepository logSearchRepository;

    @InjectMocks
    private LogStorageService logStorageService;

    private LogEntry pendingEntry() {
        LogEntry entry = new LogEntry();
        entry.setCorrelationId(CORRELATION_ID);
        entry.setMessage(MESSAGE);
        entry.setStatus(LogStatus.PENDING);
        entry.setCreatedAt(LocalDateTime.now());
        return entry;
    }

    @Nested
    @DisplayName("savePending()")
    class SavePending {

        @Test
        @DisplayName("saves entry with PENDING status")
        void savesWithPendingStatus() {
            logStorageService.savePending(CORRELATION_ID, MESSAGE);

            ArgumentCaptor<LogEntry> captor = ArgumentCaptor.forClass(LogEntry.class);
            verify(repository).save(captor.capture());

            LogEntry saved = captor.getValue();
            assertThat(saved.getCorrelationId()).isEqualTo(CORRELATION_ID);
            assertThat(saved.getMessage()).isEqualTo(MESSAGE);
            assertThat(saved.getStatus()).isEqualTo(LogStatus.PENDING);
            assertThat(saved.getCreatedAt()).isNotNull();
        }
    }

    @Nested
    @DisplayName("savePendingIfAbsent()")
    class SavePendingIfAbsent {

        @Test
        @DisplayName("saves and returns true when correlationId does not exist")
        void savesAndReturnsTrueWhenAbsent() {
            when(repository.existsByCorrelationId(CORRELATION_ID)).thenReturn(false);

            boolean result = logStorageService.savePendingIfAbsent(CORRELATION_ID, MESSAGE);

            assertThat(result).isTrue();
            verify(repository).save(any(LogEntry.class));
        }

        @Test
        @DisplayName("skips save and returns false when correlationId already exists")
        void skipsSaveAndReturnsFalseWhenPresent() {
            when(repository.existsByCorrelationId(CORRELATION_ID)).thenReturn(true);

            boolean result = logStorageService.savePendingIfAbsent(CORRELATION_ID, MESSAGE);

            assertThat(result).isFalse();
            verify(repository, never()).save(any());
        }
    }

    @Nested
    @DisplayName("saveResult()")
    class SaveResult {

        @Test
        @DisplayName("sets COMPLETED status and result on entry")
        void setsCompletedStatusAndResult() {
            LogEntry entry = pendingEntry();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));
            when(repository.save(entry)).thenReturn(entry);

            LogEntry result = logStorageService.saveResult(CORRELATION_ID, RESULT);

            assertThat(result.getStatus()).isEqualTo(LogStatus.COMPLETED);
            assertThat(result.getResult()).isEqualTo(RESULT);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("indexes to Elasticsearch after saving")
        void indexesToElasticsearch() {
            LogEntry entry = pendingEntry();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));
            when(repository.save(entry)).thenReturn(entry);

            logStorageService.saveResult(CORRELATION_ID, RESULT);

            verify(logSearchRepository).save(any(LogDocument.class));
        }

        @Test
        @DisplayName("throws when correlationId not found")
        void throwsWhenNotFound() {
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> logStorageService.saveResult(CORRELATION_ID, RESULT))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(CORRELATION_ID);
        }
    }

    @Nested
    @DisplayName("saveFailed()")
    class SaveFailed {

        @Test
        @DisplayName("sets FAILED status and error message on entry")
        void setsFailedStatusAndErrorMessage() {
            LogEntry entry = pendingEntry();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));
            when(repository.save(entry)).thenReturn(entry);

            LogEntry result = logStorageService.saveFailed(CORRELATION_ID, ERROR_MESSAGE);

            assertThat(result.getStatus()).isEqualTo(LogStatus.FAILED);
            assertThat(result.getResult()).isEqualTo(ERROR_MESSAGE);
            assertThat(result.getCompletedAt()).isNotNull();
        }

        @Test
        @DisplayName("indexes to Elasticsearch after saving")
        void indexesToElasticsearch() {
            LogEntry entry = pendingEntry();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));
            when(repository.save(entry)).thenReturn(entry);

            logStorageService.saveFailed(CORRELATION_ID, ERROR_MESSAGE);

            verify(logSearchRepository).save(any(LogDocument.class));
        }

        @Test
        @DisplayName("throws when correlationId not found")
        void throwsWhenNotFound() {
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> logStorageService.saveFailed(CORRELATION_ID, ERROR_MESSAGE))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining(CORRELATION_ID);
        }
    }

    @Nested
    @DisplayName("findByCorrelationId()")
    class FindByCorrelationId {

        @Test
        @DisplayName("returns entry when found")
        void returnsEntryWhenFound() {
            LogEntry entry = pendingEntry();
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));

            LogEntry result = logStorageService.findByCorrelationId(CORRELATION_ID);

            assertThat(result.getCorrelationId()).isEqualTo(CORRELATION_ID);
        }

        @Test
        @DisplayName("throws when entry not found")
        void throwsWhenNotFound() {
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> logStorageService.findByCorrelationId(CORRELATION_ID))
                    .isInstanceOf(RuntimeException.class)
                    .hasMessageContaining("Ikke funnet")
                    .hasMessageContaining(CORRELATION_ID);
        }
    }

    @Nested
    @DisplayName("extractSource()")
    class ExtractSources {

        @ParameterizedTest(name = "message=\"{0}\" → source={1}")
        @DisplayName("resolves correct source from message")
        @CsvSource({
                "Error in PAYROLL_SERVICE processing, PAYROLL_SERVICE",
                "Some generic error,                  LOGSENSE_AI",
                ",                                    LOGSENSE_AI"
        })
        void resolvesCorrectSource(String message, String expectedSource) {
            LogEntry entry = pendingEntry();
            entry.setMessage(message);
            when(repository.findByCorrelationId(CORRELATION_ID)).thenReturn(Optional.of(entry));
            when(repository.save(entry)).thenReturn(entry);

            logStorageService.saveResult(CORRELATION_ID, RESULT);

            ArgumentCaptor<LogDocument> captor = ArgumentCaptor.forClass(LogDocument.class);
            verify(logSearchRepository).save(captor.capture());
            assertThat(captor.getValue().getSource()).isEqualTo(expectedSource);
        }
    }
}