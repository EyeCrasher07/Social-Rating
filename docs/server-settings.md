# Social Rating 1.0.0 server settings

**English** · [Русский](server-settings.ru.md)

Edit `config/socialrating/server.json`. A complete example is created alongside it
as `server.example.json`. Missing settings use their default values. Existing files
are not overwritten. After editing, run `/socialrating admin reload`; connected
clients receive the changes automatically. If a setting or translation file is
invalid, the previous configuration stays active.

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

| Setting | Purpose | Allowed values |
| --- | --- | --- |
| `schemaVersion` | Settings format version | `1` |
| `defaultLanguage` | Fallback language and language of server messages | Code of an existing file in `lang/` |
| `ratingsEnabled` | Create and edit ratings | `true` / `false` |
| `reportsEnabled` | Submit new reports | `true` / `false` |
| `commentsEnabled` | Write comments on new or edited ratings | `true` / `false` |
| `allowEditing` | Edit your active rating | `true` / `false` |
| `allowWithdrawal` | Withdraw your rating | `true` / `false` |
| `allowSkippedCategories` | Skip an enabled category | `true` / `false` |
| `ingenuityEnabled` | Rate ingenuity | `true` / `false` |
| `skillEnabled` | Rate skill | `true` / `false` |
| `friendlinessEnabled` | Rate friendliness | `true` / `false` |
| `ratingDistance` | Maximum distance for selecting and rating a player | 1–64 blocks; decimals allowed |
| `requireLineOfSight` | Require visibility when selecting and rating a player | `true` / `false` |
| `maxCommentLength` | Comment length limit | 1–1000 characters |
| `maxReportDetailsLength` | Report details length limit | 1–1000 characters |
| `maxDecisionReasonLength` | Moderator decision reason length limit | 1–500 characters |
| `nameplatesEnabled` | Allow ratings above players | `true` / `false` |
| `interactionHintEnabled` | Allow the hint near the crosshair | `true` / `false` |
| `maxNameplateDistance` | Maximum rating display distance clients can choose | 8–64 blocks |
| `summaryRefreshTicks` | Refresh interval for ratings above players | 20–1200 client ticks; 100 ≈ 5 seconds |
| `profileReviewLimit` | Maximum reviews loaded in each profile list | 1–100; averages and counts include the entire database |
| `requestsPerSecond` | Maximum requests per client per second | 4–1000; exceeding it returns an error without punishment |
| `moderatorPermissionLevel` | Minimum Minecraft permission level for moderation menus and commands | 1–4; default 2 |
| `notifyOperators` | Notify moderators about new reports | `true` / `false` |
| `remindOperatorsOnJoin` | Remind moderators about pending reports when they join | `true` / `false`; also requires `notifyOperators` |
| `backupsEnabled` | Enable backups, including the manual command | `true` / `false` |
| `backupOnStartup` | Create a backup at startup | `true` / `false`; requires `backupsEnabled` |
| `backupIntervalMinutes` | Automatic backup interval | 1–10080 minutes |
| `backupRetention` | Number of backups to keep | 1–100 |

At least one category must stay enabled. Disabling a category affects new and
edited ratings; existing scores remain in the history and average. Disabling
comments also preserves existing text. Reducing a length limit does not truncate
saved data, but edited text must meet the new limit. Client input fields count
UTF-16 code units; the server also checks the number of Unicode code points.

`ratingsEnabled` and `reportsEnabled` control new changes. Players can still view
profiles and their report history, and moderators can process existing reports.
Withdrawal is controlled separately by `allowWithdrawal`. After a review is
removed or withdrawn, its author can post a new one when ratings are enabled.
Scores range from 1 to 5. The connection identifies the author; players cannot
rate themselves.

Personal settings are in `config/socialrating/client.json`: rating display,
`number` or `stars` mode, interaction hint, distance, and language. Clients can
hide ratings or reduce their display distance within the server limit. Menu
styles and text can be changed through a resource pack and `lang/*.json`.

## Backups and recovery

Backups are stored in `config/socialrating/backups/database-*.json`. The scheduler
checks once a minute whether a backup is due. To create a manual backup, run:

```text
/socialrating admin backup
```

Database writes and backups run sequentially on a separate thread. A successful
save is confirmed after the data is written to disk. Each backup is checked for
readability before it becomes available. Only old mod backup files are removed
according to `backupRetention`; other files in the directory are left alone.

To restore a backup, stop the server, keep a separate copy of the current
`database.json`, replace it with the chosen backup, and start the server. The mod
does not replace damaged data with an empty database or restore an older backup
automatically.

Changing `moderatorPermissionLevel` updates command suggestions for connected
players. Before raising it, make sure the owner has the required permissions.
You can also run `/socialrating admin reload` from the server console.
