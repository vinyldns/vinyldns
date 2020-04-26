import sbt._

object Resolvers {

  lazy val additionalResolvers = Seq(
    "dnvriend at bintray"     at "https://dl.bintray.com/dnvriend/maven",
    "bintray"                 at "https://jcenter.bintray.com",
    "DynamoDBLocal"           at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
  )
}
