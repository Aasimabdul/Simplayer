package com.simplayer.owncompany.dlna

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.fourthline.cling.UpnpService
import org.fourthline.cling.UpnpServiceImpl
import org.fourthline.cling.android.AndroidUpnpServiceConfiguration
import org.fourthline.cling.model.action.ActionInvocation
import org.fourthline.cling.model.meta.Device
import org.fourthline.cling.model.meta.LocalDevice
import org.fourthline.cling.model.meta.RemoteDevice
import org.fourthline.cling.model.meta.Service
import org.fourthline.cling.model.types.UDAServiceType
import org.fourthline.cling.registry.Registry
import org.fourthline.cling.registry.RegistryListener
import org.fourthline.cling.support.contentdirectory.callback.Browse
import org.fourthline.cling.support.model.BrowseFlag
import org.fourthline.cling.support.model.DIDLContent
import org.fourthline.cling.support.model.container.Container
import org.fourthline.cling.support.model.item.Item

data class DlnaServer(val name: String, val device: Device<*, *, *>)
data class DlnaEntry(
    val title: String,
    val isFolder: Boolean,
    val objectId: String,
    val url: String? = null
)

class DlnaBrowser(private val context: Context) {

    private var upnpService: UpnpService? = null
    private val servers = mutableListOf<DlnaServer>()

    fun start(onUpdate: (List<DlnaServer>) -> Unit) {
        if (upnpService != null) return

        upnpService = UpnpServiceImpl(AndroidUpnpServiceConfiguration())

        val listener = object : RegistryListener {
            override fun remoteDeviceDiscoveryStarted(registry: Registry, device: RemoteDevice) {}
            override fun remoteDeviceDiscoveryFailed(registry: Registry, device: RemoteDevice, ex: Exception) {}

            override fun remoteDeviceAdded(registry: Registry, device: RemoteDevice) {
                val name = device.details.friendlyName ?: "DLNA Device"
                if (device.findService(UDAServiceType("ContentDirectory")) != null) {
                    servers.add(DlnaServer(name, device))
                    onUpdate(servers.toList())
                }
            }

            override fun remoteDeviceUpdated(registry: Registry, device: RemoteDevice) {}

            override fun remoteDeviceRemoved(registry: Registry, device: RemoteDevice) {
                servers.removeAll { it.device.identity.udn == device.identity.udn }
                onUpdate(servers.toList())
            }

            override fun localDeviceAdded(registry: Registry, device: LocalDevice) {}
            override fun localDeviceRemoved(registry: Registry, device: LocalDevice) {}
            override fun beforeShutdown(registry: Registry) {}
            override fun afterShutdown() {}
        }

        upnpService?.registry?.addListener(listener)
        upnpService?.controlPoint?.search()
    }

    fun stop() {
        try { upnpService?.shutdown() } catch (_: Exception) {}
        upnpService = null
        servers.clear()
    }

    suspend fun browse(
        server: DlnaServer,
        objectId: String,
        onResult: (List<DlnaEntry>) -> Unit
    ) = withContext(Dispatchers.IO) {

        val service: Service<*, *> =
            server.device.findService(UDAServiceType("ContentDirectory")) ?: return@withContext

        val cp = upnpService?.controlPoint ?: return@withContext

        cp.execute(object : Browse(service, objectId, BrowseFlag.DIRECT_CHILDREN) {

            override fun received(
                invocation: ActionInvocation<*>,
                didl: DIDLContent
            ) {
                val list = mutableListOf<DlnaEntry>()

                didl.containers?.forEach { c: Container ->
                    list.add(DlnaEntry(c.title ?: "Folder", true, c.id))
                }

                didl.items?.forEach { i: Item ->
                    val url = i.resources?.firstOrNull()?.value
                    list.add(DlnaEntry(i.title ?: "Video", false, i.id, url))
                }

                onResult(list)
            }

            override fun updateStatus(status: Status?) {}

            override fun failure(
                invocation: ActionInvocation<*>,
                operation: org.fourthline.cling.model.message.UpnpResponse?,
                defaultMsg: String?
            ) {
                Log.e("DLNA", "Browse failed: $defaultMsg")
            }
        })
    }
}
