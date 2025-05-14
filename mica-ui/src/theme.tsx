import { ThemeProvider, createTheme } from '@mui/material';

import { PropsWithChildren } from 'react';

const theme = createTheme({
    colorSchemes: {
        light: true,
        dark: true,
    },
    typography: {
        fontFamily: ['Fira Sans', 'Arial', 'sans-serif'].join(','),
    },
    components: {
        MuiCssBaseline: {
            styleOverrides: {
                pre: {
                    fontFamily: [
                        'Fira Mono',
                        '"Liberation Mono"',
                        '"Courier New"',
                        'monospace',
                    ].join(','),
                },
            },
        },
    },
});

const WithMicaTheme = ({ children }: PropsWithChildren) => {
    return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

export default WithMicaTheme;
