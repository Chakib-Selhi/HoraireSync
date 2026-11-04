# PRD : HoraireSync

Nov 4, 2026 · @Chakib

## Vue d'ensemble

HoraireSync est une application web qui transforme une liste de cours universitaires en fichier .ics importable dans Apple Calendar, Google Calendar ou Outlook. L'utilisateur choisit son école, son programme et ses cours ; l'application récupère les horaires publiés sur le site de l'école, propose les groupes disponibles selon ses préférences, puis génère son horaire de session.

- **Nom de travail :** HoraireSync (modifiable)
- **Plateforme :** site web responsive (ordinateur et mobile)
- **Stack :** frontend React (JavaScript), backend Java Spring Boot
- **École couverte au MVP :** UQAM, avec une architecture prête pour d'autres écoles
- **Contrainte de conception :** aucune IA ni LLM ; toute l'extraction de données est déterministe
- **Objectif personnel :** projet de portfolio démontrant le web scraping, une API REST, un patron d'architecture extensible et la génération d'un format standard (RFC 5545)

## Problème et objectifs

Chaque session, l'étudiant consulte plusieurs pages pour comparer les groupes d'un même cours, repère les conflits à la main, puis recopie chaque séance dans son agenda. Ce travail est répétitif, sujet aux erreurs et doit être refait à chaque session.

**Objectifs du produit**

1. Construire un horaire de session complet en moins de 5 minutes.
2. Détecter automatiquement les conflits d'horaire entre groupes choisis.
3. Produire un fichier .ics qui s'importe sans erreur dans Apple Calendar, Google Calendar et Outlook.
4. Ne jamais inventer une information : toute donnée absente est signalée et saisie par l'utilisateur.

**Métriques de succès**

| Métrique | Cible |
| --- | --- |
| Import .ics réussi | 3 agendas testés (Apple, Google, Outlook) sans erreur |
| Temps du parcours complet | Moins de 5 minutes pour 5 cours |
| Couverture de tests backend | 70 % ou plus sur les services et l'adaptateur |
| Démo publique | Site déployé et lien dans le README et le portfolio |

## Utilisateurs cibles

| Profil | Besoin principal |
| --- | --- |
| Étudiant de bac à temps plein | Comparer rapidement les groupes de 4 ou 5 cours et éviter les conflits |
| Étudiant qui travaille | Trouver des cours de soir ou en ligne compatibles avec son emploi |
| Étudiant d'une école non couverte | Saisir ses cours manuellement et obtenir quand même un fichier .ics |
| Recruteur (public secondaire) | Comprendre le projet en 2 minutes grâce à la démo et au README |

## Parcours utilisateur

1. **École :** l'utilisateur choisit son école dans une liste. Si elle n'est pas prise en charge, il passe en mode saisie manuelle.
2. **Session :** il choisit la session (ex. Hiver 2027). Les dates de début et de fin sont récupérées ou, à défaut, demandées.
3. **Programme :** il recherche son programme par nom ou par code.
4. **Cours :** il ajoute ses cours par sigle (ex. INF1120) ou par recherche dans la liste du programme.
5. **Préférences :** il indique ses préférences, toutes facultatives : jour ou soir, présentiel, en ligne ou hybride, campus, jours à garder libres.
6. **Groupes :** pour chaque cours, l'application affiche les groupes disponibles avec professeur, jours, heures, local, campus et mode d'enseignement. Les groupes compatibles avec les préférences sont mis en avant ; ceux en conflit avec un choix déjà fait sont signalés.
7. **Données manquantes :** chaque champ absent est marqué « Information non disponible » et un formulaire permet de le remplir.
8. **Compte rendu :** une vue hebdomadaire et une liste récapitulent l'horaire, avec les conflits restants et les champs encore vides.
9. **Confirmation :** l'utilisateur valide le compte rendu ou revient modifier un choix.
10. **Export :** il télécharge le fichier .ics, avec des instructions d'import pour Apple Calendar, Google Calendar et Outlook.

## Exigences fonctionnelles

