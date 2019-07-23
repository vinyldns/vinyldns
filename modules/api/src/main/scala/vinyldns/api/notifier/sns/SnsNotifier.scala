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

import vinyldns.core.notifier.{Notification, Notifier}
import cats.effect.IO
import cats.syntax.functor._
import vinyldns.core.domain.batch.{BatchChange, BatchChangeInfo}
import vinyldns.api.route.VinylDNSJsonProtocol
import org.json4s.jackson.JsonMethods._
import com.amazonaws.services.sns.AmazonSNS
import com.amazonaws.services.sns.model.PublishRequest
import com.amazonaws.services.sns.model.MessageAttributeValue
import org.json4s.JsonAST.JNull

class SnsNotifier(config: SnsNotifierConfig, sns: AmazonSNS)
    extends Notifier
    with VinylDNSJsonProtocol {

  def notify(notification: Notification[_]): IO[Unit] =
    notification.change match {
      case bc: BatchChange => sendBatchChangeNotification(BatchChangeInfo(bc))
      case _ => IO.unit
    }

  def sendBatchChangeNotification(bc: BatchChangeInfo): IO[Unit] =
    IO {
      val message =
        compact(
          render(BatchChangeInfoSerializer.toJson(bc).replace(List("changes"), JNull)).noNulls)
      val request = new PublishRequest(config.topicArn, message)
      request.addMessageAttributesEntry(
        "userName",
        new MessageAttributeValue().withDataType("String").withStringValue(bc.userName))
      sns.publish(request)
    }.void
}
