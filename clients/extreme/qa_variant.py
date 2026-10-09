"""Opt-in parallel-install Android QA identity for a disposable PINK checkout.

Keeps Java namespace, Rust/Tauri bundle identifier and release signing untouched.
Only debug applicationId is suffixed, so the existing PINK installation cannot
be replaced by Android package manager when this QA build is installed.
"""
from pathlib import Path
import re
import sys

PACKAGE = "com.pinkiptv.extreme.qa2"


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
        '        getByName("debug") {\n            applicationIdSuffix = ".qa2"\n            isDebuggable = true',
    )
    assert amended.count('applicationIdSuffix = ".qa2"') == 1
    assert 'getByName("release")' in amended
    gradle.write_text(amended)

    strings = android / "src/main/res/values/strings.xml"
    resources = strings.read_text()
    for name in ("app_name", "main_activity_title"):
        matcher = re.compile(r'(<string name="' + re.escape(name) + r'"(?:\s+[^<>]*?)?>)([^<]+)(</string>)')
        found = matcher.search(resources)
        assert found and found.group(2) == "PINK IPTV", name
        resources, count = matcher.subn(r"\g<1>PINK IPTV TESTE 2\g<3>", resources)
        assert count == 1
    strings.write_text(resources)

    # Android namespace and Rust/Tauri identifier remain at the original
    # baseline. Gradle debug application ID alone changes, which isolates
    # installed app data, Android Keystore and WireGuard installation identity.
    assert 'namespace = "com.pinkiptv.extreme"' in amended
    assert 'applicationId = "com.pinkiptv.extreme"' in amended
    assert PACKAGE not in ("com.pinkiptv.extreme", "com.pinkiptv.extreme.qa")
    print("PINK_QA_PARALLEL_PACKAGE=" + PACKAGE)
    print("PINK_QA_ORIGINAL_PACKAGE_UNMODIFIED=PASS")
    print("PINK_QA_DEBUG_ONLY_SUFFIX=PASS")


def repair_after_tauri_build(dest: Path) -> None:
    """Guard and re-assert only the disposable Android debug identity.

    Tauri's Android build/synchronization may regenerate Gradle resources.
    Re-apply the debug-only suffix and user-visible QA label *after* Tauri
    has finished, then let Gradle repackage already-built native libraries.
    Never edit release config, Kotlin namespace or original app identity.
    """
    android = dest / "src-tauri/gen/android/app"
    gradle = android / "build.gradle.kts"
    text = gradle.read_text()
    marker = '        getByName("debug") {\n            isDebuggable = true'
    expected = 'applicationIdSuffix = ".qa2"'
    if expected not in text:
        assert text.count(marker) == 1, "Regenerated debug Gradle shape drift"
        text = text.replace(
            marker,
            '        getByName("debug") {\n            applicationIdSuffix = ".qa2"\n            isDebuggable = true',
        )
        gradle.write_text(text)
    assert text.count(expected) == 1, "Duplicate or unrecognized QA suffix"
    assert 'namespace = "com.pinkiptv.extreme"' in text
    assert 'applicationId = "com.pinkiptv.extreme"' in text
    debug_pos = text.index('getByName("debug")')
    suffix_pos = text.index(expected)
    release_pos = text.index('getByName("release")')
    assert debug_pos < suffix_pos < release_pos, "QA suffix escaped debug build"

    strings = android / "src/main/res/values/strings.xml"
    values = strings.read_text()
    for name in ("app_name", "main_activity_title"):
        matcher = re.compile(
            r'(<string name="' + re.escape(name) + r'"(?:\s+[^<>]*?)?>)([^<]+)(</string>)'
        )
        matches = list(matcher.finditer(values))
        assert len(matches) == 1, "Unexpected resource identity: " + name
        assert matches[0].group(2) in ("PINK IPTV", "PINK IPTV TESTE 2")
        if matches[0].group(2) == "PINK IPTV":
            values = matcher.sub(r"\g<1>PINK IPTV TESTE 2\g<3>", values)
    strings.write_text(values)
    assert values.count("PINK IPTV TESTE 2") >= 2
    print("PINK_QA_POST_TAURI_DEBUG_IDENTITY_REASSERTED=PASS")
    print("PINK_QA_POST_TAURI_ORIGINAL_PACKAGE_UNCHANGED=PASS")


if __name__ == "__main__":
    if len(sys.argv) == 2:
        apply(Path(sys.argv[1]).resolve())
    elif len(sys.argv) == 3 and sys.argv[2] == "--postbuild":
        repair_after_tauri_build(Path(sys.argv[1]).resolve())
    else:
        raise SystemExit("Usage: qa_variant.py <disposable-source> [--postbuild]")
