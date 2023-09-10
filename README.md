# Reactive Word Cloud - Kotlin

## Infrastructure Set Up
Create Kafka topic:
```shell
kafka-topics --bootstrap-server localhost:9092 --create --topic=word-cloud.chat-message --partitions=2
```
