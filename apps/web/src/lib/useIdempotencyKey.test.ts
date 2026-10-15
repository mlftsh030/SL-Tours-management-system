import { renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";
import { useIdempotencyKey } from "./useIdempotencyKey";

describe("submission idempotency key", () => {
  it("keeps the key for an identical retry and changes it once the content was edited", () => {
    const { result } = renderHook(() => useIdempotencyKey());
    const first = result.current.keyFor({ email: "a@example.test", message: "Hello" });
    expect(result.current.keyFor({ email: "a@example.test", message: "Hello" })).toBe(first);
    const edited = result.current.keyFor({ email: "a@example.test", message: "Hello, two children too" });
    expect(edited).not.toBe(first);
    expect(result.current.keyFor({ email: "a@example.test", message: "Hello, two children too" })).toBe(edited);
    result.current.reset();
    expect(result.current.keyFor({ email: "a@example.test", message: "Hello, two children too" })).not.toBe(edited);
  });
});
