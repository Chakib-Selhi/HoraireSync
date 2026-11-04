# Phase 0 : analyse du site étudier.uqam.ca

Date de l'analyse : 4 novembre 2026. Sessions publiées à cette date : Été 2026, Automne 2026, Hiver 2027.

## 1. Verdict de faisabilité

**Faisable avec Jsoup seul.** Les groupes, horaires, enseignants et modes d'enseignement sont rendus côté serveur (Drupal 7, PHP). Aucun navigateur headless ni API JSON cachée n'est nécessaire.

Nombre total de requêtes faites pendant cette analyse : 11, espacées d'au moins 10 secondes.

## 2. robots.txt et conditions d'utilisation

Source : https://etudier.uqam.ca/robots.txt

- `User-agent: *` avec **`Crawl-delay: 10`** : au plus une requête toutes les 10 secondes.
- Interdits pertinents : `/search/`, `/?q=search/`, `/admin/`, `/user/...`. La recherche interne du site ne doit donc **pas** être utilisée.
- `/cours`, `/programme`, `/programmes` et `/calendriers` sont **autorisés**.
- En-têtes HTTP : `cache-control: public, max-age=21600` (6 h), servi par CloudFront. Un cache de 24 h de notre côté est donc raisonnable.
- Conditions d'utilisation : aucune page « conditions d'utilisation » ou « avis légal » n'est liée depuis etudier.uqam.ca (le pied de page est injecté par un script UQAM). Les pages contiennent l'avertissement : « Les modalités et horaires présentés sont à jour au moment de la recherche. Ils n'impliquent pas d'engagement ni d'obligation de la part de l'UQAM… ». **Question ouverte, voir section 9.**

### Conséquences pour le code

| Règle | Mise en œuvre prévue |
| --- | --- |
| Crawl-delay 10 s | Limiteur global de requêtes sortantes vers l'UQAM : 1 requête / 10 s, file d'attente |
| Pas de `/search/` | Recherche de programmes faite localement sur la page `/programmes` mise en cache |
| User-Agent | `HoraireSync/<version> (+<url du dépôt GitHub>)` |
| Cache | Caffeine, 24 h, clé = URL |

## 3. Structure des URL

| Besoin (PRD) | URL | Remarque |
| --- | --- | --- |
| Groupes d'un cours (EF-05) | `https://etudier.uqam.ca/cours?sigle={SIGLE}` | Une seule page contient **toutes les sessions publiées**. Une requête par cours suffit pour toutes les sessions. |
| Cours d'un programme | `https://etudier.uqam.ca/programme?code={CODE}` | Code à 4 chiffres (ex. 7416) |
| Liste de tous les programmes (EF-03) | `https://etudier.uqam.ca/programmes` | 228 programmes dans une seule page (≈ 400 Ko) |
| Dates de session (EF-02, EF-12) | `https://etudier.uqam.ca/calendriers` | Tableau HTML par trimestre ; le PDF officiel a priorité en cas de divergence |

### Identifiant de session

Format `AAAAT` où `T` vaut `1` = Hiver, `2` = Été, `3` = Automne. Exemples : `20263` = Automne 2026, `20271` = Hiver 2027. Cet identifiant apparaît partout (id HTML, attributs `data-trim`, classes CSS). Je propose de l'utiliser tel quel comme `Term.id`.

### Cours inexistant

`/cours?sigle=ZZZ9999` répond **HTTP 200** (pas 404). Le titre devient `Cours | UQAM` et aucun bloc `groupes_wrapper` n'est présent. Détection : absence de `title` contenant le sigle **et** absence de `[id^=groupes_wrapper]` → `COURSE_NOT_FOUND`.

## 4. Page de cours : sélecteurs

