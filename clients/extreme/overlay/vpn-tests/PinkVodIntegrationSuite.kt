package com.pinkiptv.extreme

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Keep VPN startup and VOD Media3 verification in one instrumentation process,
 * but make the dependency explicit: PinkVpnStartupTest leaves the validated
 * MainActivity alive and PinkVodTracksTest reuses that host without a second
 * Tauri/native teardown.
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    PinkVpnStartupTest::class,
    PinkVodTracksTest::class,
)
class PinkVodIntegrationSuite
