package net.osmand.plus.plugins.communityalerts

import kotlin.math.cos

data class CommunityAlertBounds(
	val north: Double,
	val south: Double,
	val east: Double,
	val west: Double
) {
	init {
		require(north in -90.0..90.0 && south in -90.0..90.0) {
			"Latitude bounds must be between -90 and 90 degrees"
		}
		require(east in -180.0..180.0 && west in -180.0..180.0) {
			"Longitude bounds must be between -180 and 180 degrees"
		}
		require(north >= south) { "North must not be south of south" }
		require(east >= west) { "Bounds crossing the antimeridian are not supported" }
	}

	val centerLatitude: Double
		get() = (north + south) / 2.0

	val centerLongitude: Double
		get() = (east + west) / 2.0

	fun contains(latitude: Double, longitude: Double, insetRatio: Double = 0.0): Boolean {
		require(insetRatio in 0.0..0.5) { "Inset ratio must be between 0 and 0.5" }
		val latitudeInset = (north - south) * insetRatio
		val longitudeInset = (east - west) * insetRatio
		return latitude in (south + latitudeInset)..(north - latitudeInset) &&
			longitude in (west + longitudeInset)..(east - longitudeInset)
	}

	companion object {
		private const val METERS_PER_LATITUDE_DEGREE = 111_320.0

		fun around(latitude: Double, longitude: Double, radiusMeters: Double): CommunityAlertBounds {
			require(latitude in -90.0..90.0) { "Latitude must be between -90 and 90 degrees" }
			require(longitude in -180.0..180.0) { "Longitude must be between -180 and 180 degrees" }
			require(radiusMeters > 0.0) { "Radius must be positive" }
			val latitudeRadius = radiusMeters / METERS_PER_LATITUDE_DEGREE
			val longitudeScale = cos(Math.toRadians(latitude)).coerceAtLeast(0.01)
			val longitudeRadius = radiusMeters / (METERS_PER_LATITUDE_DEGREE * longitudeScale)
			return CommunityAlertBounds(
				north = (latitude + latitudeRadius).coerceAtMost(90.0),
				south = (latitude - latitudeRadius).coerceAtLeast(-90.0),
				east = (longitude + longitudeRadius).coerceAtMost(180.0),
				west = (longitude - longitudeRadius).coerceAtLeast(-180.0)
			)
		}
	}
}