```
title                                  "Cours INF1120 | Programmation I | UQAM"  → code et titre
li:has(span.label:contains(Nombre de crédits))   "Nombre de crédits : 3"          → credits
div#groupes_wrapper{TRIM}              un bloc par session publiée
  div.groupe                           un groupe
    h3.no_groupe                       "Groupe 010"                               → sectionNumber
    div.ligne (h3 "Enseignant") ul li  "Mélanie Lord" ou "Non disponible"         → instructor
    div.avertissement                  phrase du mode d'enseignement              → mode
    table:not(.alignement) tr          une ligne par séance (ligne 1 = en-têtes)
      td[0] Jour      "Lundi"
      td[1] Date      "Du 11 janvier 2027 au 2 mai 2027"
      td[2] Heure     "De 09h30 à 12h30" (espaces insécables)
      td[3] Lieu      "UQAM Campus de Laval" ou vide
      td[4] Type      "Cours magistral" | "Laboratoire" | "Atelier" | "Exercices"
    div.ligne (h3 "Remarque") ul li    texte libre
```

Le libellé de session (« Horaire - Hiver 2027 ») est dans les onglets `a[aria-controls]` et dans `h2.trigger`.

### Correspondances observées

| Texte source | Valeur du modèle |
| --- | --- |
| « Ce cours est donné en présentiel. » | `IN_PERSON` |
| « Ce cours est donné en mode hybride … » | `HYBRID` |
| « Ce cours est donné à distance . Ne pas tenir compte des locaux ou campus… » | `ONLINE` |
| Aucun avertissement / autre phrase | `UNKNOWN` + `mode` dans `missingFields` |
| « Cours magistral » | `LECTURE` |
| « Laboratoire », « Atelier », « Exercices » | `LAB` (voir question 4) |
| Enseignant « Non disponible » | `instructor = null`, `instructor` dans `missingFields` |
| Ligne de séance « - - - - - » | Aucune séance ; `meetings` dans `missingFields` |

Valeurs de campus vues : `Campus de Montréal`, `UQAM Campus de Laval`, `UQAM Campus de Longueuil`, `UQAM Campus de la Montérégie-Ouest`, `UQAM Campus Lanaudière(Terrebonne)`, vide.

## 5. Particularités et pièges

1. **Le local (salle) n'est pas publié dans la colonne Lieu.** Seul le campus y figure. Parfois le local apparaît dans les remarques en texte libre (« Le cours magistral a lieu dans le local D-4119 »). Par défaut, `room = null` et `room` va dans `missingFields`.
2. **Le campus est au niveau de la séance**, pas du groupe. Une même séance peut avoir un campus et l'autre aucun (INF1120 gr. 001 : magistral à Laval, labo sans lieu). `Section.campus` devra être dérivé (voir question 3).
3. **Lignes de séance en double** (INF1120 gr. 010 : deux labos mardi 18h-20h identiques). Dédoublonnage nécessaire pour éviter des événements .ics doublés.
4. **Examens seulement en texte libre** dans les remarques, sans année : « Examens: 27 févr. 09:30 à 12:30 et 01 mai 09:30 à 12:30 ». Format assez régulier, mais pas garanti, et sans local.
5. **Dates par séance** : chaque ligne porte « Du … au … ». Ces dates sont plus précises que le calendrier universitaire, qui donne plusieurs fins possibles (« 23 avril ou 30 avril ou 2 mai »).
6. **Groupes « Inscription par votre direction de programme »** ou réservés (ex. cohorte Casablanca, groupe en anglais) : présents dans la page, sans horaire parfois.
7. **Espaces insécables** (`&nbsp;`) dans les heures et espaces de fin dans les jours (`"Lundi   "`) : normaliser avant le parsing.
8. **Format d'heure** `09h30` ; mois en français complets dans les plages (« janvier ») mais abrégés dans les examens (« févr. »).

## 6. Page de programme

```
title                         "Baccalauréat en informatique et génie logiciel (7416) | UQAM | …"
div.ligne_cours[data-sigle]   un cours (id = sigle)
  span.texte                  "INF1120 - Programmation I"
  div.trimestres span         class="20271" (+ "actif" si offert à cette session) ; texte "H27"
h3.titre_bloc                 bloc de cours : "Informatique (48 crédits)", "Cours au choix en informatique (15 crédits)"…
```

