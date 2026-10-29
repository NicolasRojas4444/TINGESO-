# Frontend-facing API updates

## Public registration catalog

`GET /api/public/degree-programs` requires no token and returns only `id`, `code`
and `name`. It includes ACTIVE programs with exactly one CURRENT study plan.
No other academic or administrative endpoint becomes public.

`SELF_REGISTRATION_ENABLED` controls all public registration.
`TEACHER_SELF_REGISTRATION_ENABLED` additionally controls public TEACHER registration.
The existing `.env` enables both for the requested development workflow; the example
disables teacher registration. ADMIN cannot be requested through registration.
Neither flag grants roles to an existing account.

## Account lifecycle

Deleting an academic profile still requires that it has no protected academic
activity. After deletion, if the identity has no other student/teacher profile or
period-closing reference and has no effective ADMIN realm role:

- Its Keycloak account is disabled, not deleted.
- Its local user identity, RUN, email and Keycloak subject are retained.
- Its local access flag rejects previously issued JWTs at academic service access.
- Suspension also persists `tokensValidAfter`: the next whole UTC second. JWTs
  without `iat`, or with `iat` before that cutoff, receive HTTP 401 even after
  recovery. Recovery never clears the cutoff. Because `iat` has second precision,
  a login in the suspension second may require obtaining another token after the
  cutoff; tokens issued at or after it are accepted when access is active.
- Public registration cannot reclaim that RUN, email or account.

Accounts with another profile, historical reference or ADMIN role are not
automatically disabled. This policy applies to future deletions; it does not
silently modify accounts left over from previous versions.

ADMIN recovery endpoints:

- `GET /api/users/suspended`: retained identities needing recovery.
- `POST /api/users/{userId}/restore-profile`: recreate a profile for that same
  identity, then re-enable access. No new password or account is created.

Student recovery body:

```json
{"role":"STUDENT","degreeProgramId":1}
```

Teacher recovery body:

```json
{"role":"TEACHER","professionalTitle":"Engineer","academicDegree":"Bachelor"}
```

Recovery requires the requested academic role already to exist on that account
in Keycloak and refuses ADMIN accounts. It never infers ownership from an email.
Student recovery uses the selected ACTIVE program's CURRENT plan, starts REGULAR
and does not create enrollment. Teacher recovery starts ACTIVE. The original
profile was eligible for physical deletion, so it had no protected history to restore.

Administrative student/teacher creation validates that the submitted Keycloak
subject exists, is enabled and already has the requested effective realm role
before saving any identity/profile. Missing accounts return 404, disabled accounts
409 and missing roles 400. Public registration bypasses this check only for its
newly created disabled account, which is enabled after profile persistence.

Active retained identities use separate ADMIN-only endpoints:

- `GET /api/users/link-candidates?role=TEACHER` (or `STUDENT`) lists local active
  identities missing that profile. This is a local shortlist, not proof of a valid
  Keycloak account/role; those are checked on POST.
- `POST /api/users/{userId}/link-profile` accepts the same role-specific bodies as
  recovery. It reuses that exact local identity and subject; suspended identities
  must use recovery instead, and duplicate academic profiles are rejected.

Linking permits ADMIN identities and identities with another profile or closing
references, but only if Keycloak is enabled and already has the requested academic
role. It never assigns roles, toggles access, resets the JWT cutoff, or identifies
accounts by email. Student/teacher profile initialization matches recovery.

Updating a student/teacher's name or email synchronizes that metadata with
Keycloak. Changing email clears its verification; passwords and username are not
changed. A Keycloak conflict/unavailability aborts the database operation.

Keycloak and PostgreSQL are not one distributed transaction. On database rollback,
the backend attempts to restore the previous Keycloak metadata/access state. If
that recovery fails, logs include the subject and require manual reconciliation.
There is no guaranteed retry queue. Do not automatically re-register or reuse an
account to work around such a failure. Review both systems as ADMIN, reconcile
metadata/access and then retry recovery. No passwords are stored locally.

## Section availability

All section responses now include `capacity`, `registeredCount` (ACTIVE
registrations only) and `availableSeats`. The latter never displays a negative
value. These are server-calculated snapshots; the server still validates seats
inside the registration transaction. Submitted count fields are ignored.

## Replacing registrations

Existing POST additions and DELETE cancellations remain available.

`GET /api/registrations/replacement-options?studentId=1&periodId=1` includes current
sections and eligible alternatives, even when they overlap the current schedule.
This is intentional: only the final selection must have no overlap. A student
can read only their own options; ADMIN may inspect them.

`PUT /api/registrations?studentId=1&periodId=1` accepts the complete desired selection:

```json
{"sectionIds":[2,3,4]}
```

Only the owning STUDENT may replace it. The transaction preserves the existing
program/period/student locking strategy and validates state, enrollment, plan,
period, prerequisites, available seats, course load, duplicate courses, earlier
semesters and the desired schedule before making any changes. A retained section
may be full because the student already occupies its seat. Unchanged registrations
keep dates and grades; removed ones are cancelled and lose their provisional grade.
Cancelled rows are reused on reactivation. An invalid replacement changes nothing.

DELETE cancellation validates the resulting minimum load and earlier-semester
priority before modifying registrations or provisional grades. It does not
revalidate unrelated existing prerequisites or schedule conflicts just to remove
a course. POST, PUT and DELETE share the same load/semester-priority rules.

## Incomplete closing response

The closing endpoint returns HTTP 409 with a ProblemDetail extension:

```json
{
  "status":409,
  "detail":"Final grades are missing",
  "code":"FINAL_GRADES_MISSING",
  "sectionIds":[4,8]
}
```

Use `code` and `sectionIds`, not text parsing, to render the incomplete sections.
