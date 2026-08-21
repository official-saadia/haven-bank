const API_BASE = import.meta.env.VITE_API_BASE;

/** A single field-level validation problem from the backend's problem-details `errors` array. */
export interface FieldError {
  field: string;
  message: string;
}

/** A problem-details error thrown for any non-2xx API response (mirrors the backend RFC 7807 body). */
export class ApiError extends Error {
  readonly status: number;
  readonly detail?: string;
  readonly fieldErrors: FieldError[];
  constructor(status: number, message: string, detail?: string, fieldErrors: FieldError[] = []) {
    super(message);
    this.status = status;
    this.detail = detail;
    this.fieldErrors = fieldErrors;
  }

  /** A readable summary: the field messages if the backend gave any, else the top-level detail. */
  get userMessage(): string {
    if (this.fieldErrors.length > 0) {
      return this.fieldErrors.map((e) => e.message).join(" ");
    }
    return this.detail ?? this.message;
  }

  /** The message for one field, or undefined if that field has no error. */
  fieldError(field: string): string | undefined {
    return this.fieldErrors.find((e) => e.field === field)?.message;
  }

  /** The form-level message: the top-level detail, but only when there are no field-specific
      errors to show under the inputs (so it isn't duplicated). Undefined otherwise. */
  get generalMessage(): string | undefined {
    return this.fieldErrors.length > 0 ? undefined : (this.detail ?? this.message);
  }
}

/** Pulls field errors out of an unknown caught value into a plain lookup for the forms. */
export function fieldErrorsOf(err: unknown): Record<string, string> {
  if (err instanceof ApiError) {
    return Object.fromEntries(err.fieldErrors.map((e) => [e.field, e.message]));
  }
  return {};
}

/** The message to show in a form's general error slot, or null if the errors are all field-level. */
export function generalMessageOf(err: unknown, fallback: string): string | null {
  if (err instanceof ApiError) return err.generalMessage ?? null;
  return fallback;
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  token?: string;
  headers?: Record<string, string>;
}

/**
 * Thin fetch wrapper. Attaches a bearer token when supplied, sends/receives JSON, and turns error
 * responses into {@link ApiError} using the server's problem-details {@code detail} where present.
 */
export async function apiRequest<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const headers = new Headers({ "Content-Type": "application/json", ...(options.headers ?? {}) });
  if (options.token) {
    headers.set("Authorization", `Bearer ${options.token}`);
  }

  const response = await fetch(`${API_BASE}${path}`, {
    method: options.method ?? "GET",
    headers,
    body: options.body !== undefined ? JSON.stringify(options.body) : undefined,
  });

  if (!response.ok) {
    let detail: string | undefined;
    let fieldErrors: FieldError[] = [];
    try {
      const problem = await response.json();
      detail = problem?.detail;
      // The backend attaches per-field messages under `errors` for bean-validation failures
      // (see GlobalExceptionHandler). Carry them so callers can show which field was wrong.
      if (Array.isArray(problem?.errors)) {
        fieldErrors = problem.errors
          .filter((e: unknown): e is FieldError =>
            typeof e === "object" && e !== null && "message" in e)
          .map((e: { field?: string; message: string }) => ({ field: e.field ?? "", message: e.message }));
      }
    } catch {
      // non-JSON error body; fall back to status text
    }
    throw new ApiError(response.status, detail ?? response.statusText, detail, fieldErrors);
  }

  // 202 (register, forgot-password) and 204 (verify) both come back with no body, so parse
  // defensively rather than special-casing status codes.
  const text = await response.text();
  return (text ? (JSON.parse(text) as T) : (undefined as T));
}

export interface UserProfile {
  id: string;
  email: string;
  fullName: string;
  status: string;
  emailVerified: boolean;
  roles: string[];
}


export interface AccountResponse {
  id: string;
  accountNumber: string;
  type: string;
  currency: string;
  status: string;
  balance: number;
}

export interface TransactionResponse {
  id: string;
  reference: string;
  type: string;
  status: string;
  amount: number;
  fee: number;
  direction: "DEBIT" | "CREDIT";
  balanceAfter: number | null;
  createdAt: string;
}

export interface BeneficiaryResponse {
  id: string;
  name: string;
  nickname: string | null;
  accountNumber: string;
  createdAt: string;
}

export interface Page<T> {
  content: T[];
  number: number;
  totalPages: number;
  totalElements: number;
}

export interface PreferenceView {
  type: string;
  enabled: boolean;
}

/** Generate an idempotency key for a mutating money request. */
export function idempotencyKey(): string {
  return crypto.randomUUID();
}


export interface RoleResponse { id: string; name: string; description: string | null; permissions: string[]; }
export interface PermissionResponse { id: string; name: string; description: string | null; }
export interface AdminUser {
  id: string; email: string; fullName: string; status: string; emailVerified: boolean; roles: string[];
}
export interface AuditRecord {
  id: string; actorUserId: string | null; actor: string | null; action: string; targetType: string | null;
  targetId: string | null; outcome: string; detail: string | null; sourceIp: string | null;
  userAgent: string | null; correlationId: string | null; createdAt: string;
}
export interface PolicyResponse {
  id: string; policyKey: string; scope: string; value: number; effectiveFrom: string; effectiveTo: string | null;
}
export interface FeeScheduleResponse {
  id: string; appliesTo: string; tierMin: number | null; tierMax: number | null;
  feeFlat: number; feePercent: number; effectiveFrom: string; effectiveTo: string | null;
}