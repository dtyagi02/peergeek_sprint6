PeerGeek 2.0 sample data bundle
================================

Based on the uploaded db.sql schema. CSVs use UTF-8 with BOM for MySQL Workbench compatibility.

Counts:
- Users: 15 total intended (existing user ID 1 preserved; 14 new users in 01_users_new_only.csv)
- 30 courses
- 5 topics per course (150)
- 5 modules per course (150)
- 3 lessons per module (450)
- 450 JSON lesson content records
- 3 external-link resources per course (90); object_key is blank and no S3 object is fabricated
- 2 assignments per course (60): one question-based + one project
- 5 questions per question-based assignment (150)
- 3–6 enrollments per user (including existing user ID 1)
- Mixed lesson/course progress and submission states
- Readiness runs/issues intentionally omitted per request

Instructor-mode sample accounts:
- Existing user ID 1 is retained as-is.
- New IDs 2–8 have last_mode=INSTRUCTOR; IDs 9–15 have last_mode=STUDENT.
- All new users have role=USER. last_mode is only the preferred UI mode and does NOT grant instructor authorization by itself.
- Shared sample password: password
- Password hash is a BCrypt test hash. Use only in local disposable test data.

IMPORTANT:
1. Do not import categories.csv: existing categories IDs 1–10 are preserved and referenced.
2. Do not import users.csv into a database that already has any of the sample emails. This bundle assumes user ID 1 is the only existing user and new IDs 2–15 are free. If the earlier sample-users CSV was already imported, check for duplicates first.
3. Explicit IDs are used to keep foreign keys consistent. Import only into a disposable/test database or verify IDs are free before import.
4. Recommended order:
   01_users_new_only.csv
   02_courses.csv
   03_course_topics.csv
   04_course_modules.csv
   05_lessons.csv
   06_lesson_contents.csv
   07_enrollments.csv
   08_lesson_progress.csv
   09_course_progress.csv
   10_resources_external_links.csv
   11_assignments.csv
   12_assignment_questions.csv
   13_submissions.csv
   14_submission_answers.csv
   15_course_public_stats.csv
   16_public_course_index.csv
5. Do not import these files if your actual DB has different IDs or more users/courses than the uploaded dump. The generated foreign keys assume this dump's initial state (user ID 1; category IDs 1–10; no courses).
6. The external resource links are documentation landing pages (not uploaded S3 objects). If your app validates external URLs or requires different resource type/purpose/status enum values, verify those conventions before import.
7. Derived discovery/stat tables may be rebuilt by the application. Import them only if your app's indexing/statistics logic permits direct seeding.
