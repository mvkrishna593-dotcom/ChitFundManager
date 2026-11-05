# Chit Fund Manager (Android)

Native Android app (Kotlin + Jetpack Compose + Room). All data stays on the phone; no cloud, no login.

## Open and run
1. Install Android Studio (latest stable).
2. File > Open > select this `ChitFund` folder. Let Gradle sync (it creates the Gradle wrapper if missing).
3. Connect a phone with USB debugging, or start an emulator, then press Run.

## What it does
**I run a chit (organiser)**
- Members: add / remove participants (name, phone).
- Auctions: enter each month's bid and winner. Shows each member's payment, the winner's take-home and your commission.
- Payments: tick who has paid for each month; see collected and pending amounts.

**I'm a member (participant)**
- Enter the chit, the commission rate, each month's bid and the month you win.
- Shows the yearly interest you earn or pay, totals in rupees, and a bar chart of the rate for every possible winning month.

## Formulas
- Each member pays = monthly amount − (bid ÷ members)
- Commission = commission % × chit value, deducted from the winner
- Winner receives = chit value − bid − commission
- Yearly rate = IRR of the member's monthly cash flows, compounded to 12 months
  (green = interest earned, red = interest paid for taking the money early)
- Months with no bid entered reuse the last known bid, and the app marks them as estimates.
