import { registerAs } from '@nestjs/config';

/**
 * Bcrypt configuration.
 * 
 * Cost Factor Documentation:
 * - Cost factor determines computational expense of bcrypt hashing
 * - Each increment roughly doubles the hashing time
 * - Range: 4-31 (must be >= 12 for security)
 * 
 * Examples:
 * - Cost 12: ~40-80ms per hash (industry standard, meets OWASP recommendations)
 * - Cost 13: ~80-160ms per hash (higher security)
 * - Cost 14: ~160-320ms per hash (very high security, slower logins)
 * 
 * Why Cost 12+:
 * - Cost < 12: Vulnerable to brute-force attacks
 * - Cost 12: Balances security and performance
 * - Can increase over time as hardware gets faster
 */
export const bcryptConfig = registerAs('bcrypt', () => {
  const costFactor = parseInt(process.env.BCRYPT_COST_FACTOR || '12', 10);

  if (Number.isNaN(costFactor) || costFactor < 12 || costFactor > 31) {
    throw new Error(
      `Invalid BCRYPT_COST_FACTOR: ${process.env.BCRYPT_COST_FACTOR}. Must be between 12 and 31.`,
    );
  }

  return {
    costFactor,
  };
});
