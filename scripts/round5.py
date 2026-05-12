#!/usr/bin/env python3
"""Round 5: rank fusion + targeted grid. Verbose progress logs."""
import json, math, collections, itertools, sys, time
from pathlib import Path

def log(msg):
    print(msg, flush=True)

events = json.loads(Path("/tmp/usage_log.json").read_text())
es = sorted(events, key=lambda e: e["timestampMillis"])
all_pkgs = list({e["packageName"] for e in es})
n_apps = len(all_pkgs)
SESS_MS=15*60*1000; HL=7.0; DAY_MS=86400000
log(f"Loaded {len(es)} events, {n_apps} apps")

def _hd(a,b): d=abs(a-b); return min(d,24-d)
def _hm(hd,s=0.75): return math.exp(-(hd**2)/(2*s*s))
def _dm(d,nd): return 1.0 if d==0 or d==nd else (0.6 if (d>=6)==(nd>=6) else 0.2)
def _dec(ts,now,hl=HL): return 0.5**((now-ts)/DAY_MS/hl)
def _norm(d):
    m=max(d.values()) if d else 0
    return {p:v/m for p,v in d.items()} if m>0 else d

def v_param(evts, nh, nd, nm, wc=1.0, wr=2.5, wt=2.0, rh=2.5, sig=0.75, trans_hl=14.0):
    bp=collections.defaultdict(list)
    for e in evts: bp[e["packageName"]].append(e)
    se=sorted(evts,key=lambda x:x["timestampMillis"])
    tr=collections.defaultdict(lambda:collections.defaultdict(float))
    for i in range(1,len(se)):
        p,c=se[i-1],se[i]
        if c["timestampMillis"]-p["timestampMillis"]<=SESS_MS:
            tr[p["packageName"]][c["packageName"]]+=_dec(p["timestampMillis"],nm,hl=trans_hl)
    le=se[-1] if se else None
    in_s=le and(nm-le["timestampMillis"])<=SESS_MS
    row,den={},0.0
    if in_s and le["packageName"] in tr:
        row=dict(tr[le["packageName"]]); den=sum(row.values())+0.5*len(bp)
    cr,rr,tor={},{},{}
    for pkg,evs in bp.items():
        td=hs=ds=lm=0.0
        for e in evs:
            dec=_dec(e["timestampMillis"],nm)
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
    mrr=rr/n
    return {"@1":h[1]/n*100,"@3":h[3]/n*100,"@5":h[5]/n*100,"@10":h[10]/n*100,"mrr":mrr}

# ---------- Quick baselines ----------
log("\n[1] Baseline + quick variants:")
t0=time.time()
r=ev(lambda e,nh,nd,nm: v_param(e,nh,nd,nm))
log(f"  v_baseline (deployed)         @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}  [{time.time()-t0:.1f}s]")

# session_ms variants
for sm in [5*60_000, 10*60_000, 15*60_000, 20*60_000, 30*60_000]:
    t0=time.time()
    SESS_MS_LOCAL = sm
    def v_sess(evts, nh, nd, nm, _sm=sm):
        bp=collections.defaultdict(list)
        for e in evts: bp[e["packageName"]].append(e)
        se=sorted(evts,key=lambda x:x["timestampMillis"])
        tr=collections.defaultdict(lambda:collections.defaultdict(float))
        for i in range(1,len(se)):
            p,c=se[i-1],se[i]
            if c["timestampMillis"]-p["timestampMillis"]<=_sm:
                tr[p["packageName"]][c["packageName"]]+=_dec(p["timestampMillis"],nm,hl=14.0)
        le=se[-1] if se else None
        in_s=le and(nm-le["timestampMillis"])<=_sm
        row,den={},0.0
        if in_s and le["packageName"] in tr:
            row=dict(tr[le["packageName"]]); den=sum(row.values())+0.5*len(bp)
        cr,rr,tor={},{},{}
        for pkg,evs in bp.items():
            td=hs=ds=lm=0.0
            for e in evs:
                dec=_dec(e["timestampMillis"],nm)
                hm=_hm(_hd(e["hour"],nh)); dm=_dm(e.get("dayOfWeek",0),nd)
                td+=dec; hs+=hm*dec; ds+=dm*dec
                if e["timestampMillis"]>lm: lm=e["timestampMillis"]
            cr[pkg]=(hs*ds/td) if td>0 else 0
            rr[pkg]=math.exp(-((nm-lm)/3600000)/2.5)
            tor[pkg]=((row.get(pkg,0)+0.5)/den) if den>0 else 0
        cN,rN,tN=_norm(cr),_norm(rr),_norm(tor)
        return {p:1.0*cN[p]+2.5*rN[p]+2.0*tN[p] for p in bp}
    r=ev(v_sess)
    log(f"  session_ms={sm//60000}min               @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}  [{time.time()-t0:.1f}s]")

# transition decay half-life variants
log("\n[2] Transition decay half-life sweep:")
for thl in [3.0, 7.0, 14.0, 30.0, 60.0]:
    t0=time.time()
    r=ev(lambda e,nh,nd,nm,h=thl: v_param(e,nh,nd,nm,trans_hl=h))
    log(f"  trans_hl={thl}d              @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}  [{time.time()-t0:.1f}s]")

