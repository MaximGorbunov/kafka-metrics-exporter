package org.springframework.cloud.stream.micrometer;

import static io.micrometer.core.instrument.config.MeterFilter.denyUnless;
import static org.springframework.cloud.stream.micrometer.binding.MetricsBinding.METRICS;

import io.micrometer.core.instrument.Clock;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.actuate.autoconfigure.metrics.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.stream.annotation.EnableBinding;
import org.springframework.cloud.stream.binding.BindableProxyFactory;
import org.springframework.cloud.stream.micrometer.binding.MetricsBinding;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.PatternMatchUtils;
import org.springframework.util.StringUtils;

@Configuration
@ConditionalOnProperty("spring.cloud.stream.bindings." + METRICS + ".destination")
@AutoConfigureBefore(CompositeMeterRegistryAutoConfiguration.class)
@EnableConfigurationProperties(ApplicationMetricsProperties.class)
@EnableBinding(MetricsBinding.class)
public class MetricsAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public MetricsPublisherConfig metricsPublisherConfig(ApplicationMetricsProperties metersPublisherProperties) {
        return new MetricsPublisherConfig(metersPublisherProperties);
    }

    @Bean
    KafkaMetricsRegistry kafkaMetricsRegistry(ApplicationMetricsProperties applicationMetricsProperties, MetricsBinding metricsBinding, MetricsPublisherConfig metricsPublisherConfig, Clock clock) {
        KafkaMetricsRegistry registry = new KafkaMetricsRegistry(applicationMetricsProperties, metricsBinding, metricsPublisherConfig, clock);
        if (StringUtils.hasText(applicationMetricsProperties.getMeterFilter())) {
            registry.config()
                    .meterFilter(denyUnless(id -> PatternMatchUtils.simpleMatch(applicationMetricsProperties.getMeterFilter(), id.getName())));
        }
        return registry;
    }

    @Bean
    public BeanFactoryPostProcessor metersPublisherBindingRegistrant() {
        return beanFactory -> {
            RootBeanDefinition emitterBindingDefinition = new RootBeanDefinition(BindableProxyFactory.class);
            emitterBindingDefinition.getConstructorArgumentValues().addGenericArgumentValue(MetricsBinding.class);
            ((DefaultListableBeanFactory) beanFactory).registerBeanDefinition(MetricsBinding.class.getName(), emitterBindingDefinition);
        };
    }

}
