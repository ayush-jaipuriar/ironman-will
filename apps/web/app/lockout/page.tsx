export default function LockoutPage() {
  return (
    <main className="min-h-screen flex flex-col items-center justify-center bg-[#150000] text-primary px-6">
      <h1 className="text-4xl font-black tracking-widest">PROTOCOL FAILED</h1>
      <p className="text-sm mt-2 text-primary/70">
        Accountability score critical. Access revoked.
      </p>
      <div className="mt-6 text-5xl font-mono tracking-tight text-primary">
        23:59:42
      </div>
      <p className="mt-2 text-[11px] text-primary/60 uppercase">
        countdown to unlock
      </p>
    </main>
  );
}

