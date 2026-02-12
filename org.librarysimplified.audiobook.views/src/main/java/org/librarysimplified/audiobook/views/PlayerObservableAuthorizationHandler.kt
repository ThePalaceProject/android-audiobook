package org.librarysimplified.audiobook.views

import io.reactivex.Observable
import io.reactivex.android.schedulers.AndroidSchedulers
import io.reactivex.subjects.PublishSubject
import org.librarysimplified.audiobook.api.PlayerAuthorizationHandlerType
import org.librarysimplified.audiobook.api.PlayerDownloadRequest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.http.api.LSHTTPAuthorizationType

/**
 * An observable authorization handler that delegates to an underlying handler and exposes
 * information useful to the player views.
 */

object PlayerObservableAuthorizationHandler : PlayerAuthorizationHandlerType {

  @Volatile
  private var delegate: PlayerAuthorizationHandlerType? = null

  @Volatile
  private var credentialsValid: Boolean = false

  private val credentialsEventSubject =
    PublishSubject.create<Boolean>()
      .toSerialized()

  /**
   * An observable stream of values that indicate whether credentials are currently valid or not.
   */

  val credentialsEvents: Observable<Boolean> =
    this.credentialsEventSubject.observeOn(AndroidSchedulers.mainThread())

  /**
   * @return {@code true} if the current credentials are believed to be valid
   */

  fun areCredentialsValid(): Boolean {
    return this.credentialsValid
  }

  internal fun setHandler(
    delegate: PlayerAuthorizationHandlerType
  ) {
    this.delegate = delegate
    this.credentialsValid = true
    this.credentialsEventSubject.onNext(this.areCredentialsValid())
  }

  override fun onAuthorizationIsNoLongerInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    this.credentialsValid = true
    this.credentialsEventSubject.onNext(this.areCredentialsValid())
    this.delegate?.onAuthorizationIsNoLongerInvalid(source, kind)
  }

  override fun onAuthorizationIsInvalid(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ) {
    this.credentialsValid = false
    this.credentialsEventSubject.onNext(this.areCredentialsValid())
    this.delegate?.onAuthorizationIsInvalid(source, kind)
  }

  override fun onConfigureAuthorizationFor(
    source: PlayerManifestLink,
    kind: PlayerDownloadRequest.Kind
  ): LSHTTPAuthorizationType? {
    return this.delegate?.onConfigureAuthorizationFor(source, kind)
  }
}
