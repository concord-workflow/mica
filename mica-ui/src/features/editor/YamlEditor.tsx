import { MONACO_OPTIONS } from './options.ts';
import { Alert } from '@mui/material';
import { useDebounce } from '@uidotdev/usehooks';
import { editor } from 'monaco-editor';
import { MonacoYaml, configureMonacoYaml } from 'monaco-yaml';

import Editor, { useMonaco } from '@monaco-editor/react';
import React from 'react';
import { ErrorBoundary } from 'react-error-boundary';

window.MonacoEnvironment = {
    async getWorker(_moduleId, label) {
        let module;
        switch (label) {
            case 'editorWorkerService': {
                module = await import('monaco-editor/esm/vs/editor/editor.worker?worker');
                break;
            }
            case 'yaml': {
                module = await import('./yaml.worker.js?worker');
                break;
            }
            default:
                throw new Error(`Unknown label ${label}`);
        }
        return new module.default();
    },
};

const byLineAndColumn = (a: editor.IMarker, b: editor.IMarker) => {
    if (a.startLineNumber === b.startLineNumber) {
        return a.startColumn - b.startColumn;
    }
    return a.startLineNumber - b.startLineNumber;
};

const MAX_MARKERS = 5;

const Markers = ({ markers }: { markers: editor.IMarker[] }) => {
    if (!markers || markers.length === 0) {
        return <></>;
    }

    return (
        <Alert color="warning">
            {markers
                .sort(byLineAndColumn)
                .slice(0, Math.min(MAX_MARKERS, markers.length) - 1)
                .map((marker, idx) => (
                    <div key={idx}>
                        Line: {marker.startLineNumber} Col: {marker.startColumn} &mdash;{' '}
                        {marker.message}
                    </div>
                ))}
            {markers.length >= MAX_MARKERS && <div>...and other warning(s)</div>}
        </Alert>
    );
};

interface Props {
    isLoading: boolean;
    isFetching: boolean;
    isSaving: boolean;
    entityKind: string | undefined;
    value: string;
    onChange: (value: string | undefined) => void;
}

const YamlEditor = ({ isLoading, isFetching, isSaving, entityKind, value, onChange }: Props) => {
    const debouncedEntityKind = useDebounce(entityKind, 500);

    const monaco = useMonaco();
    const monacoYaml = React.useRef<MonacoYaml>();

    const [markers, setMarkers] = React.useState<editor.IMarker[]>([]);

    React.useEffect(() => {
        if (!monaco) {
            return;
        }

        if (!monacoYaml.current) {
            monacoYaml.current = configureMonacoYaml(monaco, { enableSchemaRequest: true });
        }

        let effectiveKind = debouncedEntityKind;
        if (!effectiveKind || effectiveKind === '') {
            return;
        }

        if (effectiveKind[0] != '/') {
            effectiveKind = '/' + effectiveKind;
        }

        const schemas = [
            {
                uri: `${window.location.protocol}//${window.location.host}/api/mica/ui/editorSchema${effectiveKind}`,
                fileMatch: ['*'],
            },
        ];

        monacoYaml.current.update({
            schemas,
        });
    }, [monaco, debouncedEntityKind]);

    return (
        <ErrorBoundary fallback={<b>Something went wrong while trying to render the editor.</b>}>
            <Markers markers={markers} />
            <Editor
                loading={isLoading || isFetching || isSaving}
                height="100%"
                defaultLanguage="yaml"
                options={MONACO_OPTIONS}
                value={value}
                onChange={onChange}
                onValidate={setMarkers}
            />
        </ErrorBoundary>
    );
};

export default YamlEditor;
