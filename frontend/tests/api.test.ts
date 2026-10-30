import { describe, expect, it } from "vitest";
import { AxiosError } from "axios";
import { ApiError, errorMessage, normalizeError } from "../app/lib/api";

function failure(status: number, data?: unknown) {
  return new AxiosError(
    "request failed",
    "ERR_BAD_REQUEST",
    undefined,
    undefined,
    {
      status,
      data,
      statusText: "",
      headers: {},
      config: { headers: {} },
    } as never,
  );
}
describe("API failures", () => {
  it.each([401, 403, 404, 409, 503])(
    "provides a readable fallback for HTTP %s",
    (status) => {
      const error = normalizeError(failure(status));
      expect(error.status).toBe(status);
      expect(error.message).not.toBe("request failed");
      expect(error.message.length).toBeGreaterThan(10);
    },
  );
  it("retains structured incomplete-closing identifiers", () => {
    const error = normalizeError(
      failure(409, {
        detail: "Missing grades",
        code: "FINAL_GRADES_MISSING",
        sectionIds: [3, 8],
      }),
    );
    expect(error.problem?.sectionIds).toEqual([3, 8]);
    expect(error.problem?.code).toBe("FINAL_GRADES_MISSING");
  });
  it("uses the backend validation message without rendering HTML", () => {
    expect(
      normalizeError(failure(400, { detail: "Prerequisites missing" })).message,
    ).toBe("Prerequisites missing");
  });
  it("handles network failures and unknown errors", () => {
    expect(normalizeError(new AxiosError("network down")).message).toContain(
      "conexión",
    );
    expect(errorMessage(null)).toContain("operación");
    expect(normalizeError(new Error("validation")).message).toBe("validation");
    const original = new ApiError("expired", 401);
    expect(normalizeError(original)).toBe(original);
  });
});
