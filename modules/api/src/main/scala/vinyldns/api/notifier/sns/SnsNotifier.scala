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

package vinyldns.api.notifier.sns

import cats.effect.IO
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.{MessageAttributeValue, PublishRequest}
import org.json4s.JsonAST.JNull
import org.json4s.jackson.JsonMethods._
import org.slf4j.LoggerFactory
import vinyldns.api.route.VinylDNSJsonProtocol
import vinyldns.core.domain.batch.BatchChange
import vinyldns.core.notifier.{Notification, Notifier}
import java.io.{PrintWriter, StringWriter}

class SnsNotifier(config: SnsNotifierConfig, sns: AmazonSNS)
    extends Notifier
    with VinylDNSJsonProtocol {

  private val logger = LoggerFactory.getLogger(classOf[SnsNotifier])

  def notify(notification: Notification[_]): IO[Unit] =
    notification.change match {
      case bc: BatchChange => sendBatchChangeNotification(bc)
      case _ => IO.unit
    }

  def sendBatchChangeNotification(bc: BatchChange): IO[Unit] =
    IO {
      val message =
        compact(render(BatchChangeSerializer.toJson(bc).replace(List("changes"), JNull)).noNulls)
      logger.info(s"Sending batchChange='${bc.id}'; userName='${bc.userName}'; json='$message'")

      val request = new PublishRequest(config.topicArn, message)
      request.addMessageAttributesEntry(
        "userName",
        new MessageAttributeValue().withDataType("String").withStringValue(bc.userName)
      )
      sns.publish(request)
      logger.info(s"Sending batch change success; batchChange='${bc.id}'")
    }.handleErrorWith { e =>
      val errorMessage = new StringWriter
      e.printStackTrace(new PrintWriter(errorMessage))
      IO(logger.error(s"Failed sending batch change; batchChange='${bc.id}'. Error: ${errorMessage.toString.replaceAll("\n",";").replaceAll("\t"," ")}"))
    }.void
}
