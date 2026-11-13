# gitea-idea-plugin

![Build](https://github.com/LeonDevLifeLog/gitea-idea-plugin/workflows/Build/badge.svg)
[![Version](https://img.shields.io/jetbrains/plugin/v/22477-gitea.svg)](https://plugins.jetbrains.com/plugin/22477-gitea)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/22477-gitea.svg)](https://plugins.jetbrains.com/plugin/22477-gitea)

<!-- Plugin description -->
Gitea Plugin for Jetbrains IDEs.

# Support Feature

* Gitea Repo Clone
* TaskManagement
  * Track time
  * Update state
* Issues tool window
  * Filter by state, text, label, milestone, assignee and author
  * Read description and comments, comment, close and reopen
  * Create issues, start working on an issue (branch + task, branch shown on the issue)
* Pull requests tool window
  * List, overview with checks and reviews, commits, changed files with the IDE diff
  * Comment, review (approve / request changes / comment), merge, close/reopen, checkout
  * Create a pull request from the current branch ("Closes #n" for issue branches)
* Protected branches loaded from Gitea

# New Feature Coming Soon:

* Inline review comments in the diff

<!-- Plugin description end -->

## Features

Accounts are added under <kbd>Settings</kbd> > <kbd>Version Control</kbd> > <kbd>Gitea</kbd> with the server URL and
an access token (see [Required permissions](#required-permissions)). The same page holds the clone options and the
connection timeout.

### Clone

<kbd>File</kbd> > <kbd>New</kbd> > <kbd>Project from Version Control</kbd> > <kbd>Gitea</kbd> lists the repositories
of your accounts, your own first. A repository URL can also be typed in the search field.

- Clone over HTTPS or SSH. For repositories picked from the list the plugin uses the clone URLs provided by the
  server, which already reflect its SSH domain, port and user; for URLs typed by hand the SSH port and user are taken
  from the settings.
- Self-signed certificates accepted in the IDE work too.
- Git operations over HTTPS (fetch, pull, push) authenticate with the token of the matching Gitea account; with
  several accounts for the same server the IDE asks which one to use.

### Task management

<kbd>Tools</kbd> > <kbd>Tasks & Contexts</kbd> > <kbd>Configure Servers...</kbd> > <kbd>Gitea</kbd> connects the IDE
tasks to the issues of a repository.

- Open an issue as task, also by number (`#12`), with "Include issues not assigned to me" and closed issues as options.
- Track the time spent on the task and send it to the issue.
- Change the state of the issue (open, closed) from the IDE.

### Issues tool window

The **Issues** tab of the **Gitea** tool window shows the issues of the Gitea repositories found among the git remotes
of the project; with several repositories a selector appears on top.

- Filters: state, text, label, milestone, "Assigned to me", "Created by me". Gitea searches whole words only;
  `#12` opens issue 12 directly, ignoring the other filters.
- Details: labels, milestone, assignees, branch, description and comments rendered by Gitea, so issue references and
  mentions are links.
- Comment (<kbd>Ctrl</kbd>+<kbd>Enter</kbd>), close or reopen (optionally with a comment), copy the link, open in the
  browser, create a new issue with labels and "Assign to me".
- **Start Working**: checks out a new branch named after the issue (`issue-12-fix-login-page`), sets it as reference of
  the issue on Gitea and, when a Gitea task server is configured for the repository, activates the task.

### Pull requests tool window

The **Pull Requests** tab of the same tool window lists the pull requests of the repository.

- Filters: state, "Created by me".
- **Overview**: state (open, draft, merged, closed), source and target branches, mergeability, commit status checks,
  latest review of each reviewer and pending review requests, labels, description and comments.
- **Commits**: the commits of the pull request; double click opens one in the browser.
- **Files**: the changed files in the IDE changes browser, with the IDE diff. The pull request head is fetched
  (`refs/pull/<n>/head`) without creating branches.
- **Review**: approve, request changes or comment. The review refers to the commit you have seen.
- **Merge**: the merge styles allowed by the repository settings (merge, rebase, rebase and merge, squash,
  fast-forward only), with their default, optional commit title and message, and deletion of the branch. A pull
  request that received new commits after you opened it is not merged.
- **Checkout**: a pull request from the same repository in its branch, tracking the remote one; a pull request from a
  fork in `pr-<n>`.
- Comment, close or reopen, open in the browser.
- **New Pull Request** (toolbar): from the current branch into the default branch, changeable in the dialog. The branch
  is pushed first when needed. For a branch created by "Start Working" the title is the issue title and the
  description `Closes #<n>`, so merging closes the issue. An already open pull request for the branch is selected
  instead of creating a new one.

### Protected branches

After each fetch the plugin loads the branch protection rules of the Gitea remotes, so the IDE warns before pushing
to a protected branch. Reading the rules needs admin permission on the repository.

## Features Preview

### Gitea Repo Clone

| Features Preview                    | Features Preview                    | Features Preview |
|-------------------------------------|-------------------------------------|------------------|
| ![Features Preview](./assets/5.png) | ![Features Preview](./assets/6.png) |                  |

### Task Management

| Features Preview                    | Features Preview                    | Features Preview                    |
|-------------------------------------|-------------------------------------|-------------------------------------|
| ![Features Preview](./assets/0.png) | ![Features Preview](./assets/1.png) | ![Features Preview](./assets/2.png) |
| ![Features Preview](./assets/3.png) | ![Features Preview](./assets/4.png) | ![Features Preview](./assets/7.png) |
| ![Features Preview](./assets/8.png) |                                     |                                     |

## Installation

- Using the IDE built-in plugin system:

  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>Marketplace</kbd> > <kbd>Search for "Gitea"</kbd> >
  <kbd>Install</kbd>

- Manually:

  Download the [latest release](https://github.com/LeonDevLifeLog/gitea-idea-plugin/releases/latest) and install it
  manually using
  <kbd>Settings/Preferences</kbd> > <kbd>Plugins</kbd> > <kbd>⚙️</kbd> > <kbd>Install plugin from disk...</kbd>

## Required permissions

The plugin acts on Gitea with an access token: what it can do depends both on the **scopes of the token** and on
the **permissions of the user on the repository**. Verified with Gitea 1.27.1.

### Access token scopes

Create the token in Gitea under <kbd>Settings</kbd> > <kbd>Applications</kbd> > <kbd>Generate New Token</kbd>
(the "Get Access Token" button of the login dialog opens that page). To use every feature select:

| Scope              | Needed for                                                                                                                                                                                                          |
|--------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `read:user`        | logging in, the list of your repositories in the clone dialog, the "Assigned to me" / "Created by me" filters, "New Pull Request"                                                                                     |
| `write:issue`      | issues: comment, close/reopen, create, branch set by "Start Working", time tracking and state of the task server; comments on pull requests (Gitea stores them as issue comments). `read:issue` only lists and reads |
| `write:repository` | git over HTTPS (clone and fetch need `read:repository`, push needs write); pull requests: close/reopen, review, merge, create. `read:repository` only lists pull requests, their commits, changes and checks         |

With `read:user`, `read:issue` and `read:repository` the plugin works read-only: clone, browse issues and pull
requests, see the changes and check out pull requests.

### Permissions on the repository

| Action                                                                    | Minimum permission                                     |
|---------------------------------------------------------------------------|--------------------------------------------------------|
| See issues and pull requests, comment, create issues, review              | Read (any user for public repositories)                |
| Close/reopen and edit issues, time tracking, branch set by "Start Working" | Write, or being the author of the issue                |
| Close/reopen pull requests, merge                                         | Write                                                  |
| Push a branch (needed by "New Pull Request")                              | Write                                                  |
| Load protected branch rules (warning before pushing to them)              | Admin; without it the rules are silently not loaded    |

Gitea rules apply on top of these: nobody can approve or request changes on their own pull request, a review from a
user without write access is recorded but does not count as an official approval, and branch protection may require
approvals or passing checks before merging. Time tracking must be enabled in the repository settings.

## Special Thanks

[![JetBrains Logo (Main) logo](https://resources.jetbrains.com/storage/products/company/brand/logos/jb_beam.svg)](https://jb.gg/OpenSourceSupport)

---
Plugin based on the [IntelliJ Platform Plugin Template][template].

[template]: https://github.com/JetBrains/intellij-platform-plugin-template

[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
