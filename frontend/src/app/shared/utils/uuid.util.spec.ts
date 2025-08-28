import { generateId, isValidId } from './uuid.util';

describe('UUID Utilities', () => {
  describe('generateId', () => {
    it('should generate a non-empty string with correct format', () => {
      const id = generateId();
      expect(id).toBeTruthy();
      expect(typeof id).toBe('string');
      
      const parts = id.split('_');
      expect(parts.length).toBe(2);
      expect(parts[0]).toMatch(/^\d+$/);
      expect(parts[1]).toMatch(/^[a-z0-9]+$/);
    });

    it('should generate unique IDs', () => {
      const id1 = generateId();
      const id2 = generateId();
      expect(id1).not.toBe(id2);
    });

    it('should generate valid IDs according to isValidId', () => {
      const id = generateId();
      expect(isValidId(id)).toBe(true);
    });
  });

  describe('isValidId', () => {
    it('should return true for valid format', () => {
      expect(isValidId('1234567890_abc123d')).toBe(true);
      expect(isValidId('0_a')).toBe(true);
    });

    it('should return false for invalid formats', () => {
      expect(isValidId(null as unknown as string)).toBe(false);
      expect(isValidId('')).toBe(false);
      expect(isValidId('no_underscore')).toBe(false);
      expect(isValidId('123_abc_def')).toBe(false);
      expect(isValidId('abc_def123')).toBe(false);
      expect(isValidId('123_ABC123')).toBe(false);
    });
  });
});