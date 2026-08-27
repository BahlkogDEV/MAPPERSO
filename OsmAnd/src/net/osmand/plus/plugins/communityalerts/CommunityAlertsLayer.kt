package net.osmand.plus.plugins.communityalerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import net.osmand.Location
import net.osmand.core.jni.MapMarker
import net.osmand.core.jni.MapMarkerBuilder
import net.osmand.core.jni.MapMarkersCollection
import net.osmand.core.jni.PointI
import net.osmand.data.RotatedTileBox
import net.osmand.plus.OsmandApplication
import net.osmand.plus.R
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils
import java.util.Locale

class CommunityAlertsLayer(
	context: Context,
	private val app: OsmandApplication,
	private val repository: CommunityAlertsRepository,
	private val matcher: CommunityAlertMatcher,
	private val debugGenerator: CommunityAlertsDebugGenerator,
	private val approachController: CommunityAlertApproachController,
	private val announcer: CommunityAlertAnnouncer
) : OsmandMapLayer(context) {

	@Volatile
	private var matches = emptyList<CommunityAlertMatch>()

	@Volatile
	private var markersChanged = true

	private var renderedAlerts = emptyList<RenderedAlert>()
	private var markerBitmap: Bitmap? = null
	private val demoMarkerBitmaps = mutableMapOf<CommunityAlertMatch.Status, Bitmap>()
	private val alertPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
	private val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.STROKE
		strokeCap = Paint.Cap.ROUND
	}
	private val demoCirclePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.rgb(205, 0, 255)
		style = Paint.Style.FILL
	}
	private val demoOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		style = Paint.Style.STROKE
	}
	private val demoTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		textAlign = Paint.Align.CENTER
		typeface = Typeface.DEFAULT_BOLD
	}
	// TEMPORARY DEBUG/DEMO PANEL: remove with CommunityAlertsDebugGenerator.
	private val debugPanelBackgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.argb(220, 25, 25, 25)
		style = Paint.Style.FILL
	}
	private val debugPanelOutlinePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.rgb(205, 0, 255)
		style = Paint.Style.STROKE
	}
	private val debugPanelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
		color = Color.WHITE
		typeface = Typeface.MONOSPACE
	}
	private val repositoryListener = CommunityAlertsRepository.Listener { updatedAlerts ->
		reevaluate(alerts = updatedAlerts)
	}

	override fun initLayer(view: OsmandMapTileView) {
		super.initLayer(view)
		setPointsOrder(5.7f)
		repository.addListener(repositoryListener)
		reevaluate()
	}

	fun reevaluate(
		location: Location? = null,
		alerts: List<CommunityAlert> = repository.getAlerts()
	) {
		val routingHelper = app.routingHelper
		val route = routingHelper.route
		val routeGeometry = if (routingHelper.isRouteCalculated && !route.isEmpty) {
			route.immutableAllLocations
		} else {
			null
		}
		val currentPosition = routingHelper.lastProjection
			?: location
			?: app.locationProvider.lastKnownLocation
		val updatedMatches = matcher.matchAll(
			alerts = alerts,
			routeGeometry = routeGeometry,
			currentRouteIndex = route.currentRoute,
			currentPosition = currentPosition
		)
		val approachEvents = approachController.evaluate(updatedMatches)
		approachEvents.forEach { event -> announcer.announce(event) }
		val previousRenderedAlerts = matches.map(::toRenderedAlert)
		matches = updatedMatches
		val renderedAlertsChanged = previousRenderedAlerts != updatedMatches.map(::toRenderedAlert)
		if (renderedAlertsChanged) {
			markersChanged = true
		}
		if (renderedAlertsChanged || approachEvents.isNotEmpty()) {
			view?.refreshMap()
		}
	}

	override fun onPrepareBufferImage(
		canvas: Canvas,
		tileBox: RotatedTileBox,
		settings: DrawSettings
	) {
		super.onPrepareBufferImage(canvas, tileBox, settings)
		val activeMatches = matches.filter { it.alert.expiresAt > System.currentTimeMillis() }
		val activeRenderedAlerts = activeMatches.map(::toRenderedAlert)
		if (mapRenderer != null) {
			if (markersChanged || mapRendererChanged || renderedAlerts != activeRenderedAlerts) {
				clearMapMarkersCollections()
				createOpenGlMarkers(activeMatches)
				renderedAlerts = activeRenderedAlerts
				markersChanged = false
				mapRendererChanged = false
			}
		} else {
			drawCanvasMarkers(canvas, tileBox, activeMatches)
		}
	}

	private fun drawCanvasMarkers(
		canvas: Canvas,
		tileBox: RotatedTileBox,
		activeMatches: List<CommunityAlertMatch>
	) {
		activeMatches.forEach { match ->
			val alert = match.alert
			if (tileBox.containsLatLon(alert.latitude, alert.longitude)) {
				val bitmap = getMarkerBitmap(match) ?: return@forEach
				val x = tileBox.getPixXFromLatLon(alert.latitude, alert.longitude)
				val y = tileBox.getPixYFromLatLon(alert.latitude, alert.longitude)
				canvas.drawBitmap(bitmap, x - bitmap.width / 2f, y - bitmap.height / 2f, null)
			}
		}
	}

	private fun createOpenGlMarkers(activeMatches: List<CommunityAlertMatch>) {
		val renderer = mapRenderer ?: return
		if (activeMatches.isEmpty()) {
			return
		}
		val collection = MapMarkersCollection()
		activeMatches.forEach { match ->
			val alert = match.alert
			val bitmap = getMarkerBitmap(match) ?: return@forEach
			MapMarkerBuilder()
				.setPosition(
					PointI(
						MapUtils.get31TileNumberX(alert.longitude),
						MapUtils.get31TileNumberY(alert.latitude)
					)
				)
				.setIsHidden(false)
				.setBaseOrder(pointsOrder)
				.setIsAccuracyCircleSupported(false)
				.setPinIcon(NativeUtilities.createSkImageFromBitmap(bitmap))
				.setPinIconHorisontalAlignment(MapMarker.PinIconHorisontalAlignment.CenterHorizontal)
				.setPinIconVerticalAlignment(MapMarker.PinIconVerticalAlignment.CenterVertical)
				.buildAndAddToCollection(collection)
		}
		mapMarkersCollection = collection
		renderer.addSymbolsProvider(collection)
	}

	override fun updateResources() {
		alertPaint.color = ContextCompat.getColor(context, R.color.osmand_orange)
		val scale = density.coerceAtLeast(1f)
		symbolPaint.strokeWidth = 2.5f * scale
		markerBitmap = createMarkerBitmap(scale)
		demoOutlinePaint.strokeWidth = 3f * scale
		demoTextPaint.textSize = 13f * scale
		debugPanelOutlinePaint.strokeWidth = 2f * scale
		debugPanelTextPaint.textSize = 11f * scale
		demoMarkerBitmaps.clear()
		CommunityAlertMatch.Status.entries.forEach { status ->
			demoMarkerBitmaps[status] = createDemoMarkerBitmap(scale, getDemoMarkerText(status))
		}
		markersChanged = true
	}

	private fun getMarkerBitmap(match: CommunityAlertMatch): Bitmap? =
		if (match.alert.id.startsWith(CommunityAlertsRepository.DEMO_ALERT_ID)) {
			demoMarkerBitmaps[match.status]
		} else {
			markerBitmap
		}

	private fun createMarkerBitmap(scale: Float): Bitmap {
		val size = (28f * scale).toInt().coerceAtLeast(28)
		return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
			val canvas = Canvas(bitmap)
			val center = size / 2f
			val radius = size * 0.44f
			canvas.drawCircle(center, center, radius, alertPaint)
			canvas.drawLine(center, center - radius * 0.48f, center, center + radius * 0.12f, symbolPaint)
			canvas.drawPoint(center, center + radius * 0.5f, symbolPaint)
		}
	}

	private fun createDemoMarkerBitmap(scale: Float, text: String): Bitmap {
		val horizontalPadding = 12f * scale
		val height = (36f * scale).toInt().coerceAtLeast(36)
		val width = (demoTextPaint.measureText(text) + horizontalPadding * 2)
			.toInt()
			.coerceAtLeast(height)
		return Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { bitmap ->
			val canvas = Canvas(bitmap)
			val inset = demoOutlinePaint.strokeWidth / 2f
			val radius = height / 2f
			canvas.drawRoundRect(0f, 0f, width.toFloat(), height.toFloat(), radius, radius, demoCirclePaint)
			canvas.drawRoundRect(inset, inset, width - inset, height - inset, radius, radius, demoOutlinePaint)
			val centerX = width / 2f
			val centerY = height / 2f
			val textY = centerY - (demoTextPaint.ascent() + demoTextPaint.descent()) / 2f
			canvas.drawText(text, centerX, textY, demoTextPaint)
		}
	}

	private fun getDemoMarkerText(status: CommunityAlertMatch.Status): String = when (status) {
		CommunityAlertMatch.Status.NO_ROUTE -> context.getString(R.string.community_alert_demo_test)
		CommunityAlertMatch.Status.ON_ROUTE -> context.getString(R.string.community_alert_demo_on_route)
		CommunityAlertMatch.Status.OFF_ROUTE -> context.getString(R.string.community_alert_demo_off_route)
		CommunityAlertMatch.Status.BEHIND -> context.getString(R.string.community_alert_demo_behind)
	}

	private fun toRenderedAlert(match: CommunityAlertMatch) = RenderedAlert(match.alert, match.status)

	override fun destroyLayer() {
		repository.removeListener(repositoryListener)
		super.destroyLayer()
	}

	override fun cleanupResources() {
		super.cleanupResources()
		renderedAlerts = emptyList()
		markersChanged = true
	}

	override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) {
		drawDebugDiagnosticPanel(canvas)
		drawDebugTriggerPanel(canvas)
		drawDebugVoicePanel(canvas)
	}

	private fun drawDebugDiagnosticPanel(canvas: Canvas) {
		val routingHelper = app.routingHelper
		if (!routingHelper.isRouteCalculated || routingHelper.route.isEmpty) {
			return
		}
		val diagnostic = debugGenerator.lastBehindDiagnostic ?: return
		val lines = mutableListOf(
			context.getString(
				R.string.community_alert_debug_route_progress,
				formatDebugMeters(diagnostic.routeProgressMeters)
			),
			context.getString(
				R.string.community_alert_debug_behind_available,
				formatDebugMeters(diagnostic.behindAvailableMeters)
			),
			context.getString(
				if (diagnostic.generated) {
					R.string.community_alert_debug_behind_generated
				} else {
					R.string.community_alert_debug_behind_omitted
				}
			)
		)
		if (!diagnostic.generated) {
			diagnostic.omissionReason?.let { reason ->
				lines.add(
					context.getString(
						R.string.community_alert_debug_behind_reason,
						reason
					)
				)
			}
		}

		drawDebugPanel(canvas, lines, DEBUG_PANEL_TOP_DP)
	}

	private fun drawDebugTriggerPanel(canvas: Canvas) {
		// TEMPORARY DEBUG: visual confirmation only; triggering remains UI-independent.
		val routingHelper = app.routingHelper
		if (!routingHelper.isRouteCalculated || routingHelper.route.isEmpty) {
			return
		}
		val event = approachController.lastEvent ?: return
		val lines = listOf(
			context.getString(R.string.community_alert_debug_triggered),
			context.getString(R.string.community_alert_debug_trigger_id, event.alert.id),
			context.getString(
				R.string.community_alert_debug_trigger_distance,
				String.format(Locale.US, "%.0f", event.distanceAheadMeters)
			),
			context.getString(R.string.community_alert_debug_trigger_count, event.triggerCount)
		)
		drawDebugPanel(canvas, lines, DEBUG_TRIGGER_PANEL_TOP_DP)
	}

	private fun drawDebugVoicePanel(canvas: Canvas) {
		// TEMPORARY DEBUG: confirms that the approach event reached the voice infrastructure.
		val routingHelper = app.routingHelper
		if (!routingHelper.isRouteCalculated || routingHelper.route.isEmpty) {
			return
		}
		val message = announcer.lastAnnouncedMessage ?: return
		val lines = listOf(
			context.getString(R.string.community_alert_debug_voice_announced),
			message
		)
		drawDebugPanel(canvas, lines, DEBUG_VOICE_PANEL_TOP_DP)
	}

	private fun drawDebugPanel(canvas: Canvas, lines: List<String>, topDp: Float) {
		val scale = density.coerceAtLeast(1f)
		val padding = DEBUG_PANEL_PADDING_DP * scale
		val lineHeight = debugPanelTextPaint.fontSpacing
		val panelWidth = lines.maxOf { debugPanelTextPaint.measureText(it) } + padding * 2
		val panelHeight = lineHeight * lines.size + padding * 2
		val left = DEBUG_PANEL_MARGIN_DP * scale
		val top = topDp * scale
		val right = (left + panelWidth).coerceAtMost(canvas.width - DEBUG_PANEL_MARGIN_DP * scale)
		val bottom = top + panelHeight
		val cornerRadius = DEBUG_PANEL_CORNER_RADIUS_DP * scale
		canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, debugPanelBackgroundPaint)
		canvas.drawRoundRect(left, top, right, bottom, cornerRadius, cornerRadius, debugPanelOutlinePaint)

		var baseline = top + padding - debugPanelTextPaint.ascent()
		lines.forEach { line ->
			canvas.drawText(line, left + padding, baseline, debugPanelTextPaint)
			baseline += lineHeight
		}
	}

	private fun formatDebugMeters(value: Double?): String =
		value?.let { String.format(Locale.US, "%.1f", it) } ?: "?"

	override fun drawInScreenPixels(): Boolean = true

	private data class RenderedAlert(
		val alert: CommunityAlert,
		val status: CommunityAlertMatch.Status
	)

	companion object {
		private const val DEBUG_PANEL_MARGIN_DP = 12f
		private const val DEBUG_PANEL_TOP_DP = 72f
		private const val DEBUG_TRIGGER_PANEL_TOP_DP = 156f
		private const val DEBUG_VOICE_PANEL_TOP_DP = 240f
		private const val DEBUG_PANEL_PADDING_DP = 8f
		private const val DEBUG_PANEL_CORNER_RADIUS_DP = 6f
	}
}
