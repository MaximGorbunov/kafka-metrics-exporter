package org.springframework.cloud.stream.micrometer.domain;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Collection;
import java.util.Date;
import java.util.Map;
import lombok.Data;

@Data
public class ApplicationMetrics {

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", timezone = "UTC")
    private final Date createdTime;

    private String name;

    private long interval;

    private Collection<Metric> metrics;

    private Map<String, Object> properties;

    @JsonCreator
    public ApplicationMetrics(@JsonProperty("name") String name, @JsonProperty("metrics") Collection<Metric> metrics) {
        this.name = name;
        this.metrics = metrics;
        this.createdTime = new Date();
    }
}
