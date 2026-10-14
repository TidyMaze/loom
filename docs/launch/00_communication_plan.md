# Plan de Communication et de Lancement (Ordonne par Paliers de Popularite)

Ce plan classe chaque canal selon le **niveau de popularite minimale requis** pour eviter de bider.

---

## Palier 1 : Jour 0 (0 stars / 0 users / 0 followers)
*Ces canaux jugent la qualite technique brute et l'utilite reelle. Zero dependance a une audience preexistante.*
*Play Store : **NON REQUIS** (tourne a 100% sur GitHub / F-Droid).*

| Canal | Action | Play Store requis ? | Pourquoi faisable a 0 star | Document associe |
|---|---|---|---|---|
| **Hacker News (Show HN)** | Publication du post Show HN | **Non** (prefere GitHub direct) | HN valorise la decouverte de projets bruts, open-source et sans marketing. | [02_show_hn.md](02_show_hn.md) |
| **F-Droid (RFP)** | Soumission de la recette | **Non** (100% independant de Google) | Les mainteneurs auditent le code et la licence libre, pas les stats sociales. | [01_fdroid_rfp.md](01_fdroid_rfp.md) |
| **Reddit Niche** | `r/digitalminimalism`, `r/dumbphones`, `r/opensource` | **Non** (APK GitHub direct bienvenu) | Ces communautes cherchent des outils concrets. Un post humble sans promo marche fort. | [03_reddit_posts.md](03_reddit_posts.md) |
| **Shorts / TikTok / Reels** | Publication de 2-3 videos courtes | **Non** (lien GitHub dans la bio) | Distribution 100% algorithmique sur la retention des 3 premieres secondes, pas les followers. | [07_short_video_scripts.md](07_short_video_scripts.md) / [fr/](fr/07_short_video_scripts_fr.md) |

---

## Palier 2 : Traction Initiale (50 a 150 stars & ~100 utilisateurs)
*Necessite un debut de preuve sociale et une stabilite confirmee sur plusieurs appareils.*
*Play Store : **FORTEMENT RECOMMANDE** pour lever le frein au telechargement grand public.*

| Canal | Action | Play Store requis ? | Pre-requis pour ne pas bider | Document associe |
|---|---|---|---|---|
| **Reddit Large** | `r/androidapps`, `r/france`, `r/developpeurs` | **Fortement recommande** sur r/androidapps | Moderation plus stricte. Demande des retours de testeurs deja valides pour eviter le rejet. | [03_reddit_posts.md](03_reddit_posts.md) / [fr/](fr/03_reddit_posts_fr.md) |
| **Awesome Lists PRs** | PRs sur `awesome-android` et `awesome-privacy` | **Non** (lien GitHub uniquement) | La majorite des mainteneurs refusent les projets < 50 stars pour eviter les repos abandonnes. | [05_awesome_lists.md](05_awesome_lists.md) |
| **Google Play Store** | Fin du Closed Testing 14 jours & sortie publique | **Cible de cette etape** | Exige 12 a 20 testeurs actifs prealablement recrutes via le Palier 1. | [09_play_store_aso_matrix.md](09_play_store_aso_matrix.md) |

---

## Palier 3 : Communaute Etablie (300 a 500+ stars & 1 000+ installs)
*Lancer ces etapes a 0 garantit un echec complet.*
*Play Store : **STRICTEMENT OBLIGATOIRE** (la presse et Product Hunt refusent le sideloading manuel).*

| Canal | Action | Play Store requis ? | Pre-requis pour ne pas bider | Document associe |
|---|---|---|---|---|
| **Product Hunt** | Lancement jour J a 9h01 CET | **Oui** (taux d'abandon de 90% sur APK manuel) | Sans base d'utilisateurs a mobiliser pour upvoter au lancement, le projet finit hors du Top 10. | [04_product_hunt.md](04_product_hunt.md) |
| **Presse Tech** | Android Police, Numerama, Frandroid | **Oui** (responsabilite editoriale sur la securite) | Les journalistes couvrent les projets qui font deja du bruit ou ont un track record prouve. | [06_press_pitch.md](06_press_pitch.md) / [fr/](fr/06_press_pitch_fr.md) |
| **Presse Libriste** | Korben, LinuxFr | **Non** (repo GitHub / F-Droid apprecie) | Ces medias mettent en avant les alternatives open-source pures. | [fr/06_press_pitch_fr.md](fr/06_press_pitch_fr.md) |
| **Partage Viral In-App** | Fonctionnalite "Partager mon setup" | **Oui** (les internautes veulent le bouton Store) | Ne produit un volume perceptible que s'il y a des milliers d'utilisateurs quotidiens. | [08_viral_share_feature_spec.md](08_viral_share_feature_spec.md) |

---

## Synthese de dependance au Google Play Store

1. **Jour 0 (Palier 1) se lance des aujourd'hui sans le Play Store** :
   - F-Droid, Hacker News et les subreddits de minimalisme preferent les depots open-source directs.
   - Ne pas attendre la validation Google pour commencer a obtenir les premieres etoiles et premiers retours.
2. **Le Closed Testing Google Play (14 jours) s'execute en parallele** :
   - Les 12 a 20 testeurs requis par Google sont recrutes parmi les premiers utilisateurs enthousiastes du Palier 1.
   - La validation publique du Play Store arrive idealement au moment ou le projet atteint le Palier 2 (50-100 stars), ouvrant alors la porte a `r/androidapps`, Product Hunt et la presse tech.

---

## Calendrier d'execution recommande

1. **Semaine 1 (Jour 0 a 7)** :
   - Ouvrir le ticket RFP sur F-Droid.
   - Poster sur Hacker News (Show HN en milieu de semaine, mardi-jeudi 14h-16h CET).
   - Poster sur `r/digitalminimalism` et `r/opensource`.
   - Poster 1ere video sur TikTok / Shorts.
2. **Semaine 2 a 3 (des ~50 stars atteintes)** :
   - Envoyer les PRs sur `awesome-android` et `awesome-privacy`.
   - Poster sur `r/androidapps` et `r/developpeurs`.
   - Lancer le closed testing 14 jours Play Store avec les premiers inscrits.
3. **Mois 2 (des ~300+ stars)** :
   - Coordonner le lancement Product Hunt.
   - Envoyer les pitchs personnalises a la presse tech avec les metrics d'usage.
