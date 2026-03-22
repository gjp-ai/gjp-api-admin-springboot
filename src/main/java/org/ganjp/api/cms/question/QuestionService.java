package org.ganjp.api.cms.question;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.ganjp.api.cms.question.QuestionCreateRequest;
import org.ganjp.api.cms.question.QuestionUpdateRequest;
import org.ganjp.api.cms.question.QuestionResponse;
import org.ganjp.api.cms.question.Question;
import org.ganjp.api.cms.question.QuestionRepository;
import org.ganjp.api.common.exception.ResourceNotFoundException;
import org.ganjp.api.common.exception.BusinessException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Service for Question management
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class QuestionService {

    private final QuestionRepository questionRepository;

    /**
     * Create a new question
     */
    public QuestionResponse createQuestion(QuestionCreateRequest request, String createdBy) {
        log.info("Creating new question: {} by user: {}", request.getQuestion(), createdBy);

        // Validate unique question per language
        if (questionRepository.existsByQuestionAndLang(request.getQuestion(), request.getLang())) {
            throw new BusinessException(String.format("Question '%s' already exists for language '%s'", 
                request.getQuestion(), request.getLang()));
        }

        Question question = Question.builder()
                .id(UUID.randomUUID().toString())
                .question(request.getQuestion())
                .answer(request.getAnswer())
                .tags(request.getTags())
                .lang(request.getLang())
                .displayOrder(request.getDisplayOrder())
                .isActive(request.getIsActive())
                .build();
        
        question.setCreatedBy(createdBy);
        question.setUpdatedBy(createdBy);

        Question savedQuestion = questionRepository.save(question);
        return QuestionResponse.from(savedQuestion);
    }

    /**
     * Update an existing question
     */
    public QuestionResponse updateQuestion(String id, QuestionUpdateRequest request, String updatedBy) {
        log.info("Updating question: {} by user: {}", id, updatedBy);

        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));

        // Validate unique question if changed
        if (request.getQuestion() != null && !request.getQuestion().equals(question.getQuestion())) {
            Question.Language lang = request.getLang() != null ? request.getLang() : question.getLang();
            if (questionRepository.existsByQuestionAndLangExcludingId(request.getQuestion(), lang, id)) {
                throw new BusinessException(String.format("Question '%s' already exists for language '%s'", 
                    request.getQuestion(), lang));
            }
            question.setQuestion(request.getQuestion());
        }

        if (request.getAnswer() != null) {
            question.setAnswer(request.getAnswer());
        }
        if (request.getTags() != null) {
            question.setTags(request.getTags());
        }
        if (request.getLang() != null) {
            question.setLang(request.getLang());
        }
        if (request.getDisplayOrder() != null) {
            question.setDisplayOrder(request.getDisplayOrder());
        }
        if (request.getIsActive() != null) {
            question.setIsActive(request.getIsActive());
        }

        question.setUpdatedBy(updatedBy);
        Question updatedQuestion = questionRepository.save(question);
        return QuestionResponse.from(updatedQuestion);
    }

    /**
     * Delete a question (Logical Delete)
     */
    public void deleteQuestion(String id, String updatedBy) {
        log.info("Deleting question (logical): {} by user: {}", id, updatedBy);
        Question question = questionRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));
        
        question.setIsActive(false);
        question.setUpdatedBy(updatedBy);
        questionRepository.save(question);
    }

    /**
     * Get question by ID
     */
    @Transactional(readOnly = true)
    public QuestionResponse getQuestionById(String id) {
        return questionRepository.findById(id)
                .map(QuestionResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException("Question not found with id: " + id));
    }

    /**
     * Get questions with filtering and pagination
     */
    @Transactional(readOnly = true)
    public Page<QuestionResponse> getQuestions(String question, Question.Language lang, String tags, Boolean isActive, Pageable pageable) {
        return questionRepository.search(question, lang, tags, isActive, pageable)
                .map(QuestionResponse::from);
    }

    /**
     * Get questions by language
     */
    @Transactional(readOnly = true)
    public List<QuestionResponse> getQuestionsByLanguage(Question.Language lang, boolean activeOnly) {
        List<Question> questions;
        if (activeOnly) {
            questions = questionRepository.findByLangAndIsActiveTrueOrderByDisplayOrderAsc(lang);
        } else {
            questions = questionRepository.findByLangOrderByDisplayOrderAsc(lang);
        }
        return questions.stream()
                .map(QuestionResponse::from)
                .collect(Collectors.toList());
    }
}
