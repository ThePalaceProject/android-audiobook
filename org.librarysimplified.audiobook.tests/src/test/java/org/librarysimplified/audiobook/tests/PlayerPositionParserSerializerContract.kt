package org.librarysimplified.audiobook.tests

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.api.PlayerPosition
import org.librarysimplified.audiobook.api.PlayerPositionParserType
import org.librarysimplified.audiobook.api.PlayerPositionSerializerType
import org.librarysimplified.audiobook.api.PlayerResult
import org.librarysimplified.audiobook.api.PlayerResult.Success
import org.slf4j.Logger

abstract class PlayerPositionParserSerializerContract {

  abstract fun createParser(): PlayerPositionParserType

  abstract fun createSerializer(): PlayerPositionSerializerType

  abstract fun logger(): Logger

  @Test
  fun testSimpleRoundTrip() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("A Title", 23, 137, 183991238L, currentOffset = 0L)
    )
    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is Success<PlayerPosition, Exception>)

    val resultNode = (result as Success<PlayerPosition, Exception>).result

    Assertions.assertEquals("A Title", resultNode.title)
    Assertions.assertEquals(23, resultNode.part)
    Assertions.assertEquals(137, resultNode.chapter)
    Assertions.assertEquals(183991238L, resultNode.startOffset)
    Assertions.assertEquals(0L, resultNode.currentOffset)
  }

  @Test
  fun testMissingVersion() {
    val parser = createParser()

    val objects = ObjectMapper()
    val node = objects.createObjectNode()

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is PlayerResult.Failure<PlayerPosition, Exception>)
  }

  @Test
  fun testUnsupportedVersion() {
    val parser = createParser()

    val objects = ObjectMapper()
    val node = objects.createObjectNode()
    node.put("@version", Integer.MAX_VALUE)

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is PlayerResult.Failure<PlayerPosition, Exception>)
  }

  @Test
  fun testMissingPart() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("A Title", 23, 137, 183991238L, 0L)
    )

    (node["location"] as ObjectNode).remove("part")

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is PlayerResult.Failure<PlayerPosition, Exception>)
  }

  @Test
  fun testMissingChapter() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("A Title", 23, 137, 183991238L, 0L)
    )

    (node["location"] as ObjectNode).remove("chapter")

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is PlayerResult.Failure<PlayerPosition, Exception>)
  }

  @Test
  fun testMissingOffset() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("A Title", 23, 137, 183991238L, 0L)
    )

    (node["location"] as ObjectNode).remove("time")

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is PlayerResult.Failure<PlayerPosition, Exception>)
  }

  @Test
  fun testMissingTitle() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("A Title", 23, 137, 183991238L, 0L)
    )

    (node["location"] as ObjectNode).remove("title")

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is Success<PlayerPosition, Exception>)

    val resultNode = (result as Success<PlayerPosition, Exception>).result

    Assertions.assertEquals(null, resultNode.title)
    Assertions.assertEquals(23, resultNode.part)
    Assertions.assertEquals(137, resultNode.chapter)
    Assertions.assertEquals(183991238L, resultNode.startOffset)
    Assertions.assertEquals(0L, resultNode.currentOffset)
  }

  @Test
  fun testNullTitle() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition(null, 23, 137, 183991238L, 0L)
    )

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is Success<PlayerPosition, Exception>)

    val resultNode = (result as Success<PlayerPosition, Exception>).result

    Assertions.assertEquals(null, resultNode.title)
    Assertions.assertEquals(23, resultNode.part)
    Assertions.assertEquals(137, resultNode.chapter)
    Assertions.assertEquals(183991238L, resultNode.startOffset)
    Assertions.assertEquals(0L, resultNode.currentOffset)
  }

  @Test
  fun testNullTitleExplicit() {
    val parser = createParser()
    val serial = createSerializer()

    val node = serial.serializeToObjectNode(
      PlayerPosition("Something", 23, 137, 183991238L, 0L)
    )

    val objectNode = node["location"] as ObjectNode
    objectNode.put("title", null as String?)

    val result =
      parser.parseFromObjectNode(node)

    Assertions.assertTrue(result is Success<PlayerPosition, Exception>)

    val resultNode = (result as Success<PlayerPosition, Exception>).result

    Assertions.assertEquals(null, resultNode.title)
    Assertions.assertEquals(23, resultNode.part)
    Assertions.assertEquals(137, resultNode.chapter)
    Assertions.assertEquals(183991238L, resultNode.startOffset)
    Assertions.assertEquals(0L, resultNode.currentOffset)
  }
}
