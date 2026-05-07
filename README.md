# Order Service

Микросервис управления заказами: создание, резервирование стока, обработка статусов.
Взаимодействует с Catalog Service синхронно (Feign) и асинхронно (Kafka SAGA).

## Быстрый старт

### Dev

Запустить инфраструктуру:

```bash
docker-compose up -d postgres zookeeper kafka redis
```

Запустить сервис:

```bash
./gradlew bootRun --args='--spring.profiles.active=dev'
```

### Prod

> Перед запуском замените `jwt.secret` в `application.yml` на надёжный секрет (≥32 символа).

```bash
./gradlew bootJar
docker-compose --profile app up -d
```

## Адреса

| Сервис        | URL                                         |
|---------------|---------------------------------------------|
| Order Service | http://localhost:8082                       |
| Swagger UI    | http://localhost:8082/swagger-ui/index.html |
| PostgreSQL    | localhost:5435                              |
| Kafka         | localhost:9093                              |
| Redis         | localhost:6380                              |
| Prometheus    | http://localhost:9091                       |
| Grafana       | http://localhost:3001                       |

## Тесты

```bash
./gradlew test                # unit
./gradlew integrationTest     # integration (требует Docker)
```