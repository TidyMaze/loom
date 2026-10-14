# Posts Reddit Francophones (Prets a publier)

---

## 1. r/france ou r/bonjour

**Titre du post** :
`[Projet perso] J'en avais marre de la surcharge de mon smartphone, alors j'ai developpe un lanceur Android minimaliste et 100% prive (Loom)`

**Contenu du post** :
```markdown
Bonjour a tous,

Comme beaucoup, j'avais l'impression que mon telephone etait concu comme une machine a sous : des points rouges partout, des icones fluo et des widgets qui se battent pour capter mon attention.

J'ai essaye pas mal de lanceurs minimalistes, mais soit il fallait scroller dans une liste alphabetique interminable, soit bloquer 4 favoris qui n'etaient jamais les bons au cours de la journee.

J'ai donc concu **Loom** :
- **Zero fioriture** : Fond noir pur OLED, liste textuelle epuree, aucune icone coloree distrayante.
- **Anticipation locale** : Un algorithme tres leger (en Kotlin) apprend vos habitudes au fil de la journee selon l'heure, le jour de la semaine et vos enchainements d'apps.
- **100% Hors-ligne & Prive** : L'app n'a meme pas la permission INTERNET. Zero pub, zero compte, zero donnee envoyee.
- **Micro-poids** : Moins de 1 Mo (969 Ko), instantane, zero impact sur la batterie.

Sur mes 3 derniers mois d'utilisation (10 000+ ouvertures), l'app que je veux ouvrir est deja la premiere a l'ecran 1 fois sur 3 sans rien chercher, et dans 83% des cas elle est directement visible.

Le projet est totalement gratuit et open source (licence Apache 2.0).
L'APK directe et le code sont dispo sur GitHub :
https://github.com/TidyMaze/loom

*Note : Loom est au tout debut du projet (stade Alpha initiale) et a ete vibe-code avec soin. Le moteur local et l'interface sont fluides, mais le developpement est tres actif : vos retours et signalements de bugs sont precieux !*

Preneur de vos retours et suggestions d'amelioration !
```

---

## 2. r/developpeurs

**Titre du post** :
`[Showcase] Loom : Un launcher Android minimaliste en pur Kotlin avec moteur de scoring on-device (< 1 Mo, 0 network)`

**Contenu du post** :
```markdown
Salut la commu dev,

Je vous partage un projet perso que je viens de stabiliser : **Loom**, un lanceur Android concu avec des contraintes techniques strictes :
- **Zero permission INTERNET** dans le Manifest Android.
- **Empreinte minimale** : APK de 969 Ko, startup time < 50ms, latence de scoring < 1ms a 120 FPS.
- **Scoring en pur Kotlin** : Modele double-regime combinant decroissance exponentielle de recence, noyaux gaussiens pour l'heure du jour / jour de semaine, et bigrammes de transitions (Markov P(App B | App A)).
- **Target SDK 36 (Android 16)** : Support complet du retour predictif et edge-to-edge.

Code source et architecture sur GitHub :
https://github.com/TidyMaze/loom

N'hesitez pas a jeter un oeil au code ou a proposer des PRs !
```
