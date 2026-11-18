package com.stacc.backend.lms.question;

import java.util.Objects;

import com.stacc.backend.common.validation.Require;
import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class LmsQuestionSource {

    @Column(nullable = false)
    private int position;

    @Column(name = "source_type", nullable = false, length = 30)
    private String sourceType;

    @Column(name = "source_id", nullable = false)
    private long sourceId;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(name = "file_name", length = 255)
    private String fileName;

    @Column(name = "page_number")
    private Integer pageNumber;

    @Column(name = "slide_number")
    private Integer slideNumber;

    @Column(name = "page_id")
    private Long pageId;

    protected LmsQuestionSource() {
    }

    public LmsQuestionSource(int position, String sourceType, long sourceId, String title, String fileName, Integer pageNumber,
            Integer slideNumber, Long pageId) {
        this.position = position;
        this.sourceType = Require.text(sourceType, "sourceType", 30);
        this.sourceId = sourceId;
        String trimmed = title == null ? "Source" : title.strip();
        this.title = trimmed.length() > 200 ? trimmed.substring(0, 200) : trimmed;
        this.fileName = fileName == null || fileName.length() <= 255 ? fileName : fileName.substring(0, 255);
        this.pageNumber = pageNumber;
        this.slideNumber = slideNumber;
        this.pageId = pageId;
    }

    public int getPosition() {
        return position;
    }

    public String getSourceType() {
        return sourceType;
    }

    public long getSourceId() {
        return sourceId;
    }

    public String getTitle() {
        return title;
    }

    public String getFileName() {
        return fileName;
    }

    public Integer getPageNumber() {
        return pageNumber;
    }

    public Integer getSlideNumber() {
        return slideNumber;
    }

    public Long getPageId() {
        return pageId;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof LmsQuestionSource source && position == source.position;
    }

    @Override
    public int hashCode() {
        return Objects.hash(position);
    }
}
