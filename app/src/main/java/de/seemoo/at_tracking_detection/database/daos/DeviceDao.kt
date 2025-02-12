package de.seemoo.at_tracking_detection.database.daos

import androidx.room.*
import de.seemoo.at_tracking_detection.database.relations.DeviceBeaconNotification
import de.seemoo.at_tracking_detection.database.tables.Device
import kotlinx.coroutines.flow.Flow
import java.time.LocalDateTime

@Dao
interface DeviceDao {
    @Query("SELECT * FROM device ORDER BY lastSeen DESC")
    fun getAll(): Flow<List<Device>>

    @Query("SELECT * FROM device WHERE `ignore` == 1 ORDER BY lastSeen DESC")
    fun getIgnored(): Flow<List<Device>>

    @Query("SELECT * FROM device WHERE `ignore` == 1 ORDER BY lastSeen DESC")
    fun getIgnoredSync(): List<Device>

    @Query("SELECT * FROM device WHERE address LIKE :address LIMIT 1")
    fun getByAddress(address: String): Device?

    @Query("DELETE FROM device WHERE address LIKE :address")
    suspend fun remove(address: String)

    @Query("UPDATE device SET `ignore` = 1 WHERE address = :address")
    suspend fun ignore(address: String)

    @Query("UPDATE device SET `ignore` = 0 WHERE address = :address")
    suspend fun unIgnore(address: String)

    @Query("SELECT COUNT(*) FROM device")
    fun getTotalCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE firstDiscovery >= :since")
    fun getTotalCountChange(since: LocalDateTime): Flow<Int>

    @Query("SELECT COUNT(*) FROM device WHERE lastSeen >= :since")
    fun getCurrentlyMonitored(since: LocalDateTime): Flow<Int>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM device WHERE firstDiscovery >= :dateTime")
    suspend fun getDeviceBeaconsSince(dateTime: LocalDateTime): List<DeviceBeaconNotification>

    @Transaction
    @RewriteQueriesToDropUnusedColumns
    @Query("SELECT * FROM device")
    suspend fun getDeviceBeacons(): List<DeviceBeaconNotification>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insertAll(vararg devices: Device)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(device: Device): Long

    @Update
    suspend fun update(device: Device)

    @Delete
    suspend fun delete(device: Device)
}