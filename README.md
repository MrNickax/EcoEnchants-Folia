# EcoEnchants (Folia)

> ⚠️ **Unofficial fork.** This is an independent fork of
> [EcoEnchants](https://github.com/Auxilor/EcoEnchants), patched to run on **Folia**. It is not
> affiliated with, endorsed by, or supported by Auxilor or the original EcoEnchants team.

**EcoEnchants** adds a large set of custom enchantments to your server. This fork ports it to
**Folia** (and Folia-based servers); all other functionality comes from the upstream project.

## Disclaimer

This software is provided **as is, without any warranty**. It is an unofficial adaptation
maintained on my own, and **I take no responsibility for any bugs, crashes, data loss or damage**
resulting from its use. Use it at your own risk, and always test in a controlled environment
before deploying to production.

Issues that also occur in the official version should be reported upstream — not blamed on this
fork.

## Installation

1. Download the latest jar from the [releases page](https://github.com/MrNickax/EcoEnchants-Folia/releases).
2. Drop it into your server's `plugins/` folder.
3. Restart the server.

This fork bundles its own Folia build of libreforge, so no separate libreforge install is needed.

## Building

This project consumes the Folia forks of [eco](https://github.com/MrNickax/eco-folia) and
[libreforge](https://github.com/MrNickax/libreforge-folia) from **GitHub Packages**, which require
a `read:packages` token even for public packages. Add your credentials to
`~/.gradle/gradle.properties` (`gpr.user` / `gpr.key`) or the `GITHUB_ACTOR` / `GITHUB_TOKEN`
environment variables, then:

```bash
git clone https://github.com/MrNickax/EcoEnchants-Folia
cd EcoEnchants-Folia
./gradlew build
```

The final plugin jar is produced in `bin/`.

## Credits & license

Based on [Auxilor/EcoEnchants](https://github.com/Auxilor/EcoEnchants). The original license terms
are preserved — see [LICENSE.md](LICENSE.md).
