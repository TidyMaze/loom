import sys
sys.path.insert(0, 'scripts')
import json
import numpy as np
import collections
import bench

def compute_feature_importance():
    with open('usage_log.json') as f:
        events = json.load(f)

    events = sorted(events, key=lambda e: e['timestampMillis'])
    all_pkgs = list({e['packageName'] for e in events})
    n_apps = len(all_pkgs)

    # 1. Dual-Regime Features in ScoreEngine.kt v16
    features = [
        {
            'name': 'Transitions Markov 2-gram (In-Session)',
            'key': 'W_IN_TRANSITION_2',
            'weight': float(bench.V16_IN['w_trans2']),
            'cat': 'Transitions',
            'desc': 'Séquence d ordre 2 (A -> B -> C) au sein de la session active (< 70s). Poids maximal en session.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Périodicité 168h (Cold-Start)',
            'key': 'W_COLD_REC_168H',
            'weight': float(bench.V16_COLD['w_r168']),
            'cat': 'Périodicité',
            'desc': 'Récurrence hebdomadaire exacte lors du déverrouillage / reprise. Poids maximal hors session.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Transitions Markov 1-gram (In-Session)',
            'key': 'W_IN_TRANSITION',
            'weight': float(bench.V16_IN['w_trans']),
            'cat': 'Transitions',
            'desc': 'Probabilité de transition séquentielle 1-gram app n-1 -> cible en session.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Récence courte (Cold-Start)',
            'key': 'W_COLD_RECENCY',
            'weight': float(bench.V16_COLD['w_rec']),
            'cat': 'Récence',
            'desc': 'Inertie de reprise d application active récemment (demi-vie 0.5h).',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Contexte Temporel Global (Cold-Start)',
            'key': 'W_COLD_CONTEXT',
            'weight': float(bench.V16_COLD['w_ctx']),
            'cat': 'Contexte Temporel',
            'desc': 'Créneau horaire gaussien (sigma=2.53h) et jour ouvré/weekend au déverrouillage.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Contexte Batterie (Cold-Start)',
            'key': 'W_COLD_BAT',
            'weight': float(bench.V16_COLD['w_bat']),
            'cat': 'Contexte Système',
            'desc': 'Corrélation niveau de charge batterie vs historique de lancement.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Périodicité 24h (Cold-Start)',
            'key': 'W_COLD_REC_24H',
            'weight': float(bench.V16_COLD['w_r24']),
            'cat': 'Périodicité',
            'desc': 'Même heure de la journée la veille lors d une reprise.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Périodicité 8h (Cold-Start)',
            'key': 'W_COLD_REC_8H',
            'weight': float(bench.V16_COLD['w_r8']),
            'cat': 'Périodicité',
            'desc': 'Cycle circadien 8h (matin / midi / soir) hors session.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Périodicité 8h (In-Session)',
            'key': 'W_IN_REC_8H',
            'weight': float(bench.V16_IN['w_r8']),
            'cat': 'Périodicité',
            'desc': 'Inertie de créneau horaire en cours de session.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Contexte Agenda / Calendrier',
            'key': 'W_COLD_CAL',
            'weight': float(bench.V16_COLD['w_cal']),
            'cat': 'Contexte Système',
            'desc': 'Proximité temporelle événement agenda.',
            'category_type': 'Cold-Start Regime'
        },
        {
            'name': 'Périodicité 168h (In-Session)',
            'key': 'W_IN_REC_168H',
            'weight': float(bench.V16_IN['w_r168']),
            'cat': 'Périodicité',
            'desc': 'Rappel d habitude hebdomadaire en cours de session.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Récence courte (In-Session)',
            'key': 'W_IN_RECENCY',
            'weight': float(bench.V16_IN['w_rec']),
            'cat': 'Récence',
            'desc': 'Récence atténuée en session pour laisser priorité aux transitions Markov.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Contexte Temporel (In-Session)',
            'key': 'W_IN_CONTEXT',
            'weight': float(bench.V16_IN['w_ctx']),
            'cat': 'Contexte Temporel',
            'desc': 'Heure générale en cours de session.',
            'category_type': 'In-Session Regime'
        },
        {
            'name': 'Périphérique Audio (Device)',
            'key': 'W_COLD_DEVICE',
            'weight': float(bench.V16_COLD['w_device']),
            'cat': 'Contexte Matériel',
            'desc': 'Casque Bluetooth / écouteurs vs haut-parleur.',
            'category_type': 'Hardware / State'
        },
        {
            'name': 'Temps sortie de veille (sr)',
            'key': 'W_COLD_SR',
            'weight': float(bench.V16_COLD['w_sr']),
            'cat': 'Contexte Matériel',
            'desc': 'Secondes écoulées depuis déverrouillage écran.',
            'category_type': 'Hardware / State'
        },
        {
            'name': 'Notifications Récentes',
            'key': 'W_NOTIF',
            'weight': float(bench.V16_IN['w_notif']),
            'cat': 'Contexte Système',
            'desc': 'Package de la notification active.',
            'category_type': 'Contextual Trigger'
        }
    ]

    total_weight = sum(f['weight'] for f in features)
    for f in features:
        f['importance_pct'] = round((f['weight'] / total_weight) * 100, 1)

    features.sort(key=lambda x: x['weight'], reverse=True)
    for idx, f in enumerate(features, 1):
        f['rank'] = idx

    cat_weights = collections.defaultdict(float)
    for f in features:
        cat_weights[f['cat']] += f['weight']

    categories = [
        {'category': cat, 'weight': round(w, 2), 'share_pct': round((w / total_weight) * 100, 1)}
        for cat, w in sorted(cat_weights.items(), key=lambda x: x[1], reverse=True)
    ]

    result = {
        'total_weight': round(total_weight, 2),
        'features': features,
        'categories': categories
    }

    print("Computed feature importance successfully for v16 Dual-Regime:")
    for f in features:
        print(f"#{f['rank']:<2} {f['name']:<40} | Poids: {f['weight']:<5.2f} | Importance: {f['importance_pct']:>4.1f}% | Cat: {f['cat']}")

    with open('dashboard_data.json') as f:
        data = json.load(f)

    data['feature_importance'] = result
    
    # Update benchmark numbers with official v16 full walk-forward results
    data['benchmark'] = [
        {
            'name': 'v16 (deployed dual-regime)',
            'r1': 31.8,
            'r3': 57.1,
            'r5': 68.8,
            'r10': 83.1,
            'mrr': 0.4833,
            'lift': 9.24,
            'status': 'Production v16'
        },
        {
            'name': 'bigram Markov',
            'r1': 31.4,
            'r3': 56.5,
            'r5': 68.7,
            'r10': 82.6,
            'mrr': 0.4792,
            'lift': 9.16,
            'status': 'Candidat Séquentiel'
        },
        {
            'name': 'RRF ensemble',
            'r1': 29.7,
            'r3': 56.4,
            'r5': 69.0,
            'r10': 83.4,
            'mrr': 0.4698,
            'lift': 8.98,
            'status': 'Ensemble Fusion'
        },
        {
            'name': 'v15 (single-regime)',
            'r1': 29.2,
            'r3': 55.7,
            'r5': 67.8,
            'r10': 81.9,
            'mrr': 0.4636,
            'lift': 8.86,
            'status': 'Précédent v15'
        },
        {
            'name': 'v14 (baseline initiale)',
            'r1': 29.1,
            'r3': 55.6,
            'r5': 68.0,
            'r10': 82.3,
            'mrr': 0.4625,
            'lift': 8.84,
            'status': 'Baseline v14'
        },
        {
            'name': 'recency baseline',
            'r1': 23.2,
            'r3': 53.6,
            'r5': 64.6,
            'r10': 79.4,
            'mrr': 0.4242,
            'lift': 8.11,
            'status': 'Baseline Naïve'
        }
    ]

    data['summary']['recall_1'] = 31.8
    data['summary']['recall_5'] = 68.8
    data['summary']['recall_10'] = 83.1
    data['summary']['mrr'] = 0.4833
    data['summary']['lift'] = 9.24

    with open('dashboard_data.json', 'w') as f:
        json.dump(data, f, indent=2)

    print("Updated dashboard_data.json with v16 Dual-Regime feature importance and benchmark!")
    return result

if __name__ == '__main__':
    compute_feature_importance()
