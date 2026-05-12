#!/usr/bin/env python3
"""Round 6: retune weights with session_ms=5min (new finding)."""
import json, math, collections, itertools, time
from pathlib import Path

def log(msg): print(msg, flush=True)

events = json.loads(Path("/tmp/usage_log.json").read_text())
es = sorted(events, key=lambda e: e["timestampMillis"])
all_pkgs = list({e["packageName"] for e in es})
n_apps = len(all_pkgs)
DAY_MS=86400000
log(f"Loaded {len(es)} events, {n_apps} apps")

def _hd(a,b): d=abs(a-b); return min(d,24-d)
def _hm(hd,s=0.75): return math.exp(-(hd**2)/(2*s*s))
def _dm(d,nd): return 1.0 if d==0 or d==nd else (0.6 if (d>=6)==(nd>=6) else 0.2)
def _dec(ts,now,hl): return 0.5**((now-ts)/DAY_MS/hl)
def _norm(d):
    m=max(d.values()) if d else 0
    return {p:v/m for p,v in d.items()} if m>0 else d

def score(evts, nh, nd, nm, wc=1.0, wr=2.5, wt=2.0, rh=2.5, sig=0.75, sess_ms=15*60_000, hl=7.0):
    bp=collections.defaultdict(list)
    for e in evts: bp[e["packageName"]].append(e)
    se=sorted(evts,key=lambda x:x["timestampMillis"])
    tr=collections.defaultdict(lambda:collections.defaultdict(float))
    for i in range(1,len(se)):
        p,c=se[i-1],se[i]
        if c["timestampMillis"]-p["timestampMillis"]<=sess_ms:
            tr[p["packageName"]][c["packageName"]]+=_dec(p["timestampMillis"],nm,hl=14.0)
    le=se[-1] if se else None
    in_s=le and(nm-le["timestampMillis"])<=sess_ms
    row,den={},0.0
    if in_s and le["packageName"] in tr:
        row=dict(tr[le["packageName"]]); den=sum(row.values())+0.5*len(bp)
    cr,rr,tor={},{},{}
    for pkg,evs in bp.items():
        td=hs=ds=lm=0.0
        for e in evs:
            dec=_dec(e["timestampMillis"],nm,hl=hl)
            hm=_hm(_hd(e["hour"],nh),s=sig); dm=_dm(e.get("dayOfWeek",0),nd)
            td+=dec; hs+=hm*dec; ds+=dm*dec
            if e["timestampMillis"]>lm: lm=e["timestampMillis"]
        cr[pkg]=(hs*ds/td) if td>0 else 0
        rr[pkg]=math.exp(-((nm-lm)/3600000)/rh)
        tor[pkg]=((row.get(pkg,0)+0.5)/den) if den>0 else 0
    cN,rN,tN=_norm(cr),_norm(rr),_norm(tor)
    return {p:wc*cN[p]+wr*rN[p]+wt*tN[p] for p in bp}

def ev(fn):
    h={1:0,3:0,5:0,10:0}; rr=0.0; n=0
    for i,t in enumerate(es):
        if i<10: continue
        s=fn(es[:i],t["hour"],t.get("dayOfWeek",0) or 1,t["timestampMillis"])
        ranked=sorted(all_pkgs,key=lambda p:s.get(p,0.0),reverse=True)
        for k in h:
            if t["packageName"] in ranked[:k]: h[k]+=1
        rr+=1.0/(ranked.index(t["packageName"])+1); n+=1
    return {"@1":h[1]/n*100,"@3":h[3]/n*100,"@5":h[5]/n*100,"@10":h[10]/n*100,"mrr":rr/n}

log("\n[1] Reference points:")
r=ev(lambda e,nh,nd,nm: score(e,nh,nd,nm))
log(f"  deployed (sess=15)       @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}")
r=ev(lambda e,nh,nd,nm: score(e,nh,nd,nm,sess_ms=5*60_000))
log(f"  sess=5min (current weights) @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}")
r=ev(lambda e,nh,nd,nm: score(e,nh,nd,nm,sess_ms=10*60_000))
log(f"  sess=10min                @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}")

log("\n[2] Grid: wc × wr × wt × rh, with sess_ms=5min")
combos = list(itertools.product([0.5,0.8,1.0,1.2,1.5],[2.0,2.5,3.0,3.5],[1.0,1.5,2.0,2.5,3.0],[2.0,2.5,3.0,4.0]))
log(f"  {len(combos)} combos…")
best_at1={"@1":0}; best_mrr={"mrr":0}; best_joint={"score":0}
t0=time.time()
for i,(wc,wr,wt,rh) in enumerate(combos):
    r=ev(lambda e,nh,nd,nm,a=wc,b=wr,c=wt,d=rh: score(e,nh,nd,nm,wc=a,wr=b,wt=c,rh=d,sess_ms=5*60_000))
    if r["@1"]>best_at1["@1"]: best_at1={**r,"params":(wc,wr,wt,rh)}
    if r["mrr"]>best_mrr["mrr"]: best_mrr={**r,"params":(wc,wr,wt,rh)}
    sc=r["@1"]+r["@5"]
    if sc>best_joint["score"]: best_joint={"score":sc,**r,"params":(wc,wr,wt,rh)}
    if (i+1)%50==0:
        log(f"  progress {i+1}/{len(combos)}  [{time.time()-t0:.0f}s]  current best @1={best_at1['@1']:.1f}%")

log(f"\nBest @1:    wc={best_at1['params'][0]} wr={best_at1['params'][1]} wt={best_at1['params'][2]} rh={best_at1['params'][3]}  @1={best_at1['@1']:.1f}% @5={best_at1['@5']:.1f}% MRR={best_at1['mrr']:.4f}")
log(f"Best MRR:   wc={best_mrr['params'][0]} wr={best_mrr['params'][1]} wt={best_mrr['params'][2]} rh={best_mrr['params'][3]}  @1={best_mrr['@1']:.1f}% @5={best_mrr['@5']:.1f}% MRR={best_mrr['mrr']:.4f}")
log(f"Best @1+@5: wc={best_joint['params'][0]} wr={best_joint['params'][1]} wt={best_joint['params'][2]} rh={best_joint['params'][3]}  @1={best_joint['@1']:.1f}% @5={best_joint['@5']:.1f}% MRR={best_joint['mrr']:.4f}")
log(f"\nTotal time: {time.time()-t0:.0f}s")