| ID | Exigence | Priorité |
| --- | --- | --- |
| EF-01 | Lister les écoles prises en charge et proposer un mode manuel pour les autres | MVP |
| EF-02 | Lister les sessions disponibles et leurs dates de début et de fin | MVP |
| EF-03 | Rechercher un programme par nom ou par code | MVP |
| EF-04 | Ajouter et retirer des cours par sigle ou par recherche | MVP |
| EF-05 | Récupérer les groupes d'un cours : numéro, professeur, jours, heures, local, campus, mode d'enseignement, dates particulières (examens) | MVP |
| EF-06 | Saisir des préférences : jour ou soir, mode, campus, jours libres | MVP |
| EF-07 | Trier et marquer les groupes selon leur compatibilité avec les préférences | MVP |
| EF-08 | Détecter et afficher les conflits d'horaire entre groupes choisis | MVP |
| EF-09 | Signaler chaque champ manquant et permettre sa saisie manuelle | MVP |
| EF-10 | Afficher un compte rendu en vue semaine et en liste avant confirmation | MVP |
| EF-11 | Générer un fichier .ics valide (RFC 5545) avec séances récurrentes jusqu'à la fin de session | MVP |
| EF-12 | Ajouter des exceptions à la récurrence (semaine de relâche, congés fériés) | MVP si disponibles, sinon saisie manuelle |
| EF-13 | Afficher les instructions d'import pour Apple, Google et Outlook | MVP |
| EF-14 | Conserver la configuration en cours dans le navigateur (localStorage) pour la reprendre plus tard | V1.1 |
| EF-15 | Proposer automatiquement les combinaisons sans conflit qui respectent le mieux les préférences | V1.1 |
| EF-16 | Ajouter un deuxième adaptateur d'école | V1.1 |

## Exigences non fonctionnelles

- **Performance :** une recherche de cours déjà en cache répond en moins de 500 ms ; une première récupération sur le site de l'école en moins de 5 secondes.
- **Respect des sites sources :** cache des résultats (24 h par défaut), limite de requêtes sortantes, en-tête User-Agent identifiant le projet, respect du fichier robots.txt et des conditions d'utilisation.
- **Robustesse :** si la structure du site change, l'adaptateur retourne une erreur claire plutôt que des données fausses ; l'utilisateur peut toujours passer en saisie manuelle.
- **Confidentialité :** aucun compte, aucune donnée personnelle stockée côté serveur.
- **Accessibilité :** navigation au clavier, contrastes suffisants, libellés sur tous les champs.
- **Langue :** interface en français au MVP, textes centralisés pour une version anglaise future.
- **Responsive :** utilisable sur un écran de 375 px de large.
- **Qualité :** tests unitaires et d'intégration, CI GitHub Actions, code formaté et documenté.

## Architecture technique

&#91;embedded content: architecture · frontend, API, services, adaptateurs\]

Le frontend ne parle qu'à l'API ; seul l'adaptateur connaît le HTML de l'école, ce qui permet d'ajouter une école sans modifier les services.

| Couche | Technologie | Hébergement |
| --- | --- | --- |
| Frontend | React + Vite (JavaScript), CSS Modules | Vercel |
| Backend | Java 21, Spring Boot 3, Maven | Render, Railway ou Fly.io (Docker) |
| Scraping | Jsoup | Backend |
| Cache | Caffeine en mémoire, 24 h | Backend |
| Export | ical4j ou Biweekly | Backend |
| Tests | JUnit 5, Mockito, Spring Boot Test, Vitest | GitHub Actions |

## Modèle de données

Le modèle est commun à toutes les écoles ; chaque adaptateur produit ces objets. Les champs pouvant manquer sont nullables et accompagnés d'une liste `missingFields` pour que le frontend sache quoi demander.

```java
record School(String id, String name, boolean supported) {}

record Term(String id, String label, LocalDate startDate, LocalDate endDate,
            List<LocalDate> excludedDates, List<String> missingFields) {}

record Program(String code, String name, String schoolId) {}

record Course(String code, String title, Integer credits, String termId) {}

record Section(String courseCode, String sectionNumber, String instructor,
               DeliveryMode mode, String campus, List<Meeting> meetings,
               List<String> missingFields, String sourceUrl) {}

record Meeting(DayOfWeek day, LocalTime start, LocalTime end,
               String room, MeetingType type, LocalDate onlyDate) {}

enum DeliveryMode { IN_PERSON, ONLINE, HYBRID, UNKNOWN }
enum MeetingType { LECTURE, LAB, EXAM, OTHER }

record Preferences(TimeOfDay timeOfDay, Set<DeliveryMode> modes,
                   Set<String> campuses, Set<DayOfWeek> freeDays) {}

record ScheduleRequest(String schoolId, String termId,
                       List<SelectedSection> selections) {}
```

`onlyDate` est rempli pour une séance ponctuelle (ex. examen final) ; sinon la séance se répète chaque semaine de la session.

## API REST

Toutes les routes sont préfixées par `/api/v1` et retournent du JSON, sauf l'export.

