package net.osmand.plus.plugins.communityalerts

import android.app.Activity
import android.content.Context
import net.osmand.Location
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.activities.MapActivity
import net.osmand.plus.plugins.OsmandPlugin

class CommunityAlertsPlugin(app: OsmandApplication) : OsmandPlugin(app) {

	private val repository = CommunityAlertsRepository()
	private var alertsLayer: CommunityAlertsLayer? = null
	private var demoAlertUsesGps = false

	override fun getId(): String = PLUGIN_ID

	override fun getName(): String = app.getString(R.string.community_alerts_plugin_name)

	override fun getDescription(linksEnabled: Boolean): CharSequence =
		app.getString(R.string.community_alerts_plugin_description)

	override fun getLogoResourceId(): Int = R.drawable.ic_action_alert

	override fun isEnableByDefault(): Boolean = false

	override fun init(app: OsmandApplication, activity: Activity?): Boolean {
		app.locationProvider.lastKnownLocation?.let {
			repository.replaceWithDemoAlertNear(it.latitude, it.longitude)
		}
		return true
	}

	override fun registerLayers(context: Context, mapActivity: MapActivity?) {
		if (!isActive) {
			return
		}
		val mapView = app.osmandMap.mapView
		val layer = alertsLayer ?: CommunityAlertsLayer(context, repository).also {
			alertsLayer = it
		}
		if (!mapView.layers.contains(layer)) {
			mapView.addLayer(layer, ALERTS_LAYER_Z_ORDER)
		}
	}

	override fun updateLayers(context: Context, mapActivity: MapActivity?) {
		val mapView = app.osmandMap.mapView
		if (isActive) {
			registerLayers(context, mapActivity)
		} else {
			alertsLayer?.let(mapView::removeLayer)
			alertsLayer = null
		}
		mapView.refreshMap()
	}

	override fun updateLocation(location: Location?) {
		location?.let(::updateDemoAlertLocation)
	}

	private fun updateDemoAlertLocation(location: Location) {
		if (!demoAlertUsesGps && location.latitude in -90.0..90.0 && location.longitude in -180.0..180.0) {
			demoAlertUsesGps = true
			repository.replaceWithDemoAlertNear(location.latitude, location.longitude)
		}
	}

	companion object {
		const val PLUGIN_ID = "osmand.community.alerts"
		private const val ALERTS_LAYER_Z_ORDER = 3.6f
	}
}
