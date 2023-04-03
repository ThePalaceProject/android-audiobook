package org.librarysimplified.audiobook.api

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode

/**
 * Functions to serialize and parse player positions.
 */

object PlayerPositions : PlayerPositionParserType, PlayerPositionSerializerType {

  override fun parseFromObjectNode(node: ObjectNode): PlayerResult<PlayerPosition, Exception> {
    try {
      val version = PlayerJSONParserUtilities.getInteger(node, "@version")
      when (version) {
        1 -> {
          return parseFromObjectNodeV1(node)
        }
        2 -> {
          return parseFromObjectNodeV2(node)
        }
        3 -> {
          return parseFromObjectNodeV3(node)
        }
      }

      throw PlayerJSONParseException("Unsupported format version: $version")
    } catch (e: Exception) {
      return PlayerResult.Failure(e)
    }
  }

  @Throws(PlayerJSONParseException::class)
  private fun parseFromObjectNodeV1(node: ObjectNode): PlayerResult<PlayerPosition, Exception> {
    val positionNode =
      PlayerJSONParserUtilities.getObject(node, "position")
    val chapter =
      PlayerJSONParserUtilities.getInteger(positionNode, "chapter")
    val part =
      PlayerJSONParserUtilities.getInteger(positionNode, "part")
    val offsetMilliseconds =
      PlayerJSONParserUtilities.getBigInteger(positionNode, "offsetMilliseconds").toLong()
    val title =
      PlayerJSONParserUtilities.getStringOptional(positionNode, "title")

    return PlayerResult.Success(
      PlayerPosition(
        title = title,
        part = part,
        chapter = chapter,
        startOffset = offsetMilliseconds,
        currentOffset = offsetMilliseconds
      )
    )
  }

  @Throws(PlayerJSONParseException::class)
  private fun parseFromObjectNodeV2(node: ObjectNode): PlayerResult<PlayerPosition, Exception> {
    val locationNode =
      PlayerJSONParserUtilities.getObject(node, "location")
    val chapter =
      PlayerJSONParserUtilities.getInteger(locationNode, "chapter")
    val part =
      PlayerJSONParserUtilities.getInteger(locationNode, "part")
    val offsetMilliseconds =
      PlayerJSONParserUtilities.getBigInteger(locationNode, "time").toLong()
    val title =
      PlayerJSONParserUtilities.getStringOptional(locationNode, "title")

    return PlayerResult.Success(
      PlayerPosition(
        title = title,
        part = part,
        chapter = chapter,
        startOffset = offsetMilliseconds,
        currentOffset = offsetMilliseconds
      )
    )
  }

  @Throws(PlayerJSONParseException::class)
  private fun parseFromObjectNodeV3(node: ObjectNode): PlayerResult<PlayerPosition, Exception> {
    val locationNode =
      PlayerJSONParserUtilities.getObject(node, "location")
    val chapter =
      PlayerJSONParserUtilities.getInteger(locationNode, "chapter")
    val part =
      PlayerJSONParserUtilities.getInteger(locationNode, "part")
    val startOffset =
      PlayerJSONParserUtilities.getBigInteger(locationNode, "startOffset").toLong()
    val currentOffset =
      PlayerJSONParserUtilities.getBigInteger(locationNode, "time").toLong()
    val title =
      PlayerJSONParserUtilities.getStringOptional(locationNode, "title")

    return PlayerResult.Success(
      PlayerPosition(
        title = title,
        part = part,
        chapter = chapter,
        startOffset = startOffset,
        currentOffset = currentOffset
      )
    )
  }

  override fun serializeToObjectNode(position: PlayerPosition): ObjectNode {
    val objects = ObjectMapper()
    val node = objects.createObjectNode()
    node.put("@version", 3)

    val locationNode = objects.createObjectNode()
    locationNode.put("chapter", position.chapter)
    locationNode.put("part", position.part)
    locationNode.put("startOffset", position.startOffset)
    locationNode.put("time", position.currentOffset)
    locationNode.put("title", position.title)
    node.set<ObjectNode>("location", locationNode)
    return node
  }
}
