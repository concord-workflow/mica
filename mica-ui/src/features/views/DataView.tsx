import { MONACO_OPTIONS, modeToTheme } from '../editor/options.ts';
import { useColorScheme } from '@mui/material';
import { editor } from 'monaco-editor';

import Editor from '@monaco-editor/react';
import React from 'react';

interface Props {
    data: unknown;
}

const VIEW_OPTIONS: editor.IStandaloneEditorConstructionOptions = {
    ...MONACO_OPTIONS,
    renderValidationDecorations: 'off',
    readOnly: true,
    domReadOnly: true,
    lineNumbers: 'off',
};

const DataView = ({ data }: Props) => {
    const { mode, systemMode } = useColorScheme();
    const value = React.useMemo(() => JSON.stringify(data, null, 2), [data]);
    return (
        <Editor
            width="100%"
            height="100%"
            defaultLanguage="yaml"
            options={VIEW_OPTIONS}
            theme={modeToTheme(mode === 'system' ? systemMode : mode)}
            value={value}
        />
    );
};

export default DataView;
