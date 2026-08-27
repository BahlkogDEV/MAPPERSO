package net.osmand.plus.plugins.communityalerts

import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.voice.JsCommandBuilder
import net.osmand.plus.voice.JsTtsCommandPlayer

class CommunityAlertAnnouncer(
	private val formatter: CommunityAlertAnnouncementFormatter,
	private val voiceOutput: VoiceOutput
) {
	@Volatile
	var lastAnnouncedMessage: String? = null
		private set

	fun announce(event: CommunityAlertApproachEvent): String? {
		if (voiceOutput.isRoutingProfileMuted || voiceOutput.isPlayerMuted || !voiceOutput.isAvailable) {
			return null
		}
		val message = formatter.format(event)
		if (!voiceOutput.announce(message)) {
			return null
		}
		lastAnnouncedMessage = message
		return message
	}

	interface VoiceOutput {
		val isRoutingProfileMuted: Boolean
		val isPlayerMuted: Boolean
		val isAvailable: Boolean

		fun announce(message: String): Boolean
	}

	companion object {
		fun create(app: OsmandApplication): CommunityAlertAnnouncer {
			val formatter = CommunityAlertAnnouncementFormatter(
				CommunityAlertAnnouncementFormatter.Templates(
					police = app.getString(R.string.community_alert_voice_police),
					accident = app.getString(R.string.community_alert_voice_accident),
					hazard = app.getString(R.string.community_alert_voice_hazard),
					closure = app.getString(R.string.community_alert_voice_closure),
					traffic = app.getString(R.string.community_alert_voice_traffic)
				)
			)
			return CommunityAlertAnnouncer(formatter, OsmAndVoiceOutput(app))
		}
	}

	private class OsmAndVoiceOutput(
		private val app: OsmandApplication
	) : VoiceOutput {
		override val isRoutingProfileMuted: Boolean
			get() = app.routingHelper.run { voiceRouter.isMuteForMode(appMode) }

		override val isPlayerMuted: Boolean
			get() = app.routingHelper.voiceRouter.isMute()

		override val isAvailable: Boolean
			get() {
				val routingHelper = app.routingHelper
				return !app.settings.isVoiceProviderNotSelected(routingHelper.appMode) &&
					routingHelper.voiceRouter.getPlayer() is JsTtsCommandPlayer
			}

		override fun announce(message: String): Boolean {
			if (isRoutingProfileMuted || isPlayerMuted || !isAvailable) {
				return false
			}
			val player = app.routingHelper.voiceRouter.getPlayer() as? JsTtsCommandPlayer ?: return false
			val commandBuilder = player.newCommandBuilder() as? JsCommandBuilder ?: return false
			commandBuilder.ttsText(message).play()
			return true
		}
	}
}
