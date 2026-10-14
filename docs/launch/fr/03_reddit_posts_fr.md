# French Reddit Posts (ready to post)

Notes are in English; post titles and bodies are in French for the audience.

---

## 1. r/france or r/bonjour

**Post title**:
`[Projet perso] J'ai fait un lanceur Android minimaliste qui met en haut l'appli dont j'ai besoin, sans rien epingler`

**Post body**:
```markdown
Bonjour a tous,

Mon ecran d'accueil m'agacait : pastilles rouges, icones qui clignotent, widgets. J'ai essaye plusieurs lanceurs minimalistes, mais soit je scrollais une liste alphabetique, soit j'epinglais 4 favoris qui n'etaient bons qu'une partie de la journee.

Du coup j'ai code Loom. C'est un fond noir avec une petite grille d'icones : pas de noms, pas de pastilles, pas de widgets. Il retient quand tu ouvres quoi et remonte les bonnes applis au bon moment. Rien a configurer.

Sur 3 mois de mon usage (un peu plus de 10 000 ouvertures), l'appli que je voulais etait la premiere une fois sur trois, et dans le top 10 dans 83 % des cas. Je ne cherche quasiment plus rien.

Tout reste sur le telephone, l'appli n'a meme pas la permission internet. Plus tard je proposerai peut-etre de partager des stats d'usage anonymes pour ameliorer le classement, mais uniquement si on l'active soi-meme.

C'est gratuit et open source (Apache 2.0) : https://github.com/TidyMaze/loom

Je precise que c'est une alpha et que j'en ai code une bonne partie avec un assistant IA. Les retours de bugs et les "ca m'a saoule" m'interessent.
```

---

## 2. r/developpeurs

**Post title**:
`[Showcase] Loom : un launcher Android en Kotlin avec un petit modele de classement on-device (790 Ko, pas de reseau)`

**Post body**:
```markdown
Salut,

Je partage un projet perso : Loom, un launcher Android minimaliste. L'APK fait 790 Ko, sans permission INTERNET et sans Google Play Services.

Le classement est un modele lineaire regle a la main, en Kotlin pur. Il combine une decroissance exponentielle sur la recence, des noyaux gaussiens sur l'heure et le jour de la semaine, et des transitions de Markov entre applis (P(B | A)). Deux regimes selon le temps ecoule depuis la derniere ouverture. Les constantes sont reglees hors ligne avec un script de bench sur mon propre historique.

Cible SDK 36 (Android 16), retour predictif et edge-to-edge.

Le code : https://github.com/TidyMaze/loom

Les PRs et les critiques sur le modele sont bienvenues.
```
