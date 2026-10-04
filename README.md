# ResPlan backend

REST API программного средства календарно-сетевого и ресурсного планирования проектов ResPlan
(лабораторные работы № 8–9 по дисциплине «Технологии проектирования сложных информационных систем», БГУИР).

Стек: Java 21, Spring Boot 3.4 (Web, Data JPA, Validation, Security), JWT (jjwt 0.11), springdoc-openapi 2.8,
PostgreSQL 18 (H2 – для тестов и демонстрации).

## Запуск

```bash
# демонстрационный режим: встроенная БД H2 и тестовые данные
mvn spring-boot:run

# PostgreSQL: база создается скриптом db/create_database.sql, таблицы – Hibernate
export RESPLAN_DB_USER=resplan_app RESPLAN_DB_PASSWORD=...
mvn spring-boot:run -Dspring-boot.run.profiles=postgres,demo

# автоматические тесты (43 теста)
mvn test
```

Интерактивная документация: <http://localhost:8080/swagger-ui.html>, описание OpenAPI 3: `/v3/api-docs`
(копия – `docs/openapi.json`). Примеры запросов и ответов – `docs/samples`, сценарий их получения –
`docs/capture_samples.py`.

Демонстрационные учетные записи (пароль `resplan`): `pm`, `pm2` – проектные менеджеры, `rm` – ресурсный
менеджер, `ld` – сотрудник L&D, `emp`, `emp2` – сотрудники.

## Ресурсы API

| Вариант использования | Метод и путь | Роль |
|---|---|---|
| UC-4 Войти в систему | `POST /api/auth/login`, `GET /api/auth/me` | все |
| UC-1 Управлять расписанием и сетевым графиком | `GET /api/projects`, `GET /api/projects/{id}`, `GET /api/projects/{id}/tasks`, `POST /api/projects/{id}/tasks`, `PUT/DELETE /api/projects/{id}/tasks/{taskId}`, `GET /api/projects/{id}/schedule` | PM (изменение), все (чтение) |
| UC-2 Создать запрос на подбор кандидата | `POST /api/requests`, `DELETE /api/requests/{id}` | PM |
| UC-3 Утвердить кандидата | `POST /api/requests/{id}/approval`, `POST /api/requests/{id}/rejection` | PM |
| UC-5 Просматривать выборку кандидатов | `GET /api/requests`, `GET /api/requests/{id}`, `GET /api/requests/{id}/candidates`, `POST /api/requests/{id}/proposal`, `POST /api/requests/{id}/external-hire` | RM |
| UC-6 Разрешить ресурсный конфликт | `GET /api/conflicts`, `GET /api/conflicts/{id}`, `POST /api/conflicts/{id}/resolution`, `POST /api/conflicts/{id}/escalation` | RM |
| UC-7 Управлять бронированием кандидатов | `GET /api/employees`, `GET /api/employees/{id}`, `GET /api/bookings`, `GET /api/bookings/{id}`, `POST /api/bookings`, `PATCH /api/bookings/{id}`, `DELETE /api/bookings/{id}` | RM |
| UC-8 Просматривать персональный график загрузки | `GET /api/me/calendar`, `GET /api/employees/{id}/calendar` | все / PM, RM, L&D |
| UC-9 Резервировать время на обучение | `GET /api/courses`, `POST /api/trainings`, `DELETE /api/trainings/{id}` | L&D |
| Уведомления | `GET /api/notifications`, `PATCH /api/notifications/{id}` | все |

Ошибки возвращаются в формате RFC 9457 (`application/problem+json`): 400 – некорректные поля (`errors`),
401 – нет или просрочен маркер, 403 – недостаточно прав, 404 – ресурс не найден, 409 – недопустимый
переход статуса, 422 – нарушение бизнес-правила (`code`).

## Структура

```
api/          REST-контроллеры, DTO, обработчик ошибок, разрешение текущего пользователя
security/     JWT: TokenIssuer, TokenVerifier, JwtTokenService, фильтр, конфигурация Spring Security
service/      сценарии вариантов использования, BookingLifecycle
domain/       сущности JPA и правила предметной области
booking/      цепочка правил проверки бронирования
matching/     критерии интеллектуального подбора (стратегии)
scheduling/   метод критического пути
repository/   репозитории Spring Data JPA (BaseRepository)
event/        доменные события и уведомления
config/       демонстрационные данные и параметры
```
