---
name: anti-polling
description: Enforce token-efficient synchronous execution and prohibit periodic timer polling loops.
---

# Anti-Polling & Token Efficiency Skill

## Core Rules

1. **Zero Periodic Polling Loops**:
   - Never spawn recurring timers or 10s/15s sleep loops to check ongoing processes.
   - Polling consumes tokens, floods transcript, wastes execution context.

2. **Synchronous Direct Execution**:
   - Run commands with large `WaitMsBeforeAsync` (e.g. 20000ms to 60000ms) or let script wait internally until full task termination.
   - For long-running commands (e.g. Optuna, full walk-forward eval), let script complete or wait on completion.

3. **Passive Reactive Wakeup**:
   - System automatically notifies agent on command completion.
   - Launch background command -> stop calling tools immediately -> wait for system wakeup message.
   - No intermediary status queries, no CPU loops, no status polling.
