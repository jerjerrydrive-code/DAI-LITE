package com.dai.flipper.data.repository

import com.dai.flipper.data.db.*
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Device Repository
 *
 * Provides access to device tracking data with intelligent
 * device classification and fingerprinting.
 */
@Singleton
class DeviceRepository @Inject constructor(
    private val database: DeviceDatabase
) {
    private val deviceDao = database.deviceDao()
    private val sightingDao = database.sightingDao()
    private val noteDao = database.noteDao()

    // ==================== Device Operations ====================

    fun getAllDevices(): Flow<List<TrackedDevice>> = deviceDao.getAllDevices()

    fun getDevicesByType(type: DeviceType): Flow<List<TrackedDevice>> =
        deviceDao.getDevicesByType(type)

    fun getDevicesByCategory(category: DeviceCategory): Flow<List<TrackedDevice>> =
        deviceDao.getDevicesByCategory(category)

    fun getFavoriteDevices(): Flow<List<TrackedDevice>> = deviceDao.getFavoriteDevices()

    fun getVisibleDevices(): Flow<List<TrackedDevice>> = deviceDao.getVisibleDevices()

    fun searchDevices(query: String): Flow<List<TrackedDevice>> = deviceDao.searchDevices(query)

    fun getDeviceCount(): Flow<Int> = deviceDao.getDeviceCount()

    fun getRecentDeviceCount(since: Long): Flow<Int> = deviceDao.getRecentDeviceCount(since)

    suspend fun getDeviceById(id: String): TrackedDevice? = deviceDao.getDeviceById(id)

    suspend fun getDeviceByIdentifier(identifier: String): TrackedDevice? =
        deviceDao.getDeviceByIdentifier(identifier)

    /**
     * Record a device sighting - creates new device if not seen before,
     * or updates existing device's last seen time and sighting count.
     */
    suspend fun recordDevice(
        identifier: String,
        name: String?,
        type: DeviceType,
        rssi: Int? = null,
        frequency: Long? = null,
        metadata: String? = null,
        latitude: Double? = null,
        longitude: Double? = null
    ): TrackedDevice {
        val existing = deviceDao.getDeviceByIdentifier(identifier)

        val device = if (existing != null) {
            // Update existing device
            deviceDao.updateSighting(existing.id)

            // Record sighting
            sightingDao.insertSighting(
                DeviceSighting(
                    deviceId = existing.id,
                    rssi = rssi,
                    frequency = frequency,
                    latitude = latitude,
                    longitude = longitude,
                    rawData = metadata
                )
            )

            existing.copy(
                lastSeen = System.currentTimeMillis(),
                sightingCount = existing.sightingCount + 1
            )
        } else {
            // Create new device with auto-classification
            val classified = classifyDevice(identifier, name, type, metadata)

            deviceDao.insertDevice(classified)

            // Record initial sighting
            sightingDao.insertSighting(
                DeviceSighting(
                    deviceId = classified.id,
                    rssi = rssi,
                    frequency = frequency,
                    latitude = latitude,
                    longitude = longitude,
                    rawData = metadata
                )
            )

            classified
        }

        return device
    }

    suspend fun updateDevice(device: TrackedDevice) = deviceDao.updateDevice(device)

    suspend fun deleteDevice(device: TrackedDevice) = deviceDao.deleteDevice(device)

    suspend fun setDeviceAlias(id: String, alias: String?) = deviceDao.updateAlias(id, alias)

    suspend fun setDeviceFavorite(id: String, favorite: Boolean) =
        deviceDao.updateFavorite(id, favorite)

    suspend fun setDeviceHidden(id: String, hidden: Boolean) =
        deviceDao.updateHidden(id, hidden)

    suspend fun setThreatLevel(id: String, threat: ThreatLevel) =
        deviceDao.updateThreatLevel(id, threat)

    // ==================== Sighting Operations ====================

    fun getSightingsForDevice(deviceId: String): Flow<List<DeviceSighting>> =
        sightingDao.getSightingsForDevice(deviceId)

    fun getRecentSightings(deviceId: String, limit: Int = 50): Flow<List<DeviceSighting>> =
        sightingDao.getRecentSightings(deviceId, limit)

    suspend fun cleanupOldSightings(daysToKeep: Int = 30) {
        val cutoff = System.currentTimeMillis() - (daysToKeep * 24 * 60 * 60 * 1000L)
        sightingDao.deleteOldSightings(cutoff)
    }

    // ==================== Note Operations ====================

    fun getNotesForDevice(deviceId: String): Flow<List<DeviceNote>> =
        noteDao.getNotesForDevice(deviceId)

    suspend fun addNote(deviceId: String, content: String, isAiGenerated: Boolean = false) {
        noteDao.insertNote(
            DeviceNote(
                deviceId = deviceId,
                content = content,
                isAiGenerated = isAiGenerated
            )
        )
    }

    suspend fun deleteNote(note: DeviceNote) = noteDao.deleteNote(note)

    // ==================== Device Classification ====================

    /**
     * Auto-classify a device based on its identifier, name, and type.
     * Uses manufacturer prefixes and known patterns.
     */
    private fun classifyDevice(
        identifier: String,
        name: String?,
        type: DeviceType,
        metadata: String?
    ): TrackedDevice {
        val (manufacturer, category, subType) = when (type) {
            DeviceType.BLE -> classifyBleDevice(identifier, name)
            DeviceType.WIFI -> classifyWifiDevice(identifier, name)
            DeviceType.SUBGHZ -> classifySubGhzDevice(identifier, metadata)
            DeviceType.NFC -> classifyNfcDevice(identifier, metadata)
            DeviceType.RFID -> Triple(null, DeviceCategory.ACCESS_CONTROL, null)
            DeviceType.INFRARED -> Triple(null, DeviceCategory.REMOTE, null)
            DeviceType.IBUTTON -> Triple(null, DeviceCategory.ACCESS_CONTROL, null)
        }

        return TrackedDevice(
            identifier = identifier,
            name = name,
            type = type,
            manufacturer = manufacturer,
            category = category,
            subType = subType,
            metadata = metadata
        )
    }

    private fun classifyBleDevice(
        mac: String,
        name: String?
    ): Triple<String?, DeviceCategory, String?> {
        // Extract OUI (first 3 bytes of MAC)
        val oui = mac.replace(":", "").take(6).uppercase()

        // Known manufacturer OUIs
        val manufacturer = BLE_MANUFACTURERS[oui]

        // Classify by name patterns
        val nameLower = name?.lowercase() ?: ""
        val (category, subType) = when {
            nameLower.contains("iphone") || nameLower.contains("ipad") ->
                DeviceCategory.PHONE to "Apple iPhone"
            nameLower.contains("galaxy") || nameLower.contains("samsung") ->
                DeviceCategory.PHONE to "Samsung Galaxy"
            nameLower.contains("pixel") ->
                DeviceCategory.PHONE to "Google Pixel"
            nameLower.contains("watch") || nameLower.contains("band") ->
                DeviceCategory.WEARABLE to "Smartwatch"
            nameLower.contains("airpod") || nameLower.contains("buds") ->
                DeviceCategory.WEARABLE to "Earbuds"
            nameLower.contains("airtag") || nameLower.contains("tile") || nameLower.contains("smarttag") ->
                DeviceCategory.TRACKER to "Tracker"
            nameLower.contains("beacon") ->
                DeviceCategory.BEACON to "BLE Beacon"
            nameLower.contains("tesla") ->
                DeviceCategory.VEHICLE to "Tesla"
            nameLower.contains("lock") ->
                DeviceCategory.ACCESS_CONTROL to "Smart Lock"
            nameLower.contains("light") || nameLower.contains("bulb") ->
                DeviceCategory.SMART_HOME to "Smart Light"
            nameLower.contains("speaker") || nameLower.contains("echo") || nameLower.contains("homepod") ->
                DeviceCategory.SMART_HOME to "Smart Speaker"
            manufacturer == "Apple" ->
                DeviceCategory.PHONE to "Apple Device"
            else -> DeviceCategory.UNKNOWN to null
        }

        return Triple(manufacturer, category, subType)
    }

    private fun classifyWifiDevice(
        mac: String,
        name: String?
    ): Triple<String?, DeviceCategory, String?> {
        val oui = mac.replace(":", "").take(6).uppercase()
        val manufacturer = WIFI_MANUFACTURERS[oui]

        // Classify by SSID patterns
        val nameLower = name?.lowercase() ?: ""
        val (category, subType) = when {
            nameLower.contains("iphone") || nameLower.contains("android") ->
                DeviceCategory.PHONE to "Hotspot"
            nameLower.contains("printer") || nameLower.contains("hp-") || nameLower.contains("epson") ->
                DeviceCategory.COMPUTER to "Printer"
            nameLower.contains("camera") || nameLower.contains("nest") || nameLower.contains("ring") ->
                DeviceCategory.SMART_HOME to "Camera"
            nameLower.contains("router") || nameLower.contains("gateway") ->
                DeviceCategory.SMART_HOME to "Router"
            else -> DeviceCategory.UNKNOWN to null
        }

        return Triple(manufacturer, category, subType)
    }

    private fun classifySubGhzDevice(
        identifier: String,
        metadata: String?
    ): Triple<String?, DeviceCategory, String?> {
        // Parse metadata for protocol information
        val metaLower = metadata?.lowercase() ?: ""

        val (category, subType) = when {
            metaLower.contains("tesla") ->
                DeviceCategory.VEHICLE to "Tesla Key"
            metaLower.contains("garage") || metaLower.contains("liftmaster") || metaLower.contains("chamberlain") ->
                DeviceCategory.ACCESS_CONTROL to "Garage Door"
            metaLower.contains("car") || metaLower.contains("keyfob") || metaLower.contains("remote start") ->
                DeviceCategory.KEY_FOB to "Car Key Fob"
            metaLower.contains("gate") ->
                DeviceCategory.ACCESS_CONTROL to "Gate Remote"
            metaLower.contains("weather") || metaLower.contains("temp") || metaLower.contains("humidity") ->
                DeviceCategory.SENSOR to "Weather Sensor"
            metaLower.contains("tpms") || metaLower.contains("tire") ->
                DeviceCategory.SENSOR to "TPMS Sensor"
            metaLower.contains("doorbell") ->
                DeviceCategory.SMART_HOME to "Doorbell"
            else -> DeviceCategory.REMOTE to "RF Remote"
        }

        return Triple(null, category, subType)
    }

    private fun classifyNfcDevice(
        uid: String,
        metadata: String?
    ): Triple<String?, DeviceCategory, String?> {
        val metaLower = metadata?.lowercase() ?: ""

        val (category, subType) = when {
            metaLower.contains("mifare") && metaLower.contains("classic") ->
                DeviceCategory.ACCESS_CONTROL to "Mifare Classic"
            metaLower.contains("mifare") && metaLower.contains("desfire") ->
                DeviceCategory.ACCESS_CONTROL to "Mifare DESFire"
            metaLower.contains("mifare") && metaLower.contains("ultralight") ->
                DeviceCategory.CARD to "Mifare Ultralight"
            metaLower.contains("ntag") ->
                DeviceCategory.CARD to "NTAG"
            metaLower.contains("iclass") || metaLower.contains("hid") ->
                DeviceCategory.ACCESS_CONTROL to "HID iClass"
            metaLower.contains("felica") ->
                DeviceCategory.CARD to "FeliCa"
            metaLower.contains("emv") || metaLower.contains("visa") || metaLower.contains("mastercard") ->
                DeviceCategory.CARD to "Payment Card"
            else -> DeviceCategory.CARD to "NFC Tag"
        }

        return Triple(null, category, subType)
    }

    companion object {
        // Common BLE manufacturer OUIs
        private val BLE_MANUFACTURERS = mapOf(
            "00A040" to "Apple",
            "DCFBF8" to "Apple",
            "AC3C0B" to "Apple",
            "F0D1A9" to "Apple",
            "34AB37" to "Apple",
            "002142" to "Apple",
            "7CD1C3" to "Apple",
            "A4C361" to "Apple",
            "DC2B2A" to "Apple",
            "40F4FD" to "Samsung",
            "78BD57" to "Samsung",
            "5C3E1B" to "Samsung",
            "A08CF8" to "Samsung",
            "30CDA7" to "Google",
            "54271E" to "Google",
            "F8A9D0" to "Google",
            "A4DA22" to "Google",
            "001A7D" to "Sony",
            "7CBFB1" to "Sony",
            "B8F6B1" to "Microsoft",
            "00155D" to "Microsoft",
            "7C1E52" to "Huawei",
            "5C6377" to "Huawei"
        )

        private val WIFI_MANUFACTURERS = mapOf(
            "00A040" to "Apple",
            "40F4FD" to "Samsung",
            "00155D" to "Microsoft",
            "000C29" to "VMware",
            "005056" to "VMware",
            "001B63" to "Apple",
            "4C3488" to "Google"
        )
    }
}