| Méthode | Route | Rôle |
| --- | --- | --- |
| GET | `/schools` | Liste des écoles et indicateur de prise en charge |
| GET | `/schools/{schoolId}/terms` | Sessions disponibles avec leurs dates |
| GET | `/schools/{schoolId}/programs?q=` | Recherche de programmes |
| GET | `/schools/{schoolId}/programs/{code}/courses` | Cours d'un programme |
| GET | `/schools/{schoolId}/terms/{termId}/courses/{code}/sections` | Groupes d'un cours pour une session |
| POST | `/schedule/conflicts` | Reçoit les groupes choisis, retourne les conflits |
| POST | `/schedule/rank` | Reçoit des groupes et des préférences, retourne un score par groupe |
| POST | `/schedule/export` | Reçoit l'horaire confirmé (données manuelles incluses), retourne un fichier `text/calendar` |

Les erreurs suivent un format unique : `{ "code": "SOURCE_UNAVAILABLE", "message": "..." }`. Codes prévus : `SCHOOL_NOT_SUPPORTED`, `COURSE_NOT_FOUND`, `SOURCE_UNAVAILABLE`, `PARSING_FAILED`, `INVALID_SCHEDULE`.

## Adaptateurs, données manquantes et export .ics

### Adaptateur d'école

Chaque école est un module qui implémente une interface commune. Ajouter une école revient à écrire une nouvelle classe, sans toucher au reste du code.

```java
public interface SchoolAdapter {
    School school();
    List<Term> fetchTerms();
    List<Program> searchPrograms(String query);
    List<Course> fetchProgramCourses(String programCode);
    List<Section> fetchSections(String termId, String courseCode);
}
```

Les adaptateurs sont des beans Spring découverts automatiquement et indexés par `schoolId` dans un `SchoolAdapterRegistry`.

### Adaptateur UQAM (MVP)

- **Source envisagée :** les pages publiques de cours et de programmes du site étudier.uqam.ca, qui publient les groupes par session. La structure exacte (URL, sélecteurs HTML) est à confirmer en phase 0.
- **Outil :** Jsoup pour télécharger et analyser le HTML.
- **Tests :** pages HTML enregistrées dans `src/test/resources/fixtures/uqam/` pour tester le parsing sans réseau.
- **Dates de session :** à extraire du calendrier universitaire UQAM si possible ; sinon, saisie manuelle obligatoire avant l'export.

### Gestion des données manquantes

1. L'adaptateur ne devine jamais : un champ introuvable reste `null` et son nom est ajouté à `missingFields`.
2. Le frontend affiche un badge « Information non disponible » et un champ de saisie pour chaque valeur manquante.
3. Le compte rendu liste les champs encore vides ; l'export est bloqué seulement si une donnée indispensable manque (jour, heure de début, heure de fin, dates de session).
4. Les champs facultatifs (professeur, local) peuvent rester vides ; ils sont alors omis du fichier .ics.

### Export .ics

- **Bibliothèque :** ical4j ou Biweekly (choisir la plus simple après essai).
- **Un VEVENT par séance récurrente**, avec `RRULE:FREQ=WEEKLY;UNTIL=<fin de session>` et `EXDATE` pour les dates exclues.
- **Fuseau horaire :** `America/Toronto` (heure de Montréal), inclus via un bloc VTIMEZONE.
- **Contenu de l'événement :** titre « INF1120 Programmation I (Gr. 20) », lieu = local et campus, description = professeur, mode d'enseignement et lien vers la page source.
- **UID stable** par séance pour qu'un nouvel import remplace l'ancien au lieu de créer des doublons dans certains agendas.
- **Validation :** test automatisé qui relit le fichier généré et import manuel dans Apple Calendar, Google Calendar et Outlook avant chaque version.

## Hors périmètre, risques et jalons

### Hors périmètre du MVP

- Comptes utilisateurs et base de données persistante
- Inscription réelle aux cours
- IA ou LLM, sous quelque forme que ce soit
- Abonnement de calendrier synchronisé en continu (URL webcal)
- Applications mobiles natives
- Écoles autres que l'UQAM

### Risques

| Risque | Impact | Mesure |
| --- | --- | --- |
| Le site de l'école change de structure | Parsing cassé | Tests sur fixtures, erreur `PARSING_FAILED` claire, mode manuel toujours disponible |
| Pages chargées en JavaScript côté client | Jsoup ne voit pas les données | Vérifier en phase 0 ; chercher l'appel réseau JSON sous-jacent avant d'envisager un navigateur headless |
| Conditions d'utilisation restrictives | Projet non publiable | Lire robots.txt et les conditions en phase 0 ; cache et faible volume de requêtes |
| Horaires non publiés pour une session future | Aucun groupe à afficher | Message explicite et saisie manuelle |
| Fichier .ics mal interprété par un agenda | Mauvaise expérience | Tests d'import dans les 3 agendas, fuseau horaire explicite |

### Jalons

