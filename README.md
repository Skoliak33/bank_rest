# Система управления банковскими картами

REST API на Spring Boot для управления банковскими картами с JWT-аутентификацией, шифрованием данных и ролевым доступом.

## Технологии

| Категория | Технологии |
|---|---|
| Язык / Фреймворк | Java 17, Spring Boot 3.4 |
| Безопасность | Spring Security, JWT (stateless), BCrypt, AES-256-GCM |
| Данные | Spring Data JPA, PostgreSQL, Liquibase (YAML-миграции) |
| Кеширование | Spring Cache (in-memory, `@Cacheable` / `@CacheEvict`) |
| Устойчивость | Resilience4j (rate limiting на login) |
| Наблюдаемость | Spring AOP (аудит-лог), Spring Actuator (health/readiness probes) |
| Планировщик | `@Scheduled` (автоматическая просрочка карт) |
| Документация API | SpringDoc OpenAPI / Swagger UI |
| Тестирование | JUnit 5, Mockito, MockMvc, Testcontainers (PostgreSQL) |
| Качество кода | Checkstyle (автоматически при сборке) |
| Инфраструктура | Docker (multi-stage build), Docker Compose, Maven Wrapper |

## Запуск

### Вариант 1: Локальная разработка

```bash
docker-compose up -d postgres    # PostgreSQL
./mvnw spring-boot:run           # приложение на :8080
```

### Вариант 2: Всё в Docker

```bash
docker-compose up --build        # PostgreSQL + приложение одной командой
```

Приложение: `http://localhost:8080`
Swagger UI: `http://localhost:8080/swagger-ui.html`
OpenAPI JSON: `http://localhost:8080/v3/api-docs`

## Предзаполненные данные

| Email | Пароль | Роль |
|---|---|---|
| admin@bank.com | admin123 | ADMIN |

Новых пользователей можно создать через `POST /api/auth/register` (роль USER) или через ADMIN API.

## API

Все запросы (кроме auth) требуют заголовок `Authorization: Bearer <token>`.

### Аутентификация (публичные эндпоинты)

| Метод | Путь | Описание |
|---|---|---|
| POST | `/api/auth/login` | Вход, получение JWT-токена. Rate limit: 5 попыток/мин |
| POST | `/api/auth/register` | Регистрация нового пользователя (роль USER) |

### Управление пользователями (только ADMIN)

| Метод | Путь | Описание |
|---|---|---|
| GET | `/api/users` | Список пользователей. Пагинация: `?page=0&size=10&sort=id,desc` |
| GET | `/api/users/{id}` | Пользователь по ID |
| POST | `/api/users` | Создание пользователя с ролью |
| DELETE | `/api/users/{id}` | Удаление пользователя |

### Управление картами

| Метод | Путь | Доступ | Описание |
|---|---|---|---|
| GET | `/api/cards` | ADMIN: все; USER: свои | Список карт. Фильтр: `?status=ACTIVE` |
| GET | `/api/cards/{id}` | ADMIN или владелец | Карта по ID (кешируется) |
| POST | `/api/cards` | ADMIN | Создание карты |
| DELETE | `/api/cards/{id}` | ADMIN | Удаление карты |
| PATCH | `/api/cards/{id}/block` | ADMIN: любую; USER: свою | Блокировка карты |
| PATCH | `/api/cards/{id}/activate` | ADMIN | Активация карты |
| POST | `/api/cards/transfer` | Любая роль (свои карты) | Перевод между своими картами |

## Безопасность

| Механизм | Описание |
|---|---|
| **JWT** | Stateless аутентификация. Токен содержит email, роль, userId. Срок жизни: 24ч |
| **BCrypt** | Хеширование паролей (cost factor 10) |
| **AES-256-GCM** | Шифрование номеров карт в БД. Authenticated encryption (защита от подмены) |
| **Маскирование** | Номера карт в API: `**** **** **** 1234`. Полный номер не возвращается |
| **Rate Limiting** | Resilience4j на `/api/auth/login`: 5 попыток/мин, далее `429 Too Many Requests` |
| **Ролевой доступ** | `@PreAuthorize` на контроллерах. ADMIN / USER с разными правами |
| **Optimistic Locking** | `@Version` на Card: защита от гонок при параллельных переводах (`409 Conflict`) |
| **CORS** | Настроен для Swagger UI. В проде ограничить `allowedOriginPatterns` |
| **Секреты** | Через env-переменные: `JWT_SECRET`, `AES_KEY`, `DB_PASSWORD`. Дефолты только для dev |

## Архитектурные решения

### Слоистая архитектура

```
Controller -> Service -> Repository -> PostgreSQL
     |            |
     |       @Transactional
     |       @Cacheable / @CacheEvict
     |       @Version (optimistic lock)
     |
  @PreAuthorize (роли)
  @RateLimiter (brute-force)
  @ParameterObject (Pageable в Swagger)
```

### Кеширование (Spring Cache)

- `loadUserByUsername` - `@Cacheable("users")` - JWT-фильтр не дёргает БД на каждый запрос
- `getCardById` - `@Cacheable("cards")` - повторный GET одной карты из кеша
- Все мутирующие методы (create/block/activate/delete/transfer) - `@CacheEvict` сбрасывает кеш
- In-memory ConcurrentMapCache. Для продакшена - заменить на Redis

### Optimistic Locking (`@Version`)

Поле `version` на entity Card. Два параллельных перевода с одной карты:
первый пройдёт, второй получит `409 Conflict - Concurrent modification detected, please retry`.

