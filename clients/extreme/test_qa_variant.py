"""Fast isolated QA package tests before full Android CI."""
from pathlib import Path
from tempfile import TemporaryDirectory
from qa_variant import apply, repair_after_tauri_build, PACKAGE


def main() -> None:
    with TemporaryDirectory() as temp:
        app = Path(temp) / "src-tauri/gen/android/app"
        (app / "src/main/res/values").mkdir(parents=True)
        gradle = app / "build.gradle.kts"
        gradle.write_text("""android {
    namespace = "com.pinkiptv.extreme"
    defaultConfig { applicationId = "com.pinkiptv.extreme" }
    buildTypes {
        getByName("debug") {
            isDebuggable = true
        }
        getByName("release") { isMinifyEnabled = true }
    }
}""")
        strings = app / "src/main/res/values/strings.xml"
        strings.write_text("""<resources><string name="app_name" translatable="false">PINK IPTV</string>
<string name="main_activity_title">PINK IPTV</string>
<string name="dream_label">PINK IPTV</string></resources>""")
        apply(Path(temp))
        amended = gradle.read_text()
        assert amended.count('applicationIdSuffix = ".qa2"') == 1
        assert amended.count('applicationId = "com.pinkiptv.extreme"') == 1
        assert 'getByName("release") { isMinifyEnabled = true }' in amended
        assert PACKAGE == "com.pinkiptv.extreme.qa2"
        assert PACKAGE not in ("com.pinkiptv.extreme", "com.pinkiptv.extreme.qa")
        assert strings.read_text().count("PINK IPTV TESTE 2") == 2
        assert '<string name="app_name" translatable="false">PINK IPTV TESTE 2</string>' in strings.read_text()
        assert '<string name="dream_label">PINK IPTV</string>' in strings.read_text()
        try:
            apply(Path(temp))
        except AssertionError:
            pass
        else:
            raise AssertionError("Unsafe double-application not rejected")
        assert amended == gradle.read_text()

        # Postbuild verification is idempotent if Tauri kept the QA identity.
        repair_after_tauri_build(Path(temp))
        assert amended == gradle.read_text()

        # Reproduce Tauri overwriting the package suffix and launcher names:
        # after repair the QA remains a separate installable app.
        gradle.write_text(amended.replace(
            '            applicationIdSuffix = ".qa2"\n', "", 1
        ))
        strings.write_text(strings.read_text().replace(
            "PINK IPTV TESTE 2", "PINK IPTV"
        ))
        repair_after_tauri_build(Path(temp))
        assert gradle.read_text() == amended
        assert strings.read_text().count("PINK IPTV TESTE 2") == 2
        assert 'getByName("release") { isMinifyEnabled = true }' in gradle.read_text()
        print("PINK_QA_POST_TAURI_REGENERATION_RECOVERY=PASS")
    print("PINK_QA_INSTALLATION_ISOLATION_FAST_TEST=PASS")


if __name__ == "__main__":
    main()
