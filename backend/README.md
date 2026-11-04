# HoraireSync backend

API REST Spring Boot 3 (Java 21) qui lit les horaires publics des écoles et, en phase 2, génère le fichier .ics.

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
| `service` | `CatalogService` : choix de l'adaptateur et cache des résultats analysés |
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
