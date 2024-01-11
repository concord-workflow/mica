import { useDebounce } from '@uidotdev/usehooks';
import { MonacoYaml, SchemasSettings, configureMonacoYaml } from 'monaco-yaml';

import Editor, { useMonaco } from '@monaco-editor/react';
import React, { useEffect } from 'react';
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
    const schemas: SchemasSettings[] = React.useMemo(() => {
        if (!debouncedEntityKind || debouncedEntityKind === '') {
            return [];
        }
        return [
            {
                uri: `${window.location.protocol}//${
                    window.location.host
                }/api/mica/ui/editorSchema?kind=${encodeURIComponent(debouncedEntityKind)}`,
                fileMatch: ['*'],
            },
        ];
    }, [debouncedEntityKind]);

    const monacoYaml = React.useRef<MonacoYaml>();

    // initialize monaco-yaml
    const monaco = useMonaco();
    React.useEffect(() => {
        if (!monaco) {
            return;
        }
        monacoYaml.current = configureMonacoYaml(monaco);
    }, [monaco]);

    // update monaco-yaml config every time schemas change
    useEffect(() => {
        if (!monacoYaml.current) {
            return;
        }
        monacoYaml.current.update({
            enableSchemaRequest: true,
            schemas,
        });
    }, [monacoYaml, schemas]);

    return (
        <ErrorBoundary fallback={<b>Something went wrong while trying to render the editor.</b>}>
            <Editor
                loading={isLoading || isFetching || isSaving}
                height="100%"
                defaultLanguage="yaml"
                options={{
                    minimap: { enabled: false },
                }}
                value={value}
                onChange={onChange}
            />
        </ErrorBoundary>
    );
};

export default YamlEditor;
