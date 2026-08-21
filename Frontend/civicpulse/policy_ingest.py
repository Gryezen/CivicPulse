"""
Policy ingestion pipeline — keeps the `policies` table current from an
external feed instead of only ever growing by hand-editing
policies_data.json.

Read this before assuming it's "auto-scraping a government website" in a
demo: it is NOT that, and the ideation doc itself argues against building
that. The doc's own advice (on scraping officials' contact directories,
same reasoning applies here) is: there's no single clean public source to
scrape, scraping is brittle/legally murky, and a hackathon build should
prefer a seeded/structured feed over a live scraper — treating live
directory/portal integration as a "future integration" slide, not
something actually built. This module follows that advice for policies
too: it's a pluggable ADAPTER pattern (`fetch_from_url` / `fetch_from_file`)
that expects data already in a documented JSON shape, not an HTML scraper
that parses a live government webpage. Pointing `fetch_from_url` at a real
open-data API (e.g. a state open-data portal or myScheme-style endpoint
that already returns structured JSON) is the intended real-world use —
that's an integration step for whoever deploys this, not something
verified here.

**Untested against a live source**: this sandbox has no network access.
`fetch_from_file` (the JSON-file adapter) is exercised by
scripts/demo_policy_source.json and works end-to-end. `fetch_from_url` is
written and type-correct but has not been run against a real HTTP
endpoint — test it against your actual source before relying on it, and
expect to need to adjust the expected JSON shape below to match whatever
that source actually returns.

Expected record shape (list of these, from either adapter):
    {
        "id": "PM-KISAN",                 # required — becomes external_id, used for dedup on re-sync
        "title": "PM-KISAN",              # required
        "category": "Agriculture",        # required
        "summary": "...",                 # required
        "keywords": ["farmer", "income"], # optional, list[str]
        "eligibility": "...",             # optional
        "source": "myScheme",             # optional — display name of the feed
        "source_url": "https://...",      # optional — link to the scheme's own page
    }

Usage:
    python policy_ingest.py --source scripts/demo_policy_source.json   # file adapter
    python policy_ingest.py --source https://example.gov/api/schemes  # URL adapter (untested, see above)

Run this manually or from a scheduled job (cron/Render cron job) — NOT on
every app boot, same reasoning as train_classifier.py: a sync is a
deliberate, loggable event with its own failure mode, not something that
should silently run on every `python app.py`.
"""

import argparse
import json
import re
import sys
from datetime import datetime, timezone


def _slugify(text):
    slug = re.sub(r"[^a-z0-9]+", "-", text.lower()).strip("-")
    return slug[:120] or "policy"


def fetch_from_file(path):
    with open(path, "r", encoding="utf-8") as f:
        data = json.load(f)
    return data if isinstance(data, list) else data.get("policies", [])


def fetch_from_url(url, timeout=15):
    """Untested against a live source in this environment — see module
    docstring. Written defensively (explicit timeout, checks the response
    is actually JSON, raises with a clear message on anything else) so a
    misconfigured source fails loudly at sync time rather than silently
    ingesting garbage."""
    try:
        import requests
    except ImportError:
        raise RuntimeError("The 'requests' package is required for URL sources — pip install requests.")

    resp = requests.get(url, timeout=timeout, headers={"User-Agent": "CivicPulse-PolicyIngest/1.0"})
    resp.raise_for_status()
    try:
        data = resp.json()
    except ValueError:
        raise RuntimeError(f"Source did not return valid JSON: {url}")
    return data if isinstance(data, list) else data.get("policies", [])


def _validate_record(record):
    missing = [k for k in ("id", "title", "category", "summary") if not record.get(k)]
    return missing


def ingest(records, source_label=None):
    """Upserts `records` into the Policy table by external_id (falling
    back to a slugified title if a record has no id — logged as a
    warning, since that means re-syncs can't reliably match it to update
    vs. re-create). Must be called inside an app context (needs the DB
    session) — see run_sync() below.

    Returns a summary dict: {"added": n, "updated": n, "skipped": n, "errors": [...]}.
    """
    from models import Policy
    from extensions import db

    added, updated, skipped, errors = 0, 0, 0, []
    now = datetime.now(timezone.utc)

    for record in records:
        missing = _validate_record(record)
        if missing:
            errors.append(f"Skipped record missing {missing}: {record.get('title', record.get('id', '?'))!r}")
            skipped += 1
            continue

        external_id = str(record.get("id"))
        slug = _slugify(record.get("id") or record["title"])

        existing = Policy.query.filter_by(external_id=external_id).first() or Policy.query.get(slug)

        fields = dict(
            title=record["title"],
            source=record.get("source") or source_label or "",
            category=record["category"],
            summary=record["summary"],
            keywords=record.get("keywords") or [],
            eligibility=record.get("eligibility") or "",
            roadmap=record.get("roadmap") or [],
            external_id=external_id,
            source_url=record.get("source_url") or "",
            last_synced_at=now,
        )

        if existing:
            for key, value in fields.items():
                setattr(existing, key, value)
            updated += 1
        else:
            db.session.add(Policy(slug=slug, **fields))
            added += 1

    db.session.commit()
    return {"added": added, "updated": updated, "skipped": skipped, "errors": errors}


def run_sync(source, app=None):
    """Convenience entrypoint: picks the adapter by whether `source` looks
    like a URL, runs it inside the given Flask app's context (imports
    app.py's `app` if none given — lets this double as a CLI script and as
    something officer.py or a scheduled task can call directly)."""
    if source.startswith("http://") or source.startswith("https://"):
        records = fetch_from_url(source)
    else:
        records = fetch_from_file(source)

    if app is None:
        from app import app as flask_app
        app = flask_app

    with app.app_context():
        return ingest(records, source_label=source)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--source", required=True, help="JSON file path or URL — see module docstring for expected shape.")
    args = parser.parse_args()

    try:
        result = run_sync(args.source)
    except Exception as e:
        print(f"Sync failed: {e}", file=sys.stderr)
        sys.exit(1)

    print(f"Added {result['added']}, updated {result['updated']}, skipped {result['skipped']}.")
    for err in result["errors"]:
        print(f"  ! {err}", file=sys.stderr)
