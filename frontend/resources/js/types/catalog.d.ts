/**
 * Shapes of the course catalogue, its filters and the security audit log as the backend sends them to React.
 * The pages are plain JavaScript; these declarations document the props (and type-check them in an editor that
 * reads .d.ts files). Field names are the JSON keys exactly as the Java controllers write them.
 */

// ----------------------------------------------------------------------------------------------- taxonomy

/** One item of a filter list (mediums, genres, skills, age groups) - Admin > Catalog, the Tag & Hierarchy manager. */
export interface TaxonomyItem {
    id: number;
    name: string;
    slug: string;
    /** Parent in the same list (Landscape > Seascape); null at the top level. */
    parent_id: number | null;
    /** Switched on for the site. */
    status: boolean;
    /** Archived: hidden from the filters and picklists, kept on courses that use it. */
    archived: boolean;
    /** Courses (or posts) using it. */
    usage: number;
    photo_url?: string | null;
}

export type CourseFormat = 'course' | 'workshop';

// ----------------------------------------------------------------------------------------------- courses

export interface CoursePrice {
    price_inr: number | string;
    price_usd: number | string;
}

/** A catalogue row (CourseService.withRelations + withStats). */
export interface Course {
    id: number;
    title: string;
    slug: string;
    sub_title?: string | null;
    introduction?: string | null;
    /** 1 = live course, 2 = workshop. */
    course_type_id: 1 | 2;
    medium_id: number | null;
    medium?: { id: number; name: string } | null;
    skill_id: number | null;
    skill?: { id: number; name: string } | null;
    /** Comma-separated genre ids, e.g. "2,4". */
    genre_id: string | null;
    /** The name of an age group, e.g. "Junior - 12 to 15 yrs". */
    age_group: string | null;
    teacher?: { id: number; name: string } | null;
    /** "05 Dec, 2026". */
    course_start_date: string | null;
    prices: CoursePrice[];
    photos?: { photo_name: string }[];
    /** Distinct paying students. */
    students: number;
    reviews: number;
    /** % of reviews rated 4 or 5; null without reviews. */
    positive: number | null;
    /** Seat cap (live courses / workshops); null = unlimited. */
    seat_limit: number | null;
    seats_left?: number | null;
    sold_out?: boolean;
}

/** Seat state for one visitor (SeatService.forStudent). */
export interface SeatState {
    limit: number | null;
    taken: number;
    left: number | null;
    soldOut: boolean;
    /** The visitor holds a seat (paying now, or a priority link). */
    mine?: boolean;
    waitlist?: 'WAITING' | 'INVITED' | 'JOINED' | 'EXPIRED' | 'LEFT' | null;
    /** Place in the waitlist queue, 1 = next. */
    position?: number;
}

// ----------------------------------------------------------------------------------------------- filters

export type SortKey = 'popular' | 'rated' | 'newest' | 'price-asc' | 'price-desc';

/**
 * The learner's filter state on /courses (Pages/Courses.js). Mirrored into the address bar by catalogQuery.js:
 * /courses?type=all&medium=2&age=Junior%20-%2012%20to%2015%20yrs&q=sky&sort=newest
 */
export interface FilterState {
    /** Free text; matched forgivingly (accents, case, small typos) against title, subtitle, medium, teacher, genres. */
    q: string;
    /** Visible pills. Picking a parent medium also matches its children. */
    medium: Set<string>;
    age: Set<string>;
    /** In the "More filters" drawer. */
    type: Set<CourseFormat>;
    skill: Set<string>;
    genre: Set<string>;
    sort: SortKey;
}

/** One removable chip under the filter bar. */
export interface ActiveFilterChip {
    key: string;
    kind: 'Search' | 'Medium' | 'Age' | 'Format' | 'Level' | 'Subject';
    label: string;
    /** For the zero-results view: how many courses removing just this chip would show. */
    n?: number;
}

// ----------------------------------------------------------------------------------------------- audit log

export type AuditActionType = 'CREATE' | 'UPDATE' | 'DELETE' | 'AUTH' | 'EXPORT' | 'DENIED';
export type AuditActorType = 'admin' | 'student' | 'system' | 'anonymous';
export type AuditOutcome = 'OK' | 'FAIL' | 'DENIED';

/** One row of audit_logs (security/AuditLog.java); the list omits before_json / after_json, the drawer has them. */
export interface AuditLogEntry {
    id: number;
    /** "2026-11-06 14:30:12" from the API; shown as ISO 8601 (2026-11-06T14:30:12). */
    occurred_at: string;
    actor_type: AuditActorType;
    /** The user id (admins.id or users.id depending on actor_type). */
    actor_id: number | null;
    actor_email: string | null;
    actor_role: 'admin' | 'teacher' | 'moderator' | 'student' | 'system' | null;
    action_type: AuditActionType;
    /** What happened, e.g. "course updated", "admin_login", "session_revoked". */
    action: string;
    /** Target resource, e.g. resource_type "course" + resource_id "110" = Course #110. */
    resource_type: string | null;
    resource_id: string | null;
    summary: string | null;
    /** Sorted JSON snapshots (secrets masked as "••••••") - UPDATE rows only. */
    before_json?: string | null;
    after_json?: string | null;
    ip: string | null;
    country: string | null;
    city: string | null;
    user_agent?: string | null;
    http_method: string | null;
    path: string | null;
    outcome: AuditOutcome;
    /** Hash chain (SHA-256 hex): prev_hash = row_hash of the entry before. */
    prev_hash?: string;
    row_hash?: string;
    /** List view only: the entry has a before/after diff. */
    has_diff?: 0 | 1;
}

/** Query string of /admin/security/audit (the audit filter panel). */
export interface AuditFilter {
    q?: string;
    /** Id or part of an e-mail. */
    actor?: string;
    role?: AuditLogEntry['actor_role'] | 'anonymous';
    /** Comma-separated action types. */
    types?: string;
    outcome?: AuditOutcome;
    resource?: string;
    window?: '1h' | '24h' | '7d' | '30d';
    /** datetime-local: 2026-11-06T09:00 */
    from?: string;
    to?: string;
}

/** One line of the side-by-side diff (Pages/Admin/SecurityAudit.js diffRows). */
export interface DiffRow {
    key: string;
    before?: string;
    after?: string;
    kind: 'added' | 'removed' | 'changed' | 'same';
}

/** POST /admin/api/security/audit/verify. */
export interface ChainVerification {
    ok: boolean;
    checked: number;
    brokenAt: number | null;
    problem: string | null;
}
