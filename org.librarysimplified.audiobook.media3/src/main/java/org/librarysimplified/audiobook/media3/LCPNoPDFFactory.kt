package org.librarysimplified.audiobook.media3

import org.readium.r2.shared.util.data.ReadError
import org.readium.r2.shared.util.data.ReadTry
import org.readium.r2.shared.util.pdf.PdfDocument
import org.readium.r2.shared.util.pdf.PdfDocumentFactory
import org.readium.r2.shared.util.resource.Resource
import kotlin.reflect.KClass

/**
 * A PDF factory that always fails.
 */

object LCPNoPDFFactory : PdfDocumentFactory<PdfDocument> {

  override val documentType: KClass<PdfDocument>
    get() = PdfDocument::class

  override suspend fun open(resource: Resource, password: String?): ReadTry<PdfDocument> {
    return ReadTry.failure(ReadError.UnsupportedOperation("Not supported!"))
  }
}
