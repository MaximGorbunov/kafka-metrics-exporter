# Metrics starter
Spring boot starter предназначенный для отправки метрик в приложений
Доработка [spring-cloud-stream](https://github.com/spring-cloud/spring-cloud-stream)

#Подключение
Как начать отправлять метрики в кафку? 
##1. Подключить стартер
```$xslt
compile com.mgorbunov:spring-boot-metrics-starter:0.0.1'
```
##2. Добавить конфиги
- в config server добавляем:
```
spring:
  cloud:
    stream:
      bindings:
        metrics:
          destination: metrics
          producer:
            headerMode: none
      kafka.binder:
        brokers: mqhost1:9092, mqhost2:9092, mqhost3:9092
      metrics:
        schedule-interval: 10s
        properties: "HOST"
```