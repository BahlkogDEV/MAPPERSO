package net.osmand.plus.plugins.communityalerts

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.ContextCompat
import net.osmand.core.jni.MapMarker
import net.osmand.core.jni.MapMarkerBuilder
import net.osmand.core.jni.MapMarkersCollection
import net.osmand.core.jni.PointI
import net.osmand.data.RotatedTileBox
import net.osmand.plus.R
import net.osmand.plus.utils.NativeUtilities
import net.osmand.plus.views.OsmandMapTileView
import net.osmand.plus.views.layers.base.OsmandMapLayer
import net.osmand.util.MapUtils

class CommunityAlertsLayer(
	context: Context,
	private val repository: CommunityAlertsRepository
) : OsmandMapLayer(context) {

	@Volatile
	private var alerts = repository.getActiveAlerts()

	@Volatile
	private var alertsChanged = true

	private var renderedAlerts = emptyList<CommunityAlert>()
	private var markerBitmap: Bitmap? = null
	private var demoMarkerBitmap: Bitmap? = null
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
	private val repositoryListener = CommunityAlertsRepository.Listener { updatedAlerts ->
		alerts = updatedAlerts
		alertsChanged = true
		view?.refreshMap()
	}

	override fun initLayer(view: OsmandMapTileView) {
		super.initLayer(view)
		setPointsOrder(5.7f)
		repository.addListener(repositoryListener)
		alerts = repository.getActiveAlerts()
		alertsChanged = true
	}

	override fun onPrepareBufferImage(
		canvas: Canvas,
		tileBox: RotatedTileBox,
		settings: DrawSettings
	) {
		super.onPrepareBufferImage(canvas, tileBox, settings)
		val activeAlerts = alerts.filter { it.expiresAt > System.currentTimeMillis() }
		if (mapRenderer != null) {
			if (alertsChanged || mapRendererChanged || renderedAlerts != activeAlerts) {
				clearMapMarkersCollections()
				createOpenGlMarkers(activeAlerts)
				renderedAlerts = activeAlerts
				alertsChanged = false
				mapRendererChanged = false
			}
		} else {
			drawCanvasMarkers(canvas, tileBox, activeAlerts)
		}
	}

	private fun drawCanvasMarkers(
		canvas: Canvas,
		tileBox: RotatedTileBox,
		activeAlerts: List<CommunityAlert>
	) {
		activeAlerts.forEach { alert ->
			if (tileBox.containsLatLon(alert.latitude, alert.longitude)) {
				val bitmap = getMarkerBitmap(alert) ?: return@forEach
				val x = tileBox.getPixXFromLatLon(alert.latitude, alert.longitude)
				val y = tileBox.getPixYFromLatLon(alert.latitude, alert.longitude)
				canvas.drawBitmap(bitmap, x - bitmap.width / 2f, y - bitmap.height / 2f, null)
			}
		}
	}

	private fun createOpenGlMarkers(activeAlerts: List<CommunityAlert>) {
		val renderer = mapRenderer ?: return
		if (activeAlerts.isEmpty()) {
			return
		}
		val collection = MapMarkersCollection()
		activeAlerts.forEach { alert ->
			val bitmap = getMarkerBitmap(alert) ?: return@forEach
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
		demoMarkerBitmap = createDemoMarkerBitmap(scale)
		alertsChanged = true
	}

	private fun getMarkerBitmap(alert: CommunityAlert): Bitmap? =
		if (alert.id == CommunityAlertsRepository.DEMO_ALERT_ID) demoMarkerBitmap else markerBitmap

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

	private fun createDemoMarkerBitmap(scale: Float): Bitmap {
		val size = (56f * scale).toInt().coerceAtLeast(56)
		return Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888).also { bitmap ->
			val canvas = Canvas(bitmap)
			val center = size / 2f
			val radius = size * 0.46f
			canvas.drawCircle(center, center, radius, demoCirclePaint)
			canvas.drawCircle(center, center, radius - demoOutlinePaint.strokeWidth / 2f, demoOutlinePaint)
			val textY = center - (demoTextPaint.ascent() + demoTextPaint.descent()) / 2f
			canvas.drawText("TEST", center, textY, demoTextPaint)
		}
	}

	override fun destroyLayer() {
		repository.removeListener(repositoryListener)
		super.destroyLayer()
	}

	override fun cleanupResources() {
		super.cleanupResources()
		renderedAlerts = emptyList()
		alertsChanged = true
	}

	override fun onDraw(canvas: Canvas, tileBox: RotatedTileBox, settings: DrawSettings) = Unit

	override fun drawInScreenPixels(): Boolean = true
}
