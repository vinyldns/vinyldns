package vinyldns.api.domain.batch

import org.joda.time.DateTime

final case class BatchChangeReviewInfo(reviewerId: String,
                                       reviewComment: Option[String],
                                       reviewTimestamp: DateTime = DateTime.now())
