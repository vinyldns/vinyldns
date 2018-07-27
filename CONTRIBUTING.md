# Contributing to VinylDNS
The following are a set of guidelines for contributing to VinylDNS and its associated repositories.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Issues](#issues)
- [Making Contributions](#making-contributions)
- [Style Guide](#style-guide)
- [Testing](#testing)
- [License Header Check](#license-header-check)
- [Release Management](#release-management)

## Code of Conduct
This project and everyone participating in it are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md).  By
participating, you agree to this Code.  Please report any violations to the code of conduct to vinyldns-core@googlegroups.com.

## Issues
If you would like to contribute to VinylDNS, you can look through `beginner` and `help-wanted` issues.  We keep a list
of these issues around to encourage participation in building the platform.  In the issue list, you can chose "Labels" and
choose a specific label to narrow down the issues to review.

* **Beginner issues**: only require a few lines of code to complete, rather isolated to one or two files.  A good way
to get through changing and testing your code, and meet everyone!
* **Help wanted issues**: these are more involved than beginner issues, are items that tend to come near the top of our backlog but not necessarily in the current development stream.

Besides those issues, you can sort the issue list by number of comments to find one that maybe of interest.  You do
_not_ have to limit yourself to _only_ "beginner" or "help-wanted" issues.

Before choosing an issue, see if anyone is assigned or has indicated they are working on it (either in comment or via PR).
You can work on the issue by reviewing the PR or asking where they are at; otherwise, it doesn't make sense to duplicate
work that is already in-progress.

## Making Contributions
### Submitting a Code Contribution
We follow the standard *GitHub Flow* for taking code contributions.  The following is the process typically followed:

1 - Create a fork of the repository that you want to contribute code to
1 - Clone your forked repository to your local machine
1 - In your local machine, add a remote to the "main" repository, we call this "upstream" by running
`git remote add upstream https://github.com/vinyldns/vinyldns.git`.  Note: you can also use `ssh` instead of `https`
1 - Create a local branch for your work `git checkout -b your-user-name/user-branch-name`.  Add whatever your GitHub
user name is before whatever you want your branch to be.
1 - Begin working on your local branch
1 - Make sure you run all builds before posting a PR!  It's faster to run everything locally rather than waiting for
the build server to complete its job.  See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for information on local development
1 - When you are ready to contribute your code, run `git push origin your-user-name/user-branch-name` to push your changes
to your _own fork_.
1 - Go to the [VinylDNS main repository](https://github.com/vinyldns/vinyldns.git) (or whatever repo you are contributing to)
and you will see your change waiting and a link to "Create a PR".  Click the link to create a PR.
1 - You will receive comments on your PR.  Use the PR as a dialog on your changes.

### Commit Messages
* Limit the first line to 72 characters or fewer.
* Use the present tense ("Add validation" not "Added validation").
* Use the imperative mood ("Move database call" not "Moves database call").
* Reference issues and other pull requests liberally after the first line.  Use [GitHub Auto Linking](https://help.github.com/articles/autolinked-references-and-urls/)
to link your PR to other issues.  _Note: This is essential, otherwise we may not know what issue a PR is created for_
* Use markdown syntax as much as you want

### Modifying your Pull Requests
Often times, you will need to make revisions to your PRs that you submit.  This is part of the standard process of code
review.  There are different ways that you can make revisions, but the following process is pretty standard.

1 - Sync with upstream first.  `git checkout master && git fetch upstream && git rebase upstream master && git push origin master`
1 - Checkout your branch on your local `git checkout your-user-name/user-branch-name`
1 - Sync your branch with latest `git rebase master`.  Note: If you have merge conflicts, you will have to resolve them
1 - Revise your PR, making changes recommended in the comments / code review
1 - When all tests pass, `git push origin your-user-name/user-branch-name` to revise your commit.  GitHub automatically
recognizes the update and will re-run verification on your PR!

### Merging your Pull Request
Once your PR is approved, one of the maintainers will merge your request for you.  If you are a maintainer, you can
merge your PR once you have the approval of at least 2 other maintainers.

## Style Guides
### Python Style Guide
* Use snake case for everything except classes.  `this_is_snake_case`; `thisIsNotSnakeCaseDoNotDoThis`

## Testing
For specific steps to run the tests see the [Testing](BUILDING.md#testing) section of the Building guide.

### Python for Testing
We use [pytest](https://docs.pytest.org/en/latest/) for python tests.  It is helpful that you browse the documentation
so that you are familiar with pytest and how our functional tests operate.

We also use [PyHamcrest](https://pyhamcrest.readthedocs.io/en/release-1.8/) for matchers in order to write easy
to read tests.  Please browse that documentation as well so that you are familiar with the different matchers
for PyHamcrest.  There aren't a lot, so it should be quick.

Want to become a super star?  [Write custom matchers!](https://pyhamcrest.readthedocs.io/en/release-1.8/custom_matchers/)

### Python Setup
We use python for our functional tests exclusively in this project.  You can find all python code under the
`functional_test` directory.

In that directory are a few important files for you to be familiar with:

* vinyl_client.py - this provides the interface to the VinylDNS api.  It handles signing the request for you, as well
as building and executing the requests, and giving you back valid responses.  For all new API endpoints, there should
be a corresponding function in the vinyl_client
* utils.py - provides general use functions that can be used anywhere in your tests.  Feel free to contribute new
functions here when you see repetition in the code

Functional tests run on every build, and are designed to work _in every environment_.  That means locally, in docker,
and in production environments.

The functional tests that we run live in `functional_test/live_tests` directory.  In there, we have directories / modules
for different areas of the application.

* membership - for managing groups and users
* recordsets - for managing record sets
* zones - for managing zones
* internal - for internal endpoints (not intended for public consumption)
* batch - for managing batch updates

### Functional Test Context
Our func tests use pytest contexts.  There is a main test context that lives in `shared_zone_test_context.py`
that creates and tears down a shared test context used by many functional tests.  The
beauty of pytest is that it will ensure that the test context is stood up exactly once, then all individual tests
that use the context are called using that same context.

The shared test context sets up several things that can be reused:

1. An ok user and group
1. A dummy user and group - a separate user and group helpful for tesing access controls and authorization
1. An ok zone accessible only by the ok user and ok group
1. A dummy zone accessible only by the dummy user and dummy group
1. An IPv6 reverse zone
1. A normal IPv4 reverse zone
1. A classless IPv4 reverse zone
1. A parent zone that has child zones - used for testing NS record management and zone delegations

### Really Important Test Context Rules!

1. Try to use the `shared_zone_test_context` whenever possible!  This reduces the time
it takes to run functional tests (which is in minutes).
1. Limit changes to users, groups, and zones in the shared test context, as doing so could impact downstream tests
1. If you do modify any entities in the shared zone context, roll those back when your function completes!

## License Header Check

### API
VinylDNS is configured with [sbt-header](https://github.com/sbt/sbt-header). All existing scala files have the appropriate
header. You can check for headers in `sbt` with:

```bash
> ;headerCheck;test:headerCheck;it:headerCheck
```

If you add a new file, you can add the appropriate header in `sbt` with:
```bash
> ;headerCreate;test:headerCreate;it:headerCreate
```

### Portal
>You can check for headers in `sbt` with:
```
project portal
;headerCheck;test:headerCheck;checkJsHeaders
```

>You can create headers in `sbt` with:
```
project portal
;headerCreate;test:headerCreate;createJsHeaders
```

## Release Management
As an  overview, we release on a regular schedule roughly once per month.  At any time, you can see the following releases scheduled using Milestones in GitHub.

* <current release> - for example, 0.9.8.  This constitutes the current work that is in-flight
* <next release> - for example, 0.9.9.  These are the issues pegged for the _next_ release to be worked on
* Backlog - These are the issues designated to be worked on in the not too distant future.
