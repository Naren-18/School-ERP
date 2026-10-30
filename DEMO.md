# Demo click path

A 20–25 minute walk-through for a school owner or principal. Bring a laptop and your
phone. Everything below uses the fictional demo schools; never show real data.

## The day before

1. **Re-seed the demo** (the data is dated relative to the seeding day, so "today"
   must be today). On the server, a fresh database plus the seeder; see
   `deploy/demo/README.md`.
2. **WhatsApp (if connected):** pick two students of `vjhs` Class 5 – A. Edit each:
   set the primary contact's mobile to one of your Meta test numbers and tick
   *Parent agrees to receive WhatsApp messages*. Check that your phone has WhatsApp
   open and the templates are approved.
3. **Razorpay (if keys set):** keep Razorpay's test UPI id / test card handy.
4. Sign in once on the laptop and on your phone to check the password.

Logins (every demo school): `principal`, `admin`, `accountant`, `teacher1`,
`teacher2`; password = the demo password you chose.

## The walk-through

| # | Where | Do | Say |
|---|---|---|---|
| 1 | Login, school code `vjhs` | Type the code: the school's logo and colour appear before signing in | "Your school gets its own look and its own logins. No other school can ever see your data." |
| 2 | Dashboard (principal) | Point at the greeting band, the tiles, the donuts; hover a chart | "This is your school this morning: who is present, what was collected, what is overdue." |
| 3 | Students → Admit student | Fill a short admission, tick WhatsApp consent, save | "Aadhaar is encrypted; caste and religion only the principal and admin see." Mention **Import CSV** for moving existing records. |
| 4 | **Phone**, `teacher1` → Mark attendance | Mark two students A, save | "A teacher marks 40 students in under a minute on her own phone." |
| 5 | Same screen | **Tell parents of absent students** → Send | Show the WhatsApp arriving on your phone. "Parents know within minutes that the child didn't reach school." |
| 6 | Laptop, `accountant` → Collect fee | Search a student, tick a due, **Collect**, **Print receipt** | Receipt number with no gaps, amount in words, school logo. If WhatsApp is set up, the payment confirmation arrives. |
| 7 | Collect fee → **Pay online** | Pay with Razorpay's test UPI | "Parents can pay by UPI at the counter; the receipt is made only once the payment is confirmed." |
| 8 | Fees → Reports → Dues | Tick a few students → **Remind on WhatsApp** | "One click instead of fifty phone calls." Show **WhatsApp messages** (sidebar) as proof of who was told. |
| 9 | Exams → Results & report cards | FA1 · Class 5 – A, **All report cards** (PDF), then **Tell parents** | CBSE / state grading, ranks, co-scholastic grades. |
| 10 | Student → Certificates → Issue → Transfer certificate | Pick a student with dues: the warning shows | "You never issue a TC with fees pending by mistake." Issue a bonafide instead. |
| 11 | Sign out, school code `lvps` | Sign in as principal | "A completely different school on the same system: CBSE, English medium, five-day week, its own fees." |
| 12 | Setup → Modules & preferences | Show module and WhatsApp switches | "You decide what is on, including which messages cost money." |

## If something goes wrong

- **WhatsApp not connected / message failed:** open **WhatsApp messages** and show the
  log with the reason; carry on. The flow still works without the keys (messages are
  written to the log).
- **Online payment fails:** close the window ("no receipt was made") and collect in cash.
- **Locked out** after wrong passwords: use `admin` instead of `principal`.

## Questions schools ask (short answers)

- **Price:** per student per year; WhatsApp messages included up to a limit, or at cost.
- **Our data:** belongs to the school, can be exported, is hosted on a server in India
  (Bangalore region for production), and Aadhaar is encrypted.
- **Moving from our current software:** CSV import of students and parents.
- **Parent app / online payment by parents / SMS:** on the roadmap.
