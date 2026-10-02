#!/usr/bin/env python3
"""Inventory the official WireGuard AAR and enforce the PINK APK native boundary."""

from __future__ import annotations

import argparse
import hashlib
from pathlib import Path
import zipfile

WG_GO = "libwg-go.so"
WG_TOOL = "libwg.so"
WG_QUICK = "libwg-quick.so"

def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()

def native_entries(path: Path) -> list[str]:
    with zipfile.ZipFile(path) as archive:
        return sorted(name for name in archive.namelist() if name.endswith(".so") and (name.startswith("jni/") or name.startswith("lib/")))

def abis(entries: list[str]) -> list[str]:
    result = set()
    for entry in entries:
        parts = entry.split("/")
        if len(parts) >= 3 and parts[0] in {"jni", "lib"}:
            result.add(parts[1])
    return sorted(result)

def has_library(entries: list[str], library: str) -> bool:
    return any(Path(entry).name == library for entry in entries)

def print_entries(prefix: str, entries: list[str]) -> None:
    for entry in entries:
        print(f"{prefix}_NATIVE_ENTRY={entry}")

def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--aar", required=True, type=Path)
    parser.add_argument("--apk", required=True, type=Path)
    args = parser.parse_args()
    for path in (args.aar, args.apk):
        if not path.is_file():
            raise SystemExit(f"missing artifact: {path}")
    aar_entries = native_entries(args.aar)
    apk_entries = native_entries(args.apk)
    print(f"WIREGUARD_AAR_SHA256={sha256(args.aar)}")
    print(f"WIREGUARD_AAR_SIZE={args.aar.stat().st_size}")
    print(f"WIREGUARD_AAR_ABIS={','.join(abis(aar_entries))}")
    print_entries("WIREGUARD_AAR", aar_entries)
    print(f"WIREGUARD_AAR_LIBWG_GO={'PRESENT' if has_library(aar_entries, WG_GO) else 'ABSENT'}")
    print(f"WIREGUARD_AAR_LIBWG={'PRESENT' if has_library(aar_entries, WG_TOOL) else 'ABSENT'}")
    print(f"WIREGUARD_AAR_LIBWG_QUICK={'PRESENT' if has_library(aar_entries, WG_QUICK) else 'ABSENT'}")
    print(f"PINK_DEBUG_APK_SIZE={args.apk.stat().st_size}")
    print(f"PINK_APK_ABIS={','.join(abis(apk_entries))}")
    print_entries("PINK_APK", apk_entries)
    if not has_library(aar_entries, WG_GO):
        print("WIREGUARD_AAR_WG_GO_MISSING"); return 1
    if not has_library(apk_entries, WG_GO):
        print("WG_GO_PRESENT=FAIL"); return 1
    print("WG_GO_PRESENT=PASS")
    if has_library(apk_entries, WG_TOOL):
        print("WG_TOOL_LIB_ABSENT=FAIL"); return 1
    print("WG_TOOL_LIB_ABSENT=PASS")
    if has_library(apk_entries, WG_QUICK):
        print("WG_QUICK_LIB_ABSENT=FAIL"); return 1
    print("WG_QUICK_LIB_ABSENT=PASS")
    print("WIREGUARD_NATIVE_PAYLOAD_VERIFIER=PASS")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
