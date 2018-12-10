package org.springframework.cloud.stream.micrometer;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Clock;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.FunctionTimer;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.LongTaskTimer;
import io.micrometer.core.instrument.Measurement;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.Meter.Id;
import io.micrometer.core.instrument.Meter.Type;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.distribution.DistributionStatisticConfig;
import io.micrometer.core.instrument.distribution.HistogramSupport;
import io.micrometer.core.instrument.distribution.pause.PauseDetector;
import io.micrometer.core.instrument.internal.DefaultGauge;
import io.micrometer.core.instrument.internal.DefaultLongTaskTimer;
import io.micrometer.core.instrument.internal.DefaultMeter;
import io.micrometer.core.instrument.step.StepCounter;
import io.micrometer.core.instrument.step.StepDistributionSummary;
import io.micrometer.core.instrument.step.StepFunctionCounter;
import io.micrometer.core.instrument.step.StepFunctionTimer;
import io.micrometer.core.instrument.step.StepTimer;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.ToDoubleFunction;
import java.util.function.ToLongFunction;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.cloud.stream.micrometer.binding.MetricsBinding;
import org.springframework.cloud.stream.micrometer.domain.ApplicationMetrics;
import org.springframework.cloud.stream.micrometer.domain.Metric;
import org.springframework.context.SmartLifecycle;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;

@Slf4j
public class KafkaMetricsRegistry extends MeterRegistry implements SmartLifecycle {

    private static final Log logger = LogFactory.getLog(DefaultDestinationPublishingMeterRegistry.class);

    private final MetricsPublisherConfig metricsPublisherConfig;

    private final Consumer<String> metricsConsumer;

    private final ApplicationMetricsProperties applicationProperties;

    private final ObjectMapper objectMapper = new ObjectMapper();

    private ScheduledFuture<?> publisher;

    KafkaMetricsRegistry(ApplicationMetricsProperties applicationProperties,
                         MetricsBinding metricsBinding, MetricsPublisherConfig metricsPublisherConfig, Clock clock) {
        super(clock);
        log.info("KafkaMetricsRegistry created");
        this.metricsPublisherConfig = metricsPublisherConfig;
        this.metricsConsumer = new MessageChannelPublisher(metricsBinding);
        this.applicationProperties = applicationProperties;
    }

    @Override
    public void start() {
        start(Executors.defaultThreadFactory());
    }

    @Override
    public void stop() {
        if (publisher != null) {
            publisher.cancel(false);
            publisher = null;
        }
    }

    @Override
    public boolean isRunning() {
        return this.publisher != null;
    }

    @Override
    public int getPhase() {
        return 0;
    }

    @Override
    public boolean isAutoStartup() {
        return true;
    }

    @Override
    public void stop(Runnable callback) {
        this.stop();
        callback.run();
    }

    @Override
    protected <T> Gauge newGauge(Meter.Id id, T obj, ToDoubleFunction<T> f) {
        return new DefaultGauge<>(id, obj, f);
    }

    @Override
    protected Counter newCounter(Meter.Id id) {
        return new StepCounter(id, clock, metricsPublisherConfig.step().toMillis());
    }

    @Override
    protected LongTaskTimer newLongTaskTimer(Meter.Id id) {
        return new DefaultLongTaskTimer(id, clock);
    }

    @Override
    protected TimeUnit getBaseTimeUnit() {
        return TimeUnit.MILLISECONDS;
    }

    protected void publish() {
        log.info("KafkaMetricsRegistry publish metrics");
        List<org.springframework.cloud.stream.micrometer.domain.Metric> aggregatedMeters = new ArrayList<>();
        for (Meter meter : this.getMeters()) {
            if (meter instanceof HistogramSupport) {
                aggregatedMeters.add(new Metric(meter.getId(), getMean((HistogramSupport) meter)));
            } else if (meter instanceof Gauge) {
                aggregatedMeters.add(new Metric(meter.getId(), ((Gauge) meter).value()));
            } else if (meter instanceof Counter) {
                aggregatedMeters.add(new Metric(meter.getId(), ((Counter) meter).count()));
            }
        }
        if (!aggregatedMeters.isEmpty()) {
            ApplicationMetrics metrics = new ApplicationMetrics(this.applicationProperties.getKey(), aggregatedMeters);
            metrics.setInterval(this.metricsPublisherConfig.step().toMillis());
            metrics.setProperties(this.applicationProperties.getExportProperties());
            try {
                String jsonString = this.objectMapper.writeValueAsString(metrics);
                this.metricsConsumer.accept(jsonString);
            } catch (JsonProcessingException e) {
                logger.warn("Error producing JSON String representation metric data", e);
            }
        }
    }

    @Override
    protected Timer newTimer(Id id, DistributionStatisticConfig distributionStatisticConfig,
                             PauseDetector pauseDetector) {
        return new StepTimer(id, clock, distributionStatisticConfig, pauseDetector, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionTimer newFunctionTimer(Id id, T obj, ToLongFunction<T> countFunction,
                                                 ToDoubleFunction<T> totalTimeFunction, TimeUnit totalTimeFunctionUnits) {
        return new StepFunctionTimer<T>(id, clock, metricsPublisherConfig.step().toMillis(), obj, countFunction, totalTimeFunction,
                totalTimeFunctionUnits, getBaseTimeUnit());
    }

    @Override
    protected <T> FunctionCounter newFunctionCounter(Id id, T obj, ToDoubleFunction<T> valueFunction) {
        return new StepFunctionCounter<>(id, clock, metricsPublisherConfig.step().toMillis(), obj, valueFunction);
    }

    @Override
    protected Meter newMeter(Id id, Type type, Iterable<Measurement> measurements) {
        return new DefaultMeter(id, type, measurements);
    }

    @Override
    protected DistributionSummary newDistributionSummary(Id id, DistributionStatisticConfig distributionStatisticConfig, double scale) {
        return new StepDistributionSummary(id, clock, distributionStatisticConfig, scale);
    }

    @Override
    protected DistributionStatisticConfig defaultHistogramConfig() {
        return DistributionStatisticConfig.builder().expiry(metricsPublisherConfig.step()).build()
                .merge(DistributionStatisticConfig.DEFAULT);
    }

    private void start(ThreadFactory threadFactory) {
        if (publisher != null) {
            stop();
        }
        publisher = Executors.newSingleThreadScheduledExecutor(threadFactory).scheduleAtFixedRate(this::publish,
                metricsPublisherConfig.step().toMillis(), metricsPublisherConfig.step().toMillis(),
                TimeUnit.MILLISECONDS);
    }


    private Number getMean(HistogramSupport histogramSupport) {
        return histogramSupport.takeSnapshot().mean();
    }

    private static final class MessageChannelPublisher implements Consumer<String> {

        private final MetricsBinding metersPublisherBinding;

        MessageChannelPublisher(MetricsBinding metricsBinding) {
            this.metersPublisherBinding = metricsBinding;
        }

        @Override
        public void accept(String metricData) {
            logger.trace(metricData);
            Message<String> message = MessageBuilder.withPayload(metricData).setHeader("STREAM_CLOUD_STREAM_VERSION", "2.x").build();
            this.metersPublisherBinding.applicationMetrics().send(message);
        }
    }
}
