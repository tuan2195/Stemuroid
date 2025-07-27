package com.swordfish.lemuroid.lib.saves

import com.swordfish.lemuroid.common.kotlin.runCatchingWithRetry
import com.swordfish.lemuroid.lib.library.db.entity.Game
import com.swordfish.lemuroid.lib.storage.DirectoriesManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class SavesManager(private val directoriesManager: DirectoriesManager) {
    suspend fun getSaveRAM(game: Game): ByteArray? = withContext(Dispatchers.IO) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            val saveFile = game.getSaveFile()
            if (saveFile.isValidSaveFile()) saveFile.readBytes() else null
        }.getOrNull()
    }

    suspend fun setSaveRAM(game: Game, data: ByteArray) = withContext(Dispatchers.IO) {
        runCatchingWithRetry(FILE_ACCESS_RETRIES) {
            if (data.isEmpty()) return@runCatchingWithRetry
            game.getSaveFile().writeBytes(data)
        }.getOrNull()
    }

    suspend fun getSaveRAMInfo(game: Game): SaveInfo = withContext(Dispatchers.IO) {
        val saveFile = game.getSaveFile()
        SaveInfo(saveFile.isValidSaveFile(), saveFile.lastModified())
    }

    private suspend fun Game.getSaveFile() = withContext(Dispatchers.IO) {
        // This directory & file name should make it compatible with RetroArch so that users can sync saves.
        val savesDir = directoriesManager.getSavesDirectory()
        val systemDir = File(savesDir, systemId)
        val filename = "${fileName.substringBeforeLast(".")}.srm"
        if (!systemDir.exists()) systemDir.mkdir()
        File(systemDir, filename)
    }

    private fun File.isValidSaveFile() = exists() && length() > 0

    companion object {
        private const val FILE_ACCESS_RETRIES = 3
    }
}
