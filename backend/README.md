# HoraireSync backend

API REST Spring Boot 3 (Java 21) qui lit les horaires publics des écoles, détecte les conflits, classe les groupes selon les préférences et génère le fichier .ics.

## Lancer

```bash
cd backend
./mvnw spring-boot:run          # API sur http://localhost:8080/api/v1
./mvnw verify                   # tests + rapport de couverture (target/site/jacoco/index.html)
```

Prérequis : Java 21 (`brew install openjdk@21` sur macOS, ou JDK 21 téléchargé depuis IntelliJ). Maven n'est pas nécessaire grâce au wrapper `mvnw`.

## Organisation

| Paquet | Rôle |
| --- | --- |
| `model` | Records communs à toutes les écoles (`Term`, `Course`, `Section`, `Meeting`…) |
| `adapter` | Interface `SchoolAdapter` et `SchoolAdapterRegistry` (index des adaptateurs par `schoolId`) |
| `adapter.uqam` | Adaptateur UQAM : seul code qui connaît le HTML d'etudier.uqam.ca |
| `http` | `HtmlSource` : téléchargement poli (User-Agent, délai entre requêtes, cache des pages) |
| `service` | `CatalogService` (lecture + cache), `ConflictService`, `RankingService`, `IcsExportService` |
| `web` | Contrôleurs `/api/v1` et format d'erreur unique `{code, message}` |
| `config` | Propriétés typées, cache Caffeine, CORS |

## Routes de lecture (phase 1)

| Méthode | Route |
| --- | --- |
| GET | `/api/v1/schools` |
| GET | `/api/v1/schools/{schoolId}/terms` |
| GET | `/api/v1/schools/{schoolId}/programs?q=` |
| GET | `/api/v1/schools/{schoolId}/programs/{code}/courses` |
| GET | `/api/v1/schools/{schoolId}/terms/{termId}/courses/{code}/sections` |

La route des groupes retourne `{ course, termId, sections }` afin que l'interface connaisse le titre du cours ajouté par sigle.

## Routes d'horaire (phase 2)

Sans état : le frontend renvoie les groupes qu'il détient, valeurs saisies à la main comprises. Elles fonctionnent donc aussi en mode manuel.

| Méthode | Route | Corps | Réponse |
| --- | --- | --- | --- |
| POST | `/api/v1/schedule/conflicts` | `{schoolId, termId, selections}` | Liste des chevauchements |
| POST | `/api/v1/schedule/rank` | `{sections, preferences, chosen}` | Score 0 à 100 par groupe, critères, conflits avec les choix déjà faits |
| POST | `/api/v1/schedule/export` | `{schoolId, termId, termLabel, termStart, termEnd, excludedDates, selections}` | Fichier `text/calendar` |

### Fichier .ics

- Bibliothèque : **ical4j 4.1.1**. Biweekly a été écarté : il ne sait produire le bloc VTIMEZONE qu'en le téléchargeant (tzurl.org).
- Un VEVENT par séance hebdomadaire (`RRULE:FREQ=WEEKLY;UNTIL=…Z`, `EXDATE` pour les congés et la relâche), un VEVENT par examen.
- Bloc VTIMEZONE `America/Toronto` compact (règles en vigueur seulement), généré à partir des règles du JDK.
- UID stable par séance (`uqam-20271-inf1120-020-lecture-tue-0930@horairesync`) : un nouvel import remplace l'ancien dans les agendas qui respectent l'UID.
- Pas de propriété `METHOD` : `METHOD:PUBLISH` appartient à iTIP (RFC 5546) et exige un `ORGANIZER` que l'on n'a pas.
- Export refusé (`INVALID_SCHEDULE`) si un jour, une heure ou une date de session manque. Professeur et local facultatifs.
- Tests : le fichier est relu par le parseur ical4j, les occurrences sont comptées et le validateur ical4j ne signale aucune erreur.

## Codes d'erreur

| Code | HTTP |
| --- | --- |
| `SCHOOL_NOT_SUPPORTED`, `PROGRAM_NOT_FOUND`, `COURSE_NOT_FOUND` | 404 |
| `INVALID_REQUEST`, `INVALID_SCHEDULE` | 400 |
| `PARSING_FAILED` | 502 |
| `SOURCE_UNAVAILABLE` | 503 |
| `INTERNAL_ERROR` | 500 |

## Respect du site source

- `Crawl-delay: 10` de robots.txt appliqué par hôte (`horairesync.http.crawl-delays`).
- Pages brutes et résultats analysés gardés 24 h en mémoire (Caffeine).
- User-Agent identifiant le projet (`horairesync.http.user-agent`).
- Les tests n'utilisent jamais le réseau : pages enregistrées dans `src/test/resources/fixtures/uqam/`.
