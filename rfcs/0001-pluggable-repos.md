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

The goal will be to allow users to _configure_ what database implementation they are using.

# Motivation
[motivation]: #motivation

VinylDNS is presently hard-coded to the choices made by Comcast to support our scale.  We run a combination of datastores:

* MySQL - used for zones and zone access.  Managing zone access required query patterns that were ill-suited for DynamoDB.  We also use MySQL for batch changes as well.
* DynamoDB - used for all other things, in particular Record data.  Due to the scale of Comcast, DynamoDB was a good choice as the number of records is in the 100s of millions.

Not all VinylDNS (it could be argued very few) would want to run the same setup that we do.  To encourage adoption of VinylDNS, we need to allow users to "bring their own repositories" based on their databases of choice.  For example, even at very large scale it is possible to fit everything inside a large Postgres installation.

# Design and Goals
[design]: #design-and-goals

There is some precedent in the codebase in the way that we externalized Cryptography.  In open sourcing VinylDNS, the cryptography library that was used was not available in open source.  The library was also very difficult to replace.  We decided that the best way to support Cryptography was to make it "pluggable".

The following is an example crypto config section...

```yml
crypto {
  secret = "dont tell anyone"
  type = "vinyldns.some.crypto"
  }
}
```

VinylDNS loads the class specified in `crypto.type` above.

When we load cryptography, we assume that the class has been made available to the runtime by adding the jar(s) to the classpath.  The following code snippet _loads_ the cryptography...

```scala
  def load(cryptoConfig: Config): IO[CryptoAlgebra] =
    for {
      className <- IO(cryptoConfig.getString("type"))
      classInstance <- IO(
        Class
          .forName(className)
          .getDeclaredConstructor(classOf[Config])
          .newInstance(cryptoConfig)
          .asInstanceOf[CryptoAlgebra])
    } yield classInstance
 ```

The design mandates that there is a `class` that has a constructor taking a `Config` in order to initialize itself.  The following is the NoOp implementation...

```scala
class NoOpCrypto(config: Config) extends CryptoAlgebra {
  def encrypt(value: String): String = value
  def decrypt(value: String): String = value
}
```

The current Crypto design suffers from a few flows, namely:
* there is no way to know for sure that the class has the right Constructor that takes a config.  
* there is no way to know for sure that the config passed in has the correct attributes

**Solution**
The proposed solution is to make the loading of plugins more general, and more strongly typed

* Create a `DatabaseProvider` trait that loads _all_ repositories.  This is necessary

We can extend the same concept to repositories, and make the interface more general...

```scala
object Plugin {
  def load[A](pluginConfig: Config): IO[A] = ???
}
```

We can then update the companion objects for the repositories to take a Config...

```scala
object UserRepository {
  def apply(config: Config): UserRepository =
    Plugin.load(config)
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
