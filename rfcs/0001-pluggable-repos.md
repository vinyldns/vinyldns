- Feature or Prototype: pluggable_repos
- Start Date: 2018-08-03
- RFC PR: (leave this empty, populate once the PR is created with a github link to the PR)
- Issue: (once approved, this will link to the issue tracker for implementation of the RFC)

# Summary
[summary]: #summary
The repositories are all `trait`s / interfaces that can be implemented in anyway.  We hard-code the implementation behind each repository's constructor.  For example, looking at the `UserRepository` we can see the pattern in the companion object...

```scala
object UserRepository {
  def apply(): UserRepository =
    DynamoDBUserRepository()
```

The goal will be to allow users to _configure_ what database implementation they are using so that they can use any database they want.  The database implementation will be dynamically loaded when VinylDNS starts up.

# Motivation
[motivation]: #motivation

VinylDNS is presently hard-coded to the choices made by Comcast to support our scale.  We run a combination of datastores:

* MySQL - used for zones and zone access.  Managing zone access required query patterns that were ill-suited for DynamoDB.  We also use MySQL for batch changes as well.
* DynamoDB - used for all other things, in particular Record data.  Due to the scale of Comcast, DynamoDB was a good choice as the number of records is in the 100s of millions.

Not all VinylDNS users (it could be argued very few) would want to run the same setup that we do.  To encourage adoption of VinylDNS, we need to allow users to "bring their own repositories" based on their databases of choice.  For example, even at very large scale it is possible to fit everything inside a large Postgres installation.

# Design and Goals
[design]: #design-and-goals

* Create a `DatabaseProvider` trait that loads _all_ repositories.  This is necessary as it supports 1) the setting up of the database and 2) the sharing of database connection information
* Create a `Database` trait that has all of the repositories
* The `DatabaseProvider` will be responsible for loading the `Database` via a `Config`
* Move all hard-coded database initialization into a default `DatabaseProvider`
* Load the `DatabaseProvider` implementation from a config section.

```scala
trait Database { 
  def zone: ZoneRepository
  def recordSet: RecordSetRepository
  def user: UserRepository
  ...
}

trait DatabaseProvider {
  def load(config: Config): IO[Database]
}

object Database {
  def loadDatabaseProvider(config: Config): IO[DatabaseProvider] = 
    for {
      className <- IO(config.getString("type"))
      classInstance <- IO(
        Class
          .forName(className)
          .getDeclaredConstructor()
          .newInstance()
          .asInstanceOf[DatabaseProvider])
    } yield classInstance
    
  def load(config: Config): IO[Database] = 
    for {
      provider <- loadDatabaseProvider(config)
      database <- provider.load(config)
    yield database
}
    
```


# Drawbacks
[drawbacks]: #drawbacks

None.

# Alternatives
[alternatives]: #alternatives

What other designs have been considered? What is the impact of not doing this?

# Unresolved questions
[unresolved]: #unresolved-questions

What parts of the design are still TBD?

# Outcome(s)
[outcome]: #outcome

Was this RFC implemented, subsumed by another RFC or implementation, in-waiting,
or discarded?

# References
[references]: #references
