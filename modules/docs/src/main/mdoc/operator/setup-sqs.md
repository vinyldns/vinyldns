---
layout: docs
title:  "Setup AWS SQS"
section: "operator_menu"
---

# Setup AWS SQS
SQS is used to provide high-availability and failover in the event that a node crashes mid-stream while processing a message.
The backend processing for VinylDNS is built to be idempotent so changes can be fully re-applied.  The SQS queue
also provides a mechanism to _throttle_ updates, in the event that an out-of-control client submits thousands or
millions of concurrent requests, they will all be throttled through SQS.

You must setup an SQS queue before you can start working with VinylDNS.  An [AWS SQS Getting Started Guide](https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-getting-started.html)
provides the information you need to setup your queue.

## Setting up AWS SQS
As opposed to MySQL where everything is created when the application starts up, the SQS queue needs to be setup by hand.
This section goes through those settings that are required.

The traffic with AWS SQS is rather low.  Presently, Comcast operates multiple SQS queues across multiple environments (dev, staging, prod),
and incur a cost of less than $10 USD per month.  SQS allows up to 1MM requests per month in the _free_ tier.  It is possible
to operate VinylDNS entirely in the "free" tier.  You can "tune down" your usage by increasing your polling interval.

The following SQS Queue Attributes are recommended (these are in AWS when you create an SQS Queue):

* `Queue Type` - Standard
* `Delivery Delay` - 0 seconds
* `Default Visibility Timeout` - 1 minute (how long it takes a record change to complete, usually a second)
* `Message Retention Period` - 4 days
* `Maximum Message Size` - 256KB
* `Receive Message Wait Time` - 0 seconds
* `Maximum Receives` - 100 (how many times a message will be retried before failing.  Note: if any messages retry more than 100 times, there is likely a problem requiring immediate attention)
* `Dead Letter Queue` - use a dead letter queue for all queues

## Configuring SQS
Before you can configure SQS, make note of the AWS account (access key and secret access key) as well as the
SQS Queue Url that you will be using.  Follow the [SQS Configuration](config-api.html#queue-configuration) to complete the setup.
