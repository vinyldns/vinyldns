/*
 * Copyright 2018 Comcast Cable Communications Management, LLC
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package vinyldns.api.backend.dns

import java.io.{BufferedOutputStream, DataInputStream, DataOutputStream, IOException}
import java.net.{InetAddress, InetSocketAddress, ServerSocket}
import java.time.Duration

import org.scalatest.EitherValues
import org.scalatest.matchers.should.Matchers
import org.scalatest.wordspec.AnyWordSpec
import org.xbill.DNS
import vinyldns.api.domain.zone.{DnsZoneViewLoader, ZoneTooLargeError}
import vinyldns.core.domain.record.RecordType
import vinyldns.core.domain.zone.Zone

/**
  * Exercises DnsBackend.loadZone against a local fake AXFR server, so that the limits that protect
  * the API from hostile transfer servers are tested against real dnsjava behavior.
  */
class DnsBackendZoneTransferSpec extends AnyWordSpec with Matchers with EitherValues {

  private val zone = Zone("vinyldns.", "test@test.com")
  private val zoneName = DNS.Name.fromString("vinyldns.")

  private val soa = new DNS.SOARecord(
    zoneName,
    DNS.DClass.IN,
    300L,
    DNS.Name.fromString("ns.vinyldns."),
    DNS.Name.fromString("admin.vinyldns."),
    1L,
    3600L,
    600L,
    86400L,
    300L
  )

  private def aRecord(i: Int): DNS.Record =
    new DNS.ARecord(
      DNS.Name.fromString(s"host$i.vinyldns."),
      DNS.DClass.IN,
      300L,
      InetAddress.getByName("10.1.1.1")
    )

  // HINFO is not a record type supported by VinylDNS
  private def unsupportedRecord(i: Int): DNS.Record =
    new DNS.HINFORecord(
      DNS.Name.fromString(s"host$i.vinyldns."),
      DNS.DClass.IN,
      300L,
      "cpu",
      "os"
    )

  private def axfr(body: Iterator[DNS.Record]): Iterator[DNS.Record] =
    Iterator.single[DNS.Record](soa) ++ body ++ Iterator.single[DNS.Record](soa)

