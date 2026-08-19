import './globals.css';

export const metadata = {
  title: 'SwiftTrack Portal',
  description: 'Submit delivery orders and watch them move through the SwiftLogistics middleware',
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
