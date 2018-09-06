package vinyldns.api

import com.amazonaws.services.dynamodbv2.AmazonDynamoDBClient
import org.slf4j.LoggerFactory
import vinyldns.dynamodb.repository.{DynamoDBHelper, DynamoDBIntegrationSpec}

trait DynamoDBApiIntegrationSpec extends DynamoDBIntegrationSpec {

  override val dynamoClient: AmazonDynamoDBClient = getDynamoClient(19000)

  override val dynamoDBHelper: DynamoDBHelper =
    new DynamoDBHelper(dynamoClient, LoggerFactory.getLogger("DynamoDBApiIntegrationSpec"))

}
