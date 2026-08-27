package net.osmand.test.junit

import net.osmand.plus.plugins.communityalerts.CommunityAlertBounds
import net.osmand.plus.plugins.communityalerts.CommunityAlertsRefreshPolicy
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertsRefreshPolicyTest {

	private val policy = CommunityAlertsRefreshPolicy(
		periodicRefreshIntervalMs = REFRESH_INTERVAL_MS,
		significantExitInsetRatio = 0.25
	)
	private val initialBounds = CommunityAlertBounds.around(48.0, 2.0, 10_000.0)

	@Test
	fun initialRequestNeedsRefresh() {
		assertTrue(policy.shouldRefresh(initialBounds, NOW))
	}

	@Test
	fun microMovementDoesNotRefresh() {
		policy.recordSuccessfulRefresh(initialBounds, NOW)
		val nearbyBounds = CommunityAlertBounds.around(48.001, 2.001, 10_000.0)

		assertFalse(policy.shouldRefresh(nearbyBounds, NOW + 1_000))
	}

	@Test
	fun significantExitRefreshesBeforePeriodicDeadline() {
		policy.recordSuccessfulRefresh(initialBounds, NOW)
		val movedBounds = CommunityAlertBounds.around(48.06, 2.0, 10_000.0)

		assertTrue(policy.shouldRefresh(movedBounds, NOW + 1_000))
	}

	@Test
	fun periodicDeadlineAllowsRefreshWithoutMovement() {
		policy.recordSuccessfulRefresh(initialBounds, NOW)

		assertTrue(policy.shouldRefresh(initialBounds, NOW + REFRESH_INTERVAL_MS))
	}

	companion object {
		private const val NOW = 1_000_000L
		private const val REFRESH_INTERVAL_MS = 15 * 60 * 1000L
	}
}
