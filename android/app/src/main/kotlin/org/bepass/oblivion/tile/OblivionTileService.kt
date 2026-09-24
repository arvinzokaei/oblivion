package org.bepass.oblivion.tile

import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import org.bepass.oblivion.MainActivity
import org.bepass.oblivion.R
import org.bepass.oblivion.vpn.AetherVpnService
import org.bepass.oblivion.vpn.TunnelBus
import org.bepass.oblivion.vpn.TunnelPrefs
import org.bepass.oblivion.vpn.TunnelSnapshot
import org.bepass.oblivion.vpn.TunnelStage

/**
 * The "on/off from the top of the phone" switch. It reuses exactly the
 * same start/stop calls the app itself uses (AetherVpnService.start/stop)
 * and the same settings the user already configured (saved by
 * TunnelPrefs whenever they pressed Connect inside the app at least once).
 */
class OblivionTileService : TileService() {

    private val statusListener: (TunnelSnapshot) -> Unit = { snapshot ->
        updateTile(snapshot.stage)
    }

    override fun onStartListening() {
        super.onStartListening()
        TunnelBus.addStatusListener(statusListener)
        updateTile(TunnelBus.snapshot.stage)
    }

    override fun onStopListening() {
        TunnelBus.removeStatusListener(statusListener)
        super.onStopListening()
    }

    override fun onClick() {
        super.onClick()

        val stage = TunnelBus.snapshot.stage
        if (!stage.isIdle) {
            // Already connected/connecting/validating -> tapping turns it off.
            AetherVpnService.stop(applicationContext)
            updateTile(TunnelStage.DISCONNECTING)
            return
        }

        // First-ever VPN permission grant has to happen inside an activity,
        // the system won't show that dialog from a tile. Same if the user
        // has never pressed Connect once inside the app yet (nothing saved).
        val needsVpnPermission = VpnService.prepare(applicationContext) != null
        val savedConfig = if (needsVpnPermission) null else TunnelPrefs.load(applicationContext)

        if (needsVpnPermission || savedConfig == null) {
            openApp()
            return
        }

        AetherVpnService.start(applicationContext, savedConfig)
        updateTile(TunnelStage.CONNECTING)
    }

    private fun openApp() {
        val intent = Intent(applicationContext, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val pendingIntent = PendingIntent.getActivity(
                applicationContext,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            startActivityAndCollapse(pendingIntent)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }

    private fun updateTile(stage: TunnelStage) {
        val tile = qsTile ?: return

        val isOn = stage == TunnelStage.CONNECTED ||
            stage == TunnelStage.CONNECTING ||
            stage == TunnelStage.VALIDATING

        tile.state = if (isOn) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
        tile.label = getString(R.string.tile_label)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) tile.subtitle = getString(
            when (stage) {
                TunnelStage.CONNECTED -> R.string.tile_state_connected
                TunnelStage.CONNECTING, TunnelStage.VALIDATING -> R.string.tile_state_connecting
                TunnelStage.DISCONNECTING -> R.string.tile_state_disconnecting
                else -> R.string.tile_state_disconnected
            },
        )
        tile.updateTile()
    }
}
