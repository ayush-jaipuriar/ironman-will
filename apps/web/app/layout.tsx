import "./globals.css";
import { ReactNode } from "react";

export const metadata = {
  title: "Iron Will",
  description: "Ruthless accountability HUD",
};

export default function RootLayout({ children }: { children: ReactNode }) {
  return (
    <html lang="en" className="dark">
      <body className="bg-background-dark text-white antialiased">
        {children}
      </body>
    </html>
  );
}

