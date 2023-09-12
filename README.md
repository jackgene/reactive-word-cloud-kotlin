# Reactive Word Cloud - Kotlin

## Infrastructure Set Up
Create Kafka topic:
```shell
kafka-topics --bootstrap-server localhost:9092 --create --topic=word-cloud.chat-message --partitions=2
```

Observe Kafka topic chat message records:
```shell
kafka-console-consumer --bootstrap-server=localhost:9092 --topic=word-cloud.words-by-person
```
TODO:
- safe enough state to be able to restart
- refactor, unit test etc