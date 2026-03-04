package org.librarysimplified.audiobook.demo

import android.app.Application
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
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

    val httpClient: LSHTTPClientType
      get() = LSHTTPClients()
        .create(
          this.INSTANCE,
          LSHTTPClientConfiguration("AudioBookDemo", "1.0.0", networkAccess = LSHTTPNetworkAccess)
        )
  }

  override fun onCreate() {
    super.onCreate()
    INSTANCE = this
    this.databaseField = ExampleBookmarkDatabase(this)

    System.out.println("Package name: ${this.packageName}")
    PlayerModel.start(this)
  }
}
