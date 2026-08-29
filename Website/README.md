# VYBE Website

A multi-page static website built to match VYBE's rounded Material 3 visual language.

## Pages

- `index.html` — Home / product overview
- `features.html` — Full feature set + real app screenshots + technical snapshot
- `download.html` — Latest GitHub Release / ARM64 APK download page
- `help.html` — Searchable help and FAQ
- `styles.css` — Shared Material 3-inspired theme and responsive motion system
- `script.js` — theme switching, ripple states, scroll reveal, navigation, mobile menu, release API hydration, FAQ search and lightweight motion

## Live data

The download page calls:

`https://api.github.com/repos/anshdeepofficial/VYBE/releases/latest`

It prefers an ARM64 APK asset and falls back to the latest GitHub Releases page if the API cannot be reached.

## Hosting

The site has no build step. Upload the files as-is to GitHub Pages, Cloudflare Pages, Netlify, Vercel static hosting, or another static web host.

## Design source

The palette follows VYBE's repository Material 3 theme values, including purple / pink / orange accents, rounded 8 / 16 / 24 dp-style radii, light/dark styling and real screenshots from `docs/screenshots/`.
