package org.ganjp.api.cms.question;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Min;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.ganjp.api.cms.question.Question;

/**
 * DTO for updating an existing question
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateQuestionRequest {

    @Size(max = 255, message = "Question must not exceed 255 characters")
    private String question;

    @Size(max = 2000, message = "Answer must not exceed 2000 characters")
    private String answer;

    @Size(max = 500, message = "Tags must not exceed 500 characters")
    private String tags;

    private Question.Language lang;

    @Min(value = 0, message = "Display order must be non-negative")
    private Integer displayOrder;

    private Boolean isActive;
}
