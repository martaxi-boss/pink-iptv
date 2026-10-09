"""Opt-in parallel-install Android QA identity for a disposable PINK checkout.

Keeps Java namespace, Rust/Tauri bundle identifier and release signing untouched.
Only debug applicationId is suffixed, so the existing PINK installation cannot
be replaced by Android package manager when this QA build is installed.
"""
from pathlib import Path
import re
import sys

PACKAGE = "com.pinkiptv.extreme.qa"


def apply(dest: Path) -> None:
    android = dest / "src-tauri/gen/android/app"
    gradle = android / "build.gradle.kts"
    original = gradle.read_text()
    marker = '        getByName("debug") {\n            isDebuggable = true'
    assert original.count(marker) == 1, "Unexpected debug Gradle layout"
    assert 'applicationId = "com.pinkiptv.extreme"' in original
    assert "applicationIdSuffix" not in original
    amended = original.replace(
        marker,
        '        getByName("debug") {\n            applicationIdSuffix = ".qa"\n            isDebuggable = true',
    )
    assert amended.count('applicationIdSuffix = ".qa"') == 1
    assert 'getByName("release")' in amended
    gradle.write_text(amended)

    strings = android / "src/main/res/values/strings.xml"
    resources = strings.read_text()
    for name in ("app_name", "main_activity_title"):
        matcher = re.compile(r'(<string name="' + name + r'">)([^<]+)(</string>)')
        found = matcher.search(resources)
        assert found and found.group(2) == "PINK IPTV", name
        resources, count = matcher.subn(r"\g<1>PINK IPTV TESTE\g<3>", resources)
        assert count == 1
    strings.write_text(resources)

    # Android namespace and Rust/Tauri identifier remain at the original
    # baseline. Gradle debug application ID alone changes, which isolates
    # installed app data, Android Keystore and WireGuard installation identity.
    assert 'namespace = "com.pinkiptv.extreme"' in amended
    assert 'applicationId = "com.pinkiptv.extreme"' in amended
    print("PINK_QA_PARALLEL_PACKAGE=" + PACKAGE)
    print("PINK_QA_ORIGINAL_PACKAGE_UNMODIFIED=PASS")
    print("PINK_QA_DEBUG_ONLY_SUFFIX=PASS")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: qa_variant.py <disposable-pinned-source>")
    apply(Path(sys.argv[1]).resolve())
