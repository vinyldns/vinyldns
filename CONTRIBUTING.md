# Contributing to VinylDNS
The following are a set of guidelines for contributing to VinylDNS and its associated repositories.

## Table of Contents
- [Code of Conduct](#code-of-conduct)
- [Finding things to work on](#finding-things-to-work-on)
- [Submitting an issue](#submitting-an-issue)
- [Making Contributions](#making-contributions)
- [Style Guides](#style-guides)
- [Testing](#testing)
- [License Header Check](#license-header-check)
- [Release Management](#release-management)

## Code of Conduct
This project and everyone participating in it are governed by the [VinylDNS Code Of Conduct](CODE_OF_CONDUCT.md).  By
participating, you agree to this Code.  Please report any violations to the code of conduct to vinyldns-core@googlegroups.com.

## Finding things to work on
If you would like to contribute to VinylDNS, you can look through `beginner` and `help-wanted` issues.  We keep a list
of these issues around to encourage participation in building the platform.  In the issue list, you can chose "Labels" and
choose a specific label to narrow down the issues to review.

* **Beginner issues**: only require a few lines of code to complete, rather isolated to one or two files.  A good way
to get through changing and testing your code, and meet everyone!
* **Help wanted issues**: these are more involved than beginner issues, are items that tend to come near the top of our backlog but not necessarily in the current development stream.

Besides those issues, you can sort the issue list by number of comments to find one that maybe of interest.  You do
_not_ have to limit yourself to _only_ "beginner" or "help-wanted" issues.

Before choosing an issue, see if anyone is assigned or has indicated they are working on it (either in comment or via PR).

## Submitting an issue
When submitting an issue you will notice there are three issue templates to choose from. Before making any issue, please
go through the issue list and check to see if a similar issue has been made. If so, we ask that you do not duplicate an
issue, but feel free to comment on the existing issue with additional details.  

* **Bug report**: If you find a bug in the project you can report it with this template and the Vinyl team will take a
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
When an issue is submitted the Vinyl team will give time for maintainers and the rest of the community to discuss it. 
This discussion phase will officially start when a maintainer has added the **discussion** label to the issue, but
people can still comment on it at any time. After this phase, the team will decide whether it is something 
that is suited for our backlog, in which case it will be prioritized eventually depending on the VinylDNS roadmap. 

## Making Contributions
### Submitting a Code Contribution
We follow the standard *GitHub Flow* for taking code contributions.  The following is the process typically followed:

1. Create a fork of the repository that you want to contribute code to
1. Clone your forked repository to your local machine
1. In your local machine, add a remote to the "main" repository, we call this "upstream" by running 
`git remote add upstream https://github.com/vinyldns/vinyldns.git`.  Note: you can also use `ssh` instead of `https`
1. Create a local branch for your work `git checkout -b your-user-name/user-branch-name`.  Add whatever your GitHub
user name is before whatever you want your branch to be.
1. Begin working on your local branch
1. Be sure to add necessary unit, integration, and functional tests, see the [Testing](DEVELOPER_GUIDE.md#testing) section of the Developer Guide.
1. Make sure you run all builds before posting a PR!  It's faster to run everything locally rather than waiting for
the build server to complete its job.  See [DEVELOPER_GUIDE.md](DEVELOPER_GUIDE.md) for information on local development
1. When you are ready to contribute your code, run `git push origin your-user-name/user-branch-name` to push your changes
to your _own fork_.
1. Go to the [VinylDNS main repository](https://github.com/vinyldns/vinyldns.git) (or whatever repo you are contributing to)
and you will see your change waiting and a link to "Create a PR".  Click the link to create a PR (Pull Request).
1. Be as detailed as possible in the description of your pr. Describe what you changed, why you changed it, and 
give a detailed list of changes and impacted files. If your PR is related to an existing issue, be sure to link the 
issue in the PR itself, in addition to the PR description. 
1. You will receive comments on your PR.  Use the PR as a dialog on your changes. 

### Commit Messages
* Limit the first line to 72 characters or fewer.
* Use the present tense ("Add validation" not "Added validation").
* Use the imperative mood ("Move database call" not "Moves database call").
* Reference issues and other pull requests liberally after the first line.  Use [GitHub Auto Linking](https://help.github.com/articles/autolinked-references-and-urls/)
to link your PR to other issues.  _Note: This is essential, otherwise we may not know what issue a PR is created for_
* Use markdown syntax as much as you want

### Modifying your Pull Requests
Often times, you will need to make revisions to your PRs that you submit.  This is part of the standard process of code
review. There are different ways that you can make revisions, but the following process is pretty standard.

1. Sync with upstream first.  `git checkout master && git fetch upstream && git rebase upstream master && git push origin master`
1. Checkout your branch on your local `git checkout your-user-name/user-branch-name`
1. Sync your branch with latest `git rebase master`.  Note: If you have merge conflicts, you will have to resolve them
1. Revise your PR, making changes recommended in the comments / code review
1. Stage and commit these changes on top of your existing commits
1. When all tests pass, `git push origin your-user-name/user-branch-name` to revise your commit.  GitHub automatically
recognizes the update and will re-run verification on your PR!

### Contributor License Agreement

Before Comcast merges your code into the project you must sign the [Comcast Contributor License Agreement (CLA)](https://gist.github.com/ComcastOSS/a7b8933dd8e368535378cda25c92d19a).

If you haven't previously signed a Comcast CLA, you'll automatically be asked to when you open a pull request. 
Alternatively, we can send you a PDF that you can sign and scan back to us. Please create a new GitHub issue to request a PDF version of the CLA.

### PR Approval
Things your PR needs before it is approved:

* An indication that you signed the [Comcast Contributor License Agreement (CLA)](https://gist.github.com/ComcastOSS/a7b8933dd8e368535378cda25c92d19a)
* Its most recent build in Travis must pass
* Cannot have conflicts with the master branch, Github will indicate if it does

Afterwards, if your PR is approved, a maintainer of the project will merge it for you.
If you are a maintainer, you can merge your PR once you have the approval of at least 2 other maintainers.

> Note: The first time you make a PR, add yourself to the authors list [here]AUTHORS.md) as part of the PR

## Style Guides
### Python Style Guide
* Use snake case for everything except classes.  `this_is_snake_case`; `thisIsNotSnakeCaseDoNotDoThis`

## Testing
When making changes to the VinylDNS codebase, be sure to add necessary unit, integration, and functional tests.
For specifics on our tests, see the [Testing](DEVELOPER_GUIDE.md#testing) section of the Developer Guide.

## License Header Check
VinylDNS is configured with [sbt-header](https://github.com/sbt/sbt-header). All existing scala files have the appropriate
header. To add or check for headers, follow these steps: 

### API
You can check for headers in the API in `sbt` with:

```
> ;project api;headerCheck;test:headerCheck;it:headerCheck
```

If you add a new file, you can add the appropriate header in `sbt` with:

```
> ;project api;headerCreate;test:headerCreate;it:headerCreate
```

### Portal
You can check for headers in the Portal `sbt` with:

```
> ;project portal;headerCheck;test:headerCheck;checkJsHeaders
```

If you add a new file, you can add the appropriate header in `sbt` with:

```
> ;project portal;headerCreate;test:headerCreate;createJsHeaders
```

## Release Management
As an  overview, we release on a regular schedule roughly once per month.  At any time, you can see the following releases scheduled using Milestones in GitHub.

* <current release> - for example, 0.9.8.  This constitutes the current work that is in-flight
* <next release> - for example, 0.9.9.  These are the issues pegged for the _next_ release to be worked on
* Backlog - These are the issues designated to be worked on in the not too distant future.
