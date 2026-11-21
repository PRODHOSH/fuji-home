# Security

## Reporting a problem

Please **don't open a public issue** for a security problem. Use GitHub's private reporting instead:
**Security → Report a vulnerability** on this repo
([direct link](https://github.com/PRODHOSH/fuji-home/security/advisories/new)).

Tell us what you found, how to reproduce it, and which TV and Android version. You'll get a reply within a week.

## What counts

- Anything that lets another app or device on the network control Fuji Home or read data through it.
- The accessibility helper (`HomeRedirectService`) doing more than opening Fuji Home.
- Network requests sending more than the README's Privacy table says.

## Good habits when using Fuji Home

- **Switch debugging off when you're done.** Network debugging (ADB on port 5555) lets anyone on your Wi-Fi
  control the TV. Turn it off in Developer options, and check with CleanYourTV's `tools/adb-closed.py`.
- Only build from this repo's source. There are no official prebuilt APKs.
