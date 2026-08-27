package net.osmand.plus.plugins.communityalerts

data class CommunityAlert(
	val id: String,
	val type: Type,
	val latitude: Double,
	val longitude: Double,
	val timestamp: Long,
	val expiresAt: Long,
	val source: String? = null
) {
	enum class Type {
		POLICE,
		ACCIDENT,
		HAZARD,
		CLOSURE,
		TRAFFIC
	}
}
