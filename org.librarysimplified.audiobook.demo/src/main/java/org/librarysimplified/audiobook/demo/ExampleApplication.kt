package org.librarysimplified.audiobook.demo

import android.app.Application
import android.content.Intent
import org.librarysimplified.audiobook.views.PlayerModel
import org.librarysimplified.http.api.LSHTTPClientConfiguration
import org.librarysimplified.http.api.LSHTTPClientType
import org.librarysimplified.http.api.LSHTTPNetworkAccess
import org.librarysimplified.http.network_access.LSHTTPNetworkAvailabilityService
import org.librarysimplified.http.vanilla.LSHTTPClients

class ExampleApplication : Application() {
  private lateinit var databaseField: ExampleBookmarkDatabase

  val bookmarkDatabase: ExampleBookmarkDatabase
    get() = this.databaseField

  companion object {
    private lateinit var instance: ExampleApplication

    @JvmStatic
    val application: ExampleApplication
      get() = this.instance

    val httpClient: LSHTTPClientType
      get() =
        LSHTTPClients()
          .create(
            this.instance,
            LSHTTPClientConfiguration("AudioBookDemo", "1.0.0", networkAccess = LSHTTPNetworkAccess)
          )
  }

  override fun onCreate() {
    super.onCreate()
    instance = this
    this.databaseField = ExampleBookmarkDatabase(this)

    System.out.println("Package name: ${this.packageName}")
    PlayerModel.start(this)

    this.startService(
      Intent(
        this,
        LSHTTPNetworkAvailabilityService::class.java
      )
    )
  }
}
