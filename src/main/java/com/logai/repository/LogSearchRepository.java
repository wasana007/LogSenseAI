package com.logai.repository;


import com.logai.model.LogDocument;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LogSearchRepository extends ElasticsearchRepository<LogDocument, String> {

    Optional<LogDocument> findByCorrelationId(String correlationId);

    List<LogDocument> findByMessageContaining(String keyword);

    List<LogDocument> findByStatus(String status);

    List<LogDocument> findBySource(String source);
}