/** @type {import('next').NextConfig} */
const nextConfig = {
  // Produces a self-contained server bundle so the Docker image can be small
  // and does not need node_modules copied into it.
  output: 'standalone',
};

module.exports = nextConfig;
