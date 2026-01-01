export default function DashboardPage() {
  return (
    <main className="min-h-screen p-6 space-y-6">
      <header className="flex items-center justify-between">
        <div className="text-white/80 text-sm font-mono tracking-[0.2em]">
          IRON WILL
        </div>
        <div className="flex items-center gap-2">
          <span className="w-2 h-2 rounded-full bg-primary animate-pulse" />
          <span className="text-xs font-mono text-primary uppercase tracking-widest">
            online
          </span>
        </div>
      </header>
      <section className="bg-black/40 border border-white/10 rounded-xl p-4">
        <div className="text-xs text-white/60 uppercase tracking-[0.2em]">
          Accountability Level
        </div>
        <div className="mt-2 h-14 bg-white/5 rounded-full overflow-hidden border border-white/10 relative">
          <div className="h-full bg-primary w-3/4 shadow-[0_0_20px_rgba(64,243,32,0.5)]" />
          <div className="absolute inset-0 flex items-center justify-center text-3xl font-bold text-black mix-blend-screen">
            8.5
          </div>
        </div>
      </section>
      <section className="grid gap-4">
        <div className="bg-black/40 border border-white/10 rounded-lg p-4">
          <div className="flex justify-between items-center">
            <div>
              <div className="text-sm font-bold">Deep Sleep Protocol</div>
              <div className="text-[11px] text-white/50">Due 11:00 PM</div>
            </div>
            <span className="text-xs px-2 py-1 rounded-full border border-yellow-400/50 text-yellow-200">
              PENDING
            </span>
          </div>
          <button className="mt-4 w-full h-12 rounded-full border border-white/10 bg-white/5 hover:border-primary transition-all">
            Upload Proof
          </button>
        </div>
      </section>
    </main>
  );
}

