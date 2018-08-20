# Contributing to VinylDNS
The following are a set of guidelines for contributing to VinylDNS and its associated repositories.

## Table of Contents
* [Code of conduct](#code-of-conduct)
* [Issues](#issues)
    * [Working on an issue](#working-on-an-issue)
    * [Submitting an issue](#submitting-an-issue)
    * [Discussion process](#discussion-process)
* [Pull requests](#pull-requests)
    * [General flow](#general-flow)
    * [Pull request requirements](#pull-request-requirements)
        * [Commit messages](#commit-messages)
        * [Testing](#testing)
        * [Documentation edits](#documentation-edits)
        * [Style guides](#style-guides)
        * [License header checks](#license-header-checks)
        * [Contributor license agreement](#contributor-license-agreement)
    * [Modifying your pull request](#modifying-your-pull-requests)
    * [Pull request approval](#pull-request-approval)
* [Release management](#release-management)

## Code of Conduct
This project and everyone participating in it are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md).  By
participating, you agree to this Code. Please report any violations to the code of conduct to vinyldns-core@googlegroups.com.

## Issues

Work on VinylDNS is tracked by [Github Issues](https://guides.github.com/features/issues/). To contribute to VinylDNS,
you can join the discussion on an issue, submit a Pull Request to resolve the issue, or make an issue of your own.
VinylDNS issues are generally labeled as bug reports, feature requests, or maintenance requests.  

### Working on an issue
If you would like to contribute to VinylDNS, you can look through `beginner` and `help-wanted` issues.  We keep a list
of these issues around to encourage participation in building the platform.  In the issue list, you can chose "Labels" and
choose a specific label to narrow down the issues to review.

* **Beginner issues**: only require a few lines of code to complete, rather isolated to one or two files.  A good way
to get through changing and testing your code, and meet everyone!
* **Help wanted issues**: these are more involved than beginner issues, are items that tend to come near the top of our 
backlog but not necessarily in the current development stream.

Besides those issues, you can sort the issue list by number of comments to find one that may be of interest.  You do
_not_ have to limit yourself to _only_ "beginner" or "help-wanted" issues.

When resolving an issue, you generally will do so by making a [Pull Request](#pull-requests), and adding a link to the issue.

Before choosing an issue, see if anyone is assigned or has indicated they are working on it (either in comment or via Pull Request).
If that is the case, then instead of making a Pull Request of your own, you can help out by reviewing their Pull Request. 

### Submitting an issue
When submitting an issue you will notice there are three issue templates to choose from. Before making any issue, please
go search the issue list (open and closed issues) and check to see if a similar issue has been made. If so, we ask that you do not duplicate an
issue, but feel free to comment on the existing issue with additional details.  

* **Bug report**: If you find a bug in the project you can report it with this template and the VinylDNS team will take a
look at it. Please be as detailed as possible as it will help us recreate the bug and figure out what exactly is going on.
If you are unsure whether what you found is a bug, we encourage you to first pop in our [dev gitter](https://gitter.im/vinyldns/vinyldns), and we can
help determine if what you're seeing is unexpected behavior, and if it is we will direct to make the bug report. 
* **Feature request**: Use this template if you have something you wish to be added to the project. Please be detailed 
when describing why you are requesting the feature, what you want it to do, and alternative solutions you have considered.
If the feature is a substantial change to VinylDNS, it may be better suited as an RFC, through our [RFC process](https://github.com/vinyldns/rfcs).
* **Maintenance request**: This template is for suggesting upgrades to the existing code base. This could include 
code refactoring, new libraries, additional testing, among other things. Please be detailed when describing the 
reason for the maintenance, and what benefits will come out of it. Please describe the scope of the change, and 
what parts of the system will be impacted. 

### Discussion process
When an issue is submitted the VinylDNS team will give time for maintainers and the rest of the community to discuss it. 
This discussion phase will officially start when a maintainer has added the **discussion** label to the issue, but
people can still comment on it at any time. After this phase, the team will decide whether it is something 
that is suited for our backlog, in which case it will be prioritized eventually depending on the VinylDNS roadmap. 

## Pull Requests
Contributions to VinylDNS are generally made via [Github Pull Requests](https://help.github.com/articles/about-pull-requests/).
Most Pull Requests are related to an [issue](#issues), and will have a link to the issue in the Pull Request. 

### General Flow
We follow the standard *GitHub Flow* for taking code contributions.  The following is the process typically followed:

1. Create a fork of the repository that you want to contribute code to
1. Clone your forked repository to your local machine
1. In your local machine, add a remote to the "main" repository, we call this "upstream" by running 
`git remote add upstream https://github.com/vinyldns/vinyldns.git`.  Note: you can also use `ssh` instead of `https`
1. Create a local branch for your work `git checkout -b your-user-name/user-branch-name`.  Add whatever your GitHub
user name is before whatever you want your branch to be.
1. Begin working on your local branch
1. Be sure to add necessary unit, integration, and functional tests, see the [Testing](DEVELOPER_GUIDE.md#testing) section of the Developer Guide.
1. Make sure you run all builds before posting a Pull Request! It's faster to run everything locally rather than waiting for
the build server to complete its job. See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for information on local development.
1. When you are ready to contribute your code, run `git push origin your-user-name/user-branch-name` to push your changes
to your _own fork_
1. Go to the [VinylDNS main repository](https://github.com/vinyldns/vinyldns.git) (or whatever repo you are contributing to)
and you will see your change waiting and a link to "Create a Pull Request".  Click the link to create a Pull Request.
1. Be as detailed as possible in the description of your Pull Request. Describe what you changed, why you changed it, and 
give a detailed list of changes and impacted files. If your Pull Request is related to an existing issue, be sure to link the 
issue in the Pull Request itself, in addition to the Pull Request description. 
1. You will receive comments on your Pull Request.  Use the Pull Request as a dialog on your changes. 

### Pull Request Requirements

#### Commit Messages
* Limit the first line to 72 characters or fewer.
* Use the present tense ("Add validation" not "Added validation").
* Use the imperative mood ("Move database call" not "Moves database call").
* Reference issues and other pull requests liberally after the first line.  Use [GitHub Auto Linking](https://help.github.com/articles/autolinked-references-and-urls/)
to link your Pull Request to other issues.  _Note: This is essential, otherwise we may not know what issue a Pull Request is created for_
* Use markdown syntax as much as you want

#### Testing
When making changes to the VinylDNS codebase, be sure to add necessary unit, integration, and functional tests.
For specifics on our tests, see the [Testing](DEVELOPER_GUIDE.md#testing) section of the Developer Guide.

#### Documentation edits
Documentation for the VinylDNS project lives in files such as this one in the root of the project directory, as well
as in `modules/docs/src/main/tut` for the docs you see on <www.vinyldns.io>. Many changes, such as those that impact
an API endpoint, config, portal usage, etc, will also need corresponding documentation edited to prevent it from going stale.
Include those changes in the Pull Request. 

#### Style Guides
* For Scala code we use [Scalastyle](http://www.scalastyle.org/). The configs are `scalastyle-config.xml` and 
`scalastyle-test-config.xml` for source code and testing
* For our python code that we use for functional testing, we generally try to follow [PEP 8](https://www.python.org/dev/peps/pep-0008/)

#### License Header Checks
VinylDNS is configured with [sbt-header](https://github.com/sbt/sbt-header). All existing scala files have the appropriate
header. To add or check for headers, follow these steps: 

##### API
You can check for headers in the API in `sbt` with:

```
> ;project api;headerCheck;test:headerCheck;it:headerCheck
```

If you add a new file, you can add the appropriate header in `sbt` with:

```
> ;project api;headerCreate;test:headerCreate;it:headerCreate
```

##### Portal
You can check for headers in the Portal in `sbt` with:

```
> ;project portal;headerCheck;test:headerCheck;checkJsHeaders
```

If you add a new file, you can add the appropriate header in `sbt` with:

```
> ;project portal;headerCreate;test:headerCreate;createJsHeaders
```

#### Contributor License Agreement
Before Comcast merges your code into the project you must sign the 
[Comcast Contributor License Agreement (CLA)](https://gist.github.com/ComcastOSS/a7b8933dd8e368535378cda25c92d19a).

If you haven't previously signed a Comcast CLA, you'll automatically be asked to when you open a pull request. 
Alternatively, we can send you a PDF that you can sign and scan back to us. Please create a new GitHub issue to request a PDF version of the CLA.

### Modifying your Pull Requests
Often times, you will need to make revisions to your Pull Requests that you submit.  This is part of the standard process of code
review. There are different ways that you can make revisions, but the following process is pretty standard.

1. Sync with upstream first.  `git checkout master && git fetch upstream && git rebase upstream master && git push origin master`
1. Checkout your branch on your local `git checkout your-user-name/user-branch-name`
1. Sync your branch with latest `git rebase master`.  Note: If you have merge conflicts, you will have to resolve them
1. Revise your Pull Request, making changes recommended in the comments / code review
1. Stage and commit these changes on top of your existing commits
1. When all tests pass, `git push origin your-user-name/user-branch-name` to revise your commit.  GitHub automatically
recognizes the update and will re-run verification on your Pull Request!

### Pull Request Approval
A pull request satisfy our [pull request requirements](#pull-request-requirements)

Afterwards, if a Pull Request is approved, a maintainer of the project will merge it.
If you are a maintainer, you can merge your Pull Request once you have the approval of at least 2 other maintainers.

> Note: The first time you make a Pull Request, add yourself to the authors list [here](AUTHORS.md) as part of the Pull Request

## Release Management
As an overview, we release on a regular schedule roughly once per month.  At any time, you can see the following releases scheduled using Milestones in GitHub.

* <current release> - for example, 0.9.8.  This constitutes the current work that is in-flight
* <next release> - for example, 0.9.9.  These are the issues pegged for the _next_ release to be worked on
* Backlog - These are the issues designated to be worked on in the not too distant future.
