package net.osmand.test.junit

import net.osmand.plus.plugins.communityalerts.CommunityAlert
import net.osmand.plus.plugins.communityalerts.CommunityAlertAnnouncementFormatter
import net.osmand.plus.plugins.communityalerts.CommunityAlertAnnouncer
import net.osmand.plus.plugins.communityalerts.CommunityAlertApproachEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityAlertAnnouncerTest {

	private val voiceOutput = RecordingVoiceOutput()
	private val announcer = CommunityAlertAnnouncer(FORMATTER, voiceOutput)

	@Test
	fun hazardAtTwoHundredEightySevenMetersUsesRoundedMessage() {
		assertAnnouncement(
			type = CommunityAlert.Type.HAZARD,
			distanceMeters = 287.0,
			expected = "Danger signalé à 300 mètres"
		)
	}

	@Test
	fun policeAtOneHundredEighteenMetersUsesRoundedMessage() {
		assertAnnouncement(
			type = CommunityAlert.Type.POLICE,
			distanceMeters = 118.0,
			expected = "Contrôle signalé à 100 mètres"
		)
	}

	@Test
	fun accidentAtFourHundredEightyMetersUsesRoundedMessage() {
		assertAnnouncement(
			type = CommunityAlert.Type.ACCIDENT,
			distanceMeters = 480.0,
			expected = "Accident signalé à 500 mètres"
		)
	}

	@Test
	fun closureUsesExpectedLabel() {
		assertAnnouncement(
			type = CommunityAlert.Type.CLOSURE,
			distanceMeters = 287.0,
			expected = "Route fermée signalée à 300 mètres"
		)
	}

	@Test
	fun trafficUsesExpectedLabel() {
		assertAnnouncement(
			type = CommunityAlert.Type.TRAFFIC,
			distanceMeters = 287.0,
			expected = "Ralentissement signalé à 300 mètres"
		)
	}

	@Test
	fun mutedRoutingProfileDoesNotAnnounce() {
		voiceOutput.routingProfileMuted = true

		assertNull(announcer.announce(event(CommunityAlert.Type.HAZARD, 287.0)))
		assertTrue(voiceOutput.messages.isEmpty())
	}

	@Test
	fun mutedVoicePlayerDoesNotAnnounce() {
		voiceOutput.playerMuted = true

		assertNull(announcer.announce(event(CommunityAlert.Type.HAZARD, 287.0)))
		assertTrue(voiceOutput.messages.isEmpty())
	}

	@Test
	fun unavailableVoiceProviderDoesNotAnnounce() {
		voiceOutput.available = false

		assertNull(announcer.announce(event(CommunityAlert.Type.HAZARD, 287.0)))
		assertTrue(voiceOutput.messages.isEmpty())
	}

	private fun assertAnnouncement(
		type: CommunityAlert.Type,
		distanceMeters: Double,
		expected: String
	) {
		val message = announcer.announce(event(type, distanceMeters))

		assertEquals(expected, message)
		assertEquals(listOf(expected), voiceOutput.messages)
		assertEquals(expected, announcer.lastAnnouncedMessage)
	}

	private fun event(
		type: CommunityAlert.Type,
		distanceMeters: Double
	): CommunityAlertApproachEvent {
		val alert = CommunityAlert(
			id = "community-alert-${type.name.lowercase()}",
			type = type,
			latitude = 0.0,
			longitude = 0.0,
			timestamp = NOW,
			expiresAt = NOW + 1_000,
			source = "test"
		)
		return CommunityAlertApproachEvent(
			alert = alert,
			distanceAheadMeters = distanceMeters,
			triggerCount = 1,
			triggeredAt = NOW
		)
	}

	private class RecordingVoiceOutput : CommunityAlertAnnouncer.VoiceOutput {
		var routingProfileMuted = false
		var playerMuted = false
		var available = true
		val messages = mutableListOf<String>()

		override val isRoutingProfileMuted: Boolean
			get() = routingProfileMuted

		override val isPlayerMuted: Boolean
			get() = playerMuted

		override val isAvailable: Boolean
			get() = available

		override fun announce(message: String): Boolean {
			messages.add(message)
			return true
		}
	}

	companion object {
		private const val NOW = 1_000_000L
		private val FORMATTER = CommunityAlertAnnouncementFormatter(
			CommunityAlertAnnouncementFormatter.Templates(
				police = "Contrôle signalé à %1\$d mètres",
				accident = "Accident signalé à %1\$d mètres",
				hazard = "Danger signalé à %1\$d mètres",
				closure = "Route fermée signalée à %1\$d mètres",
				traffic = "Ralentissement signalé à %1\$d mètres"
			)
		)
	}
}
