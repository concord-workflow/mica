import { ThemeProvider, createTheme } from '@mui/material';

import { PropsWithChildren } from 'react';

const theme = createTheme({
    typography: {
        h5: {
            fontSize: '22px',
        },
    },
});

const WithMicaTheme = ({ children }: PropsWithChildren) => {
    return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

export default WithMicaTheme;
