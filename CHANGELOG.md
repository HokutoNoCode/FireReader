# Changelog

All notable changes to FireReader. Format inspired by [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).

## [3.2.0] - 2026-11-19
### Added
- Long-press a book for a new menu: **Read**, **Open with ReadEra** (PDF), **Mark as unread**, **Rename** and **Delete**.
- **Rename**: edit title and author; the file is renamed to `Author - Title` and reading progress, margins and cover are kept.
- **Delete**: asks for confirmation, then removes the file from the tablet permanently, along with its saved data.
### Documentation
- English is now the main language of the repository (README, guide, architecture, contributing).
  Italian versions remain available: `README.it.md`, `docs/GUIDA.md`, `docs/ARCHITETTURA.md`.

## [3.1.0] - 2026-11-19
### Added
- The screen stays on while you read and turns itself off after 15 minutes without a page turn.
### Documentation
- Guide: how to remove the lock screen (black on this ROM) and ambient display.

## [3.0.0] - 2026-11-19
### Added
- **Built-in PDF reader with automatic margin cropping**: text box computed separately for even and odd pages
  and merged with each page's content, text at full width, the file is never modified.
- Page turns by tap, swipe or volume keys; menu with a slider to jump to any page and a per-book
  *cropped / original margins* option.
- Real progress on the home screen: *"Page 34 of 190 · 18%"*.
- Long-press a PDF: *Open with ReadEra*.

## [2.0.0] - 2026-11-18
### Added
- Search by title and author, library sorting (recent, title, author).
- *Recently read* row.
- **Comfort** tab: brightness, adjustable warm light, grayscale, screen timeout.
- **Receive books over Wi-Fi** from a PC or phone browser.

## [1.0.0] - 2026-11-18
### Added
- First version: cover-based home with *Continue reading* and a library grid.
- Fastboot driver for Windows, VM for the bootrom step, full guide.