# decay half-life main
log("\n[3] Main decay half-life sweep (HL):")
def v_hl(evts, nh, nd, nm, hl=7.0):
    bp=collections.defaultdict(list)
    for e in evts: bp[e["packageName"]].append(e)
    se=sorted(evts,key=lambda x:x["timestampMillis"])
    tr=collections.defaultdict(lambda:collections.defaultdict(float))
    for i in range(1,len(se)):
        p,c=se[i-1],se[i]
        if c["timestampMillis"]-p["timestampMillis"]<=SESS_MS:
            tr[p["packageName"]][c["packageName"]]+=0.5**((nm-p["timestampMillis"])/DAY_MS/14.0)
    le=se[-1] if se else None
    in_s=le and(nm-le["timestampMillis"])<=SESS_MS
    row,den={},0.0
    if in_s and le["packageName"] in tr:
        row=dict(tr[le["packageName"]]); den=sum(row.values())+0.5*len(bp)
    cr,rr,tor={},{},{}
    for pkg,evs in bp.items():
        td=hs=ds=lm=0.0
        for e in evs:
            dec=0.5**((nm-e["timestampMillis"])/DAY_MS/hl)
            hm=_hm(_hd(e["hour"],nh)); dm=_dm(e.get("dayOfWeek",0),nd)
            td+=dec; hs+=hm*dec; ds+=dm*dec
            if e["timestampMillis"]>lm: lm=e["timestampMillis"]
        cr[pkg]=(hs*ds/td) if td>0 else 0
        rr[pkg]=math.exp(-((nm-lm)/3600000)/2.5)
        tor[pkg]=((row.get(pkg,0)+0.5)/den) if den>0 else 0
    cN,rN,tN=_norm(cr),_norm(rr),_norm(tor)
    return {p:1.0*cN[p]+2.5*rN[p]+2.0*tN[p] for p in bp}

for hl in [3.0, 5.0, 7.0, 10.0, 14.0, 21.0]:
    t0=time.time()
    r=ev(lambda e,nh,nd,nm,_h=hl: v_hl(e,nh,nd,nm,hl=_h))
    log(f"  hl={hl}d                     @1={r['@1']:5.1f}% @5={r['@5']:5.1f}% MRR={r['mrr']:.4f}  [{time.time()-t0:.1f}s]")

# Final focused grid: just refine around current best
log("\n[4] Focused grid: rh, wc, wr, wt")
combos = list(itertools.product([2.0,2.5,3.0,4.0], [0.5,0.8,1.0,1.5], [2.0,2.5,3.0], [1.5,2.0,2.5]))
log(f"  {len(combos)} combos…")
best={"@1":0,"params":None}; best_joint={"score":0,"params":None}; best_mrr={"mrr":0,"params":None}
t_start=time.time()
for i,(rh,wc,wr,wt) in enumerate(combos):
    r=ev(lambda e,nh,nd,nm,a=wc,b=wr,c=wt,d=rh: v_param(e,nh,nd,nm,wc=a,wr=b,wt=c,rh=d))
    if r["@1"]>best["@1"]:
        best={"@1":r["@1"],"params":(rh,wc,wr,wt),"r":r}
    sc=r["@1"]+r["@5"]
    if sc>best_joint["score"]:
        best_joint={"score":sc,"params":(rh,wc,wr,wt),"r":r}
    if r["mrr"]>best_mrr["mrr"]:
        best_mrr={"mrr":r["mrr"],"params":(rh,wc,wr,wt),"r":r}
    if (i+1) % 30 == 0:
        log(f"  progress {i+1}/{len(combos)}  [{time.time()-t_start:.1f}s]  current best @1={best['@1']:.1f}%")

log("\n=== RESULTS ===")
log(f"Best @1:    rh={best['params'][0]} wc={best['params'][1]} wr={best['params'][2]} wt={best['params'][3]}  @1={best['r']['@1']:.1f}% @5={best['r']['@5']:.1f}% MRR={best['r']['mrr']:.4f}")
log(f"Best @1+@5: rh={best_joint['params'][0]} wc={best_joint['params'][1]} wr={best_joint['params'][2]} wt={best_joint['params'][3]}  @1={best_joint['r']['@1']:.1f}% @5={best_joint['r']['@5']:.1f}% MRR={best_joint['r']['mrr']:.4f}")
log(f"Best MRR:   rh={best_mrr['params'][0]} wc={best_mrr['params'][1]} wr={best_mrr['params'][2]} wt={best_mrr['params'][3]}  @1={best_mrr['r']['@1']:.1f}% @5={best_mrr['r']['@5']:.1f}% MRR={best_mrr['r']['mrr']:.4f}")
log(f"\nDeployed:   rh=2.5 wc=1.0 wr=2.5 wt=2.0     @1=19.0% @5=58.5% MRR=0.3607")
log(f"\nTotal time: {time.time()-t_start:.0f}s")
