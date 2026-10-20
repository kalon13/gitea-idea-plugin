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
