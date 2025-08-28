/**
 * Generates a UUID-like string for use as unique identifiers
 * This is a simple implementation for prototype purposes
 */
export function generateId(): string {
  const timestamp = Date.now().toString();
  const randomPart = Math.random().toString(36).substring(2, 9);
  return `${timestamp}_${randomPart}`;
}

/**
 * Validates if a string is a valid ID format
 */
export function isValidId(id: string): boolean {
  if (!id || typeof id !== 'string') {
    return false;
  }

  // Check if it matches the pattern: timestamp_randomstring
  const parts = id.split('_');
  if (parts.length !== 2) {
    return false;
  }

  const [timestamp, randomPart] = parts;

  // Validate timestamp part (should be a number)
  if (!/^\d+$/.test(timestamp)) {
    return false;
  }

  // Validate random part (should be alphanumeric)
  if (!/^[a-z0-9]+$/.test(randomPart)) {
    return false;
  }

  return true;
}
