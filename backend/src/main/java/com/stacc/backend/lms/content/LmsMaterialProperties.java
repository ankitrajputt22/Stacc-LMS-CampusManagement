package com.stacc.backend.lms.content;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

@ConfigurationProperties(prefix = "stacc.materials")
public record LmsMaterialProperties(DataSize maxFileSize) {

    public LmsMaterialProperties {
        if (maxFileSize == null || maxFileSize.toBytes() <= 0) {
            throw new IllegalArgumentException("stacc.materials.max-file-size must be a positive size, such as 25MB");
        }
    }
}
