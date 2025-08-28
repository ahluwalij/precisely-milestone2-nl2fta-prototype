import { } from '@angular/core/testing';

/**
 * Helper function to handle async operations in tests with proper timeout
 */
export function asyncTest(fn: () => Promise<unknown>): (done: DoneFn) => void {
  return (done: DoneFn) => {
    fn()
      .then(() => done())
      .catch((err) => done.fail(err));
  };
}

/**
 * Helper to flush all pending async operations
 */
export function flushMicrotasks(): void {
  // Flush all pending promises
  return new Promise<void>((resolve) => {
    setTimeout(() => resolve(), 0);
  }) as void;
}

/**
 * Default test timeout for async operations
 */
export const DEFAULT_ASYNC_TIMEOUT = 10000;

/**
 * Configure Jasmine default timeout
 */
export function configureTestTimeout(timeout: number = DEFAULT_ASYNC_TIMEOUT): void {
  jasmine.DEFAULT_TIMEOUT_INTERVAL = timeout;
}