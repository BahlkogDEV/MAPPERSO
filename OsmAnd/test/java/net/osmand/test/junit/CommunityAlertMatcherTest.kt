package net.osmand.test.junit

import net.osmand.Location
import net.osmand.plus.plugins.communityalerts.CommunityAlert
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatch
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertMatcherTest {

	private val matcher = CommunityAlertMatcher()
	private val route = listOf(
		location(0.0, 0.0),
		location(0.0, 0.01)
	)

	@Test
	fun alertExactlyOnRouteIsOnRoute() {
		val match = match(alert(latitude = 0.0, longitude = 0.005))

		assertEquals(CommunityAlertMatch.Status.ON_ROUTE, match?.status)
		assertEquals(0.0, match?.distanceToRouteMeters ?: Double.NaN, 0.1)
		assertTrue(match?.ahead == true)
	}

	@Test
	fun alertTwentyMetersFromRouteIsOnRoute() {
		val match = match(alert(latitude = metersAsLatitude(20.0), longitude = 0.005))

		assertEquals(CommunityAlertMatch.Status.ON_ROUTE, match?.status)
		assertEquals(20.0, match?.distanceToRouteMeters ?: Double.NaN, 1.0)
	}

	@Test
	fun alertOneHundredMetersFromRouteIsOffRoute() {
		val match = match(alert(latitude = metersAsLatitude(100.0), longitude = 0.005))

		assertEquals(CommunityAlertMatch.Status.OFF_ROUTE, match?.status)
		assertEquals(100.0, match?.distanceToRouteMeters ?: Double.NaN, 2.0)
	}

	@Test
	fun alertBehindCurrentProgressIsBehind() {
		val match = match(
			alert = alert(latitude = 0.0, longitude = 0.003),
			currentPosition = location(0.0, 0.008)
		)

		assertEquals(CommunityAlertMatch.Status.BEHIND, match?.status)
		assertFalse(match?.ahead ?: true)
		assertTrue((match?.distanceAheadMeters ?: 0.0) < 0.0)
	}

	@Test
	fun noRouteProducesNoActiveMatching() {
		val match = matcher.match(
			alert = alert(latitude = 0.0, longitude = 0.005),
			routeGeometry = null,
			currentRouteIndex = 0,
			currentPosition = null,
			now = NOW
		)

		assertEquals(CommunityAlertMatch.Status.NO_ROUTE, match?.status)
		assertFalse(match?.activeRoute ?: true)
		assertNull(match?.distanceToRouteMeters)
		assertNull(match?.distanceAheadMeters)
	}

	@Test
	fun expiredAlertIsIgnored() {
		val match = match(alert(latitude = 0.0, longitude = 0.005, expiresAt = NOW))

		assertNull(match)
	}

	private fun match(
		alert: CommunityAlert,
		currentPosition: Location = location(0.0, 0.002)
	): CommunityAlertMatch? = matcher.match(
		alert = alert,
		routeGeometry = route,
		currentRouteIndex = 1,
		currentPosition = currentPosition,
		now = NOW
	)

	private fun alert(
		latitude: Double,
		longitude: Double,
		expiresAt: Long = NOW + 1_000
	) = CommunityAlert(
		id = "test-alert-$latitude-$longitude",
		type = CommunityAlert.Type.HAZARD,
		latitude = latitude,
		longitude = longitude,
		timestamp = NOW - 1_000,
		expiresAt = expiresAt
	)

	private fun location(latitude: Double, longitude: Double) =
		Location("test", latitude, longitude)

	private fun metersAsLatitude(meters: Double): Double = meters / METERS_PER_LATITUDE_DEGREE

	companion object {
		private const val NOW = 1_000_000L
		private const val METERS_PER_LATITUDE_DEGREE = 111_320.0
	}
}
