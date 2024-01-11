import { ThemeProvider, createTheme } from '@mui/material';

import { PropsWithChildren } from 'react';

const theme = createTheme();

const WithMicaTheme = ({ children }: PropsWithChildren) => {
    return <ThemeProvider theme={theme}>{children}</ThemeProvider>;
};

export default WithMicaTheme;
