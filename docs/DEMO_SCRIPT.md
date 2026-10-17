# Demo video script (about 5 minutes)

A short, calm video is better than a long one. Speak slowly. It is fine to read from this script.

## Before you record (10 minutes)

- [ ] Open the live site **3 minutes before**, so it is awake (free hosting sleeps).
- [ ] Use fresh accounts for the demo (for example `demo.alice@test.com` and `demo.bob@test.com`).
- [ ] Open these tabs: the GitHub repo (README), the live app, Swagger, and a **private window** for the second user.
- [ ] Open IntelliJ with `TransferService.java` and `TransferConcurrencyTest.java` ready.
- [ ] Browser zoom 125%, close notifications, hide bookmarks, a clean desktop.
- [ ] Recording: **Win + G** (Xbox Game Bar) or **OBS Studio**. Record at 1080p, with your voice.

## The script

| Time | Show | Say (in your own words) |
|---|---|---|
| 0:00 | README top | "Hi, I am Deepanshu. This is SecureBank, a banking backend I built with Spring Boot and MySQL. All money is demo money. The main goal was to keep money correct even when many requests arrive at the same time." |
| 0:20 | README: architecture diagram | "The code has three layers: controllers, services with the business rules, and repositories with plain SQL through JdbcTemplate. Login uses JWT tokens and BCrypt." |
| 0:45 | Live app: register, login, dashboard | "A customer registers and gets an account with a unique 12-digit number." |
| 1:15 | Deposit, withdraw, history | "I deposit and withdraw. The history is newest first, with filters and paging. If I try to withdraw more than my balance, the app refuses and nothing changes." |
| 1:50 | Private window: second user. Transfer | "Now I transfer money to the second user. The transfer is all or nothing, and the other account number is masked in the history." |
| 2:30 | Admin panel: search, **Freeze** | "An admin can search accounts and freeze one. Every admin action goes into an audit log." |
| 2:50 | Frozen user tries to deposit | "The frozen customer is blocked. Now I unfreeze it, and it works again." |
| 3:15 | 3 wrong passwords, LOCKED badge, Unlock | "After 3 wrong passwords the user is locked for 15 minutes. The admin can unlock." |
| 3:40 | Swagger | "All APIs are documented with Swagger, and you can try them with a token." |
| 4:00 | IntelliJ: `TransferService` lock order | "The important part: for a transfer I lock both accounts, always the lower id first. This prevents a deadlock when two people send money to each other at the same time." |
| 4:25 | Run `TransferConcurrencyTest`, show it green | "This test sends 50 parallel transfers against a real MySQL. Exactly the right number succeed, no money is created, and there is no deadlock." |
| 4:50 | GitHub: green CI badge | "There are 57 automated tests, and GitHub Actions runs them on every push. It runs in Docker on Render. The link and the code are in the description. Thank you for watching." |

## Tips

- If you make a mistake, pause two seconds and say the sentence again. You can cut it later.
- Do not show passwords, the Aiven page, or the Render environment variables.
- Put these in the video description: the live link, the GitHub link, the tech stack, and "demo project, demo money".
- Keep a 60-second version too (register, transfer, admin freeze) for LinkedIn.
