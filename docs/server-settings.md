# Серверные настройки Social Rating 1.0.0

Действующий файл: `config/socialrating/server.json`. Полный пример создаётся рядом
как `server.example.json`. Параметры, отсутствующие в старом файле, получают значения
по умолчанию. Файлы владельца не перезаписываются. После редактирования выполни
`/socialrating admin reload`; подключённые клиенты получат изменения автоматически.
При неверном значении или повреждённом переводе прежние настройки продолжают работать.

```json
{
  "schemaVersion": 1,
  "defaultLanguage": "en_us",
  "ratingsEnabled": true,
  "reportsEnabled": true,
  "commentsEnabled": true,
  "allowEditing": true,
  "allowWithdrawal": true,
  "allowSkippedCategories": true,
  "ingenuityEnabled": true,
  "skillEnabled": true,
  "friendlinessEnabled": true,
  "ratingDistance": 6.0,
  "requireLineOfSight": true,
  "maxCommentLength": 300,
  "maxReportDetailsLength": 300,
  "maxDecisionReasonLength": 200,
  "nameplatesEnabled": true,
  "interactionHintEnabled": true,
  "maxNameplateDistance": 64,
  "summaryRefreshTicks": 100,
  "profileReviewLimit": 100,
  "requestsPerSecond": 20,
  "moderatorPermissionLevel": 2,
  "notifyOperators": true,
  "remindOperatorsOnJoin": true,
  "backupsEnabled": true,
  "backupOnStartup": true,
  "backupIntervalMinutes": 30,
  "backupRetention": 10
}
```

| Параметр | Что меняет | Допустимые значения |
| --- | --- | --- |
| `schemaVersion` | Версия формата настроек | `1` |
| `defaultLanguage` | Запасной язык и язык сообщений сервера | Код существующего файла в `lang/` |
| `ratingsEnabled` | Создание и изменение оценок | `true` / `false` |
| `reportsEnabled` | Подача новых жалоб | `true` / `false` |
| `commentsEnabled` | Написание комментариев к новым/изменяемым оценкам | `true` / `false` |
| `allowEditing` | Изменение своей действующей оценки | `true` / `false` |
| `allowWithdrawal` | Отзыв своей оценки | `true` / `false` |
| `allowSkippedCategories` | Возможность пропустить включённую категорию | `true` / `false` |
| `ingenuityEnabled` | Оценивание смекалки | `true` / `false` |
| `skillEnabled` | Оценивание навыков | `true` / `false` |
| `friendlinessEnabled` | Оценивание дружелюбия | `true` / `false` |
| `ratingDistance` | Расстояние для оценки и выбора игрока | 1–64 блока, допускается дробное |
| `requireLineOfSight` | Проверка видимости при выборе и оценивании | `true` / `false` |
| `maxCommentLength` | Лимит комментария | 1–1000 символов |
| `maxReportDetailsLength` | Лимит пояснения жалобы | 1–1000 символов |
| `maxDecisionReasonLength` | Лимит причины решения модератора | 1–500 символов |
| `nameplatesEnabled` | Разрешить рейтинг над игроками | `true` / `false` |
| `interactionHintEnabled` | Разрешить подсказку возле прицела | `true` / `false` |
| `maxNameplateDistance` | Верхний предел дальности рейтинга для клиентов | 8–64 блока |
| `summaryRefreshTicks` | Период обновления рейтинга над игроками | 20–1200 клиентских тиков; 100 ≈ 5 секунд |
| `profileReviewLimit` | Максимум загружаемых отзывов в каждом списке профиля | 1–100; среднее и счётчики учитывают всю базу |
| `requestsPerSecond` | Максимум запросов одного клиента за секунду | 4–1000; при превышении возвращается ошибка, наказание не назначается |
| `moderatorPermissionLevel` | Минимальный уровень прав Minecraft для меню/команд модерации | 1–4; по умолчанию 2 |
| `notifyOperators` | Сообщать модераторам о новой жалобе | `true` / `false` |
| `remindOperatorsOnJoin` | Напоминать об ожидающих жалобах при входе | `true` / `false`; зависит также от `notifyOperators` |
| `backupsEnabled` | Включить резервное копирование, включая ручную команду | `true` / `false` |
| `backupOnStartup` | Создать копию при запуске | `true` / `false`; зависит от `backupsEnabled` |
| `backupIntervalMinutes` | Интервал автоматических копий | 1–10080 минут |
| `backupRetention` | Число сохраняемых копий | 1–100 |

Хотя бы одна категория должна оставаться включённой. Отключение категории влияет
на новые и редактируемые оценки; сохранённые оценки остаются в истории и среднем.
Отключение комментариев также сохраняет существующие тексты. Уменьшение лимита
не обрезает старые данные, но при редактировании текст должен соответствовать новому лимиту.
Клиентское поле ввода ограничивает число UTF-16 символов; сервер дополнительно проверяет
число Unicode-кодовых точек.

`ratingsEnabled` и `reportsEnabled` управляют новыми изменениями. Просмотр профилей,
своей истории жалоб и обработка накопившихся жалоб остаются доступны. Отзыв оценки
управляется отдельно через `allowWithdrawal`. После удаления или отзыва можно
создать новый отзыв, когда оценивание включено. Шкала — 1–5; автор определяется
подключением, оценивать себя нельзя.

Личные настройки — `config/socialrating/client.json`: показ рейтинга, режим `number`
или `stars`, подсказка, дальность и язык. Клиент может скрыть рейтинг и уменьшить
дальность в пределах серверного ограничения. Стили меню и тексты меняются через
ресурс-пак и `lang/*.json`.

## Копирование и восстановление

Копии находятся в `config/socialrating/backups/database-*.json`. Планировщик
проверяет срок копирования раз в минуту. Ручная команда:

```text
/socialrating admin backup
```

Запись базы и создание копий происходят последовательно в отдельном потоке.
Успешное сохранение подтверждается после записи на диск. Перед публикацией копии
проверяется, что она читается; затем удаляются только старые файлы копий мода
согласно `backupRetention`. Прочие файлы в каталоге не удаляются.

Для восстановления останови сервер, сохрани текущий `database.json` отдельно,
скопируй нужную копию вместо него и запусти сервер. Мод не заменяет повреждённые
данные пустой базой и не восстанавливает старую копию автоматически.

Изменение `moderatorPermissionLevel` обновляет подсказки команд у подключённых игроков.
При повышении уровня убедись, что у владельца есть нужные права; консоль сервера
также позволяет выполнить `/socialrating admin reload`.
