package org.ganjp.api.cms.question;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.question.Question;

import java.time.LocalDateTime;

/**
 * DTO for question response
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuestionResponse {

    private String id;
    private String question;
    private String answer;
    private String tags;
    private Question.Language lang;
    private Integer displayOrder;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private String createdBy;
    private String updatedBy;

    /**
     * Convert from entity to response DTO
     */
    public static QuestionResponse from(Question question) {
        return QuestionResponse.builder()
                .id(question.getId())
                .question(question.getQuestion())
                .answer(question.getAnswer())
                .tags(question.getTags())
                .lang(question.getLang())
                .displayOrder(question.getDisplayOrder())
                .isActive(question.getIsActive())
                .createdAt(question.getCreatedAt())
                .updatedAt(question.getUpdatedAt())
                .createdBy(question.getCreatedBy())
                .updatedBy(question.getUpdatedBy())
                .build();
    }
}
