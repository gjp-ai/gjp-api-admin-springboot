package org.ganjp.api.cms.question;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.ganjp.api.auth.security.JwtUtils;
import org.ganjp.api.cms.question.CreateQuestionRequest;
import org.ganjp.api.cms.question.UpdateQuestionRequest;
import org.ganjp.api.cms.question.QuestionResponse;
import org.ganjp.api.cms.question.Question;
import org.ganjp.api.cms.question.QuestionService;
import org.ganjp.api.common.model.ApiResponse;
import org.ganjp.api.common.model.PaginatedResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller for Question management
 */
@RestController
@RequestMapping("/v1/questions")
@RequiredArgsConstructor
public class QuestionController {

    private final QuestionService questionService;
    private final JwtUtils jwtUtils;

    /**
     * Flexible search questions by question text, language, tags, and status
     * GET /v1/questions?question=xxx&lang=EN&tags=yyy&isActive=true&page=0&size=20&sort=updatedAt&direction=desc
     */
    @GetMapping
    public ResponseEntity<ApiResponse<PaginatedResponse<QuestionResponse>>> getQuestions(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "updatedAt") String sort,
            @RequestParam(defaultValue = "desc") String direction,
            @RequestParam(required = false) String question,
            @RequestParam(required = false) Question.Language lang,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) Boolean isActive
    ) {
        try {
            Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction)
                ? Sort.Direction.DESC : Sort.Direction.ASC;

            Pageable pageable = PageRequest.of(page, size, sortDirection, sort);
            Page<QuestionResponse> questions = questionService.getQuestions(question, lang, tags, isActive, pageable);

            PaginatedResponse<QuestionResponse> response = PaginatedResponse.of(questions.getContent(), questions.getNumber(), questions.getSize(), questions.getTotalElements());
            return ResponseEntity.ok(ApiResponse.success(response, "Questions found"));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(ApiResponse.error(500, "Error searching questions: " + e.getMessage(), null));
        }
    }

    /**
     * Get question by ID
     */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<QuestionResponse>> getQuestionById(@PathVariable String id) {
        QuestionResponse question = questionService.getQuestionById(id);
        return ResponseEntity.ok(ApiResponse.success(question, "Question retrieved successfully"));
    }

    /**
     * Get questions by language
     */
    @GetMapping("/by-language/{lang}")
    public ResponseEntity<ApiResponse<List<QuestionResponse>>> getQuestionsByLanguage(
            @PathVariable Question.Language lang,
            @RequestParam(defaultValue = "false") boolean activeOnly
    ) {
        List<QuestionResponse> questions = questionService.getQuestionsByLanguage(lang, activeOnly);
        return ResponseEntity.ok(ApiResponse.success(questions, "Questions retrieved successfully"));
    }

    /**
     * Create a new question
     */
    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QuestionResponse>> createQuestion(
            @Valid @RequestBody CreateQuestionRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        QuestionResponse question = questionService.createQuestion(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(question, "Question created successfully"));
    }

    /**
     * Update an existing question
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<QuestionResponse>> updateQuestion(
            @PathVariable String id,
            @Valid @RequestBody UpdateQuestionRequest request,
            HttpServletRequest httpRequest
    ) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        QuestionResponse question = questionService.updateQuestion(id, request, userId);
        return ResponseEntity.ok(ApiResponse.success(question, "Question updated successfully"));
    }

    /**
     * Delete a question
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> deleteQuestion(
            @PathVariable String id,
            HttpServletRequest httpRequest
    ) {
        String userId = jwtUtils.extractUserIdFromToken(httpRequest);
        questionService.deleteQuestion(id, userId);
        return ResponseEntity.ok(ApiResponse.success(null, "Question deleted successfully"));
    }
}
