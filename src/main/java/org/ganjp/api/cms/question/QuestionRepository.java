package org.ganjp.api.cms.question;

import org.ganjp.api.cms.question.Question;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for Question management
 */
@Repository
public interface QuestionRepository extends JpaRepository<Question, String> {

    /**
     * Find question by question text and language
     */
    Optional<Question> findByQuestionAndLang(String question, Question.Language lang);

    /**
     * Find questions by language
     */
    List<Question> findByLangOrderByDisplayOrderAsc(Question.Language lang);

    /**
     * Find active questions by language
     */
    List<Question> findByLangAndIsActiveTrueOrderByDisplayOrderAsc(Question.Language lang);

    /**
     * Find questions by tags containing keyword
     */
    @Query("SELECT q FROM Question q WHERE q.tags LIKE %:tag% ORDER BY q.displayOrder ASC")
    List<Question> findByTagsContaining(@Param("tag") String tag);

    /**
     * Find active questions by tags containing keyword
     */
    @Query("SELECT q FROM Question q WHERE q.tags LIKE %:tag% AND q.isActive = true ORDER BY q.displayOrder ASC")
    List<Question> findActiveByTagsContaining(@Param("tag") String tag);

    /**
     * Check if question exists for a language
     */
    boolean existsByQuestionAndLang(String question, Question.Language lang);

    /**
     * Check if question exists for a language (excluding specific ID for updates)
     */
    @Query("SELECT COUNT(q) > 0 FROM Question q WHERE q.question = :question AND q.lang = :lang AND q.id != :excludeId")
    boolean existsByQuestionAndLangExcludingId(@Param("question") String question, @Param("lang") Question.Language lang, @Param("excludeId") String excludeId);

    /**
     * Search questions with filters
     */
    @Query("SELECT q FROM Question q WHERE " +
           "(:question IS NULL OR q.question LIKE %:question%) AND " +
           "(:lang IS NULL OR q.lang = :lang) AND " +
           "(:tags IS NULL OR q.tags LIKE %:tags%) AND " +
           "(:isActive IS NULL OR q.isActive = :isActive)")
    Page<Question> search(
            @Param("question") String question,
            @Param("lang") Question.Language lang,
            @Param("tags") String tags,
            @Param("isActive") Boolean isActive,
            Pageable pageable
    );
}
