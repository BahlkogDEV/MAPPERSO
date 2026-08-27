package net.osmand.plus.plugins.communityalerts

import java.util.Locale
import kotlin.math.roundToInt

class CommunityAlertAnnouncementFormatter(
	private val templates: Templates
) {
	fun format(event: CommunityAlertApproachEvent): String = format(
		type = event.alert.type,
		distanceMeters = event.distanceAheadMeters
	)

	fun format(type: CommunityAlert.Type, distanceMeters: Double): String = String.format(
		Locale.FRANCE,
		getTemplate(type),
		roundDistanceMeters(distanceMeters)
	)

	fun roundDistanceMeters(distanceMeters: Double): Int =
		(distanceMeters.coerceAtLeast(0.0) / DISTANCE_ROUNDING_METERS).roundToInt() * DISTANCE_ROUNDING_METERS

	private fun getTemplate(type: CommunityAlert.Type): String = when (type) {
		CommunityAlert.Type.POLICE -> templates.police
		CommunityAlert.Type.ACCIDENT -> templates.accident
		CommunityAlert.Type.HAZARD -> templates.hazard
		CommunityAlert.Type.CLOSURE -> templates.closure
		CommunityAlert.Type.TRAFFIC -> templates.traffic
	}

	data class Templates(
		val police: String,
		val accident: String,
		val hazard: String,
		val closure: String,
		val traffic: String
	)

	companion object {
		private const val DISTANCE_ROUNDING_METERS = 100
	}
}
