package app.coolapk.kmp.ui

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal interface PublishDraftStore {
    suspend fun load(accountKey: String): String
    suspend fun save(accountKey: String, message: String)
    suspend fun clear(accountKey: String)
}

internal class AndroidPublishDraftStore(context: Context) : PublishDraftStore {
    private val preferences = context.applicationContext.getSharedPreferences(
        "coolapk_kmp_publish_drafts",
        Context.MODE_PRIVATE,
    )

    override suspend fun load(accountKey: String): String = withContext(Dispatchers.IO) {
        preferences.getString(accountKey, null).orEmpty()
    }

    override suspend fun save(accountKey: String, message: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().putString(accountKey, message).commit()) { "草稿保存失败" }
    }

    override suspend fun clear(accountKey: String) = withContext(Dispatchers.IO) {
        check(preferences.edit().remove(accountKey).commit()) { "草稿清除失败" }
    }
}
