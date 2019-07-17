### Structured Logging



#### Extended ECS Schema for VinylDNS specific fields

Elastic ECS Schema is open to extend for application specific field groups. We have created a generic field group called `entity` to include domain object information to the log statements. The information included in the log statement can be centrally managed using `vinyldns.core.logging.StructuredArgs` utility component.


1. Entity

    Entity field group can be used to include custom domain information along with `id` and `type`. For e.g. an `entity` field for `zone` includes:
    
    ```json
    {
      "entity": {
        "name": "ok.",
        "id": "3588a416-4a6e-48c8-b1c1-14580170efee",
        "rsCount": 8,
        "type": "zone",
        "rawRsCount": 8
      }
    }
    ```
    If the domain model encapsulate other model, it is recommended to nest the field groups as shown below in a sample for `recordSetChange` below. This will enable us to perform contextual information and also helps to frame the search query for accurate results.

1. DNS

    Elastic ECS contains a work in progress PR to handle DNS question and answer information which can be included to log statements. We can refer the documentation https://github.com/elastic/ecs/pull/438 and in the sample section below. 

#### Samples

These sample are hand picked directly from the logs from VinylDNS portal and API application running locally. 


1. API Access Log
    ```json
    {
      "@timestamp": "2019-07-17T14:56:21.016+00:00",
      "message": "request processed",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "http": {
        "request": {
          "method": "GET",
          "headers": {
            "Timeout-Access": "<function1>",
            "X-Amz-Date": "20190717T145620Z",
            "Accept": "*/*",
            "User-Agent": "AHC/2.0",
            "Host": "localhost:9000"
          }
        },
        "url": {
          "path": "/zones/<zone id>/recordsetchanges",
          "query": "maxItems=5"
        },
        "user_agent": {
          "original": "AHC/2.0"
        },
        "version": "HTTP/1.1",
        "response": {
          "duration": 145,
          "status_code": 200
        }
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.api.route.VinylDNSService",
        "caller_class_name": "vinyldns.api.route.VinylDNSService$",
        "caller_method_name": "$anonfun$captureAccessLog$2",
        "caller_file_name": "VinylDNSService.scala",
        "caller_line_number": 127
      },
      "process": {
        "thread": {
          "name": "VinylDNS-akka.actor.default-dispatcher-7"
        }
      }
    }
    ```

1. Portal Access Log:

    ```json
    {
      "@timestamp": "2019-07-17T14:39:15.451+00:00",
      "message": "",
      "labels": {
        "application": "vinyldns-portal",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "request": {
        "method": "GET"
      },
      "user_agent": {
        "original": "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_14_5) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/12.1.1 Safari/605.1.15"
      },
      "response": {
        "status_code": 200
      },
      "client": {
        "address": "127.0.0.1"
      },
      "http": {
        "url": {
          "path": "/api/zones/3588a416-4a6e-48c8-b1c1-14580170efee/recordsetchanges",
          "query": "maxItems=5"
        }
      },
      "log": {
        "level": "INFO",
        "logger_name": "filters.AccessLoggingFilter",
        "caller_class_name": "filters.AccessLoggingFilter",
        "caller_method_name": "$anonfun$apply$1",
        "caller_file_name": "AccessLoggingFilter.scala",
        "caller_line_number": 53
      },
      "process": {
        "thread": {
          "name": "application-akka.actor.default-dispatcher-16"
        }
      }
    }
    ```

1. User landing a login page

    ```json
    {
      "@timestamp": "2019-07-16T18:11:06.567+00:00",
      "message": "User is not logged in or token expired; redirecting to login screen",
      "labels": {
        "application": "vinyldns-portal",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "login-redirect"
      },
      "entity": {
        "id": "None",
        "type": "user",
        "reason": "expired-token"
      },
      "log": {
        "level": "INFO",
        "logger_name": "actions.FrontendAction",
        "caller_class_name": "actions.VinylDnsAction",
        "caller_method_name": "invokeBlock",
        "caller_file_name": "VinylDnsAction.scala",
        "caller_line_number": 66
      },
      "process": {
        "thread": {
          "name": "application-akka.actor.default-dispatcher-5"
        }
      }
    }
    ```

1. 
    ```json
    {
      "@timestamp": "2019-07-16T18:19:03.576+00:00",
      "message": "dns.loadDnsView",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "dns-zone-view"
      },
      "entity": {
        "name": "ok.",
        "id": "3588a416-4a6e-48c8-b1c1-14580170efee",
        "rsCount": 8,
        "type": "zone",
        "rawRsCount": 8
      },
      "log": {
        "level": "INFO",
        "logger_name": "DnsZoneViewLoader",
        "caller_class_name": "vinyldns.api.domain.zone.DnsZoneViewLoader",
        "caller_method_name": "$anonfun$load$14",
        "caller_file_name": "ZoneViewLoader.scala",
        "caller_line_number": 92
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-17"
        }
      }
    }
    ```

1. SQS Message Queue

    ```json
    {
      "@timestamp": "2019-07-17T14:15:27.744+00:00",
      "message": "Deleting message",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "delete"
      },
      "entity": {
        "id": "38554b7b-64c7-47bc-ad16-ff269075fc65#5d2bbe22-c3f4-4a8b-a043-23f11e09582a",
        "type": "receiptHandle"
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.sqs.queue.SqsMessageQueue",
        "caller_class_name": "vinyldns.sqs.queue.SqsMessageQueue",
        "caller_method_name": "$anonfun$delete$1",
        "caller_file_name": "SqsMessageQueue.scala",
        "caller_line_number": 112
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-18"
        }
      }
    }
    ```

