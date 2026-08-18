import './globals.css';

export const metadata = {
  title: 'SwiftLogistics Portal',
  description: 'Submit a delivery order and follow it through the middleware',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
