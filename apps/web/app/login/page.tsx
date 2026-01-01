export default function LoginPage() {
  return (
    <main className="min-h-screen flex items-center justify-center px-6">
      <div className="max-w-md w-full space-y-6 text-center">
        <h1 className="text-4xl font-bold tracking-[0.3em]">IRON WILL</h1>
        <p className="text-xs text-white/60 uppercase tracking-[0.2em]">
          Protocol Access v2.0
        </p>
        <div className="space-y-3">
          <button className="w-full h-12 rounded-full border border-white/10 bg-white/5 hover:border-primary transition-all">
            Sign in with Google
          </button>
          <button className="w-full h-12 rounded-full border border-white/10 bg-white/5 hover:border-primary transition-all">
            Sign in with Email
          </button>
        </div>
        <p className="text-[10px] text-primary font-mono tracking-[0.2em]">
          SYSTEM STATUS: ONLINE_
        </p>
      </div>
    </main>
  );
}

