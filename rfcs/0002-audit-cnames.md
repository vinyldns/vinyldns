_NOTE: This format comes from the
[Rust language RFC process](https://github.com/rust-lang/rfcs)._

- Feature or Prototype: audit_cnames
- Start Date: 2018-08-03
- RFC PR: 
- Issue: https://github.com/vinyldns/vinyldns/issues/9

# Summary
[summary]: #summary

Create a way to audit zones or DNS infrastructure's CNAME entries to ensure they
are still resolvable. This feature would allow the zone owners, security teams,
or DNS operators to better understand if there are any possible issues with
CNAME endpoints.

# Motivation
[motivation]: #motivation

DNS records can become stale, people forget to remove them. This can be an issue
if it's a CNAME and the end point of the CNAME is no longer in control of the
zone owner. This could lead to hijacking of that endpoint, which in turn could
be a major security issue.

# Design and Goals
[design]: #design-and-goals

Audit CNAMEs in a zone, this should be ran adhoc or on an interval by the zone
owners and VinylDNS admins. Ideally an alert would show that the CNAME endpoint
is no longer reachable and should be investigated. This should also be checked
when initially importing a zone into Vinyl.

We can discuss ways on how to implement, ideally we could use the dnspython
library to utilize and create some of the back end checks that could feed into
the Vinyl UI.

# Drawbacks
[drawbacks]: #drawbacks

Why should we *not* do this?

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
