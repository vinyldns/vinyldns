import sbt._

object Resolvers {

  lazy val additionalResolvers = Seq(
    "spray"                   at "http://repo.spray.io/",
    "dnvriend at bintray"     at "https://dl.bintray.com/dnvriend/maven",
    "bintray"                 at "https://jcenter.bintray.com",
    "DynamoDBLocal"           at "https://s3-us-west-2.amazonaws.com/dynamodb-local/release"
  )
}
