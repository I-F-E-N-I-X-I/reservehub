# REST API

Базовый путь: `/api/v1`. Тела запросов и ответов — JSON. Все маршруты, кроме регистрации и health, требуют HTTP Basic. Время возвращается в ISO 8601 со смещением.

| Метод и путь | Доступ | Назначение |
|---|---|---|
| POST /users | Публичный | Регистрация USER, 201 |
| GET /equipment?search=&offset=0&limit=20 | USER, ADMIN | Поиск и пагинация |
| GET /equipment/{id} | USER, ADMIN | Одна позиция |
| POST /equipment | ADMIN | Создание позиции, 201 |
| POST /equipment/{id}/stock | ADMIN | Изменить общее и доступное количество |
| POST /reservations | USER, ADMIN | Создать резерв, 201 |
| GET /reservations?offset=0&limit=20 | USER, ADMIN | Свои резервы; ADMIN видит все |
| GET /reservations/{id} | Владелец, ADMIN | Детали резерва |
| GET /reservations/{id}/events | Владелец, ADMIN | История переходов |
| POST /reservations/{id}/cancel | Владелец, ADMIN | Отменить RESERVED |
| POST /admin/reservations/{id}/issue | ADMIN | RESERVED → ISSUED |
| POST /admin/reservations/{id}/return | ADMIN | ISSUED → RETURNED |
| GET /admin/reports/reservations | ADMIN | COUNT и SUM по статусам |
| GET /actuator/health | Публичный, вне /api/v1 | Состояние приложения |

## Тела запросов

Регистрация: username содержит 3–64 латинских буквы, цифры, `_` или `-`. Пароль: 12–64 печатных ASCII-символа без пробелов (ограничение предотвращает неявное усечение BCrypt).

```json
{"username":"alice","password":"alice-demo-password"}
```

Создание оборудования: непустое name до 120 символов; description обязателен, до 1000; quantity от 0 до 1 000 000.

```json
{"name":"Camera","description":"Camera with lens","quantity":3}
```

Корректировка количества: delta от -1 000 000 до 1 000 000. Нельзя убрать зарезервированное или выданное оборудование.

```json
{"delta":-1}
```

Создание резерва: equipmentId > 0, quantity от 1 до 1 000 000. Пользователь определяется по аутентификации, передать userId нельзя.

```json
{"equipmentId":1,"quantity":2}
```

Действия cancel/issue/return не требуют тела. Успех возвращает обновлённый резерв и HTTP 200.

## Примеры ответов

Каталог:

```json
{"items":[{"id":1,"name":"Camera","description":"Camera with lens","totalQuantity":3,"availableQuantity":1}],"offset":0,"limit":20}
```

Резерв:

```json
{"id":1,"userId":2,"equipmentId":1,"quantity":2,"status":"RESERVED","createdAt":"2026-11-03T12:00:00Z","updatedAt":"2026-11-03T12:00:00Z"}
```

История сортируется по возрастанию id. Каталог — по id, резервы — по убыванию id. Пагинация не возвращает total: клиент завершает обход, когда items короче limit. При конкурентном добавлении записей offset-пагинация не гарантирует снимок.

## Ошибки

| Код | Ситуация |
|---|---|
| 400 | Невалидное тело, неизвестные поля, неверный id/limit/offset |
| 401 | Нет учётных данных либо неверный пароль |
| 403 | Операция требует ADMIN |
| 404 | Ресурс отсутствует либо чужой резерв недоступен |
| 409 | Не хватает остатков, переход запрещён, имя уже занято |

```json
{"type":"about:blank","title":"Conflict","status":409,"detail":"Insufficient available stock","instance":"/api/v1/reservations"}
```

Ошибки безопасности содержат минимальные status/title. SQL и пароли в ответ не включаются.
