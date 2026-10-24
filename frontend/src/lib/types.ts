export type User = { id: number; name: string; email: string; role: string; profilePictureUrl: string | null };
export type AuthResponse = { token: string; tokenType: string; expiresIn: number; role: string; user: User };
export type Page<T> = { content: T[]; totalElements: number; totalPages: number };
export type LeaderboardEntry = { rank: number; userId: number; name: string; profilePictureUrl: string | null; gameCategory: string; matchesPlayed: number; matchesWon: number; totalKills: number; reputationScore: number; winRatePercent: number };