- 48 cours distincts pour le programme 7416 (certains sigles apparaissent deux fois).
- Les crédits par cours ne sont **pas** sur cette page ; ils sont sur la page du cours.
- Les horaires par cours (onglets) y sont chargés en AJAX : on ne les utilise pas, la page `/cours` suffit.

## 7. Liste des programmes

`table#tableProgrammes tbody tr` avec les cellules `td a[href^=programme?code=]` (titre), `td.types` (Baccalauréat, Certificat…), `td.cycles`, `td.code`, `td.campus`. Le titre n'inclut pas le type : « Informatique et génie logiciel » + type « Baccalauréat ». La recherche par nom ou code (EF-03) se fera en mémoire sur cette page en cache.

## 8. Calendrier universitaire

Page `/calendriers`, tableaux `tr > th` (libellé) + `th`/`td` (valeur), regroupés sous « Trimestre d'automne 2026 », « Trimestre d'hiver 2027 »…

| Donnée | Hiver 2027 | Automne 2026 |
| --- | --- | --- |
| Début | 11 janvier | 8 septembre (« d'autres dates peuvent s'appliquer ») |
| Fin | « 23 avril ou 30 avril ou 2 mai » | « 18 décembre ou 21 décembre ou 23 décembre » |
| Relâche / lecture | 1er au 7 mars « selon facultés/écoles » | 26 au 30 octobre « ESG seulement » |
| Congés | 26 mars, 29 mars | 7 sept., 30 sept., 5 oct. (élections), 12 oct. |

Les années ne sont pas dans les cellules ; elles se déduisent du titre du trimestre. Format du texte variable (« 1er », plages « au »). Le parsing est possible mais fragile : en cas d'échec, `startDate`/`endDate`/`excludedDates` restent vides et vont dans `missingFields`.

## 9. Questions pour toi avant la phase 1

1. **Conditions d'utilisation.** Aucune page de conditions trouvée. Es-tu d'accord pour avancer en respectant robots.txt (10 s, cache 24 h) et en ajoutant un avis dans le README et l'interface (« données publiques de l'UQAM, non officiel »)? Tu peux aussi écrire au Registrariat si tu veux un accord explicite avant la mise en ligne publique.
2. **Bornes de la récurrence.** Je propose d'utiliser les dates « Du … au … » de chaque séance pour `DTSTART`/`UNTIL`, et le calendrier seulement pour les congés. D'accord?
3. **Campus du groupe.** Si les séances ont des campus différents ou vides : `Section.campus` = campus commun s'il est unique, sinon `null` + `missingFields`. On garde aussi le campus par séance (ajout d'un champ `campus` dans `Meeting`, petite modification du modèle du PRD). D'accord?
4. **Types de séance.** `Atelier` et `Exercices` → `LAB`, ou ajouter `TUTORIAL` à `MeetingType`?
5. **Local tiré des remarques.** Extraire « local D-4119 » des remarques quand la phrase est explicite, ou laisser `room = null` dans tous les cas (plus strict, conforme à « ne jamais inventer »)?
6. **Examens.** Parser « Examens: 27 févr. 09:30 à 12:30 … » en `Meeting` de type `EXAM` avec `onlyDate` (année déduite de la session), ou ignorer les examens au MVP et les laisser en saisie manuelle?
7. **Semaine de relâche.** Elle dépend de la faculté. L'exclure par défaut (avec possibilité de la désactiver dans l'interface), ou ne rien exclure et laisser l'utilisateur la cocher?
8. **Fixtures supplémentaires.** Pour la phase 1, j'ajouterai `programmes.html`, `calendriers.html` et `cours-inexistant.html` (déjà téléchargées pendant l'analyse, aucune requête de plus). D'accord?
