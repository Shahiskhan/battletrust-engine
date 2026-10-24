import type { Metadata } from 'next';
import { Providers } from '@/components/providers';
import './globals.css';
import './platform.css';
import './staking.css';
import './discovery.css';
export const metadata: Metadata = { title: 'BattleTrust — Play hard. Win with trust.', description: 'Your competitive arena. Discover esports tournaments and compete with escrow-protected prize pools.' };
export default function RootLayout({ children }: Readonly<{ children: React.ReactNode }>) {
  return <html lang="en"><body><Providers>{children}</Providers></body></html>;
}
