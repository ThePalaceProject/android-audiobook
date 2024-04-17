package org.librarysimplified.audiobook.demo

import android.app.Application
import android.content.Intent
import org.librarysimplified.audiobook.api.PlayerUserAgent
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.vanilla.LSHTTPClients

class ExampleApplication : Application() {

  private lateinit var databaseField: ExampleBookmarkDatabase

  val bookmarkDatabase: ExampleBookmarkDatabase
    get() = this.databaseField

  companion object {
    private lateinit var INSTANCE: ExampleApplication

    @JvmStatic
    val application: ExampleApplication
      get() = this.INSTANCE

    val userAgent: PlayerUserAgent =
      PlayerUserAgent("AudioBookDemo")

    val httpClient: LSHTTPClientType
      get() = LSHTTPClients()
        .create(this.INSTANCE, LSHTTPClientConfiguration("AudioBookDemo", "1.0.0"))
  }

  override fun onCreate() {
    super.onCreate()
    INSTANCE = this
    this.databaseField = ExampleBookmarkDatabase(this)

    val intent = Intent(this, ExamplePlayerActivity::class.java)
    intent.addCategory(Intent.CATEGORY_LAUNCHER)
    intent.setAction(Intent.ACTION_MAIN)
    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP

    PlayerModel.startService(
      context = this,
      intentForPlayerService = intent
    )
  }
}
