package org.librarysimplified.audiobook.tests.rbdigital

import com.fasterxml.jackson.databind.ObjectMapper
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test
import org.librarysimplified.audiobook.rbdigital.RBDigitalLinkDocumentParser
import org.librarysimplified.audiobook.rbdigital.RBDigitalLinkDocumentParser.ParseResult.ParseFailed
import org.librarysimplified.audiobook.rbdigital.RBDigitalLinkDocumentParser.ParseResult.ParseSuccess
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

abstract class RBDigitalLinkDocumentParserContract {

  private val mapper = ObjectMapper()

  @Test
  fun parseFromObjectNode() {
    val parser = RBDigitalLinkDocumentParser()

    val objectNode = this.mapper.createObjectNode()
    objectNode.put("type", "application/octet-stream")
    objectNode.put("url", "http://www.example.com")

    val result = parser.parseFromObjectNode(objectNode)
    Assertions.assertTrue(result is ParseSuccess)
    val document = result as ParseSuccess
    Assertions.assertEquals("application/octet-stream", document.document.type)
    Assertions.assertEquals("http://www.example.com", document.document.uri.toString())
  }

  @Test
  fun parseFromObjectNodeMissingType() {
    val parser = RBDigitalLinkDocumentParser()

    val objectNode = this.mapper.createObjectNode()
    objectNode.put("url", "http://www.example.com")

    val result = parser.parseFromObjectNode(objectNode)
    Assertions.assertTrue(result is ParseFailed)
  }

  @Test
  fun parseFromObjectNodeMissingURL() {
    val parser = RBDigitalLinkDocumentParser()

    val objectNode = this.mapper.createObjectNode()
    objectNode.put("type", "application/octet-stream")

    val result = parser.parseFromObjectNode(objectNode)
    Assertions.assertTrue(result is ParseFailed)
  }

  @Test
  fun parseFromNodeNotObject() {
    val parser = RBDigitalLinkDocumentParser()

    val node = this.mapper.createArrayNode()

    val result = parser.parseFromNode(node)
    Assertions.assertTrue(result is ParseFailed)
  }

  @Test
  fun parseFromStream() {
    val parser = RBDigitalLinkDocumentParser()

    val objectNode = this.mapper.createObjectNode()
    objectNode.put("type", "application/octet-stream")
    objectNode.put("url", "http://www.example.com")

    ByteArrayInputStream(this.mapper.writeValueAsBytes(objectNode)).use { stream ->
      val result = parser.parseFromStream(stream)
      Assertions.assertTrue(result is ParseSuccess)
      val document = result as ParseSuccess
      Assertions.assertEquals("application/octet-stream", document.document.type)
      Assertions.assertEquals("http://www.example.com", document.document.uri.toString())
    }
  }

  @Test
  fun parseFromFile() {
    val parser = RBDigitalLinkDocumentParser()

    val objectNode = this.mapper.createObjectNode()
    objectNode.put("type", "application/octet-stream")
    objectNode.put("url", "http://www.example.com")

    val file = File.createTempFile("rbdigital-test-", ".tmp")
    FileOutputStream(file).use { output -> this.mapper.writeValue(output, objectNode) }

    FileInputStream(file).use { stream ->
      val result = parser.parseFromStream(stream)
      Assertions.assertTrue(result is ParseSuccess)
      val document = result as ParseSuccess
      Assertions.assertEquals("application/octet-stream", document.document.type)
      Assertions.assertEquals("http://www.example.com", document.document.uri.toString())
    }
  }
}
