"""Fast isolated QA package tests before full Android CI."""
from pathlib import Path
from tempfile import TemporaryDirectory
from qa_variant import apply, PACKAGE


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
        strings.write_text("""<resources><string name="app_name">PINK IPTV</string>
<string name="main_activity_title">PINK IPTV</string>
<string name="dream_label">PINK IPTV</string></resources>""")
        apply(Path(temp))
        amended = gradle.read_text()
        assert amended.count('applicationIdSuffix = ".qa"') == 1
        assert amended.count('applicationId = "com.pinkiptv.extreme"') == 1
        assert 'getByName("release") { isMinifyEnabled = true }' in amended
        assert PACKAGE == "com.pinkiptv.extreme.qa"
        assert strings.read_text().count("PINK IPTV TESTE") == 2
        assert '<string name="dream_label">PINK IPTV</string>' in strings.read_text()
        try:
            apply(Path(temp))
        except AssertionError:
            pass
        else:
            raise AssertionError("Unsafe double-application not rejected")
        assert amended == gradle.read_text()
    print("PINK_QA_INSTALLATION_ISOLATION_FAST_TEST=PASS")


if __name__ == "__main__":
    main()
