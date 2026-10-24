'use client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { useEffect, useState } from 'react';
import { refreshSession } from '@/lib/api';
import { useAuth } from '@/stores/auth';
import { post } from '@/lib/platform';
export function Providers({ children }: { children: React.ReactNode }) {
  const [client] = useState(() => new QueryClient({ defaultOptions: { queries: { staleTime: 30000, retry: 1 } } }));
  const ownerId = useAuth((s) => s.session?.user.id);
  useEffect(() => { void refreshSession().catch(() => {}); }, []);
  useEffect(() => {
    client.clear();
    if (!ownerId) return;
    const tickets = Object.keys(sessionStorage).filter((key) => key.startsWith('bte-ticket-')).map((key) => sessionStorage.getItem(key));
    void Promise.allSettled(tickets.filter(Boolean).map((ticketCode) => post('/api/v1/me/registrations/claim', { ticketCode }))).then(() => client.invalidateQueries({ queryKey: ['my-entries'] }));
  }, [ownerId, client]);
  return <QueryClientProvider client={client}>{children}</QueryClientProvider>;
}