### Аудит-лог (Spring AOP)

`@Aspect` перехватывает все методы `CardService` и логирует:
```
[AUDIT] user=admin@bank.com action=transfer args=[TransferRequest[sourceCardId=1, targetCardId=2, amount=500]] status=SUCCESS
[AUDIT] user=user@test.com  action=blockCard args=[5] status=FAILED reason=Card is already blocked
```

### Автоматическая просрочка карт (`@Scheduled`)

Ежедневно в 00:00 планировщик находит активные карты с `expirationDate < today`
и переводит их в статус `EXPIRED`.

### Фильтрация карт (JPA Specification)

Динамическая фильтрация через Criteria API: по статусу, по владельцу.
ADMIN видит все карты, USER автоматически видит только свои.

### Обработка ошибок (GlobalExceptionHandler)

Единый формат ответа для всех ошибок:

```json
{
  "timestamp": "2026-03-23T15:30:00",
  "status": 400,
  "message": "Insufficient funds on source card",
  "details": null
}
```

| HTTP-код | Когда |
|---|---|
| 400 | Валидация, бизнес-ошибки (недостаточно средств, карта уже заблокирована) |
| 401 | Нет токена или невалидный токен / неверный пароль |
| 403 | Нет прав (USER пытается создать карту, посмотреть чужую) |
| 404 | Карта/пользователь не найден |
| 409 | Конкурентное изменение (optimistic lock) |
| 429 | Превышен лимит попыток логина |
| 500 | Непредвиденная ошибка (логируется в stdout) |

## Тесты

```bash
./mvnw test                                    # все тесты
./mvnw -Dtest=CardServiceTest test             # один класс
./mvnw -Dtest=CardServiceTest#transfer* test   # один метод
```

59 тестов:

| Тип | Кол-во | Что тестируется |
|---|---|---|
| Unit (сервисы) | 24 | Переводы, блокировка, CRUD пользователей, регистрация, optimistic lock |
| Unit (утилиты) | 6 | AES encrypt/decrypt, Luhn-генерация, маскирование |
| Controller (MockMvc) | 16 | HTTP-статусы, роли, валидация, transfer, blockCard, getById |
| AOP Audit | 5 | Логирование успехов/ошибок, null-аргументы |
| Rate Limiter | 1 | 429 после 5 попыток логина |
| Scheduler | 2 | Автоматическая просрочка карт |
| Cache | 2 | Кеширование и инвалидация |
| Integration (Testcontainers) | 3 | Полный flow на реальном PostgreSQL |

## Docker

### Сборка образа

```bash
docker build -t bankcards .
```

Multi-stage build: `maven:3.9-eclipse-temurin-17` (сборка) -> `eclipse-temurin:17-jre-alpine` (runtime, ~200MB).

### Docker Compose

```bash
docker-compose up --build
```

- PostgreSQL 16 с healthcheck (`pg_isready`)
- Приложение с healthcheck на `/actuator/health/readiness`
- `depends_on: condition: service_healthy` - app стартует после готовности БД
- Liquibase автоматически применяет миграции

### Health Checks (Spring Actuator)

| Эндпоинт | Назначение | Kubernetes |
|---|---|---|
| `/actuator/health` | Общий статус (БД, диск) | - |
| `/actuator/health/liveness` | Процесс жив? | `livenessProbe` |
| `/actuator/health/readiness` | Готов принимать трафик? | `readinessProbe` |

### Переменные окружения

| Переменная | По умолчанию | Описание |
|---|---|---|
| `DB_HOST` | localhost | Хост PostgreSQL |
| `DB_PORT` | 5432 | Порт PostgreSQL |
| `DB_NAME` | bankcards | Имя базы данных |
| `DB_USERNAME` | postgres | Пользователь БД |
| `DB_PASSWORD` | postgres | Пароль БД |
| `JWT_SECRET` | (dev-ключ) | Base64-ключ для подписи JWT |
| `JWT_EXPIRATION` | 86400000 | Время жизни токена (мс) |
| `AES_KEY` | (dev-ключ) | Base64-ключ для AES шифрования карт |

## Качество кода

```bash
./mvnw checkstyle:check    # линтер (запускается автоматически при сборке)
./mvnw clean verify        # полная проверка: checkstyle + compile + tests + package
```

## Структура проекта

```
src/main/java/com/example/bankcards/
  aspect/          AOP аудит-логирование
  config/          SecurityConfig, OpenApiConfig
  controller/      REST-контроллеры (Auth, User, Card)
  dto/             Request/Response объекты (Java records)
  entity/          JPA-сущности (User, Card) + enums (Role, CardStatus)
  exception/       Кастомные исключения + GlobalExceptionHandler
  repository/      Spring Data JPA репозитории
  scheduler/       Планировщик просрочки карт
  security/        JWT (провайдер, фильтр, UserDetails)
  service/         Бизнес-логика (Auth, User, Card)
  util/            Шифрование, маскирование, генерация номеров

src/main/resources/
  application.yml           Конфигурация (env-переменные)
  application-test.yml      Тестовый профиль (H2)
  db/migration/             Liquibase YAML-миграции (4 файла)

src/test/java/com/example/bankcards/
  aspect/          Тесты AOP аудита
  controller/      MockMvc тесты + Rate Limiter
  integration/     Testcontainers (PostgreSQL)
  scheduler/       Тесты планировщика
  service/         Unit-тесты сервисов + кеша
  util/            Тесты шифрования/маскирования
```
