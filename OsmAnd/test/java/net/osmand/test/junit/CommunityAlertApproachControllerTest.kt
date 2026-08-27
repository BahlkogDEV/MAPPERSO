package net.osmand.test.junit

import net.osmand.plus.plugins.communityalerts.CommunityAlert
import net.osmand.plus.plugins.communityalerts.CommunityAlertApproachController
import net.osmand.plus.plugins.communityalerts.CommunityAlertMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertApproachControllerTest {

	private val controller = CommunityAlertApproachController()

	@Test
	fun onRouteAlertAtThreeHundredMetersTriggers() {
		val events = controller.evaluate(listOf(match(distanceAheadMeters = 300.0)), NOW)

		assertEquals(1, events.size)
		assertEquals(300.0, events.single().distanceAheadMeters, 0.0)
		assertEquals(1, events.single().triggerCount)
	}

	@Test
	fun onRouteAlertAtSevenHundredMetersDoesNotTrigger() {
		val events = controller.evaluate(listOf(match(distanceAheadMeters = 700.0)), NOW)

		assertTrue(events.isEmpty())
		assertEquals(0, controller.triggerCount)
	}

	@Test
	fun offRouteAlertDoesNotTrigger() {
		val events = controller.evaluate(
			listOf(match(distanceAheadMeters = 300.0, onRoute = false)),
			NOW
		)

		assertTrue(events.isEmpty())
	}

	@Test
	fun behindAlertDoesNotTrigger() {
		val events = controller.evaluate(
			listOf(match(distanceAheadMeters = -50.0, ahead = false)),
			NOW
		)

		assertTrue(events.isEmpty())
	}

	@Test
	fun noRouteAlertDoesNotTrigger() {
		val events = controller.evaluate(
			listOf(
				match(
					distanceAheadMeters = null,
					activeRoute = false,
					onRoute = false,
					ahead = false
				)
			),
			NOW
		)

		assertTrue(events.isEmpty())
	}

	@Test
	fun expiredAlertDoesNotTrigger() {
		val events = controller.evaluate(
			listOf(match(distanceAheadMeters = 300.0, expiresAt = NOW)),
			NOW
		)

		assertTrue(events.isEmpty())
	}

	@Test
	fun sameAlertEvaluatedTenTimesTriggersOnce() {
		val match = match(distanceAheadMeters = 300.0)
		val events = buildList {
			repeat(10) {
				addAll(controller.evaluate(listOf(match), NOW + it))
			}
		}

		assertEquals(1, events.size)
		assertEquals(1, controller.triggerCount)
		assertEquals(1, controller.lastEvent?.triggerCount)
	}

	@Test
	fun recalculatedRouteWithSameAlertIdDoesNotTriggerAgain() {
		val firstMatch = match(id = ALERT_ID, distanceAheadMeters = 300.0)
		val recalculatedMatch = match(
			id = ALERT_ID,
			distanceAheadMeters = 250.0,
			source = "recalculated-route"
		)

		assertEquals(1, controller.evaluate(listOf(firstMatch), NOW).size)
		assertTrue(controller.evaluate(listOf(recalculatedMatch), NOW + 1).isEmpty())
		assertEquals(1, controller.triggerCount)
	}

	@Test
	fun temporaryDisappearanceDoesNotRearmAlertBeforeExpiration() {
		val match = match(distanceAheadMeters = 300.0)
		assertEquals(1, controller.evaluate(listOf(match), NOW).size)

		controller.evaluate(emptyList(), NOW + 1)

		assertTrue(controller.evaluate(listOf(match), NOW + 2).isEmpty())
		assertEquals(1, controller.triggerCount)
	}

	@Test
	fun sameIdCanTriggerAgainAfterTriggeredAlertExpires() {
		val activeMatch = match(id = ALERT_ID, distanceAheadMeters = 300.0, expiresAt = NOW + 1)
		val renewedMatch = match(id = ALERT_ID, distanceAheadMeters = 300.0, expiresAt = NOW + 100)
		assertEquals(1, controller.evaluate(listOf(activeMatch), NOW).size)

		controller.evaluate(emptyList(), NOW + 1)

		assertEquals(1, controller.evaluate(listOf(renewedMatch), NOW + 2).size)
		assertEquals(2, controller.triggerCount)
	}

	@Test
	fun alertCanBeRearmedExplicitly() {
		val match = match(distanceAheadMeters = 300.0)
		assertEquals(1, controller.evaluate(listOf(match), NOW).size)

		assertTrue(controller.rearm(ALERT_ID))

		assertEquals(1, controller.evaluate(listOf(match), NOW + 1).size)
		assertEquals(2, controller.triggerCount)
	}

	private fun match(
		id: String = ALERT_ID,
		distanceAheadMeters: Double?,
		activeRoute: Boolean = true,
		onRoute: Boolean = true,
		ahead: Boolean = true,
		expiresAt: Long = NOW + 1_000,
		source: String = "test"
	): CommunityAlertMatch {
		val alert = CommunityAlert(
			id = id,
			type = CommunityAlert.Type.HAZARD,
			latitude = 0.0,
			longitude = 0.0,
			timestamp = NOW - 1,
			expiresAt = expiresAt,
			source = source
		)
		return CommunityAlertMatch(
			alert = alert,
			distanceToRouteMeters = if (onRoute) 0.0 else 100.0,
			distanceAheadMeters = distanceAheadMeters,
			onRoute = onRoute,
			ahead = ahead,
			activeRoute = activeRoute
		)
	}

	companion object {
		private const val NOW = 1_000_000L
		private const val ALERT_ID = "community-alert-test"
	}
}
