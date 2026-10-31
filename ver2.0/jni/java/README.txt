FileQueue Java Map Generator / Audit
====================================

Requirements: JDK 17+ (no external JARs)

Files:
  FieldMap.java        - common 6-column map loader, validation
  JsonGenerator.java   - JSONL generator
  JsonAudit.java       - JSONL validator
  *.map                - SMS/LMS/MMS/ACK/RESULT and random sample
  Makefile

Build:
  make
  # or: javac -encoding UTF-8 FieldMap.java JsonGenerator.java JsonAudit.java

Generate:
  java JsonGenerator sms.map 100 > sms.jsonl
  java JsonGenerator ack.map 100 > ack.jsonl

Audit:
  java JsonAudit sms.map sms.jsonl
  cat sms.jsonl | java JsonAudit sms.map -

Test:
  make sample

Map columns:
  NAME | LENGTH_TYPE | LENGTH | REQUIRED | VALIDATION | GENERATOR

Generator rules:
  FIXED:text
  SEQ:START,STEP,WIDTH
  SEQ:PREFIX,START,WIDTH
  NOW14
  RANDOM_ENUM   (requires ENUM validation)
  RANDOM_NUM:TOTAL_LENGTH[,PREFIX]
  RANDOM_ALPHA:TOTAL_LENGTH[,PREFIX]
  RANDOM_ALNUM:TOTAL_LENGTH[,PREFIX]
  EMPTY

RANDOM TOTAL_LENGTH includes PREFIX; e.g.
  PHONE       | EXACT | 11 | Y | ANY | RANDOM_NUM:11,010
  HISTORY_KEY | EXACT | 16 | Y | ANY | RANDOM_ALNUM:16,HIST_

Differences / limitations:
- JSONL (one flat JSON object per line). All values are strings.
- JsonAudit rejects duplicate keys and non-string values, nested JSON/arrays.
- Unknown extra fields are allowed (same as previous C audit).
- Optional fields are generated, not omitted (same as previous C generator).
- UTF-8 bytes are counted, not Java String.length().
- For RANDOM, non-ASCII PREFIX byte count is used; suffix is ASCII.
- Generator requires 6 columns. Audit also requires 6 columns in this version.
- Sequence starts at 1 each program run; random output is not guaranteed unique.
- Java SecureRandom algorithm differs from C xorshift; outputs are not bit-for-bit identical.
- DATETIME14 in Java checks calendar-valid dates; generator's C version only checks 14 digits.
- C generator limits map to 128 fields and value to 2047 bytes; Java uses same bounds.
- Java audit does not accept nested JSON or non-string values; this matches field data contract.

Exit codes:
  0 success
  1 validation/generation failure
  2 usage/map/file error (audit)

Verified:
  Compiled on OpenJDK 21 with javac; generated and audited 2 records each
  for sms.map, lms.map, mms.map, ack.map, result.map.
