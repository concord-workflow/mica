import { editor } from 'monaco-editor';

export const MONACO_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
    minimap: { enabled: false },
    fontFamily: 'Fira Mono',
};

type ColorScheme = 'system' | 'light' | 'dark' | undefined;

export const modeToTheme = (mode: ColorScheme): string => {
    switch (mode) {
        case 'system':
            return 'vs';
        case 'light':
            return 'vs';
        case 'dark':
            return 'vs-dark';
        default:
            return 'vs';
    }
};