1. **Phase 0, recherche :** analyser le site UQAM (URL, HTML, robots.txt), enregistrer des pages fixtures, valider la faisabilité.
2. **Phase 1, backend :** modèle de données, interface d'adaptateur, adaptateur UQAM, cache, endpoints de lecture, tests.
3. **Phase 2, logique d'horaire :** détection de conflits, classement selon les préférences, génération .ics et ses tests.
4. **Phase 3, frontend :** parcours complet en étapes, gestion des champs manquants, compte rendu en vue semaine, export.
5. **Phase 4, mise en ligne :** Dockerfile du backend, déploiement (frontend sur Vercel, backend sur Render, Railway ou Fly.io), CI GitHub Actions, README avec captures et démo.
6. **V1.1 :** sauvegarde locale, générateur de combinaisons sans conflit, deuxième école.

## Prompt pour Claude Code

Avant de lancer Claude Code, exporte ce document en Markdown et place-le dans le dépôt sous `docs/PRD.md`. Ouvre ensuite Claude Code à la racine du dépôt vide et colle le prompt ci-dessous.

```text
Tu vas m'aider à construire HoraireSync, une application web qui transforme une liste de cours universitaires en fichier .ics. Le PRD complet est dans docs/PRD.md : lis-le entièrement avant de faire quoi que ce soit.

CONTEXTE
- Je suis étudiant en génie logiciel. Je maîtrise Java, JavaScript, HTML, CSS et SQL. Je travaille sur MacBook Air M3 avec IntelliJ et WebStorm.
- C'est un projet de portfolio : la qualité du code, la clarté de l'architecture, les tests et le README comptent autant que les fonctionnalités.
- Je veux comprendre ce que tu fais : explique brièvement chaque choix technique important.

CONTRAINTES STRICTES
- Aucune IA, aucun LLM, aucune API payante dans le produit. Toute l'extraction de données est déterministe (Jsoup).
- Ne jamais inventer une donnée de cours : un champ introuvable reste null et son nom va dans missingFields.
- Respecter les sites sources : cache, peu de requêtes, User-Agent identifiant le projet, robots.txt.

STACK
- Monorepo : /backend et /frontend, plus /docs.
- Backend : Java 21, Spring Boot 3, Maven, Jsoup, Caffeine (cache), ical4j ou Biweekly, JUnit 5, Mockito, Spring Boot Test.
- Frontend : React avec Vite, en JavaScript. CSS simple ou CSS Modules, pas de framework lourd. Interface en français, textes centralisés dans un fichier pour une traduction future.
- CI : GitHub Actions qui compile et teste le backend et le frontend.

ARCHITECTURE
- Interface SchoolAdapter (voir PRD) et un SchoolAdapterRegistry qui indexe les adaptateurs Spring par schoolId.
- Premier adaptateur : UQAM. Les pages HTML de test sont enregistrées dans backend/src/test/resources/fixtures/uqam/ pour que les tests ne dépendent pas du réseau.
- Services séparés : CatalogService (lecture via adaptateurs + cache), ConflictService, RankingService, IcsExportService.
- API REST sous /api/v1 exactement comme décrite dans le PRD, avec un format d'erreur unique.

MÉTHODE DE TRAVAIL
1. Commence par la PHASE 0 uniquement. Analyse les pages publiques de cours et de programmes de l'UQAM (étudier.uqam.ca) : structure des URL, sélecteurs HTML des groupes, présence ou non de JavaScript côté client, robots.txt. Rédige tes conclusions dans docs/phase0-uqam.md, enregistre 2 ou 3 pages comme fixtures, puis ARRÊTE-TOI et attends ma validation.
2. Ensuite, avance phase par phase selon les jalons du PRD. À chaque phase : propose un plan court, attends mon accord, implémente, lance les tests, puis résume ce qui a été fait.
3. Fais des commits petits et clairs (Conventional Commits, en anglais).
4. Écris les tests en même temps que le code. Vise 70 % de couverture sur les services et l'adaptateur.
5. Si une information du site est ambiguë ou si le PRD ne répond pas à une question, demande-moi au lieu de supposer.

LIVRABLES FINAUX
- Application fonctionnelle de bout en bout pour l'UQAM avec mode manuel pour les autres écoles.
- Fichier .ics validé par un test automatisé (relecture du fichier) et importable dans Apple Calendar, Google Calendar et Outlook.
- Dockerfile pour le backend, configuration de déploiement du frontend pour Vercel.
- README en français et en anglais : problème résolu, capture ou GIF de démo, architecture (diagramme), choix techniques justifiés, comment lancer le projet, comment ajouter une école, limites connues.

Commence maintenant par lire docs/PRD.md, puis lance la phase 0.
```
