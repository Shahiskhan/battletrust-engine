import { create } from 'zustand';
import type { AuthResponse } from '@/lib/types';
// Access tokens stay in memory. The backend owns the HttpOnly refresh cookie.
export const useAuth = create<{ session: AuthResponse | null; initialized: boolean; setSession: (session: AuthResponse | null) => void }>((set) => ({ session: null, initialized: false, setSession: (session) => set({ session, initialized: true }) }));
