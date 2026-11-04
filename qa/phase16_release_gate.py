#!/usr/bin/env python3
"""Fail-closed *evidence* checklist. Does not inspect Minecraft client behavior.

A PASS entry only means a human supplied a nonempty log whose SHA-256 matches.
It does NOT prove the tests passed or authorize enabling the Simulator. A human
still needs to inspect each attachment and sign off before any server rollout.
"""
import hashlib
import json
import pathlib
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
EVIDENCE = ROOT / "qa/evidence/PHASE16_RELEASE_EVIDENCE.json"
REQUIRED = (
    "GRADLE_COMPILE", "GRADLE_JUNIT", "G3_NATIVE_HOOK", "G5_PACKET_CLIENT",
    "G7_CATCH_REWARD", "G15_CUSTOM_MODEL", "RUNTIME_LOAD_TEST",
    "RELOAD_SHUTDOWN", "ROLLBACK_TEST",
)

def main():
    issues = []
    try:
        doc = json.loads(EVIDENCE.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        print(f"RELEASE BLOCKED: cannot load evidence: {exc}")
        return 2
    if doc.get("schema-version") != 1 or not isinstance(doc.get("gates"), dict):
        print("RELEASE BLOCKED: invalid evidence schema")
        return 2
    for gate in REQUIRED:
        entry = doc["gates"].get(gate)
        if not isinstance(entry, dict) or entry.get("status") != "PASS":
            issues.append(f"{gate}: missing verified real-world evidence")
            continue
        relative = entry.get("log", "")
        digest = entry.get("sha256", "")
        if (not isinstance(relative, str) or not relative or
            not isinstance(digest, str) or len(digest) != 64):
            issues.append(f"{gate}: log path and 64-character sha256 are required")
            continue
        path = (ROOT / relative).resolve()
        # A release log should be included in the checked-in handoff, not an
        # arbitrary absolute file on the reviewer's filesystem.
        if not path.is_relative_to(ROOT / "qa/evidence") or not path.is_file():
            issues.append(f"{gate}: evidence file missing/outside qa/evidence")
            continue
        data = path.read_bytes()
        if not data or hashlib.sha256(data).hexdigest() != digest.lower():
            issues.append(f"{gate}: log empty or SHA-256 mismatch")
    signoff = doc.get("release_signoff", {})
    if not isinstance(signoff, dict) or not all(signoff.get(key) for key in ("reviewer", "date", "server_build")):
        issues.append("SIGNOFF: missing server owner/reviewer, date or exact Paper build")

    # Never approve a public release simply because evidence JSON was edited:
    # the source has no validated public rollout implementation yet.
    if issues:
        print("RELEASE BLOCKED. Outstanding evidence:")
        for item in issues:
            print(" -", item)
    else:
        print("EVIDENCE ATTACHMENTS COMPLETE — MANUAL RELEASE REVIEW STILL REQUIRED.")
        print("Check report content and source/public rollout gates before enabling any feature.")
    return 2 if issues else 0

if __name__ == "__main__":
    sys.exit(main())
