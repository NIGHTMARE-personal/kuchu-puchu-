package com.example.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class LocalDataException(message: String) : Exception(message)

class UserProfileRepository(
    private val userProfileDao: UserProfileDao,
    private val context: Context
) {
    companion object {
        const val LOCAL_USER_ID = "user_default"
    }

    init {
        migrateOldCacheIfNeeded()
    }

    private fun migrateOldCacheIfNeeded() {
        try {
            val oldCacheDir = File(context.cacheDir, "avatar_cache")
            val targetDir = File(context.filesDir, "avatar_cache")
            if (oldCacheDir.exists() && oldCacheDir.isDirectory) {
                if (!targetDir.exists()) targetDir.mkdirs()
                oldCacheDir.listFiles()?.forEach { oldFile ->
                    val newFile = File(targetDir, oldFile.name)
                    if (!newFile.exists()) {
                        oldFile.copyTo(newFile, overwrite = true)
                    }
                    oldFile.delete()
                }
                oldCacheDir.delete()
            }
        } catch (e: Exception) {
            Log.w("UserProfileRepository", "Avatar cache migration skipped or failed: ${e.message}")
        }
    }

    fun getMyProfile(): Flow<UserProfileEntity?> {
        return userProfileDao.getUserProfile(LOCAL_USER_ID)
    }

    suspend fun getMyProfileSync(): UserProfileEntity {
        var profile = userProfileDao.getUserProfileSync(LOCAL_USER_ID)
        if (profile == null) {
            profile = UserProfileEntity(
                userId = LOCAL_USER_ID,
                displayName = "Alex Rivera",
                email = "alex.rivera@example.com",
                bio = "AI Assistant Power User"
            )
            userProfileDao.insertOrUpdate(profile)
        }
        return profile
    }

    suspend fun updateProfile(profile: UserProfileEntity) = withContext(Dispatchers.IO) {
        if (profile.userId != LOCAL_USER_ID) {
            throw LocalDataException("User ID mismatch for local profile update.")
        }
        userProfileDao.insertOrUpdate(profile.copy(updatedAt = System.currentTimeMillis()))
    }

    /**
     * Upload avatar image:
     * - Stores in persistent app files directory (filesDir/avatar_cache)
     * - Saves local URI to user profile in Room database
     */
    suspend fun uploadAvatarImage(imageUri: Uri): UserProfileEntity = withContext(Dispatchers.IO) {
        val avatarDir = File(context.filesDir, "avatar_cache").apply { if (!exists()) mkdirs() }
        val avatarFile = File(avatarDir, "avatar_${LOCAL_USER_ID}.jpg")

        // Read image, compress, and store in local storage
        context.contentResolver.openInputStream(imageUri)?.use { input ->
            val bitmap = BitmapFactory.decodeStream(input)
            if (bitmap != null) {
                FileOutputStream(avatarFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
                }
            }
        }

        val localFilePath = avatarFile.absolutePath
        val localAvatarUrl = "file://$localFilePath"

        val current = getMyProfileSync()
        val updated = current.copy(
            avatarUrl = localAvatarUrl,
            localCachePath = localFilePath,
            updatedAt = System.currentTimeMillis()
        )

        userProfileDao.insertOrUpdate(updated)
        updated
    }
}
