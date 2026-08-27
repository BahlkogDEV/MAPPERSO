package net.osmand.test.junit

import net.osmand.Location
import net.osmand.plus.plugins.communityalerts.CommunityAlertApproachController
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatch
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatcher
import net.osmand.plus.plugins.communityalerts.CommunityAlertsDebugGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertsDebugGeneratorTest {

	private val matcher = CommunityAlertMatcher()
	private val generator = CommunityAlertsDebugGenerator(matcher)
	private val route = listOf(
		location(0.0, 0.0),
		location(0.0, 0.02)
	)

	@Test
	fun routeDemoAlertsProduceExpectedMatcherStatuses() {
		val currentPosition = location(0.0, 0.005)
		val alerts = generator.createRouteDemoAlerts(
			routeGeometry = route,
			currentRouteIndex = 1,
			currentPosition = currentPosition,
			now = NOW
		)
		val matches = matcher.matchAll(
			alerts = alerts,
			routeGeometry = route,
			currentRouteIndex = 1,
			currentPosition = currentPosition,
			now = NOW
		)

		assertEquals(3, matches.size)
		assertEquals(CommunityAlertMatch.Status.ON_ROUTE, matches[0].status)
		assertEquals(CommunityAlertMatch.Status.OFF_ROUTE, matches[1].status)
		assertEquals(CommunityAlertMatch.Status.BEHIND, matches[2].status)
		assertEquals(300.0, matches[0].distanceAheadMeters ?: Double.NaN, 2.0)
		assertEquals(100.0, matches[1].distanceToRouteMeters ?: Double.NaN, 2.0)
		assertEquals(-150.0, matches[2].distanceAheadMeters ?: Double.NaN, 2.0)
		val approachEvents = CommunityAlertApproachController().evaluate(matches, NOW)
		assertEquals(1, approachEvents.size)
		assertEquals("local-debug-route-on", approachEvents.single().alert.source)
		val diagnostic = generator.lastBehindDiagnostic
		assertTrue(diagnostic?.generated == true)
		assertEquals(150.0, diagnostic?.placedBehindMeters ?: Double.NaN, 2.0)
		assertNull(diagnostic?.omissionReason)
	}

	@Test
	fun behindAlertUsesAvailableDistanceNearRouteStart() {
		val currentPosition = location(0.0, metersAsLongitude(30.0))
		val alerts = generator.createRouteDemoAlerts(
			routeGeometry = route,
			currentRouteIndex = 1,
			currentPosition = currentPosition,
			now = NOW
		)
		val matches = matcher.matchAll(
			alerts = alerts,
			routeGeometry = route,
			currentRouteIndex = 1,
			currentPosition = currentPosition,
			now = NOW
		)

		assertEquals(3, matches.size)
		assertEquals(CommunityAlertMatch.Status.BEHIND, matches[2].status)
		assertEquals(-30.0, matches[2].distanceAheadMeters ?: Double.NaN, 1.0)
		assertEquals(30.0, generator.lastBehindDiagnostic?.behindAvailableMeters ?: Double.NaN, 1.0)
	}

	@Test
	fun behindAlertIsOmittedWhenNoDistinctPortionExists() {
		val currentPosition = location(0.0, metersAsLongitude(10.0))
		val alerts = generator.createRouteDemoAlerts(
			routeGeometry = route,
			currentRouteIndex = 1,
			currentPosition = currentPosition,
			now = NOW
		)

		assertEquals(2, alerts.size)
		assertTrue(alerts.none { it.source == "local-debug-route-behind" })
		val diagnostic = generator.lastBehindDiagnostic
		assertFalse(diagnostic?.generated ?: true)
		assertTrue(diagnostic?.omissionReason?.contains("25.0 m") == true)
	}

	@Test
	fun invalidRouteProducesNoRouteDemoAlerts() {
		val alerts = generator.createRouteDemoAlerts(
			routeGeometry = emptyList(),
			currentRouteIndex = 0,
			currentPosition = null,
			now = NOW
		)

		assertTrue(alerts.isEmpty())
		assertNull(generator.lastBehindDiagnostic?.routeProgressMeters)
		assertNull(generator.lastBehindDiagnostic?.behindAvailableMeters)
		assertFalse(generator.lastBehindDiagnostic?.generated ?: true)
	}

	private fun location(latitude: Double, longitude: Double) =
		Location("test", latitude, longitude)

	private fun metersAsLongitude(meters: Double): Double = meters / METERS_PER_DEGREE

	companion object {
		private const val NOW = 1_000_000L
		private const val METERS_PER_DEGREE = 111_320.0
	}
}
