# Finance Tracker

Native Android finance tracker built with Java, Android SDK views, and local SQLite storage.

## What It Does

- Tracks income and expenses date-wise with title, amount, category, account, and notes.
- Splits income and expenses into separate add flows with title suggestions from previous entries.
- Shows the last five years of income/expense entries grouped by year, month, and day.
- Supports repeating income/expense metadata with day, week, month, and year frequencies.
- Uploads bill files using Android's document picker and stores persistable document URIs.
- Flags expenses for tax and asks for the tax amount plus tax title.
- Starts with onboarding for your first name, first country, and currency.
- Supports any number of countries, each with a country name and a selected ISO currency.
- Switches country workspaces with a Gmail-style profile avatar: tap to see all country accounts, swipe the avatar to toggle.
- Uses a fixed bottom navigation bar for the main app sections.
- Keeps a monthly exchange-rate table for country-to-country transfers.
- Uses calendar pickers for date entry fields.
- Includes separate income and expense categories with icons, plus custom categories for each country.
- Manages accounts per country, with separate balances and selectable combined balance totals.
- Tracks Fixed Deposits, Recurring Deposits, and Market Linked Funds with opening date, maturity date, present value, maturity value, capital-gains updates, and maturity handling.
- Tracks goals and monthly budgets, including budgets across combinations of expense categories.
- Adds a hamburger menu for income, expense, tax-flagged, and investment reports by period.

## Open And Run

1. Open this folder in Android Studio.
2. Install Android SDK 35 or newer if prompted.
3. Let Android Studio sync Gradle.
4. Run the `app` configuration on an emulator or Android device.

The project has been built successfully from the command line with `./gradlew :app:assembleDebug`. Android Studio with its bundled JDK and SDK 35 or newer should sync and run it.
