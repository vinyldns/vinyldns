- Feature or Prototype: pluggable_repos
- Start Date: 2018-08-03
- RFC PR: https://github.com/vinyldns/vinyldns/pull/54/files
- Issue: https://github.com/vinyldns/vinyldns/issues/55

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

1. Support having any number of database backends for the repositories.  This is required as we have to support things like running database migrations and setting up connection pools
1. Support each database backend to have 1 or more repositories.  This allows mix-and-match across database types

```yaml
mysql {
  url = ...
  user = ...
  password = ...

  user { // define user table name here }
  zone { // define zone table name here }
}

dynamodb {
  access-key = ...
  secret-key = ...
  endpoint = ...
  
  recordSet { // record set table properties go here, throughput, name }
  recordSetChange  { // record set change properties go in here}
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
