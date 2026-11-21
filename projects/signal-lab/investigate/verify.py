"""Recompute the 20 September 2011 investigation from the public release files."""
import argparse
import gzip
import hashlib
import json
from datetime import date, timedelta
from decimal import Decimal
from pathlib import Path
from statistics import median

ALERT = "c646f48c31360b392784.weekday-1.median.net.2011-09-20"


def money(amount, places=2, signed=False):
    sign = "−" if amount < 0 else "+" if signed else ""
    return f"{sign}£{abs(amount):,.{places}f}"


def investigate(manifest, compressed):
    release = manifest["financial"]["revenue"]
    digest = hashlib.sha256(compressed).hexdigest()
    if digest != release["sha256"]:
        raise ValueError("Compressed snapshot failed the manifest's SHA-256 check")
    snapshot = json.loads(gzip.decompress(compressed))
    row = next((entry for entry in snapshot["alerts"] if entry["id"] == ALERT), None)
    if row is None or snapshot["revision"] != release["revision"]:
        raise ValueError("This release does not contain the requested alert and revision")
    when = date.fromisoformat(row["date"])
    series = {date.fromisoformat(day["date"]): day for day in snapshot["series"]}
    history = sorted(Decimal(day["net"]) for observed, day in series.items()
                     if when - timedelta(weeks=12) <= observed < when
                     and observed.weekday() == when.weekday())
    daily = series[when]
    actual = Decimal(daily["gross"]) - Decimal(daily["credits"])
    expected = median(history)
    delta = actual - expected
    contributions = {country: Decimal(value) for country, value in row["countries"].items()}
    if (len(history) < 6 or actual != Decimal(row["actual"]) or expected != Decimal(row["expected"])
            or delta != Decimal(row["delta"]) or sum(contributions.values()) != delta):
        raise ValueError("Daily figures, weekday baseline or country contributions do not reconcile")
    uk = contributions["United Kingdom"]
    nl = contributions["Netherlands"]
    return {
        "alert": ALERT, "revision": release["revision"], "sha256": digest, "file": release["file"],
        "gross": money(Decimal(daily["gross"])), "credits": money(Decimal(daily["credits"])),
        "actual": money(actual), "expected": money(expected), "delta": money(delta, signed=True),
        "tuesdays": len(history), "middle_low": money(history[(len(history) - 1) // 2]),
        "middle_high": money(history[len(history) // 2]),
        "uk": money(uk, 3, True), "netherlands": money(nl, 3, True),
        "others": money(delta - uk - nl, 3, True),
    }


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("snapshot", type=Path)
    parser.add_argument("--check", type=Path, help="compare with the case study's saved calculation")
    args = parser.parse_args()
    receipt = investigate(json.loads(args.manifest.read_bytes()), args.snapshot.read_bytes())
    if args.check and receipt != json.loads(args.check.read_bytes()):
        raise SystemExit("Case-study figures differ from the public release")
    print(json.dumps(receipt, ensure_ascii=False, indent=2))
