import { defineConfig } from 'vite';

import react from '@vitejs/plugin-react';

// https://vitejs.dev/config/
export default defineConfig({
    base: '/mica',
    build: {
        outDir: 'target/classes/META-INF/mica-ui',
        rollupOptions: {
            output: {
                manualChunks: {
                    '@mui/material': ['@mui/material'],
                    'swagger-ui-react': ['swagger-ui-react'],
                    lodash: ['lodash'],
                    yaml: ['yaml'],
                },
            },
        },
    },
    plugins: [react()],
    server: {
        proxy: {
            '/api': {
                target: 'http://localhost:8080',
                changeOrigin: false,
            },
        },
    },
});