  /** Minimal AXFR-over-TCP server; streams `records` (possibly endless) or stalls if `stall` */
  private class FakeAxfrServer(records: Iterator[DNS.Record], stall: Boolean = false)
      extends AutoCloseable {
    private val server = new ServerSocket(0, 1, InetAddress.getLoopbackAddress)

    val address = new InetSocketAddress(InetAddress.getLoopbackAddress, server.getLocalPort)

    val thread: Thread = new Thread(new Runnable {
      def run(): Unit = {
        val socket = server.accept()
        try {
          val in = new DataInputStream(socket.getInputStream)
          val out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream))
          val query = new DNS.Message({
            val buf = new Array[Byte](in.readUnsignedShort())
            in.readFully(buf)
            buf
          })

          if (stall) {
            // hold the connection open and say nothing until the client gives up
            while (in.read() != -1) {}
          } else {
            records.grouped(50).foreach { batch =>
              val msg = new DNS.Message(query.getHeader.getID)
              msg.getHeader.setFlag(DNS.Flags.QR)
              msg.getHeader.setFlag(DNS.Flags.AA)
              msg.addRecord(query.getQuestion, DNS.Section.QUESTION)
              batch.foreach(msg.addRecord(_, DNS.Section.ANSWER))
              val wire = msg.toWire
              out.writeShort(wire.length)
              out.write(wire)
              out.flush()
            }
          }
        } catch {
          case _: IOException => // client went away
        } finally {
          socket.close()
        }
      }
    })
    thread.setDaemon(true)
    thread.start()

    def close(): Unit = {
      server.close()
      thread.interrupt()
    }
  }

  private def backend(
      server: FakeAxfrServer,
      timeout: Duration = DnsBackend.ZoneTransferTimeout
  ): DnsBackend =
    new DnsBackend(
      "test",
      new DNS.SimpleResolver("127.0.0.1"),
      TransferInfo(server.address, None),
      timeout
    )

  private def withServer[A](server: FakeAxfrServer)(test: FakeAxfrServer => A): A =
    try test(server)
    finally server.close()

  "DnsBackend.loadZone" should {
    "load a zone with fewer records than the max" in withServer(
      new FakeAxfrServer(axfr((1 to 5).iterator.map(aRecord)))
    ) { server =>
      val result = backend(server).loadZone(zone, 10).unsafeRunSync()
      result.count(_.typ == RecordType.A) shouldBe 5
    }

    "load a zone with exactly the max number of unique records" in withServer(
      // 4 A records + the SOA (sent twice, counted once) = 5
      new FakeAxfrServer(axfr((1 to 4).iterator.map(aRecord)))
    ) { server =>
      val result = backend(server).loadZone(zone, 5).unsafeRunSync()
      result.count(_.typ == RecordType.A) shouldBe 4
    }

    "fail with an unwrapped ZoneTooLargeError when unique records exceed the max" in withServer(
      new FakeAxfrServer(axfr((1 to 5).iterator.map(aRecord)))
    ) { server =>
      val result = backend(server).loadZone(zone, 5).attempt.unsafeRunSync()
      result.left.value shouldBe a[ZoneTooLargeError]
    }

    "not count duplicate records toward the unique record limit" in withServer(
      // 30 records received, only 3 unique A records + SOA
      new FakeAxfrServer(axfr((1 to 3).iterator.flatMap(i => Iterator.fill(10)(aRecord(i)))))
    ) { server =>
      val result = backend(server).loadZone(zone, 10).unsafeRunSync()
      result.count(_.typ == RecordType.A) shouldBe 3
    }

    "stop a flood of duplicate records" in withServer(
      new FakeAxfrServer(axfr(Iterator.continually(aRecord(1))))
    ) { server =>
      val result = backend(server).loadZone(zone, 10).attempt.unsafeRunSync()
      result.left.value shouldBe a[ZoneTooLargeError]
    }

    "stop a flood of unsupported records" in withServer(
      new FakeAxfrServer(axfr(Iterator.from(1).map(unsupportedRecord)))
    ) { server =>
      val result = backend(server).loadZone(zone, 10).attempt.unsafeRunSync()
      result.left.value shouldBe a[ZoneTooLargeError]
    }

    "abort and disconnect from a server that streams records endlessly" in withServer(
      new FakeAxfrServer(axfr(Iterator.from(1).map(aRecord)))
    ) { server =>
      val result = backend(server).loadZone(zone, 100).attempt.unsafeRunSync()
      result.left.value shouldBe a[ZoneTooLargeError]

      // the server only stops writing once the client has closed the connection
      server.thread.join(10000)
      server.thread.isAlive shouldBe false
    }

    "time out when the server stops responding" in withServer(
      new FakeAxfrServer(Iterator.empty, stall = true)
    ) { server =>
      val start = System.nanoTime()
      val result =
        backend(server, Duration.ofSeconds(1)).loadZone(zone, 100).attempt.unsafeRunSync()

      result.left.value shouldBe a[IOException]
      Duration.ofNanos(System.nanoTime() - start).getSeconds should be < 10L
    }
  }

  // ZoneSyncHandler.runSync and zone connection validation both load the DNS view this way
  "DnsZoneViewLoader" should {
    "enforce the max zone size when loading through a real DnsBackend" in withServer(
      new FakeAxfrServer(axfr(Iterator.from(1).map(aRecord)))
    ) { server =>
      val loader = DnsZoneViewLoader(zone, backend(server), 100)
      val result = loader.load().attempt.unsafeRunSync()
      result.left.value shouldBe a[ZoneTooLargeError]
    }
  }
}
