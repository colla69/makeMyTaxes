# Official tax forms 2025

The Formular-Management-System of the federal finance administration publishes these forms. The
official copies are at https://www.formulare-bfinv.de, under Steuerformulare > Einkommensteuer >
Einkommensteuer 2025. That site needs a browser session, so the worker read the forms on
10.11.2026 from unchanged copies of the FMS files, which the table names.

The PDF files are not in this folder yet. The commit hook blocks a PDF until its SHA-256 is in
`scripts/privacy-scan-approved.sha256`, and the owner approves changes to `scripts/`. The owner
adds the files from formulare-bfinv.de.

| Form | Mark | Copy that was read |
|---|---|---|
| 005 - Hauptvordruck ESt 1 A (2025) | 2025ESt1A011NET and 2025ESt1A012NET, September 2025 | https://www.schwaebisch-hall.de/content/dam/dambsh/bsh/foerderungen/bausparförderung/arbeitnehmersparzulage/ESt_1_A_2025.pdf |
| Anlage N (2025) | 2025AnlN031NET to 2025AnlN034NET, Oktober 2025 | https://www.buhl.de, with an advert page before the form |
| 083 - Anlage Vorsorgeaufwand (2025) | 2025AnlVor241NET, Oktober 2025 | https://www.buhl.de/steuer/wp-content/uploads/sites/31/2026/02/Anlage_Vorsorgeaufwand_2025.pdf, with an advert page before the form |

## Lines that the wage tax certificate fills

The certificate is the sample "Ausdruck der elektronischen Lohnsteuerbescheinigung für 2025",
print mark 6.24, from the notice of the BMF of 24.09.2024, BStBl I 2024, 1262.

- Anlage N line 51 takes Nr. 17 and Nr. 18 of the certificate in two boxes: 290 for amounts
  that the employer paid tax-free, and 295 for amounts that the employer taxed at a flat rate.
  The two lines are not added.
- Anlage N line 20 takes Nr. 15 of the certificate. Nr. 15a, the short-time allowance within
  Nr. 15, is not taken.
- Anlage Vorsorgeaufwand line 4 takes the employee share of Nr. 23 a/b in one box (300).
- Anlage Vorsorgeaufwand line 9 takes the employer share of Nr. 22 a/b in one box (304).

## Changes from 2022 that the app follows

- Anlage N line 58 (box 335) counts the days of § 4(5) no. 6c EStG: work mostly at home, with
  no visit to the first place of work. Line 59 (box 336) is for a person with no other place of
  work at all. The app has a field for line 58 only.
- Anlage N lines 31 to 33 split the distance by the means of transport. The app shows its
  transport field on line 31.
