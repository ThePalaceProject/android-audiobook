package org.librarysimplified.audiobook.manifest_parser.webpub

import one.irradia.fieldrush.api.FRAbstractParserObject
import one.irradia.fieldrush.api.FRParseError
import one.irradia.fieldrush.api.FRParseResult
import one.irradia.fieldrush.api.FRParserContextType
import one.irradia.fieldrush.api.FRParserObjectFieldSchema
import one.irradia.fieldrush.api.FRParserObjectSchema
import one.irradia.fieldrush.vanilla.FRValueParsers
import org.librarysimplified.audiobook.manifest.api.PlayerManifest
import org.librarysimplified.audiobook.manifest.api.PlayerManifestExtensionValueType
import org.librarysimplified.audiobook.manifest.api.PlayerManifestLink
import org.librarysimplified.audiobook.manifest.api.PlayerManifestMetadata
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderID
import org.librarysimplified.audiobook.manifest.api.PlayerManifestReadingOrderItem
import org.librarysimplified.audiobook.manifest.api.PlayerPalaceID
import org.librarysimplified.audiobook.manifest_parser.extension_spi.ManifestParserExtensionType

/**
 * A parser that parses manifest objects.
 */

class WebPubManifestParser(
  private val palaceId: PlayerPalaceID,
  private val extensions: List<ManifestParserExtensionType>,
  private val originalBytes: ByteArray,
  onReceive: (FRParserContextType, PlayerManifest) -> Unit = FRValueParsers.ignoringReceiverWithContext()
) : FRAbstractParserObject<PlayerManifest>(onReceive) {

  private lateinit var metadata: PlayerManifestMetadata
  private val spineItems = mutableListOf<PlayerManifestReadingOrderItem>()
  private val tocElements = mutableListOf<PlayerManifestLink>()
  private val links = mutableListOf<PlayerManifestLink>()
  private val extensionValues = mutableListOf<PlayerManifestExtensionValueType>()
  private val errors = mutableListOf<FRParseError>()

  override fun onCompleted(context: FRParserContextType): FRParseResult<PlayerManifest> {
    return FRParseResult.errorsOr(listOf(), this.errors) {
      FRParseResult.succeed(
        PlayerManifest(
          palaceId = this.palaceId,
          originalBytes = this.originalBytes,
          readingOrder = this.spineItems.toList(),
          metadata = this.metadata,
          links = this.links.toList(),
          extensions = this.extensionValues.toList(),
          toc = this.tocElements.toList()
        )
      )
    }
  }

  override fun schema(context: FRParserContextType): FRParserObjectSchema {
    val metadataSchema =
      FRParserObjectFieldSchema(
        name = "metadata",
        parser = {
          WebPubMetadataParser(
            extensions = this.extensions,
            onExtensionValueProvided = { extensionValue ->
              this.extensionValues.add(extensionValue)
            },
            onReceive = { _, metadata ->
              this.metadata = metadata
            }
          )
        }
      )

    val readingOrderSchema =
      FRParserObjectFieldSchema(
        name = "readingOrder",
        parser = {
          FRValueParsers.forArrayMonomorphic(
            forEach = {
              WebPubLinkParser()
            },
            receiver = { spineItems ->
              this.spineItems.addAll(spineItems.mapIndexed { index, link ->
                this.toReadingOrderItem(index, link)
              })
            }
          )
        },
        isOptional = true
      )

    val tocSchema =
      FRParserObjectFieldSchema(
        name = "toc",
        parser = {
          FRValueParsers.forArrayMonomorphic(
            forEach = {
              WebPubLinkParser()
            },
            receiver = { spineItems ->
              this.tocElements.addAll(spineItems)
            }
          )
        },
        isOptional = true
      )

    val spineSchema =
      FRParserObjectFieldSchema(
        name = "spine",
        parser = {
          FRValueParsers.forArrayMonomorphic(
            forEach = {
              WebPubLinkParser()
            },
            receiver = { spineItems ->
              this.spineItems.addAll(spineItems.mapIndexed { index, link ->
                this.toReadingOrderItem(index, link)
              })
            }
          )
        },
        isOptional = true
      )

    val linksSchema =
      FRParserObjectFieldSchema(
        name = "links",
        parser = {
          FRValueParsers.forArrayMonomorphic(
            forEach = {
              WebPubLinkParser()
            },
            receiver = { links ->
              this.links.addAll(links)
            }
          )
        },
        isOptional = true
      )

    return this.finishSchema(
      context = context,
      linksSchema = linksSchema,
      metadataSchema = metadataSchema,
      readingOrderSchema = readingOrderSchema,
      spineSchema = spineSchema,
      tocSchema = tocSchema
    )
  }

  private fun toReadingOrderItem(
    index: Int,
    link: PlayerManifestLink
  ): PlayerManifestReadingOrderItem {
    return PlayerManifestReadingOrderItem(
      PlayerManifestReadingOrderID.create(index, link.hrefURI),
      link as PlayerManifestLink.LinkBasic
    )
  }

  private fun finishSchema(
    metadataSchema: FRParserObjectFieldSchema<PlayerManifestMetadata>,
    readingOrderSchema: FRParserObjectFieldSchema<List<PlayerManifestLink>>,
    spineSchema: FRParserObjectFieldSchema<List<PlayerManifestLink>>,
    linksSchema: FRParserObjectFieldSchema<List<PlayerManifestLink>>,
    tocSchema: FRParserObjectFieldSchema<List<PlayerManifestLink>>,
    context: FRParserContextType
  ): FRParserObjectSchema {
    /*
     * Register the existing schema fields, then consult any registered extensions to
     * see if there are more.
     */

    val schemas = mutableMapOf<String, FRParserObjectFieldSchema<*>>()
    schemas[linksSchema.name] = linksSchema
    schemas[metadataSchema.name] = metadataSchema
    schemas[readingOrderSchema.name] = readingOrderSchema
    schemas[spineSchema.name] = spineSchema
    schemas[tocSchema.name] = tocSchema

    WebPubParserExtensions.addToSchemas(
      context = context,
      containerName = "top-level",
      extensions = this.extensions,
      schemas = schemas,
      extensionMethod = { extension ->
        extension.topLevelObjectSchemas { extensionValue ->
          this.extensionValues.add(extensionValue)
        }
      },
      onError = { error ->
        this.errors.add(error)
      }
    )
    return FRParserObjectSchema(schemas.values.toList())
  }
}
