package org.springframework.cloud.stream.micrometer.binding;

import org.springframework.cloud.stream.annotation.Output;
import org.springframework.messaging.MessageChannel;

public interface MetricsBinding {

    String METRICS = "metrics";

    @Output(METRICS)
    MessageChannel applicationMetrics();
}