1. Recordset Change Handler

    ```json
    {
      "@timestamp": "2019-07-17T15:13:09.154+00:00",
      "message": "CHANGE COMPLETED",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "action": {
        "name": "RSChangeHandlerFSM"
      },
      "entity": {
        "state": "completed",
        "recordSet": {
          "ownerGroupId": "",
          "name": "jenkins",
          "id": "1b44b8bb-0ef5-41fe-a61f-fb9b35c9beee",
          "updated": 1563376389153,
          "status": "Active",
          "records": [
            {
              "name": "jenkins.",
              "data": "10.1.1.2",
              "op_code": "UPDATE",
              "class": "IN",
              "ttl": 38400,
              "type": "A"
            }
          ],
          "account": "system",
          "type": "recordSet",
          "zoneId": "3588a416-4a6e-48c8-b1c1-14580170efee",
          "recordSetType": "A",
          "created": 1563376388873
        },
        "changeType": "Update",
        "singleBatchChangeIds": [
          
        ],
        "zoneName": "ok.",
        "id": "8b0f8325-7e08-4e35-977d-5980b299d97b",
        "status": "Complete",
        "systemMessage": "None",
        "userId": "testuser",
        "type": "recordSetChange",
        "zoneId": "3588a416-4a6e-48c8-b1c1-14580170efee"
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.api.engine.RecordSetChangeHandler",
        "caller_class_name": "vinyldns.api.engine.RecordSetChangeHandler$",
        "caller_method_name": "fsm",
        "caller_file_name": "RecordSetChangeHandler.scala",
        "caller_line_number": 217
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-22"
        }
      }
    }
    ```

1. Command Handler 
    ```json
    {
      "@timestamp": "2019-07-17T14:15:27.743+00:00",
      "message": "Successfully completed processing of message",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "process-success"
      },
      "entity": {
        "id": "38554b7b-64c7-47bc-ad16-ff269075fc65#5d2bbe22-c3f4-4a8b-a043-23f11e09582a",
        "commandId": "e9a9f143-d383-4674-856a-314a2184f25c",
        "zoneId": "3588a416-4a6e-48c8-b1c1-14580170efee",
        "type": "commandMessage"
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.api.backend.CommandHandler",
        "caller_class_name": "vinyldns.api.backend.CommandHandler$",
        "caller_method_name": "$anonfun$outcomeOf$2",
        "caller_file_name": "CommandHandler.scala",
        "caller_line_number": 157
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-18"
        }
      }
    }
    ```

1. DynamoDBRecordChange

    ```json
    {
      "@timestamp": "2019-07-17T14:56:20.932+00:00",
      "message": "Saving change set",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "save"
      },
      "entity": {
        "id": "a8a4e17e-8d82-4c98-9268-e5eb6cadf6b5",
        "zone": "3588a416-4a6e-48c8-b1c1-14580170efee",
        "type": "changeSet",
        "size": 1
      },
      "log": {
        "level": "INFO",
        "logger_name": "DynamoDBRecordChangeRepository",
        "caller_class_name": "vinyldns.dynamodb.repository.DynamoDBRecordChangeRepository",
        "caller_method_name": "$anonfun$save$1",
        "caller_file_name": "DynamoDBRecordChangeRepository.scala",
        "caller_line_number": 115
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-22"
        }
      }
    }
    ```

1. DNS Question:

    ```json
    {
      "@timestamp": "2019-07-17T15:13:09.147+00:00",
      "message": "Querying for DNS",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "dns.question"
      },
      "dns": {
        "question": {
          "jenkins.ok.": "jenkins.ok",
          "type": "A"
        }
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.api.domain.dns.DnsConnection",
        "caller_class_name": "vinyldns.api.domain.dns.DnsConnection",
        "caller_method_name": "toQuery",
        "caller_file_name": "DnsConnection.scala",
        "caller_line_number": 113
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-22"
        }
      }
    }
    ```

1. DNS Answer:

    ```json
    {
      "@timestamp": "2019-07-17T15:13:09.151+00:00",
      "message": "Result of DNS lookup",
      "labels": {
        "application": "vinyldns-api",
        "env": "dev"
      },
      "ecs": {
        "version": "1.0.0"
      },
      "event": {
        "action": "dns.answer"
      },
      "dns": {
        "answers": [
          {
            "name": "jenkins.ok.",
            "data": "10.1.1.2",
            "op_code": "NOERROR",
            "class": "IN",
            "ttl": 38400,
            "type": "A"
          }
        ],
        "answers_count": 1,
        "response_code": "NOERROR"
      },
      "log": {
        "level": "INFO",
        "logger_name": "vinyldns.api.domain.dns.DnsConnection",
        "caller_class_name": "vinyldns.api.domain.dns.DnsConnection",
        "caller_method_name": "runQuery",
        "caller_file_name": "DnsConnection.scala",
        "caller_line_number": 171
      },
      "process": {
        "thread": {
          "name": "scala-execution-context-global-22"
        }
      }
    }
    ```
