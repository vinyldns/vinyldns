package vinyldns.core.queue

final case class MessageQueueConfig(
    embedded: Boolean,
    accessKey: String,
    secretKey: String,
    signingRegion: String,
    serviceEndpoint: String,
    queueUrl: String)
