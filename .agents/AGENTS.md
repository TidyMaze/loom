# Loom ML Modeling and Testing Rules

## 1. Hyperparameter Tuning
- **No Large Stride Bias**: When running hyperparameter optimization (like Optuna) on time-series usage logs, do not use evaluation strides greater than 2 (e.g., `--tune-stride 4`). High striding alters the temporal distribution of target evaluations, resulting in parameters that overfit and fail to generalize on the test set. Use stride=1 or at most 2 for tuning.

## 2. Unit Testing ML Scorer Features
- **Isolate Transition Tests**: When unit testing bigram/unigram transitions, set sequence timestamps to be extremely close to each other (e.g., separated by 10s–30s) and at the same hour of day. This prevents strong features like recency (`W_RECENCY`) and hour matching (`W_CONTEXT`) from overpowering the transition weights, ensuring the assertion isolates the transition logic.
