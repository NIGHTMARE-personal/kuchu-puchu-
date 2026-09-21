package com.example.data.local

import kotlinx.coroutines.flow.Flow

class CommandHistoryRepository(private val dao: CommandHistoryDao) {
    val historyList: Flow<List<CommandHistoryEntity>> = dao.getAllHistory()

    suspend fun recordCommand(
        transcript: String,
        action: String,
        target: String,
        datetime: String?,
        source: String,
        outcome: String,
        details: String? = null,
        attachmentBadge: String? = null,
        engineName: String? = null,
        routeType: String? = null,
        latencyMs: Long? = null,
        confidence: Float? = null,
        rawIntentJson: String? = null,
        attachmentUri: String? = null
    ): Long {
        val entity = CommandHistoryEntity(
            transcript = transcript,
            action = action,
            target = target,
            datetime = datetime,
            source = source,
            outcome = outcome,
            details = details,
            attachmentBadge = attachmentBadge,
            engineName = engineName,
            routeType = routeType,
            latencyMs = latencyMs,
            confidence = confidence,
            rawIntentJson = rawIntentJson,
            attachmentUri = attachmentUri
        )
        return dao.insert(entity)
    }

    suspend fun updateOutcome(id: Int, newOutcome: String, newDetails: String? = null) {
        // Simple update
    }

    suspend fun clearHistory() {
        dao.clearAll()
    }

    suspend fun deleteEntry(id: Int) {
        dao.deleteById(id)
    }
}
