package com.articlepilot.core.database

import com.articlepilot.core.model.Article
import com.articlepilot.core.model.ArticleId
import com.articlepilot.core.model.DraftRevision
import com.articlepilot.core.model.PublishingSession
import kotlinx.coroutines.flow.Flow

interface ArticleDraftStore {
    suspend fun saveDraft(article: Article): ArticleId
    suspend fun loadDraft(id: ArticleId): Article?
    fun observeDrafts(): Flow<List<Article>>
    suspend fun deleteDraft(id: ArticleId)
}

interface DraftRevisionStore {
    suspend fun appendRevision(revision: DraftRevision)
    suspend fun revisionsFor(articleId: ArticleId): List<DraftRevision>
}

interface PublishingSessionStore {
    suspend fun save(session: PublishingSession)
    suspend fun load(id: String): PublishingSession?
    fun observeActive(): Flow<PublishingSession?>
}

/** Room entities/DAOs are intentionally not generated until the persistence schema is approved. */
interface AutomationLogStore {
    suspend fun append(entry: AutomationLogEntry)
    fun observe(sessionId: String): Flow<List<AutomationLogEntry>>
}

data class AutomationLogEntry(
    val sessionId: String,
    val timestampEpochMillis: Long,
    val level: LogLevel,
    val event: String,
    val message: String,
)

enum class LogLevel {
    DEBUG,
    INFO,
    WARNING,
    ERROR,
}
