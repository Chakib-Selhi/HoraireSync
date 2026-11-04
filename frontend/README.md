# HoraireSync frontend

Interface React (Vite, JavaScript) du parcours en 9 étapes : école, session, programme, cours, préférences, groupes, données manquantes, compte rendu, export.

## Lancer

```bash
cd frontend
npm install
npm run dev      # http://localhost:5173, /api est redirigé vers le backend sur le port 8080
npm test         # Vitest + Testing Library
npm run build    # version de production dans dist/
```

En production, `VITE_API_URL` donne l'adresse du backend (ex. `https://api.exemple.com`).

## Organisation

| Dossier | Rôle |
| --- | --- |
| `src/i18n` | Tous les textes (`fr.js`) et la fonction `t('cle', { param })` |
| `src/api` | Client `fetch` ; toute erreur devient une `ApiError` avec le code du backend |
| `src/state` | Un seul `useReducer` pour tout le parcours, exposé par un contexte |
| `src/lib` | Fonctions pures : champs manquants, règles de blocage de l'export, vue semaine, contenu de l'export |
| `src/components` | Composants réutilisables (champ adapté au type de valeur, carte de groupe, vue semaine…) |
| `src/steps` | Un composant par étape |

## Choix

- Pas de routeur ni de Redux : le parcours est linéaire et l'état tient dans un reducer testable sans React.
- Les règles de blocage de l'export sont les mêmes que celles du backend (`essentialProblems`), pour prévenir l'utilisateur avant l'appel.
- Un champ rempli reste affiché dans l'étape « Données manquantes », pour pouvoir le corriger.
- Accessibilité : chaque champ a un libellé, le titre de chaque étape reçoit le focus, les erreurs sont annoncées (`role="alert"`), la vue semaine est aussi une liste lisible par un lecteur d'écran.
- Mode manuel (école non prise en charge) : les étapes programme, préférences et groupes sont sautées.
