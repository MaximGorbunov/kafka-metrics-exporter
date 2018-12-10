package org.springframework.cloud.stream.micrometer.domain;

import io.micrometer.core.instrument.Meter;
import lombok.Data;

@Data
public class Metric {
    private final Meter.Id id;
    private final Number mean;
}
