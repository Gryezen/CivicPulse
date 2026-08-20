"""
Offline classifier training script — run this once (or whenever
data/grievance_simulated_dataset.csv changes) to produce classifier_model.joblib,
which classify.py loads at runtime.

This is NOT run automatically on every server start — training on 10k rows
takes a couple of seconds, but it's still "offline model build" territory,
not request-path work. Re-run manually with:

    python train_classifier.py

What it trains, all on TF-IDF(1-2 grams) of `case_name + complaint_description`:
  1. A primary-category classifier — target is the FIRST label in
     `suggested_labels` (rows like "Pension / EPFO + Vigilance / Anti-
     Corruption Bureau" get split on " + "; we train the routing model on
     the primary department and treat the secondary label as a flag, same
     idea as the corruption/threat flags below).
  2. A corruption_flag classifier — positive where `suggested_workflow`
     contains "corruption_flag".
  3. A threat_flag classifier — positive where `suggested_workflow`
     contains "threat_flag".

All three share one TfidfVectorizer so classify.py only has to transform
the input text once per request.

Honesty note (see classify.py's own docstring): this is a real, if small
and simple, supervised model — not a placeholder. It's trained on a
*simulated* dataset, which is disclosed in the pitch/README, not hidden.
"""

import csv
import os

import joblib
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.model_selection import train_test_split
from sklearn.metrics import classification_report, f1_score

HERE = os.path.dirname(os.path.abspath(__file__))
CSV_PATH = os.path.join(HERE, "data", "grievance_simulated_dataset.csv")
MODEL_PATH = os.path.join(HERE, "classifier_model.joblib")


def load_rows():
    with open(CSV_PATH, newline="", encoding="utf-8") as f:
        return list(csv.DictReader(f))


def primary_label(suggested_labels):
    return suggested_labels.split(" + ")[0].strip()


def main():
    rows = load_rows()
    texts = [f"{r['case_name']} {r['complaint_description']}" for r in rows]
    primary = [primary_label(r["suggested_labels"]) for r in rows]
    corruption = [1 if "corruption_flag" in r["suggested_workflow"] else 0 for r in rows]
    threat = [1 if "threat_flag" in r["suggested_workflow"] else 0 for r in rows]

    X_train, X_test, y_train, y_test, corr_train, corr_test, thr_train, thr_test = train_test_split(
        texts, primary, corruption, threat, test_size=0.2, random_state=42, stratify=primary
    )

    vectorizer = TfidfVectorizer(ngram_range=(1, 2), min_df=2, max_features=8000, sublinear_tf=True)
    Xtr = vectorizer.fit_transform(X_train)
    Xte = vectorizer.transform(X_test)

    category_model = LogisticRegression(max_iter=2000, class_weight="balanced", C=5.0)
    category_model.fit(Xtr, y_train)
    pred = category_model.predict(Xte)
    print("=== Category classifier ===")
    print(classification_report(y_test, pred, zero_division=0))

    corruption_model = LogisticRegression(max_iter=2000, class_weight="balanced")
    corruption_model.fit(Xtr, corr_train)
    corr_pred = corruption_model.predict(Xte)
    print("=== Corruption-flag classifier === f1:", f1_score(corr_test, corr_pred, zero_division=0))

    threat_model = LogisticRegression(max_iter=2000, class_weight="balanced")
    threat_model.fit(Xtr, thr_train)
    thr_pred = threat_model.predict(Xte)
    print("=== Threat-flag classifier === f1:", f1_score(thr_test, thr_pred, zero_division=0))

    # Refit on the full dataset for the shipped model (common practice once
    # held-out metrics are recorded above — those numbers are what should
    # go in the pitch deck, not numbers from a model trained on 100% of data).
    Xall = vectorizer.fit_transform(texts)
    category_model.fit(Xall, primary)
    corruption_model.fit(Xall, corruption)
    threat_model.fit(Xall, threat)

    bundle = {
        "vectorizer": vectorizer,
        "category_model": category_model,
        "corruption_model": corruption_model,
        "threat_model": threat_model,
        "categories": sorted(set(primary)),
    }
    joblib.dump(bundle, MODEL_PATH)
    print(f"\nSaved model bundle -> {MODEL_PATH}")


if __name__ == "__main__":
    main()
